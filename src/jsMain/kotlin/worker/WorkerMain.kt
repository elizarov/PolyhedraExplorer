/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.worker

import kotlinx.browser.*
import kotlinx.serialization.*
import org.w3c.dom.*
import polyhedra.common.*
import kotlin.js.*
import kotlinx.serialization.json.*

private external val self: DedicatedWorkerGlobalScope
private external var onmessage: (MessageEvent) -> Unit

fun runWorkerMain(): Boolean {
    if (jsTypeOf(document) != "undefined") return false
    onmessage = ::onMessageInWorker
    return true
}

private val worker: Worker by lazy { createWorker() }
private val json by lazy { Json { } }
private var lastTaskId = 0L

@Serializable
private sealed class MessageToWorker {
    @Serializable
    data class Task<T, R : WorkerResult<T>>(val id: Long, val task: WorkerTask<T, R>) : MessageToWorker() {
        fun invoke(progress: OperationProgressContext): MessageToMain =
            try {
                MessageToMain.Result(id, task.invoke(progress))
            } catch (e: Throwable) {
                e.printStackTrace()
                MessageToMain.Failure(id, e.toString())
            }
    }
    @Serializable
    data class Cancel(val id: Long) : MessageToWorker()
}

@Serializable
private sealed class MessageToMain {
    @Serializable
    data class Progress(val id: Long, val done: Int) : MessageToMain()
    @Serializable
    data class Result<T>(val id: Long, val result: WorkerResult<T>) : MessageToMain()
    @Serializable
    data class Failure(val id: Long, val message: String) : MessageToMain()
}

@Suppress("UnsafeCastFromDynamic")
@OptIn(ExperimentalSerializationApi::class)
private fun serializeAndPostMessageToWorker(msg: MessageToWorker) {
    worker.postMessage(json.encodeToDynamic(MessageToWorker.serializer(), msg))
}

@Suppress("UnsafeCastFromDynamic")
@OptIn(ExperimentalSerializationApi::class)
private fun serializeAndPostMessageToMain(msg: MessageToMain) {
    self.postMessage(json.encodeToDynamic(MessageToMain.serializer(), msg))
}

private class ActiveTaskInMain(
    val progress: OperationProgressContext,
    val resolve: (Any?) -> Unit,
    val reject: (Throwable) -> Unit
)

private val activeTasksInMain = HashMap<Long, ActiveTaskInMain>()

fun <T, R : WorkerResult<T>> sendWorkerTask(task: WorkerTask<T, R>, progress: OperationProgressContext): Promise<T> =
    Promise { resolve, reject ->
        val id = ++lastTaskId
        @Suppress("UNCHECKED_CAST")
        activeTasksInMain[id] = ActiveTaskInMain(progress, resolve as (Any?) -> Unit, reject)
        serializeAndPostMessageToWorker(MessageToWorker.Task(id, task))
    }


private var activeTaskInWorker: ActiveTaskInWorker? = null

private class ActiveTaskInWorker(val id: Long) : OperationProgressContext {
    override var isActive = true
    override fun reportProgress(done: Int)=
        serializeAndPostMessageToMain(MessageToMain.Progress(id, done))
}

@OptIn(ExperimentalSerializationApi::class)
private fun onMessageInWorker(e: MessageEvent) {
    val msg = json.decodeFromDynamic(MessageToWorker.serializer(), e.data)
    when (msg) {
        is MessageToWorker.Task<*, *> ->
            try {
                activeTaskInWorker = ActiveTaskInWorker(msg.id).also {
                    serializeAndPostMessageToMain(msg.invoke(it))
                }
            } finally {
                activeTaskInWorker = null
            }
        is MessageToWorker.Cancel -> activeTaskInWorker?.isActive = false
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun onMessageInMain(e: MessageEvent) {
    val msg = json.decodeFromDynamic(MessageToMain.serializer(), e.data)
    when (msg) {
        is MessageToMain.Progress -> activeTasksInMain[msg.id]?.progress?.reportProgress(msg.done)
        is MessageToMain.Result<*> -> activeTasksInMain.remove(msg.id)?.resolve?.invoke(msg.result.value)
        is MessageToMain.Failure -> activeTasksInMain.remove(msg.id)?.reject?.invoke(Exception(msg.message))
    }
}

private fun createWorker(): Worker {
    val script = document.body?.firstElementChild as? HTMLScriptElement ?:
        error("The first element in <body> must be <script>")
    return Worker(script.src).apply {
        onmessage = ::onMessageInMain
    }
}