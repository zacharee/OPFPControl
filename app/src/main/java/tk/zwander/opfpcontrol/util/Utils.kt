package tk.zwander.opfpcontrol.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import tk.zwander.opfpcontrol.App
import java.io.ByteArrayOutputStream


val Context.app: App
    get() = applicationContext as App

val Context.prefs: PrefManager
    get() = PrefManager.getInstance(this)

val Context.isInstalled: Boolean
    get() = packageManager.getPackageArchiveInfo(
        "${Keys.baseDest}/${Keys.folderName}/${Keys.folderName}.apk",
        PackageManager.GET_META_DATA) != null

fun Context.getProperIcon(key: String): BitmapDrawable {
    return BitmapDrawable(
        resources,
        when (key) {
            PrefManager.FP_ICON_PATH -> prefs.fpIconNormalTinted
            PrefManager.FP_ICON_PATH_DISABLED -> prefs.fpIconDisabledTinted
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

fun Bitmap.setOpacity(opacity: Int): Bitmap {
    val opacityVal = opacity / 100f * 255
    val copy = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(copy)
    val paint = Paint()
        .apply { alpha = opacityVal.toInt() }

    canvas.drawBitmap(this, 0f, 0f, paint)

    return copy
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

@ColorInt
fun stripAlpha(@ColorInt color: Int): Int {
    return Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
}

@ColorInt
fun brightenAndOpacify(@ColorInt color: Int): Int {
    val hsl = FloatArray(3)

    ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl)

    hsl[2] = Math.max(0.175f, hsl[2])

    return ColorUtils.HSLToColor(hsl)
}

fun isDark(@ColorInt color: Int) = ColorUtils.calculateLuminance(color) < 0.5f

@ColorInt
fun progressAccent(@ColorInt color: Int): Int {
    return ColorUtils.blendARGB(color, if (isDark(color)) Color.WHITE else Color.BLACK, 0.2f)
}