package io.github.ddmoyu.picomic.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class UpdateAbiSelectionTest {
    private val artifacts = listOf("armeabi-v7a", "x86_64", "arm64-v8a").mapIndexed { index, abi ->
        val name = "PiComic-0.3.7-$abi.apk"
        UpdateArtifact(ReleaseAsset(index + 1L, name, 1234,
            "https://github.com/ddmoyu/PiComic/releases/download/v0.3.7/$name".toHttpUrl(), null), listOf(abi), 26, "a".repeat(64))
    }
    private fun bundle(artifacts: List<UpdateArtifact> = this.artifacts) = UpdateBundle(
        ReleaseInfo(1, "v0.3.7", "", "", "", artifacts.map { it.asset }), "0.3.7", 3007, artifacts, "", "")

    @Test fun choosesEachDeviceNativeAbiRegardlessOfAssetOrder() {
        val devices = listOf(listOf("arm64-v8a", "armeabi-v7a") to "arm64-v8a",
            listOf("armeabi-v7a", "armeabi") to "armeabi-v7a",
            listOf("x86_64", "arm64-v8a", "x86") to "x86_64")
        devices.forEach { (abis, expected) ->
            assertEquals("PiComic-0.3.7-$expected.apk", bundle().select(abis, 26)?.asset?.name)
        }
    }
    @Test fun incompatibleArchitectureOrAndroidVersionDoesNotOfferAnApk() {
        assertNull(bundle().select(listOf("x86"), 36))
        assertNull(bundle().select(emptyList(), 36))
        assertNull(bundle().select(listOf("arm64-v8a", "armeabi-v7a"), 25))
    }
    @Test fun fallsBackOnlyToAnAbiAndSdkTheDeviceActuallySupports() {
        val restricted = artifacts.map { if (it.abis == listOf("arm64-v8a")) it.copy(minSdk = 35) else it }
        assertEquals(listOf("armeabi-v7a"), bundle(restricted).select(listOf("arm64-v8a", "armeabi-v7a"), 34)?.abis)
        assertEquals(listOf("arm64-v8a"), bundle(restricted).select(listOf("arm64-v8a", "armeabi-v7a"), 35)?.abis)
        assertNull(bundle(restricted).select(listOf("arm64-v8a"), 34))
    }
}
