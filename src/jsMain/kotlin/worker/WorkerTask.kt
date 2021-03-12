/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.worker

import kotlinx.serialization.*
import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*

@Serializable
sealed class WorkerTask<T, R : WorkerResult<T>> {
    abstract suspend fun invoke(progress: OperationProgressContext): R
}

@Serializable
sealed class WorkerResult<T> {
    abstract val value: T
}

@Serializable
data class TransformTask(
    val poly: Polyhedron,
    val transform: Transform
) : WorkerTask<Polyhedron, PolyhedronResult>() {
    override suspend fun invoke(progress: OperationProgressContext): PolyhedronResult {
        val value = when(val twp = transform.transformWithProgress) {
            null -> poly.transformed(transform)
            else -> twp(poly, progress)
        }
        value.validateGeometry()
        return PolyhedronResult(value)
    }
}

@Serializable
data class PolyhedronResult(override val value: Polyhedron): WorkerResult<Polyhedron>()


