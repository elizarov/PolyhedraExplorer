package polyhedra.core.poly

import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.times

/** True when every planar face is a supporting plane of the complete vertex set. */
val Polyhedron.isConvexGeometry: Boolean
    get() {
        val tolerance = 1e-8 * circumradius.coerceAtLeast(1.0)
        return fs.all { face ->
            face.isPlanar && vs.all { vertex -> face * vertex <= face.d + tolerance }
        }
    }
