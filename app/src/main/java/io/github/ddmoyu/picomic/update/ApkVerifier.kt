package io.github.ddmoyu.picomic.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object ApkVerifier {
    @Suppress("DEPRECATION") private val flags get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION") fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION") fun installed(context: Context) = context.packageManager.getPackageInfo(context.packageName, flags)
    @Suppress("DEPRECATION") private fun signers(info: PackageInfo): Set<String> {
        val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return values.orEmpty().map { UpdateContract.hash(it.toByteArray()) }.toSet()
    }
    suspend fun verify(context: Context, file: File, bundle: UpdateBundle, artifact: UpdateArtifact) = verifyAgainst(context, file, bundle, artifact, installed(context))
    @Suppress("DEPRECATION") internal suspend fun verifyAgainst(context: Context, file: File, bundle: UpdateBundle, artifact: UpdateArtifact, current: PackageInfo) = withContext(Dispatchers.IO) {
        if (file.length() != artifact.asset.size) throw UpdateFailure("更新包不完整，请重新下载")
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) {
            currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count)
        } }
        if (hash.digest().joinToString("") { "%02x".format(it) } != artifact.sha256) throw UpdateFailure("更新包校验失败，请重新下载")
        val apk = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: throw UpdateFailure("更新包无法解析，请重新下载")
        if (apk.packageName != context.packageName || version(apk) != bundle.versionCode || apk.versionName != bundle.versionName || version(apk) <= version(current))
            throw UpdateFailure("更新包的应用或版本不匹配")
        if (apk.applicationInfo?.minSdkVersion != artifact.minSdk || artifact.minSdk > Build.VERSION.SDK_INT) throw UpdateFailure("更新包不兼容当前系统")
        val trusted = signers(current); if (trusted.isEmpty() || signers(apk) != trusted) throw UpdateFailure("更新包签名与当前应用不一致")
        val abis = ZipFile(file).use { zip -> zip.entries().asSequence().mapNotNull { entry -> Regex("lib/([^/]+)/[^/]+\\.so").matchEntire(entry.name)?.groupValues?.get(1) }.toSet() }
        if (abis != artifact.abis.toSet() || Build.SUPPORTED_ABIS.none { it in abis }) throw UpdateFailure("更新包架构与清单或设备不匹配")
        // Android's installer performs the final complete signature and replacement verification.
    }
}
