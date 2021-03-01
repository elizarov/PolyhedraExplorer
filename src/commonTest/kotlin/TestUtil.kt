fun <T> testParameter(name: String, list: Iterable<T>, block: (T) -> Unit) {
    for (value in list) {
        try {
            block(value)
        } catch (e: Throwable) {
            val msg = e.message
            val cause = if (e is TestParameterException) e.cause!! else e
            val sep = if (e is TestParameterException) "," else ":"
            throw TestParameterException("$name = $value$sep $msg", cause)
        }
    }
}

class TestParameterException(message: String, cause: Throwable) : Exception(message, cause)