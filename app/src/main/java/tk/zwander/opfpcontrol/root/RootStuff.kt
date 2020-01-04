package tk.zwander.opfpcontrol.root

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.util.Log
import eu.chainfire.librootjava.RootIPC
import eu.chainfire.librootjava.RootJava
import tk.zwander.opfpcontrol.BuildConfig
import tk.zwander.opfpcontrol.RootBridge

object RootStuff {
    @JvmStatic
    fun main(args: Array<String>) {
        RootJava.restoreOriginalLdLibraryPath()

        val ipc = RootBridgeImpl()
        
        try {
            RootIPC(BuildConfig.APPLICATION_ID, ipc, 100, 30 * 1000, true)
        } catch (e: RootIPC.TimeoutException) {
            Log.e("OPFPControl", "Root IPC connection failed", e)
        }
    }

    class RootBridgeImpl : RootBridge.Stub() {
        @SuppressLint("PrivateApi")
        val iPMClass: Class<*> = Class.forName("android.os.IPowerManager")

        @SuppressLint("PrivateApi")
        val pm: Any = run {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val iPMStubClass = Class.forName("android.os.IPowerManager\$Stub")

            val getServiceOrThrow = serviceManagerClass.getMethod("getServiceOrThrow", String::class.java)
            val asInterface = iPMStubClass.getMethod("asInterface", IBinder::class.java)

            val binder = getServiceOrThrow.invoke(null, Context.POWER_SERVICE) as IBinder
            asInterface.invoke(null, binder)
        }

        override fun reboot(reason: String?) {
            iPMClass.getMethod("reboot", Boolean::class.java, String::class.java, Boolean::class.java)
                .invoke(pm, false, reason, true)
        }
    }
}