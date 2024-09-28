import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class RealClient {
    private val host = "localhost"
    private val port = 80
    private val url = "http://$host:$port"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            gson()
        }
        install(WebSockets) {
            contentConverter = GsonWebsocketContentConverter()
        }
    }

    suspend fun users(): List<User> = client.get("$url/users").body()

    suspend fun delete(id: Long) = client.delete("$url/user/$id").status

    suspend fun insert(name: String) =
        client.submitForm("$url/add", parameters {
            append("name", name)
        }).status

    suspend fun update(user: User) =
        client.post("$url/user") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }.status

    fun flow() = flow {
        client.webSocket(HttpMethod.Get, host, port, path = "user") {
            while (coroutineContext.isActive) {
                runCatching {
                    receiveDeserialized<DataAction<User>>()
                }.onSuccess {
                    emit(it)
                }
            }
        }
    }
}