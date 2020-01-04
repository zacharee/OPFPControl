package tk.zwander.opfpcontrol

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.opfpcontrol.util.app
import tk.zwander.opfpcontrol.util.prefs
import tk.zwander.opfpcontrol.util.reboot

class BCRRebootActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.rebooting)
            .setCancelable(false)
            .show()

        prefs.needsAdditionalReboot = false
        reboot()
    }
}