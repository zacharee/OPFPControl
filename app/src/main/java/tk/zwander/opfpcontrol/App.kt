package tk.zwander.opfpcontrol

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import eu.chainfire.librootjava.RootIPCReceiver
import eu.chainfire.librootjava.RootJava
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.*
import tk.zwander.opfpcontrol.root.RootStuff
import tk.zwander.opfpcontrol.util.prefs
import tk.zwander.opfpcontrol.util.rootShell

class App : Application(), CoroutineScope by MainScope() {
    val ipcReceiver by lazy { IPCReceiverImpl(this, 100) }
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()

        ipcReceiver.setContext(this)

        launch {
            async {
                rootShell.addCommand(
                    RootJava.getLaunchScript(
                        this@App,
                        RootStuff::class.java,
                        null, null, null,
                        "${BuildConfig.APPLICATION_ID}:root")
                )
            }
        }

        val channel = NotificationChannel(
            "opfp_main",
            resources.getText(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
    }

    fun notifyForSecondReboot() {
        if (prefs.needsAdditionalReboot) {
            val notification = Notification.Builder(this, "opfp_main")
                .setContentTitle(resources.getText(R.string.reboot))
                .setContentText(resources.getText(R.string.reboot_first_again_desc))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setStyle(Notification.BigTextStyle())
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        100,
                        Intent(BootReceiver.ACTION_DO_REBOOT).apply {
                            `package` = packageName
                            component = ComponentName(this@App, BCRRebootActivity::class.java)
                        },
                        0
                    )
                )

            nm.notify(1000, notification.build())
        }
    }

    class IPCReceiverImpl(context: Context, code: Int) : RootIPCReceiver<RootBridge>(context, code) {
        var ipc: RootBridge? = null
            set(value) {
                field = value

                if (value != null) {
                    queuedActions.forEach {
                        it.invoke(value)
                    }
                    queuedActions.clear()
                }
            }

        private var queuedActions = ArrayList<(RootBridge) -> Unit>()

        override fun onConnect(ipc: RootBridge?) {
            this.ipc = ipc
        }

        override fun onDisconnect(ipc: RootBridge?) {
            this.ipc = null
        }

        fun postIPCAction(action: (RootBridge) -> Unit) {
            if (ipc == null) {
                queuedActions.add(action)
            } else {
                action.invoke(ipc!!)
            }
        }
    }
}