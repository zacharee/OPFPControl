package tk.zwander.opfpcontrol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceFragmentCompat
import eu.chainfire.librootjava.RootIPCReceiver
import eu.chainfire.librootjava.RootJava
import eu.chainfire.libsuperuser.Shell
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.opfpcontrol.root.RootStuff
import tk.zwander.opfpcontrol.util.*

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val ipcReceiver by lazy { IPCReceiverImpl(this, 100) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Shell.SU.available()) {
            GlobalScope.launch {
                Shell.SU.run("pm grant $packageName ${android.Manifest.permission.WRITE_SECURE_SETTINGS}")
                Shell.SU.run("pm grant $packageName ${android.Manifest.permission.WRITE_EXTERNAL_STORAGE}")

                Shell.SU.run(RootJava.getLaunchScript(
                    this@MainActivity,
                    RootStuff::class.java,
                    null, null, null,
                    "${BuildConfig.APPLICATION_ID}:root"))
            }
        } else {
            finish()

            return
        }

        ipcReceiver.setContext(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        updateColors()

        apply.apply {
            setOnClickListener {
                progress_apply.visibility = View.VISIBLE

                val wasInstalled = isInstalled
                applyOverlay {
                    progress_apply.visibility = View.GONE
                    remove.visibility = if (isInstalled) View.VISIBLE else View.GONE
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.reboot)
                        .setMessage(
                            if (wasInstalled)
                                R.string.reboot_others_desc else R.string.reboot_first_desc
                        )
                        .setPositiveButton(R.string.reboot) { _, _ -> ipcReceiver.ipc?.reboot(null) }
                        .setNegativeButton(R.string.later, null)
                        .setCancelable(false)
                        .show()
                }
            }
        }

        remove.apply {
            visibility = if (isInstalled) View.VISIBLE else View.GONE
            setOnClickListener {
                progress_remove.visibility = View.VISIBLE

                deleteOverlay {
                    progress_remove.visibility = View.GONE

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.reboot)
                        .setMessage(R.string.reboot_uninstall_desc)
                        .setPositiveButton(R.string.reboot) { _, _ -> ipcReceiver.ipc?.reboot(null) }
                        .setNegativeButton(R.string.later, null)
                        .setCancelable(false)
                        .show()
                }
            }
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, Prefs(), "prefs")
            .commit()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val frag = supportFragmentManager.findFragmentByTag("prefs") as Prefs
        when (key) {
            PrefManager.FP_ICON_NORMAL,
            PrefManager.FP_ICON_NORMAL_TINT -> {
                frag.iconNormal.updateIcon()
                updateColors()
            }
            PrefManager.FP_ICON_DISABLED,
            PrefManager.FP_ICON_DISABLED_TINT -> {
                frag.iconDisabled.updateIcon()
                updateColors()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefs.unregisterOnSharedPreferenceChangeListener(this)
        ipcReceiver.release()
    }

    private fun updateColors() {
        val normalColor = prefs.fpIconNormalTint
        val disabledColor = prefs.fpIconDisabledTint

        apply.supportBackgroundTintList = ColorStateList.valueOf(normalColor)
        progress_apply.indeterminateTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(normalColor, Color.BLACK, 0.2f))

        remove.supportBackgroundTintList = ColorStateList.valueOf(disabledColor)
        progress_remove.indeterminateTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(disabledColor, Color.BLACK, 0.2f)
        )

        val normalImgTint = if (Color.luminance(normalColor) < 0.5f) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        val disabledImgTint = if (Color.luminance(disabledColor) < 0.5f) {
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

        val iconNormal by lazy { findPreference(PrefManager.FP_ICON_PATH) as IconPreference }
        val iconDisabled by lazy { findPreference(PrefManager.FP_ICON_PATH_DISABLED) as IconPreference }

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

        private fun getBitmapFromUri(uri: Uri?): Bitmap? {
            if (uri == null) return null

            val parcelFileDescriptor = context?.contentResolver?.openFileDescriptor(uri, "r")
            val fileDescriptor = parcelFileDescriptor?.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor?.close()
            return image
        }
    }

    class IPCReceiverImpl(context: Context, code: Int) : RootIPCReceiver<RootBridge>(context, code) {
        var ipc: RootBridge? = null

        override fun onConnect(ipc: RootBridge?) {
            this.ipc = ipc
        }

        override fun onDisconnect(ipc: RootBridge?) {
            this.ipc = null
        }
    }
}
