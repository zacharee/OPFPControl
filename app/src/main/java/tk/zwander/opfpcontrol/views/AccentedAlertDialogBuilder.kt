package tk.zwander.opfpcontrol.views

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.opfpcontrol.util.prefs

class AccentedAlertDialogBuilder(context: Context) : MaterialAlertDialogBuilder(context) {
    override fun create(): AlertDialog {
        val dialog = super.create()

        dialog.setOnShowListener {
            arrayOf(
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL,
                DialogInterface.BUTTON_POSITIVE
            ).map {
                dialog.getButton(it)
            }.forEach {
                it.setTextColor(context.prefs.fpIconNormalTint)
            }
        }

        return dialog
    }
}