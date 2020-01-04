package tk.zwander.opfpcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import tk.zwander.opfpcontrol.util.*

class BootReceiver : BroadcastReceiver(), CoroutineScope by MainScope() {
    companion object {
        const val ACTION_DO_REBOOT = "${BuildConfig.APPLICATION_ID}.intent.action.DO_REBOOT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                if (isInstalled) {
                    Shell.su("cmd overlay enable ${Keys.systemuiPkg}.${Keys.opfpcontrol}.${Keys.suffix}.${Keys.overlay}").exec()

                    context.app.notifyForSecondReboot()
                }
            }
        }
    }
}
