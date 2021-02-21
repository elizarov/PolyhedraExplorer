package polyhedra.js

import polyhedra.common.*
import polyhedra.js.params.*
import polyhedra.js.poly.*

class RootParams : Param.Composite("") {
    val baseScale = using(EnumParam("s", Scale.Circumradius, Scales))
    val poly = using(PolyParams(""))
}