package tk.zwander.opfpcontrol

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.jaredrummler.android.colorpicker.ColorPreferenceCompat
import java.lang.Exception

class AutoColorPreference : ColorPreferenceCompat {
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    init {
        isPersistent = false

        try {
            saveValue(Color.parseColor(Settings.Global.getString(context.contentResolver, key)))
        } catch (e: Exception) {}
    }

    override fun saveValue(color: Int) {
        Settings.Global.putString(context.contentResolver, key, "#${Integer.toHexString(color)}")

        super.saveValue(color)
    }
}