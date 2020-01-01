package tk.zwander.opfpcontrol.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.topjohnwu.superuser.io.SuFile
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.opfpcontrol.App
import tk.zwander.opfpcontrol.BuildConfig
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.lang.Exception
import java.lang.StringBuilder
import kotlin.math.max

val MAGISK_PATH = "/sbin/.magisk"
val MAGISK_MODULE_PATH = "$MAGISK_PATH/img/opfpcontrol"

val mainHandler = Handler(Looper.getMainLooper())

val rootShell by lazy {
    Shell.Builder()
        .useSU()
        .open()
}

val Context.app: App
    get() = applicationContext as App

val Context.prefs: PrefManager
    get() = PrefManager.getInstance(this)

val Context.isInstalled: Boolean
    get() = File("$appDir/${Keys.folderName}/${Keys.folderName}.apk").exists()

val moduleExists: Boolean
    get() = SuFile(MAGISK_MODULE_PATH).exists()

val moduleEnabled: Boolean
    get() = !SuFile(MAGISK_MODULE_PATH, "disable").exists()
            && SuFile(MAGISK_PATH).exists()

val appDir: File
    get() = if (moduleEnabled)
        SuFile(MAGISK_MODULE_PATH, "/system/app").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        } else
        SuFile("/system_root/app")

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

    hsl[2] = max(0.175f, hsl[2])

    return ColorUtils.HSLToColor(hsl)
}

fun isDark(@ColorInt color: Int) = ColorUtils.calculateLuminance(color) < 0.5f

@ColorInt
fun progressAccent(@ColorInt color: Int): Int {
    return ColorUtils.blendARGB(color, if (isDark(color)) Color.WHITE else Color.BLACK, 0.2f)
}

fun createMagiskModule(result: ((needsToReboot: Boolean) -> Unit)? = null) {
    val doesExist = moduleExists
    val currentVersion = try {
        BufferedReader(FileReader(SuFile("$MAGISK_MODULE_PATH/module.prop")))
            .lines()
            .filter { it.startsWith("versionCode") }
            .findFirst()
            .get()
            .split("=")[1]
            .toInt()
    } catch (e: Exception) {
        0
    }

    val needsToUpdate = !doesExist || currentVersion < BuildConfig.MODULE_VERSION

    if (needsToUpdate) {
        val prop = StringBuilder()
            .appendln("name=OPFPControl Module")
            .appendln("version=${BuildConfig.MODULE_VERSION}")
            .appendln("versionCode=${BuildConfig.MODULE_VERSION}")
            .appendln("author=Zachary Wander")
            .appendln("description=Systemlessly install FP themes")

        Shell.Builder()
            .useSU()
            .addCommand("mkdir -p $MAGISK_MODULE_PATH")
            .addCommand("chmod -R 0755 $MAGISK_MODULE_PATH")
            .addCommand("touch $MAGISK_MODULE_PATH/auto_mount")
            .addCommand("touch $MAGISK_MODULE_PATH/update")
            .addCommand("echo \"$prop\" > $MAGISK_MODULE_PATH/module.prop")
            .open()
    }

    mainHandler.post {
        result?.invoke(needsToUpdate)
    }
}