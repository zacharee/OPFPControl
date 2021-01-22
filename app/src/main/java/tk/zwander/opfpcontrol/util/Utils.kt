package tk.zwander.opfpcontrol.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.opfpcontrol.App
import tk.zwander.opfpcontrol.BuildConfig
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.lang.Exception
import java.lang.StringBuilder
import kotlin.math.max

val MAGISK_PATH = "/data/adb"
val MAGISK_MODULE_PATH = "$MAGISK_PATH/modules/opfpcontrol"

val mainHandler = Handler(Looper.getMainLooper())

val Context.app: App
    get() = applicationContext as App

val Context.prefs: PrefManager
    get() = PrefManager.getInstance(this)

val isInstalled: Boolean
    get() = File("$appDir/${Keys.folderName}/${Keys.folderName}.apk").exists()

val moduleExists: Boolean
    get() = SuFile(MAGISK_MODULE_PATH).exists()

val appDir: File
    get() = moduleAppDir

val moduleAppDir: File
    get() = SuFile(MAGISK_MODULE_PATH, "/system/app/").also {
        if (!it.exists()) {
            it.mkdirs()
        }
    }

val systemAppDir: File
    get() = SuFile("/system/app")

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

fun createMagiskModule(result: ((needsToReboot: Boolean) -> Unit)? = null) = MainScope().launch {
    val needsToUpdate = withContext(Dispatchers.IO) {
        val doesExist = moduleExists
        val currentVersion = try {
            SuFileInputStream(SuFile("$MAGISK_MODULE_PATH/module.prop")).bufferedReader()
                .useLines {
                    it.filter { it.startsWith("versionCode") }
                        .first()
                        .split("=")[1]
                        .toInt()
                }
        } catch (e: Exception) {
            Log.e("OPFPControl", e.message, e)
            0
        }

        val needsToUpdate = !doesExist || currentVersion < BuildConfig.MODULE_VERSION

        if (needsToUpdate) {
            val prop = StringBuilder()
                .appendLine("name=OPFPControl Module")
                .appendLine("version=${BuildConfig.MODULE_VERSION}")
                .appendLine("versionCode=${BuildConfig.MODULE_VERSION}")
                .appendLine("author=Zachary Wander")
                .appendLine("description=Systemlessly install FP themes")

            Shell.su(
                "mkdir -p $MAGISK_MODULE_PATH",
                "chmod -R 0755 $MAGISK_MODULE_PATH",
                "touch $MAGISK_MODULE_PATH/auto_mount",
                "touch $MAGISK_MODULE_PATH/update",
                "echo \"$prop\" > $MAGISK_MODULE_PATH/module.prop"
            ).exec()
        }

        needsToUpdate
    }

    result?.invoke(needsToUpdate)
}

fun reboot() {
    Shell.su("/system/bin/svc power reboot").submit {
        Log.e("OPFPControl", "Reboot failed?! ${it.code} ${it.isSuccess} \n${it.out.joinToString("\n")} \n${it.err.joinToString(",")}")
    }
}

fun SuFile.copyTo(target: SuFile, overwrite: Boolean = false, bufferSize: Int = DEFAULT_BUFFER_SIZE): File {
    if (!this.exists()) {
        throw NoSuchFileException(file = this, reason = "The source file doesn't exist.")
    }

    if (target.exists()) {
        if (!overwrite)
            throw FileAlreadyExistsException(file = this, other = target, reason = "The destination file already exists.")
        else if (!target.delete())
            throw FileAlreadyExistsException(file = this, other = target, reason = "Tried to overwrite the destination, but failed to delete it.")
    }

    if (this.isDirectory) {
        if (!target.mkdirs())
            throw FileSystemException(file = this, other = target, reason = "Failed to create target directory.")
    } else {
        target.parentFile?.mkdirs()

        this.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize)
            }
        }
    }

    return target
}

fun SuFile.inputStream(): SuFileInputStream = SuFileInputStream(this)
fun SuFile.outputStream(): SuFileOutputStream = SuFileOutputStream(this)