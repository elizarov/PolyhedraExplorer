package polyhedra.js.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import polyhedra.js.params.Param

@Composable
fun ObserveParam(
    params: Param,
    updateType: Param.UpdateType = Param.TargetValue + Param.AnimatedValue,
): State<Long> {
    val version = remember(params, updateType) { mutableStateOf(0L) }
    val dependency = remember(params, updateType) {
        params.onNotifyUpdated(updateType) {
            version.value++
        }
    }
    DisposableEffect(dependency) {
        onDispose { dependency.destroy() }
    }
    return version
}
