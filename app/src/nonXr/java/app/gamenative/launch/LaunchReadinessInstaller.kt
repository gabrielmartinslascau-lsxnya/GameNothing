package app.gamenative.launch

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun installLaunchReadiness(context: Context, scope: CoroutineScope) {
    val check = DoctorLaunchReadinessCheck(context)
    LaunchReadiness.check = check
    scope.launch {
        check.refresh()
    }
}
