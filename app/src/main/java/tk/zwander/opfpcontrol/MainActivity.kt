package tk.zwander.opfpcontrol

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.*

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Shell.rootAccess()) {
            Shell.su("pm grant $packageName ${android.Manifest.permission.WRITE_SECURE_SETTINGS}")
                .submit()
            Shell.su("pm grant $packageName ${android.Manifest.permission.WRITE_EXTERNAL_STORAGE}")
                .submit()
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
    class Prefs : PreferenceFragmentCompat() {
        companion object {
            const val REQ_NORM = 100
            const val REQ_DIS = 101
        }

        val iconNormal by lazy { findPreference("fp_icon_path") as IconPreference }
        val iconDisabled by lazy { findPreference("fp_icon_path_disabled") as IconPreference }

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

            (findPreference("fp_play_anim") as SwitchPreference).apply {
                isChecked = Settings.Global.getInt(context?.contentResolver, key, 1) == 1

                setOnPreferenceChangeListener { _, newValue ->
                    Settings.Global.putInt(context?.contentResolver, key, if (newValue.toString().toBoolean()) 1 else 0)
                }
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            when (requestCode) {
                REQ_NORM -> {
                    if (resultCode == Activity.RESULT_OK) {
                        convertToBitmapAndSave(data?.data, iconNormal.key) { path -> iconNormal.setPath(path) }
                    }
                }
            }
        }

        private fun convertToBitmapAndSave(uri: Uri?, filename: String, listener: ((String?) -> Unit)?) {
            GlobalScope.launch {
                if (uri == null) {
                    listener?.invoke(null)
                    return@launch
                }

                val file = File(context?.getExternalFilesDir(null), filename)
                val bmp = getBitmapFromUri(uri)

                bmp?.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))

                MainScope().launch { listener?.invoke(file.absolutePath) }
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
