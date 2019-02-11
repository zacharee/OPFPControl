package tk.zwander.opfpcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.chainfire.libsuperuser.Shell
import tk.zwander.opfpcontrol.util.Keys
import tk.zwander.opfpcontrol.util.app
import tk.zwander.opfpcontrol.util.isInstalled
import tk.zwander.opfpcontrol.util.prefs

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DO_REBOOT = "${BuildConfig.APPLICATION_ID}.intent.action.DO_REBOOT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                if (context.isInstalled) {
                    Shell.SU.run("cmd overlay enable ${Keys.systemuiPkg}.${Keys.opfpcontrol}.${Keys.suffix}.${Keys.overlay}")

                    context.app.notifyForSecondReboot()
                }
            }
            ACTION_DO_REBOOT -> {
                context.prefs.needsAdditionalReboot = false
                context.app.ipcReceiver.postIPCAction {
                    it.reboot(null)
                }
            }
        }
    }
}
