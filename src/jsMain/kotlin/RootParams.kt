package polyhedra.js

import polyhedra.common.*
import polyhedra.js.params.*
import polyhedra.js.poly.*

class RootParams : Param.Composite("") {
    val seed = using(EnumParam("s", Seed.Tetrahedron, Seeds))
    val transforms = using(EnumListParam("t", emptyList(), Transforms))
    val baseScale = using(EnumParam("bs", Scale.Circumradius, Scales))
    val poly = using(PolyParams(""))
}