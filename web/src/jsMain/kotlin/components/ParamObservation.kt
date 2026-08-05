package polyhedra.js.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.currentRecomposeScope
import polyhedra.js.params.Param

@Composable
@NonRestartableComposable
fun Param.observe(updateType: Param.UpdateType = Param.TargetValue) {
    val scope = currentRecomposeScope
    DisposableEffect(this, updateType, scope) {
        val dependency = onNotifyUpdated(updateType, scope::invalidate)
        onDispose { dependency.destroy() }
    }
}
