package tk.zwander.opfpcontrol.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import java.io.ByteArrayOutputStream


val Context.prefs: PrefManager
    get() = PrefManager.getInstance(this)

fun Context.getProperIcon(key: String): BitmapDrawable {
    return BitmapDrawable(
        resources,
        when (key) {
            PrefManager.FP_ICON_PATH -> prefs.fpIconNormalNotNull.tint(prefs.fpIconNormalTint)
            PrefManager.FP_ICON_PATH_DISABLED -> prefs.fpIconDisabledNotNull.tint(prefs.fpIconDisabledTint)
            else -> throw IllegalArgumentException("Bad key: $key")
        }
    )
}

fun Context.setProperIcon(key: String, bmp: Bitmap?) {
    when (key) {
        PrefManager.FP_ICON_PATH -> prefs.fpIconNormalBmp = bmp
        PrefManager.FP_ICON_PATH_DISABLED -> prefs.fpIconDisabledBmp = bmp
        else -> throw IllegalArgumentException("Bad key: $key")
    }
}

fun Bitmap.tint(color: Int): Bitmap {
    val paint = Paint()
    paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)

    val new = copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(new)
    canvas.drawBitmap(this, 0f, 0f, paint)

    return new
}

fun Bitmap?.asString(): String? {
    if (this == null) return null

    val encodedImage: String
    val byteArrayBitmapStream = ByteArrayOutputStream()
    compress(
        Bitmap.CompressFormat.PNG, 100,
        byteArrayBitmapStream
    )
    val b = byteArrayBitmapStream.toByteArray()
    encodedImage = Base64.encodeToString(b, Base64.DEFAULT)
    return encodedImage
}

fun bitmapStringToBitmap(src: String?): Bitmap? {
    if (src == null) return null

    val decodedString = Base64.decode(src, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
}