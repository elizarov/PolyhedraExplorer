package polyhedra.model.api

const val MIN_FAMILY_SEED_N = 3
const val MAX_FAMILY_SEED_N = 100
const val DEFAULT_STAR_FAMILY_SEED_N = 5
const val DEFAULT_STAR_FAMILY_SEED_Q = 2
const val MAX_STAR_FAMILY_SEED_Q = 10

enum class SeedFamily(
    val tagPrefix: String,
    val displayName: String,
) {
    Prism("P", "Prism"),
    Antiprism("A", "Antiprism"),
    Pyramid("Y", "Pyramid"),
    Bipyramid("B", "Bipyramid"),
    ;

    override fun toString(): String = displayName

    val starTagPrefix: String
        get() = "S$tagPrefix"
}

data class FamilySeedId(
    val family: SeedFamily,
    val n: Int,
) {
    init {
        require(n in MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N)
    }

    val tag: String
        get() = family.tagPrefix + n

    override fun toString(): String = "$family $n"
}

fun String.toFamilySeedIdOrNull(): FamilySeedId? {
    val family = SeedFamily.entries.firstOrNull { startsWith(it.tagPrefix) } ?: return null
    val n = removePrefix(family.tagPrefix).toIntOrNull() ?: return null
    return n.takeIf { it in MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N }
        ?.let { FamilySeedId(family, it) }
        ?.takeIf { it.tag == this }
}

/** Canonical identity of one regular-star member of an existing seed family. */
data class StarFamilySeedId(
    val family: SeedFamily,
    val n: Int,
    val q: Int,
) {
    init {
        require(n in MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N)
        require(q in 2..MAX_STAR_FAMILY_SEED_Q)
        require(q < n / 2.0)
        require(gcd(n, q) == 1)
    }

    val tag: String
        get() = "${family.starTagPrefix}${n}_$q"

    override fun toString(): String = "$family $n/$q"
}

fun String.toStarFamilySeedIdOrNull(): StarFamilySeedId? {
    val family = SeedFamily.entries.singleOrNull { startsWith(it.starTagPrefix) } ?: return null
    val values = removePrefix(family.starTagPrefix).split('_')
    if (values.size != 2) return null
    val n = values[0].toIntOrNull() ?: return null
    val q = values[1].toIntOrNull() ?: return null
    return runCatching { StarFamilySeedId(family, n, q) }
        .getOrNull()
        ?.takeIf { it.tag == this }
}

private fun gcd(first: Int, second: Int): Int {
    var a = first
    var b = second
    while (b != 0) {
        val next = a % b
        a = b
        b = next
    }
    return a
}
