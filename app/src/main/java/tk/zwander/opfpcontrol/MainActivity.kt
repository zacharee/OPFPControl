package tk.zwander.opfpcontrol

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.opfpcontrol.util.PrefManager
import tk.zwander.opfpcontrol.util.applyOverlay
import tk.zwander.opfpcontrol.util.prefs

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Shell.SU.available()) {
            GlobalScope.launch {
                Shell.SU.run("pm grant $packageName ${android.Manifest.permission.WRITE_SECURE_SETTINGS}")
                Shell.SU.run("pm grant $packageName ${android.Manifest.permission.WRITE_EXTERNAL_STORAGE}")
            }
        } else {
            finish()

            return
        }

        supportFragmentManager
            ?.beginTransaction()
            ?.replace(R.id.content, Prefs())
            ?.commit()
    }

    @ExperimentalCoroutinesApi
    class Prefs : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
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

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            when (key) {
                PrefManager.FP_ICON_NORMAL,
                PrefManager.FP_ICON_NORMAL_TINT -> iconNormal.updateIcon()
                PrefManager.FP_ICON_DISABLED,
                PrefManager.FP_ICON_DISABLED_TINT -> iconDisabled.updateIcon()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.main, rootKey)

            preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

            iconNormal.setOnPreferenceClickListener {
                startActivityForResult(pickIntent, REQ_NORM)
                true
            }

            iconDisabled.setOnPreferenceClickListener {
                startActivityForResult(pickIntent, REQ_DIS)
                true
            }

//            (findPreference(PrefManager.FP_PLAY_ANIM) as SwitchPreference).apply {
//                isChecked = Settings.Global.getInt(context?.contentResolver, key, 1) == 1
//
//                setOnPreferenceChangeListener { _, newValue ->
//                    Settings.Global.putInt(context?.contentResolver, key, if (newValue.toString().toBoolean()) 1 else 0)
//                }
//            }

            findPreference("apply").setOnPreferenceClickListener {
                context?.applyOverlay()
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

        override fun onDestroy() {
            super.onDestroy()

            preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
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
