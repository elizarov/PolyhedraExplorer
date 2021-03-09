package polyhedra.common

interface OperationProgressContext {
    // true if operation is still running
    val isActive: Boolean
    // done percent from 0 to 100
    fun reportProgress(done: Int)
}