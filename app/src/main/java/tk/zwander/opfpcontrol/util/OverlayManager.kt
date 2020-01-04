package tk.zwander.opfpcontrol.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.android.apksig.ApkSigner
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.*
import tk.zwander.opfpcontrol.util.Keys.drawable
import tk.zwander.opfpcontrol.util.Keys.drawable_xxhdpi_v4
import tk.zwander.opfpcontrol.util.Keys.fod_icon_default
import tk.zwander.opfpcontrol.util.Keys.folderName
import tk.zwander.opfpcontrol.util.Keys.fp_02_touch_down_animation
import tk.zwander.opfpcontrol.util.Keys.fp_02_touch_up_animation
import tk.zwander.opfpcontrol.util.Keys.fp_03_touch_down_animation
import tk.zwander.opfpcontrol.util.Keys.fp_03_touch_up_animation
import tk.zwander.opfpcontrol.util.Keys.fp_default_touch_down_animation
import tk.zwander.opfpcontrol.util.Keys.fp_default_touch_up_animation
import tk.zwander.opfpcontrol.util.Keys.fp_icon_default_disable
import tk.zwander.opfpcontrol.util.Keys.fp_mc_touch_down_animation
import tk.zwander.opfpcontrol.util.Keys.fp_mc_touch_up_animation
import tk.zwander.opfpcontrol.util.Keys.opfpcontrol
import tk.zwander.opfpcontrol.util.Keys.overlay
import tk.zwander.opfpcontrol.util.Keys.partition
import tk.zwander.opfpcontrol.util.Keys.suffix
import tk.zwander.opfpcontrol.util.Keys.systemuiPkg
import java.io.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream

enum class OverlayType {
    UNSIGNED_UNALIGNED,
    UNSIGNED,
    SIGNED
}

object Keys {
    const val systemuiPkg = "com.android.systemui"
    const val opfpcontrol = "opfpcontrol"
    const val suffix = "fp"
    const val overlay = "overlay"

    const val partition = "/system_root"
    const val folderName = "$systemuiPkg.$opfpcontrol.$suffix.$overlay"

    const val drawable_xxhdpi_v4 = "drawable-xxhdpi-v4"
    const val drawable = "drawable"

    const val fod_icon_default = "fod_icon_default.png"
    const val fp_icon_default_disable = "fp_icon_default_disable.png"

    const val fp_default_touch_down_animation = "fp_default_touch_down_animation.xml"
    const val fp_default_touch_up_animation = "fp_default_touch_up_animation.xml"
    const val fp_02_touch_down_animation = "fp_02_touch_down_animation.xml"
    const val fp_02_touch_up_animation = "fp_02_touch_up_animation.xml"
    const val fp_03_touch_down_animation = "fp_03_touch_down_animation.xml"
    const val fp_03_touch_up_animation = "fp_03_touch_up_animation.xml"
    const val fp_mc_touch_down_animation = "fp_mc_touch_down_animation.xml"
    const val fp_mc_touch_up_animation = "fp_mc_touch_up_animation.xml"
}

val Context.aapt: String?
    get() {
        val aapt = SuFile(cacheDir, "aapt")

        if (!aapt.exists() && !assets.extractAsset("aapt", aapt.absolutePath))
            return null

        aapt.setExecutable(true)
        aapt.setWritable(true)
        aapt.setReadable(true)

        return aapt.absolutePath
    }

val Context.zipalign: String?
    get() {
        val zipalign = File(cacheDir, "zipalign")

        if (!zipalign.exists() && !assets.extractAsset("zipalign", zipalign.absolutePath))
            return null

        zipalign.setExecutable(true)
        zipalign.setWritable(true)
        zipalign.setReadable(true)

        return zipalign.absolutePath
    }

@ExperimentalCoroutinesApi
fun Context.applyOverlay(listener: (() -> Unit)? = null) {
    GlobalScope.launch {
        val resources = arrayListOf<ResourceFileData>(
            ResourceImageData(
                fod_icon_default, drawable_xxhdpi_v4,
                prefs.fpIconNormalTinted),
            ResourceImageData(
                fp_icon_default_disable, drawable_xxhdpi_v4,
                prefs.fpIconDisabledTinted)
        ).apply {
            if (!prefs.fpPlayAnim) {
                add(ResourceFileData(
                    fp_default_touch_down_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_default_touch_up_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_02_touch_down_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_02_touch_up_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_03_touch_down_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_03_touch_up_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_mc_touch_down_animation, drawable,
                    makeEmptyAnimationList()
                ))
                add(ResourceFileData(
                    fp_mc_touch_up_animation, drawable,
                    makeEmptyAnimationList()
                ))
            }
        }

        doCompileAlignAndSign(
            systemuiPkg,
            suffix,
            { installToSystem(folderName, it, listener) },
            resources
        )
    }
}

fun deleteOverlay(listener: (() -> Unit)? = null) = MainScope().launch {
    withContext(Dispatchers.IO) {
        removeFromSystem(folderName)
    }

    listener?.invoke()
}

fun makeResourceXml(vararg data: ResourceData): String {
    return StringBuilder()
        .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        .append("<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">")
        .apply {
            data.forEach {
                append("<item type=\"${it.type}\" ${it.otherData} name=\"${it.name}\">${it.value}</item>")
            }
        }
        .append("</resources>")
        .toString()
}

fun makeEmptyAnimationList(): String {
    return StringBuilder()
        .append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        .append("<animation-list xmlns:android=\"http://schemas.android.com/apk/res/android\" android:oneshot=\"false\">\n")
        .append("<item android:duration=\"50\" android:drawable=\"@android:color/transparent\" />\n")
        .append("</animation-list>\n")
        .toString()
}

fun makeOverlayFile(base: File, suffix: String, type: OverlayType): SuFile {
    return SuFile(base, "${suffix}_$type.apk")
}

fun makeResDir(base: File): File {
    val dir = File(base, "res/")
    if (dir.exists()) dir.deleteRecursively()

    dir.mkdirs()
    dir.mkdir()

    return dir
}

fun Context.doCompileAlignAndSign(
    targetPackage: String,
    suffix: String,
    listener: ((apk: SuFile) -> Unit)? = null,
    resFiles: List<ResourceFileData>
) {
    val base = makeBaseDir(suffix)
    val manifest = getManifest(base, suffix, targetPackage)
    val unsignedUnaligned = makeOverlayFile(base, suffix, OverlayType.UNSIGNED_UNALIGNED)
    val unsigned = makeOverlayFile(base, suffix, OverlayType.UNSIGNED)
    val signed = makeOverlayFile(base, suffix, OverlayType.SIGNED)
    val resDir = makeResDir(base)

    resFiles.forEach {
        val dir = File(resDir, it.fileDirectory)

        dir.mkdirs()
        dir.mkdir()

        val resFile = File(dir, it.filename)
        if (resFile.exists()) resFile.delete()

        if (it is ResourceImageData) {
            it.image?.let {
                FileOutputStream(resFile).use { stream ->
                    it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
        } else {
            OutputStreamWriter(resFile.outputStream()).use { writer ->
                writer.write(it.contents)
                writer.write("\n")
            }
        }
    }

    compileOverlay(manifest, unsignedUnaligned, resDir, targetPackage)
    alignOverlay(unsignedUnaligned, unsigned)
    signOverlay(unsigned, signed)

    Shell.su("cp ${signed.absolutePath} ${signed.absolutePath}").submit {
        listener?.invoke(signed)
    }
}

@SuppressLint("SetWorldReadable")
@ExperimentalCoroutinesApi
fun installToSystem(folderName: String, signed: SuFile, listener: (() -> Unit)? = null) = MainScope().launch {
    withContext(Dispatchers.IO) {
        val folder = SuFile(appDir, folderName)
        if (!folder.exists()) folder.mkdirs()

        val dst = SuFile(folder, "$folderName.apk")

        folder.setWritable(true, true)
        folder.setReadable(true, false)
        folder.setExecutable(true, false)

        dst.setWritable(true, true)
        dst.setReadable(true, false)
        dst.setExecutable(false)

        signed.copyTo(dst, true)
    }

    listener?.invoke()
}

fun removeFromSystem(folderName: String) {
    Shell.su("rm -rf $moduleAppDir/$folderName").exec()
    Shell.su("rm -rf $systemAppDir/$folderName").exec()
}

fun Context.makeBaseDir(suffix: String): File {
    val dir = File(cacheDir, suffix)

    if (dir.exists()) dir.deleteRecursively()

    dir.mkdirs()
    dir.mkdir()

    Shell.su("chmod 777 ${dir.absolutePath}").exec()

    return dir
}

fun Context.getManifest(base: File, suffix: String, packageName: String): File {
    val info = packageManager.getPackageInfo(packageName, 0)

    val builder = StringBuilder()
    builder.append("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>")
    builder.append(
        "<manifest " +
                "xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "package=\"$packageName.$opfpcontrol.$suffix.$overlay\" " +
                "android:versionCode=\"100\" " +
                "android:versionName=\"100\"> "
    )
    builder.append("<uses-permission android:name=\"com.samsung.android.permission.SAMSUNG_OVERLAY_COMPONENT\" />")
    builder.append("<overlay android:targetPackage=\"$packageName\" />")
    builder.append("<application android:allowBackup=\"false\" android:hasCode=\"false\">")
    builder.append("<meta-data android:name=\"app_version\" android:value=\"v=${info.versionName}\" />")
    builder.append("<meta-data android:name=\"app_version_code\" android:value=\"v=${PackageInfoCompat.getLongVersionCode(info)}\" />")
    builder.append("<meta-data android:name=\"overlay_version\" android:value=\"100\" />")
    builder.append("<meta-data android:name=\"target_package\" android:value=\"$packageName\" />")
    builder.append("</application>")
    builder.append("</manifest>")

    val manifestFile = File(base, "AndroidManifest.xml")
    if (manifestFile.exists()) manifestFile.delete()

    OutputStreamWriter(manifestFile.outputStream()).use {
        it.write(builder.toString())
        it.write("\n")
    }

    return manifestFile
}

fun Context.compileOverlay(manifest: File, overlayFile: File, resFile: File, targetPackage: String) {
    if (overlayFile.exists()) {
        overlayFile.delete()
    }

    val aaptCmd = StringBuilder()
        .append(aapt)
        .append(" p")
        .append(" -M ")
        .append(manifest)
        .append(" -I ")
        .append("/system/framework/framework-res.apk")
        .apply {
            if (targetPackage != "android") {
                append(" -I ")
                append(packageManager.getApplicationInfo(targetPackage, 0).sourceDir)
            }
        }
        .append(" -S ")
        .append(resFile)
        .append(" -F ")
        .append(overlayFile)
        .toString()

    Shell.su(aaptCmd).exec()

    Shell.su("chmod 777 ${overlayFile.absolutePath}").exec()
}

fun Context.alignOverlay(overlayFile: File, alignedOverlayFile: File) {
    if (alignedOverlayFile.exists()) alignedOverlayFile.delete()

    val zipalignCmd = StringBuilder()
        .append(zipalign)
        .append(" 4 ")
        .append(overlayFile.absolutePath)
        .append(" ")
        .append(alignedOverlayFile.absolutePath)
        .toString()

    Shell.su(zipalignCmd).exec()
    Shell.su("chmod 777 ${alignedOverlayFile.absolutePath}").exec()
}

fun Context.signOverlay(overlayFile: File, signed: File) {
    Shell.su("chmod 777 ${overlayFile.absolutePath}").exec()

    val key = File(cacheDir, "/signing-key-new")
    val pass = "overlay".toCharArray()

    if (key.exists()) key.delete()

    val store = KeyStore.getInstance(KeyStore.getDefaultType())
    store.load(assets.open("signing-key-new"), pass)

    val privKey = store.getKey("key", pass) as PrivateKey
    val certs = ArrayList<X509Certificate>()

    certs.add(store.getCertificateChain("key")[0] as X509Certificate)

    val signConfig = ApkSigner.SignerConfig.Builder("overlay", privKey, certs).build()
    val signConfigs = ArrayList<ApkSigner.SignerConfig>()

    signConfigs.add(signConfig)

    val signer = ApkSigner.Builder(signConfigs)
    signer.setV1SigningEnabled(true)
        .setV2SigningEnabled(true)
        .setInputApk(overlayFile)
        .setOutputApk(signed)
        .setMinSdkVersion(Build.VERSION.SDK_INT)
        .build()
        .sign()

    Shell.su("chmod 777 ${signed.absolutePath}").exec()
}

fun AssetManager.extractAsset(assetPath: String, devicePath: String, cipher: Cipher?): Boolean {
    try {
        val files = list(assetPath) ?: emptyArray()
        if (files.isEmpty()) {
            return handleExtractAsset(this, assetPath, devicePath, cipher)
        }
        val f = File(devicePath)
        if (!f.exists() && !f.mkdirs()) {
            throw RuntimeException("cannot create directory: $devicePath")
        }
        var res = true
        for (file in files) {
            val assetList = list("$assetPath/$file") ?: emptyArray()
            res = if (assetList.isEmpty()) {
                res and handleExtractAsset(this, "$assetPath/$file", "$devicePath/$file", cipher)
            } else {
                res and extractAsset("$assetPath/$file", "$devicePath/$file", cipher)
            }
        }
        return res
    } catch (e: IOException) {
        e.printStackTrace()
        return false
    }
}

fun AssetManager.extractAsset(assetPath: String, devicePath: String): Boolean {
    return extractAsset(assetPath, devicePath, null)
}

private fun handleExtractAsset(
    am: AssetManager, assetPath: String, devicePath: String,
    cipher: Cipher?
): Boolean {
    var path = devicePath
    var `in`: InputStream? = null
    var out: OutputStream? = null
    val parent = File(path).parentFile
    if (!parent.exists() && !parent.mkdirs()) {
        throw RuntimeException("cannot create directory: " + parent.absolutePath)
    }

    if (path.endsWith(".enc")) {
        path = path.substring(0, path.lastIndexOf("."))
    }

    try {
        `in` = if (cipher != null && assetPath.endsWith(".enc")) {
            CipherInputStream(am.open(assetPath), cipher)
        } else {
            am.open(assetPath)
        }
        out = FileOutputStream(File(path))
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
        var len: Int = `in`!!.read(bytes)
        while (len != -1) {
            out.write(bytes, 0, len)
            len = `in`.read(bytes)
        }
        return true
    } catch (e: IOException) {
        e.printStackTrace()
        return false
    } finally {
        try {
            `in`?.close()
            out?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }
}