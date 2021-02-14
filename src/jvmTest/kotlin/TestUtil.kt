package polyhedra.test

fun <T> testParameter(name: String, list: Iterable<T>, block: (T) -> Unit) {
    for (value in list) {
        try {
            block(value)
        } catch (e: Throwable) {
            throw TestParameterException("$name = $value: $e", e)
        }
    }
}

class TestParameterException(message: String, cause: Throwable) : Exception(message, cause)