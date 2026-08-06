package polyhedra.model.poly

const val CHIRALITY_PRIME = "'"

enum class Chirality(val suffix: String) {
    Default(""),
    Flipped(CHIRALITY_PRIME),
    ;

    fun flipped(): Chirality = when (this) {
        Default -> Flipped
        Flipped -> Default
    }
}

fun String.withChirality(chirality: Chirality?): String =
    this + chirality?.suffix.orEmpty()
