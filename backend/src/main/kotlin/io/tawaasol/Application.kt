package io.tawaasol

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import redis.clients.jedis.Jedis
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.http.cio.websocket.*
import io.ktor.server.plugins.cors.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(WebSockets) { pingPeriod = Duration.ofSeconds(15) }
    install(Authentication) {
        jwt("jwt") {
            val secret = System.getenv("JWT_SECRET") ?: "changeme"
            realm = "tawaasol"
            verifier(com.auth0.jwt.JWT.require(Algorithm.HMAC256(secret)).build())
            validate { credential -> if (credential.payload.getClaim("userId").asInt() != null) JWTPrincipal(credential.payload) else null }
        }
    }

    DatabaseFactory.init()
    val jedis = Jedis(System.getenv("REDIS_HOST") ?: "localhost", (System.getenv("REDIS_PORT") ?: "6379").toInt())

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        route("/api") {
            post("/auth/request-otp") {
                val req = call.receive<RequestOtpRequest>()
                // In real system generate OTP and send via SMS provider (Twilio)
                val otp = (100000..999999).random().toString()
                // store OTP in redis with short TTL
                jedis.setex("otp:${req.phone}", 300, otp)
                println("DEBUG OTP for ${req.phone} = $otp")
                call.respond(mapOf("ok" to true))
            }

            post("/auth/verify-otp") {
                val req = call.receive<VerifyOtpRequest>()
                val stored = jedis.get("otp:${req.phone}")
                if (stored == null || stored != req.otp) {
                    call.respond(mapOf("error" to "invalid_otp"))
                    return@post
                }
                // create or find user
                val userId = DatabaseFactory.findOrCreateUser(req.name ?: "", req.phone)
                // issue JWT
                val secret = System.getenv("JWT_SECRET") ?: "changeme"
                val token = JWT.create()
                    .withClaim("userId", userId)
                    .withClaim("phone", req.phone)
                    .sign(Algorithm.HMAC256(secret))
                call.respond(mapOf("token" to token, "userId" to userId))
            }

            // file upload to MinIO (multipart/form-data)
            post("/upload") {
                val mp = call.receiveMultipart()
                // Very small example: store to ./uploads and return URL
                val uploadDir = java.io.File("uploads")
                if (!uploadDir.exists()) uploadDir.mkdirs()
                var savedPath: String? = null
                mp.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName ?: "file"
                        val file = java.io.File(uploadDir, System.currentTimeMillis().toString() + "_" + name)
                        part.streamProvider().use { its ->
                            file.outputStream().buffered().use { fos -> its.copyTo(fos) }
                        }
                        savedPath = "/uploads/${file.name}"
                    }
                    part.dispose()
                }
                call.respond(mapOf("url" to (savedPath ?: "")))
            }

            authenticate("jwt") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val uid = principal.payload.getClaim("userId").asInt()
                    call.respond(mapOf("userId" to uid))
                }
            }

            get("/conversations") {
                val convs = DatabaseFactory.listConversations()
                call.respond(convs)
            }

            post("/conversations") {
                val req = call.receive<CreateConversationRequest>()
                val id = DatabaseFactory.createConversation(req.title)
                call.respond(mapOf("conversationId" to id))
            }

            get("/conversations/{id}/messages") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) call.respond(emptyList<MessageDto>())
                else {
                    val msgs = DatabaseFactory.getMessages(id)
                    call.respond(msgs)
                }
            }

            post("/conversations/{id}/messages") {
                val id = call.parameters["id"]?.toIntOrNull()
                val payload = call.receive<SendMessageRequest>()
                if (id != null) {
                    val mid = DatabaseFactory.createMessage(id, payload.senderId, payload.content)
                    // publish to redis channel for real-time delivery
                    val msgJson = Json.encodeToString(mapOf("type" to "message", "conversationId" to id, "id" to mid, "senderId" to payload.senderId, "content" to payload.content))
                    jedis.publish("conversation:$id", msgJson)
                    call.respond(mapOf("messageId" to mid))
                } else call.respond(mapOf("error" to "invalid conversation id"))
            }
        }

        // WebSocket endpoint for real-time messages (clients must provide token as query param)
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "missing token"))
                return@webSocket
            }
            // verify token
            try {
                val secret = System.getenv("JWT_SECRET") ?: "changeme"
                val verifier = com.auth0.jwt.JWT.require(Algorithm.HMAC256(secret)).build()
                val decoded = verifier.verify(token)
                val userId = decoded.getClaim("userId").asInt()
                // subscribe to Redis pubsub for channels of conversations this user will join
                val jedisSub = Jedis(System.getenv("REDIS_HOST") ?: "localhost", (System.getenv("REDIS_PORT") ?: "6379").toInt())
                val listener = object : redis.clients.jedis.JedisPubSub() {
                    override fun onMessage(channel: String, message: String) {
                        // send message down websocket
                        try {
                            outgoing.trySend(Frame.Text(message))
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                // run subscription in background thread
                val subThread = Thread {
                    try {
                        jedisSub.subscribe(listener)
                    } catch (e: Exception) {
                        println("Redis subscribe stopped: ${e.message}")
                    }
                }
                subThread.start()

                // receive messages from client
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // expected protocol: {"type":"join","conversationId":1} or {"type":"message","conversationId":1,"content":"hi"}
                        val map = Json.parseToJsonElement(text).jsonObject
                        val type = map["type"]?.jsonPrimitive?.content
                        if (type == "join") {
                            val convId = map["conversationId"]?.jsonPrimitive?.int ?: continue
                            // subscribe to channel
                            jedisSub.subscribe(object : redis.clients.jedis.JedisPubSub() {}, "conversation:$convId")
                        } else if (type == "message") {
                            val convId = map["conversationId"]?.jsonPrimitive?.int ?: continue
                            val content = map["content"]?.jsonPrimitive?.content ?: continue
                            // persist message
                            val mid = DatabaseFactory.createMessage(convId, userId, content)
                            val msgJson = Json.encodeToString(mapOf("type" to "message", "conversationId" to convId, "id" to mid, "senderId" to userId, "content" to content))
                            jedis.publish("conversation:$convId", msgJson)
                        }
                    }
                }
                // cleanup
                listener.unsubscribe()
                jedisSub.close()
                subThread.interrupt()
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "invalid token"))
                return@webSocket
            }
        }
    }
}

@Serializable
data class RequestOtpRequest(val phone: String)

@Serializable
data class VerifyOtpRequest(val phone: String, val otp: String, val name: String? = null)

@Serializable
data class CreateConversationRequest(val title: String)

@Serializable
data class SendMessageRequest(val senderId: Int, val content: String)

@Serializable
data class MessageDto(val id: Int, val conversationId: Int, val senderId: Int, val content: String, val timestamp: String)
