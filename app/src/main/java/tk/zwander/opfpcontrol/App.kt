package tk.zwander.opfpcontrol

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.topjohnwu.superuser.Shell
import eu.chainfire.librootjava.RootIPCReceiver
import eu.chainfire.librootjava.RootJava
import tk.zwander.opfpcontrol.root.RootStuff
import tk.zwander.opfpcontrol.util.prefs

class App : Application() {
    private val ipcReceiver by lazy { IPCReceiverImpl(this, 100) }
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()

        ipcReceiver.setContext(this)

        // Something about changing su policies on the OnePlus 7T breaks everything
//        Log.e("OPFPControl", "starting root daemon")
//        val commands = RootJava.getLaunchScript(
//            this@App,
//            RootStuff::class.java,
//            null, null, null,
//            "${BuildConfig.APPLICATION_ID}:root").toTypedArray()
//
//        Log.e("OPFPControl", "SU process commands: \n${commands.joinToString("\n")}")
//
//        Shell.su(
//            *commands
//        ).submit {
//            Log.e("OPFPControl", "started root daemon: ${it.out}")
//        }

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
                        PendingIntent.FLAG_UPDATE_CURRENT
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