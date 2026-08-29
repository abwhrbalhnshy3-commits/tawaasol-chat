package io.tawaasol

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    DatabaseFactory.init()
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        route("/api") {
            post("/register") {
                val req = call.receive<RegisterRequest>()
                val id = DatabaseFactory.createUser(req.username, req.phone)
                call.respond(mapOf("userId" to id))
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
                    call.respond(mapOf("messageId" to mid))
                } else call.respond(mapOf("error" to "invalid conversation id"))
            }
        }
    }
}

@Serializable
data class RegisterRequest(val username: String, val phone: String)

@Serializable
data class CreateConversationRequest(val title: String)

@Serializable
data class SendMessageRequest(val senderId: Int, val content: String)

@Serializable
data class MessageDto(val id: Int, val conversationId: Int, val senderId: Int, val content: String, val timestamp: String)

object DatabaseFactory {
    private lateinit var db: Database

    fun init() {
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("JDBC_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/tawaasol"
            username = System.getenv("POSTGRES_USER") ?: "tawaasol"
            password = System.getenv("POSTGRES_PASSWORD") ?: "password"
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 3
        }
        val ds = HikariDataSource(config)
        db = Database.connect(ds)
        transaction(db) {
            create(Users, Conversations, ConversationMembers, Messages)
        }
    }

    fun createUser(username: String, phone: String): Int {
        return transaction(db) {
            Users.insertAndGetId {
                it[Users.username] = username
                it[Users.phone] = phone
            }.value
        }
    }

    fun createConversation(title: String): Int {
        return transaction(db) {
            Conversations.insertAndGetId {
                it[Conversations.title] = title
            }.value
        }
    }

    fun listConversations(): List<Map<String, Any>> {
        return transaction(db) {
            Conversations.selectAll().map { row ->
                mapOf(
                    "id" to row[Conversations.id],
                    "title" to row[Conversations.title]
                )
            }
        }
    }

    fun createMessage(conversationId: Int, senderId: Int, content: String): Int {
        return transaction(db) {
            Messages.insertAndGetId {
                it[Messages.conversationId] = conversationId
                it[Messages.senderId] = senderId
                it[Messages.content] = content
            }.value
        }
    }

    fun getMessages(conversationId: Int): List<MessageDto> {
        return transaction(db) {
            Messages.select { Messages.conversationId eq conversationId }
                .orderBy(Messages.timestamp to SortOrder.ASC)
                .map { row ->
                    MessageDto(
                        id = row[Messages.id],
                        conversationId = row[Messages.conversationId],
                        senderId = row[Messages.senderId],
                        content = row[Messages.content],
                        timestamp = row[Messages.timestamp].toString()
                    )
                }
        }
    }
}

object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 100)
    val phone = varchar("phone", 20)
    override val primaryKey = PrimaryKey(id)
}

object Conversations : Table() {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 200)
    override val primaryKey = PrimaryKey(id)
}

object ConversationMembers : Table() {
    val id = integer("id").autoIncrement()
    val conversationId = integer("conversation_id").references(Conversations.id)
    val userId = integer("user_id").references(Users.id)
    override val primaryKey = PrimaryKey(id)
}

object Messages : Table() {
    val id = integer("id").autoIncrement()
    val conversationId = integer("conversation_id").references(Conversations.id)
    val senderId = integer("sender_id").references(Users.id)
    val content = text("content")
    val timestamp = datetime("timestamp").clientDefault { org.joda.time.DateTime.now() }
    override val primaryKey = PrimaryKey(id)
}
