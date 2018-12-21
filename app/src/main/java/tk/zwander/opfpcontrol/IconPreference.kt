package tk.zwander.opfpcontrol

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import java.io.File
import java.lang.Exception

class IconPreference : Preference {
    var resetListener: (() -> Boolean)? = null

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context) : super(context)

    init {
        widgetLayoutResource = R.layout.icon_pref_widget

        updateIcon()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)

        val view = holder?.itemView

        val widgetView = view?.findViewById<LinearLayout>(android.R.id.widget_frame)

        val widgetButton = widgetView?.findViewById<ImageView>(R.id.reset)
        widgetButton?.setOnClickListener {
            onReset()
        }
    }

    fun setPath(newPath: String?) {
        Settings.Global.putString(context.contentResolver, key, newPath)
        updateIcon()

        notifyChanged()
    }

    private fun updateIcon() {
        try {
            icon = BitmapDrawable(context.resources, File(Settings.Global.getString(context.contentResolver, key)).absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onReset() {
        setPath(null)
        resetListener?.invoke()
    }
}