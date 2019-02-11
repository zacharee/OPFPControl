package tk.zwander.opfpcontrol.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.preference.PreferenceManager
import androidx.annotation.ColorInt
import tk.zwander.opfpcontrol.R

class PrefManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: PrefManager? = null

        fun getInstance(context: Context): PrefManager {
            if (instance == null) instance = PrefManager(context.applicationContext)

            return instance!!
        }

        const val FP_ICON_NORMAL = "fp_icon_normal"
        const val FP_ICON_DISABLED = "fp_icon_disabled"
        const val FP_ICON_NORMAL_TINT = "fp_icon_normal_tint"
        const val FP_ICON_DISABLED_TINT = "fp_icon_disabled_tint"

        const val FP_ICON_PATH = "fp_icon_path"
        const val FP_ICON_PATH_DISABLED = "fp_icon_path_disabled"

        const val FP_PLAY_ANIM = "fp_play_anim"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    var fpIconNormalBmp: Bitmap?
        get() = bitmapStringToBitmap(getString(FP_ICON_NORMAL))
        set(value) {
            putString(FP_ICON_NORMAL, value.asString())
        }
    val fpIconNormalNotNull: Bitmap
        get() = fpIconNormalBmp
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.fod_icon_default)

    var fpIconDisabledBmp: Bitmap?
        get() = bitmapStringToBitmap(getString(FP_ICON_DISABLED))
        set(value) {
            putString(FP_ICON_DISABLED, value.asString())
        }
    val fpIconDisabledNotNull: Bitmap
        get() = fpIconDisabledBmp
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.fp_icon_default_disable)

    var fpIconNormalTint: Int
        @ColorInt
        get() = getInt(FP_ICON_NORMAL_TINT, Color.TRANSPARENT)
        set(@ColorInt value) {
            putInt(FP_ICON_NORMAL_TINT, value)
        }
    var fpIconDisabledTint: Int
        @ColorInt
        get() = getInt(FP_ICON_DISABLED_TINT, Color.TRANSPARENT)
        set(@ColorInt value) {
            putInt(FP_ICON_DISABLED_TINT, value)
        }

    val fpPlayAnim: Boolean
        get() = getBoolean(FP_PLAY_ANIM, true)

    fun getInt(key: String, def: Int = 0) = prefs.getInt(key, def)
    fun getString(key: String, def: String? = null): String? = prefs.getString(key, def)
    fun getBoolean(key: String, def: Boolean = false) = prefs.getBoolean(key, def)

    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
}