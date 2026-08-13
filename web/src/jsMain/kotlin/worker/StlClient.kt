package polyhedra.web.worker

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import polyhedra.model.api.CoreJson
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.api.CoreStlResponse

private const val PROGRESS = "progress"
private const val SUCCESS = "success"
private const val FAILURE = "failure"

@Serializable
private data class StlWorkerRequest(
    val id: Int,
    val kind: String = "stl",
    val requestJson: String,
)

@Serializable
private data class StlWorkerMessage(
    val id: Int,
    val type: String,
    val done: Int? = null,
    val responseJson: String? = null,
    val error: String? = null,
)

private var lastStlRequestId = 0

/** Converts and validates one presentation mesh in an isolated Wasm worker. */
fun convertStlInWasm(
    request: CoreStlRequest,
    reportProgress: (Int) -> Unit,
    onSuccess: (CoreStlResponse) -> Unit,
    onFailure: (Throwable) -> Unit,
): () -> Unit {
    val requestId = ++lastStlRequestId
    val worker = Worker(CORE_WORKER_URL)
    var active = true

    fun finish(block: () -> Unit) {
        if (!active) return
        active = false
        worker.terminate()
        block()
    }

    worker.onmessage = { event: MessageEvent ->
        if (active) {
            val message = runCatching {
                CoreJson.decodeFromString<StlWorkerMessage>(event.data as String)
            }.getOrElse { cause ->
                finish { onFailure(IllegalStateException("Invalid Wasm STL worker response", cause)) }
                null
            }
            if (message != null && message.id == requestId) when (message.type) {
                PROGRESS -> runCatching {
                    reportProgress(requireNotNull(message.done) { "Missing STL progress value" })
                }.onFailure { cause -> finish { onFailure(cause) } }

                SUCCESS -> {
                    val response = runCatching {
                        CoreJson.decodeFromString<CoreStlResponse>(
                            requireNotNull(message.responseJson) { "Missing STL response" },
                        )
                    }.getOrElse { cause ->
                        finish { onFailure(IllegalStateException("Invalid Wasm STL response", cause)) }
                        null
                    }
                    if (response != null) finish { onSuccess(response) }
                }

                FAILURE -> finish {
                    onFailure(IllegalStateException(message.error ?: "Wasm STL request failed"))
                }

                else -> finish {
                    onFailure(IllegalStateException("Unknown Wasm STL worker message: ${message.type}"))
                }
            }
        }
    }
    worker.onerror = { event ->
        finish { onFailure(IllegalStateException("Wasm STL worker failed: ${event.type}")) }
    }
    runCatching {
        val message = StlWorkerRequest(requestId, requestJson = CoreJson.encodeToString(request))
        worker.postMessage(CoreJson.encodeToString(message))
    }.onFailure { cause -> finish { onFailure(cause) } }

    return { finish {} }
}
