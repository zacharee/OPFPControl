package tk.zwander.opfpcontrol.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import tk.zwander.opfpcontrol.util.prefs

class PreviewImageView : AppCompatImageView {
    enum class State {
        NORMAL,
        DISABLED
    }

    var currentState = State.NORMAL
        set(value) {
            field = value

            updateIcon()
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    fun updateIcon() {
        setImageBitmap(context.prefs.run {
            if (currentState == State.NORMAL) fpIconNormalTinted else fpIconDisabledTinted
        })
    }

    fun toggleIcon() {
        currentState = if (currentState == State.NORMAL) State.DISABLED else State.NORMAL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_UP -> toggleIcon()
        }

        return super.onTouchEvent(event)
    }
}