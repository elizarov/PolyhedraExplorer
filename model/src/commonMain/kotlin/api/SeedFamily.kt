package polyhedra.model.api

const val MIN_FAMILY_SEED_N = 3
const val MAX_FAMILY_SEED_N = 100

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
