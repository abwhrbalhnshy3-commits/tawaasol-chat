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
import okhttp3.*
import io.minio.MinioClient
import io.minio.PutObjectArgs
import java.util.UUID
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
    val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
    val redisPort = (System.getenv("REDIS_PORT") ?: "6379").toInt()
    val jedis = Jedis(redisHost, redisPort)

    // Typing throttle map: track last typing event per (conversationId, userId)
    val typingLastSent = ConcurrentHashMap<Pair<Int, Int>, Long>()
    val TYPING_THROTTLE_MS = 500L

    // MinIO client init (optional)
    val minioEndpoint = System.getenv("MINIO_ENDPOINT") ?: System.getenv("MINIO_HOST") ?: "http://localhost:9000"
    val minioAccess = System.getenv("MINIO_ROOT_USER") ?: System.getenv("MINIO_ACCESS_KEY") ?: "minioadmin"
    val minioSecret = System.getenv("MINIO_ROOT_PASSWORD") ?: System.getenv("MINIO_SECRET_KEY") ?: "minioadmin"
    val minioBucket = System.getenv("MINIO_BUCKET") ?: "tawaasol"
    val minioClient = MinioClient.builder().endpoint(minioEndpoint).credentials(minioAccess, minioSecret).build()
    try {
        if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(minioBucket).build())) {
            minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(minioBucket).build())
        }
    } catch (e: Exception) {
        println("MinIO init error: ${e.message}")
    }

    // create MinioService instance for presign endpoints (if present)
    val minioService = MinioService(minioClient, minioBucket)

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        route("/api") {
            post("/auth/request-otp") {
                val req = call.receive<RequestOtpRequest>()
                // rate limit: allow max 5 requests per 10 minutes per phone
                val rateKey = "otp:rate:${req.phone}"
                val attempts = jedis.incr(rateKey)
                if (attempts == 1L) jedis.expire(rateKey, 600)
                if (attempts > 5) {
                    call.respond(mapOf("error" to "rate_limited"))
                    return@post
                }

                val otp = (100000..999999).random().toString()
                jedis.setex("otp:${req.phone}", 300, otp)

                // If Twilio credentials are configured, send SMS via Twilio API
                val twilioSid = System.getenv("TWILIO_ACCOUNT_SID")
                val twilioAuth = System.getenv("TWILIO_AUTH_TOKEN")
                val twilioFrom = System.getenv("TWILIO_FROM")
                if (twilioSid != null && twilioAuth != null && twilioFrom != null) {
                    try {
                        val client = OkHttpClient()
                        val url = "https://api.twilio.com/2010-04-01/Accounts/$twilioSid/Messages.json"
                        val body = FormBody.Builder()
                            .add("From", twilioFrom)
                            .add("To", req.phone)
                            .add("Body", "تواصل شات - رمز التحقق: $otp")
                            .build()
                        val credential = okhttp3.Credentials.basic(twilioSid, twilioAuth)
                        val request = Request.Builder().url(url).post(body).header("Authorization", credential).build()
                        client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) println("Twilio send failed: ${resp.code}")
                        }
                    } catch (e: Exception) {
                        println("Twilio error: ${e.message}")
                    }
                } else {
                    // for demo only: print OTP in server logs
                    println("DEBUG OTP for ${req.phone} = $otp")
                }

                call.respond(mapOf("ok" to true))
            }

            post("/auth/verify-otp") {
                val req = call.receive<VerifyOtpRequest>()
                val stored = jedis.get("otp:${req.phone}")
                if (stored == null || stored != req.otp) {
                    call.respond(mapOf("error" to "invalid_otp"))
                    return@post
                }
                val userId = DatabaseFactory.findOrCreateUser(req.name ?: "", req.phone)
                val secret = System.getenv("JWT_SECRET") ?: "changeme"
                val token = JWT.create()
                    .withClaim("userId", userId)
                    .withClaim("phone", req.phone)
                    .sign(Algorithm.HMAC256(secret))
                // create refresh token and store in redis with TTL 30 days
                val refreshToken = UUID.randomUUID().toString()
                jedis.setex("refresh:$refreshToken", 60 * 60 * 24 * 30, userId.toString())
                call.respond(mapOf("token" to token, "refreshToken" to refreshToken, "userId" to userId))
            }

            post("/auth/refresh") {
                val body = call.receive<Map<String, String>>()
                val rtoken = body["refreshToken"]
                if (rtoken == null) { call.respond(mapOf("error" to "missing")); return@post }
                val uid = jedis.get("refresh:$rtoken")?.toIntOrNull()
                if (uid == null) { call.respond(mapOf("error" to "invalid_refresh")); return@post }
                val secret = System.getenv("JWT_SECRET") ?: "changeme"
                val newToken = JWT.create().withClaim("userId", uid).sign(Algorithm.HMAC256(secret))
                call.respond(mapOf("token" to newToken))
            }

            // file upload to MinIO or local
            post("/upload") {
                val mp = call.receiveMultipart()
                var savedUrl: String? = null
                mp.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val name = part.originalFileName ?: "file"
                        val temp = File.createTempFile("upload-", name)
                        part.streamProvider().use { its ->
                            temp.outputStream().buffered().use { fos -> its.copyTo(fos) }
                        }
                        try {
                            // upload to MinIO
                            val objectName = "uploads/${System.currentTimeMillis()}_${UUID.randomUUID()}_$name"
                            minioClient.putObject(
                                PutObjectArgs.builder().bucket(minioBucket).`object`(objectName).stream(temp.inputStream(), temp.length(), -1).contentType("application/octet-stream").build()
                            )
                            // generate a presigned URL (simple public path for demo)
                            savedUrl = "/minio/$objectName"
                        } catch (e: Exception) {
                            println("MinIO upload failed: ${e.message}")
                            // fallback to local
                            val uploadDir = File("uploads")
                            if (!uploadDir.exists()) uploadDir.mkdirs()
                            val file = File(uploadDir, temp.name)
                            temp.copyTo(file, overwrite = true)
                            savedUrl = "/uploads/${file.name}"
                        }
                    }
                    part.dispose()
                }
                call.respond(mapOf("url" to (savedUrl ?: "")))
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
                    val msgJson = Json.encodeToString(mapOf("type" to "message", "conversationId" to id, "id" to mid, "senderId" to payload.senderId, "content" to payload.content))
                    jedis.publish("conversation:$id", msgJson)
                    call.respond(mapOf("messageId" to mid))
                } else call.respond(mapOf("error" to "invalid conversation id"))
            }
        }

        // register minio presign routes
        minioRoutes(minioService)

        // serve uploaded files (local) and provide simple route for minio proxy (demo only)
        static("/uploads") {
            staticRootFolder = File("uploads")
        }

        // WebSocket endpoint
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (token == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "missing token"))
                return@webSocket
            }
            try {
                val secret = System.getenv("JWT_SECRET") ?: "changeme"
                val verifier = com.auth0.jwt.JWT.require(Algorithm.HMAC256(secret)).build()
                val decoded = verifier.verify(token)
                val userId = decoded.getClaim("userId").asInt()

                // Jedis subscriber per connection
                val jedisSub = Jedis(redisHost, redisPort)
                val listener = object : redis.clients.jedis.JedisPubSub() {
                    override fun onMessage(channel: String, message: String) {
                        try { outgoing.trySend(Frame.Text(message)) } catch (e: Exception) {}
                    }
                }

                // track joined conversations for this socket to unsubscribe later
                val joined = mutableSetOf<Int>()

                // spawn thread for subscription processing (we will manage channels by subscribing/unsubscribing explicitly)
                val subThread = Thread {
                    try { jedisSub.subscribe(listener) } catch (e: Exception) { println("Redis subscribe error: ${e.message}") }
                }
                subThread.start()

                // read incoming frames
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val map = Json.parseToJsonElement(text).jsonObject
                        val type = map["type"]?.jsonPrimitive?.content
                        when (type) {
                            "join" -> {
                                val convId = map["conversationId"]?.jsonPrimitive?.int ?: continue
                                if (!joined.contains(convId)) {
                                    try {
                                        jedisSub.subscribe(object : redis.clients.jedis.JedisPubSub() {}, "conversation:$convId")
                                    } catch (e: Exception) { println("subscribe error: ${e.message}") }
                                    joined.add(convId)
                                    // notify client joined
                                    val evt = Json.encodeToString(mapOf("type" to "joined", "conversationId" to convId))
                                    outgoing.trySend(Frame.Text(evt))
                                }
                            }
                            "leave" -> {
                                val convId = map["conversationId"]?.jsonPrimitive?.int ?: continue
                                if (joined.remove(convId)) {
                                    val evt = Json.encodeToString(mapOf("type" to "left", "conversationId" to convId))
                                    outgoing.trySend(Frame.Text(evt))
                                }
                            }
                            "message" -> {
                                val convId = map["conversationId"]?.jsonPrimitive?.int ?: continue
                                val content = map["content"]?.jsonPrimitive?.content ?: continue
                                val mid = DatabaseFactory.createMessage(convId, userId, content)
                                val msgJson = Json.encodeToString(mapOf("type" to "message", "conversationId" to convId, "id" to mid, "senderId" to userId, "content" to content))
                                jedis.publish("conversation:$convId", msgJson)
                                // send ack back to sender
                                val ack = Json.encodeToString(mapOf("type" to "ack", "messageId" to mid))
                                outgoing.trySend(Frame.Text(ack))
                            }
                            "typing" -> {
                                val convId = map["conversationId"]?.jsonPrimitive?.int ?: continue
                                val state = map["state"]?.jsonPrimitive?.content ?: continue

                                // validate participant
                                if (!DatabaseFactory.validateParticipant(convId, userId)) continue

                                val key = Pair(convId, userId)
                                val now = System.currentTimeMillis()
                                val last = typingLastSent[key] ?: 0L
                                if (state == "start") {
                                    if (now - last < TYPING_THROTTLE_MS) continue
                                    typingLastSent[key] = now
                                } else {
                                    typingLastSent.remove(key)
                                }

                                val typingEvent = Json.encodeToString(mapOf("type" to "typing", "conversationId" to convId, "userId" to userId, "state" to state))
                                jedis.publish("conversation:$convId", typingEvent)
                            }
                        }
                    }
                }

                // cleanup
                try { listener.unsubscribe() } catch (_: Exception) {}
                try { jedisSub.close() } catch (_: Exception) {}
                subThread.interrupt()
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "invalid token"))
                return@webSocket
            }
        }
    }
}
