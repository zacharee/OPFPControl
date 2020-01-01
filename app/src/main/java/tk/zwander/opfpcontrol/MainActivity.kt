package tk.zwander.opfpcontrol

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.chainfire.librootjava.RootJava
import eu.chainfire.libsuperuser.Shell
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import tk.zwander.opfpcontrol.prefs.IconPreference
import tk.zwander.opfpcontrol.root.RootStuff
import tk.zwander.opfpcontrol.util.*

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        preview.updateIcon()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, Prefs(), "prefs")
            .commit()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.root)
            .setMessage(R.string.waiting_for_root)
            .setCancelable(false)
            .create()
            .apply {
                setOnShowListener {
                    launch {
                        val hasSu = withContext(Dispatchers.Main) {
                            Shell.SU.available()
                        }

                        if (hasSu) {
                            setup().join()
                        } else {
                            Toast.makeText(this@MainActivity, R.string.root_not_detected, Toast.LENGTH_SHORT).show()
                            finish()
                        }

                        it.dismiss()
                    }
                }
                show()
            }
    }

    private fun setup() = launch {
        async {
            rootShell.addCommand("pm grant $packageName ${android.Manifest.permission.WRITE_SECURE_SETTINGS}")
            rootShell.addCommand("pm grant $packageName ${android.Manifest.permission.WRITE_EXTERNAL_STORAGE}")

            createMagiskModule {
                if (it) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setCancelable(false)
                        .setTitle(R.string.magisk_module_installed)
                        .setMessage(R.string.magisk_module_installed_desc)
                        .setPositiveButton(R.string.reboot) { _, _ ->
                            app.ipcReceiver.postIPCAction { ipc -> ipc.reboot(null) }
                        }
                        .setNegativeButton(R.string.later, null)
                        .show()
                }
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(this@MainActivity)
        updateColors()

        apply.apply {
            setOnClickListener {
                progress_apply.visibility = View.VISIBLE

                val wasInstalledBeforeApply = isInstalled
                applyOverlay {
                    progress_apply.visibility = View.GONE
                    remove.visibility = if (isInstalled) View.VISIBLE else View.GONE

                    prefs.needsAdditionalReboot = !wasInstalledBeforeApply

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.reboot)
                        .setMessage(
                            if (wasInstalledBeforeApply)
                                R.string.reboot_others_desc else R.string.reboot_first_desc
                        )
                        .setPositiveButton(R.string.reboot) { _, _ -> app.ipcReceiver.postIPCAction { it.reboot(null) } }
                        .setNegativeButton(R.string.later, null)
                        .setCancelable(false)
                        .show()
                }
            }
        }

        remove.apply {
            visibility = withContext(Dispatchers.Main) {
                if (isInstalled) View.VISIBLE else View.GONE
            }
            setOnClickListener {
                progress_remove.visibility = View.VISIBLE

                deleteOverlay {
                    progress_remove.visibility = View.GONE

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.reboot)
                        .setMessage(R.string.reboot_uninstall_desc)
                        .setPositiveButton(R.string.reboot) { _, _ -> app.ipcReceiver.postIPCAction { it.reboot(null) } }
                        .setNegativeButton(R.string.later, null)
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val frag = supportFragmentManager.findFragmentByTag("prefs") as Prefs?
        when (key) {
            PrefManager.FP_ICON_NORMAL,
            PrefManager.FP_ICON_NORMAL_TINT,
            PrefManager.ICON_OPACITY_NORMAL,
            PrefManager.FP_ICON_DISABLED,
            PrefManager.FP_ICON_DISABLED_TINT,
            PrefManager.ICON_OPACITY_DISABLED -> {
                mainHandler.post {
                    preview.updateIcon()
                    updateColors()
                    frag?.updateIcons()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun updateColors() {
        val normalColor = prefs.fpIconNormalTint.run {
            brightenAndOpacify(this)
        }
        val disabledColor = prefs.fpIconDisabledTint.run {
            brightenAndOpacify(this)
        }

        apply.supportBackgroundTintList = ColorStateList.valueOf(normalColor)
        progress_apply.indeterminateTintList = ColorStateList.valueOf(progressAccent(normalColor))

        remove.supportBackgroundTintList = ColorStateList.valueOf(disabledColor)
        progress_remove.indeterminateTintList = ColorStateList.valueOf(progressAccent(disabledColor))

        val normalImgTint = if (isDark(normalColor)) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        val disabledImgTint = if (isDark(disabledColor)) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        apply.setColorFilter(normalImgTint)
        remove.setColorFilter(disabledImgTint)
    }

    @ExperimentalCoroutinesApi
    class Prefs : PreferenceFragmentCompat() {
        companion object {
            const val REQ_NORM = 100
            const val REQ_DIS = 101
        }

        val iconNormal by lazy { findPreference<IconPreference>(PrefManager.FP_ICON_PATH) as IconPreference }
        val iconDisabled by lazy { findPreference<IconPreference>(PrefManager.FP_ICON_PATH_DISABLED) as IconPreference }

        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)

            type = "image/*"
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.main, rootKey)

            iconNormal.setOnPreferenceClickListener {
                startActivityForResult(pickIntent, REQ_NORM)
                true
            }

            iconDisabled.setOnPreferenceClickListener {
                startActivityForResult(pickIntent, REQ_DIS)
                true
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            when (requestCode) {
                REQ_NORM -> {
                    if (resultCode == Activity.RESULT_OK) {
                        context?.prefs?.fpIconNormalBmp = getBitmapFromUri(data?.data)
                    }
                }
                REQ_DIS -> {
                    if (resultCode == Activity.RESULT_OK) {
                        context?.prefs?.fpIconDisabledBmp = getBitmapFromUri(data?.data)
                    }
                }
            }
        }

        fun updateIcons() {
            if (isVisible) {
                iconNormal.updateIcon()
                iconDisabled.updateIcon()
            }
        }

        private fun getBitmapFromUri(uri: Uri?): Bitmap? {
            if (uri == null) return null

            val parcelFileDescriptor = context?.contentResolver?.openFileDescriptor(uri, "r")
            val fileDescriptor = parcelFileDescriptor?.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor?.close()
            return image
        }
    }
}
