package polyhedra.js.params

fun List<Param>.collectAffectedDependencies(updateType: Param.UpdateType): List<Param.Dependency> {
    val affectedDependencies = ArrayList<Param.Dependency>()
    val affectedCur = ArrayList<Param.Dependency>()
    val affectedSet = HashSet<Param.Dependency>()
    for (p in this) {
        p.visitAffectedDependencies(updateType) { affectedCur += it }
        // the last affected should be root, start from it
        for (i in affectedCur.size - 1 downTo 0) {
            val c = affectedCur[i]
            if (affectedSet.add(c)) affectedDependencies += c
        }
        affectedCur.clear()
    }
    affectedDependencies.reverse()
    return affectedDependencies
}