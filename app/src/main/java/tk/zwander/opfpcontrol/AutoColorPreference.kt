package tk.zwander.opfpcontrol

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.util.AttributeSet
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat

class AutoColorPreference : ColorPreferenceCompat {
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    init {
        isPersistent = false
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(try {
            Color.parseColor(Settings.Global.getString(context.contentResolver, key))
        } catch (e: Exception) { defaultValue })
    }

    override fun saveValue(color: Int) {
        Settings.Global.putString(context.contentResolver, key, "#${Integer.toHexString(color)}")

        super.saveValue(color)
    }
}