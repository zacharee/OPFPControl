package tk.zwander.opfpcontrol.prefs

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.widget.Switch
import androidx.preference.AndroidResources
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import tk.zwander.opfpcontrol.util.PrefManager
import tk.zwander.opfpcontrol.util.prefs

class AccentedSwitchPreference : SwitchPreference, SharedPreferences.OnSharedPreferenceChangeListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)

        context.prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPrepareForRemoval() {
        super.onPrepareForRemoval()

        context.prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val switchView = holder.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET) as Switch
        val color = if (switchView.isChecked) context.prefs.fpIconNormalTint else Color.TRANSPARENT

        switchView.thumbDrawable?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        switchView.trackDrawable?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefManager.FP_ICON_NORMAL_TINT -> notifyChanged()
        }
    }
}