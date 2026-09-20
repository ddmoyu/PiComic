package io.github.ddmoyu.picomic

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.update.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ApkVerifierTest {
    private fun copy(info: android.content.pm.PackageInfo): android.content.pm.PackageInfo {
        val parcel = android.os.Parcel.obtain()
        return try { info.writeToParcel(parcel, 0); parcel.setDataPosition(0); android.content.pm.PackageInfo.CREATOR.createFromParcel(parcel) }
        finally { parcel.recycle() }
    }
    @Suppress("DEPRECATION") @Test fun realSignedApkChecksHashVersionPackageAbiAndCurrentCertificate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.applicationInfo.sourceDir)
        val abis = ZipFile(file).use { zip -> zip.entries().asSequence().mapNotNull { Regex("lib/([^/]+)/[^/]+\\.so").matchEntire(it.name)?.groupValues?.get(1) }.toSet().toList() }
        val installed = ApkVerifier.installed(context)
        val artifact = UpdateArtifact(ReleaseAsset(1, "fixture.apk", file.length(), "https://github.com/fixture/repo/releases/download/v1/fixture.apk".toHttpUrl(), null), abis, 26, UpdateContract.hash(file.readBytes()))
        val bundle = UpdateBundle(ReleaseInfo(1, "fixture", "", "", "", listOf(artifact.asset)), installed.versionName!!, ApkVerifier.version(installed), listOf(artifact), "", "")
        // Real archive and real certificate, with only the prior installed version simulated.
        val previous = copy(installed) // PackageManager caches instances on recent Android versions.
        if (Build.VERSION.SDK_INT >= 28) previous.longVersionCode = bundle.versionCode - 1 else previous.versionCode = bundle.versionCode.toInt() - 1
        ApkVerifier.verifyAgainst(context, file, bundle, artifact, previous)
        assertTrue(runCatching { ApkVerifier.verify(context, file, bundle, artifact) }.exceptionOrNull() is UpdateFailure)
        assertTrue(runCatching { ApkVerifier.verifyAgainst(context, file, bundle, artifact.copy(sha256 = "0".repeat(64)), previous) }.exceptionOrNull() is UpdateFailure)
        assertTrue(runCatching { ApkVerifier.verifyAgainst(context, file, bundle, artifact.copy(minSdk = 27), previous) }.exceptionOrNull() is UpdateFailure)
        assertTrue(runCatching { ApkVerifier.verifyAgainst(context, file, bundle, artifact.copy(abis = emptyList()), previous) }.exceptionOrNull() is UpdateFailure)
        val wrongPackage = File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir)
        val otherArtifact = artifact.copy(asset = artifact.asset.copy(size = wrongPackage.length()), sha256 = UpdateContract.hash(wrongPackage.readBytes()))
        assertTrue(runCatching { ApkVerifier.verifyAgainst(context, wrongPackage, bundle, otherArtifact, previous) }.exceptionOrNull() is UpdateFailure)
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val platform = copy(context.packageManager.getPackageInfo("android", flags))
        if (Build.VERSION.SDK_INT >= 28) platform.longVersionCode = 1 else platform.versionCode = 1
        val failure = runCatching { ApkVerifier.verifyAgainst(context, file, bundle, artifact, platform) }.exceptionOrNull() as UpdateFailure
        assertTrue(failure.message!!.contains("签名"))
    }
}
