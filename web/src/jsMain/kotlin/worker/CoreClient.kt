package polyhedra.web.worker

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import polyhedra.model.api.CoreJson
import polyhedra.model.api.CoreProgress
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreResponse

private const val CORE_WORKER_URL = "./core-worker.js"
private const val PROGRESS = "progress"
private const val SUCCESS = "success"
private const val FAILURE = "failure"

@Serializable
private data class WorkerRequest(
    val id: Int,
    val requestJson: String,
)

@Serializable
private data class WorkerMessage(
    val id: Int,
    val type: String,
    val transformIndex: Int? = null,
    val done: Int? = null,
    val responseJson: String? = null,
    val error: String? = null,
)

private data class ActiveRequest(
    val id: Int,
    val reportProgress: (CoreProgress) -> Unit,
    val onSuccess: (CoreResponse) -> Unit,
    val onFailure: (Throwable) -> Unit,
)

private var worker: Worker? = null
private var activeRequest: ActiveRequest? = null
private var lastRequestId = 0

/**
 * Evaluates one request in a dedicated worker whose manipulation engine is the WasmGC core.
 *
 * Starting another request cancels the current computation by terminating its worker. The
 * returned callback provides the same cancellation behavior to the owning UI component.
 */
fun evaluateInWasm(
    request: CoreRequest,
    reportProgress: (CoreProgress) -> Unit,
    onSuccess: (CoreResponse) -> Unit,
    onFailure: (Throwable) -> Unit,
): () -> Unit {
    cancelActiveRequest()

    val requestId = ++lastRequestId
    activeRequest = ActiveRequest(requestId, reportProgress, onSuccess, onFailure)
    val target = getOrCreateWorker()
    val message = WorkerRequest(requestId, CoreJson.encodeToString(request))
    runCatching { target.postMessage(CoreJson.encodeToString(message)) }
        .onFailure(::failWorker)

    return cancel@{
        if (activeRequest?.id != requestId) return@cancel
        cancelActiveRequest()
    }
}

private fun getOrCreateWorker(): Worker {
    worker?.let { return it }
    return Worker(CORE_WORKER_URL).also { created ->
        worker = created
        created.onmessage = { event -> onWorkerMessage(created, event) }
        created.onerror = { event ->
            if (worker === created) {
                failWorker(IllegalStateException("Wasm core worker failed: ${event.type}"))
            }
        }
    }
}

private fun onWorkerMessage(source: Worker, event: MessageEvent) {
    if (worker !== source) return
    val message = runCatching {
        CoreJson.decodeFromString<WorkerMessage>(event.data as String)
    }.getOrElse { cause ->
        failWorker(IllegalStateException("Invalid Wasm core worker response", cause))
        return
    }
    val active = activeRequest ?: return
    if (message.id != active.id) return

    when (message.type) {
        PROGRESS -> runCatching {
            active.reportProgress(
                CoreProgress(
                    transformIndex = requireNotNull(message.transformIndex) { "Missing progress transform index" },
                    done = requireNotNull(message.done) { "Missing progress value" },
                )
            )
        }.onFailure(::failWorker)

        SUCCESS -> {
            val response = runCatching {
                CoreJson.decodeFromString<CoreResponse>(
                    requireNotNull(message.responseJson) { "Missing core response" }
                )
            }.getOrElse { cause ->
                failWorker(IllegalStateException("Invalid Wasm core response", cause))
                return
            }
            activeRequest = null
            runCatching { active.onSuccess(response) }.onFailure(active.onFailure)
        }

        FAILURE -> failWorker(IllegalStateException(message.error ?: "Wasm core request failed"))
        else -> failWorker(IllegalStateException("Unknown Wasm core worker message: ${message.type}"))
    }
}

private fun cancelActiveRequest() {
    if (activeRequest == null) return
    activeRequest = null
    worker?.terminate()
    worker = null
}

private fun failWorker(cause: Throwable) {
    val failed = activeRequest
    activeRequest = null
    worker?.terminate()
    worker = null
    failed?.onFailure(cause)
}
