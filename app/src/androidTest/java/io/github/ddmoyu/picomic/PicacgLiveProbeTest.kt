package io.github.ddmoyu.picomic

import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.NetworkProfile
import io.github.ddmoyu.picomic.source.picacg.PicacgClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in only; normal regression never contacts a source website. No user credentials. */
class PicacgLiveProbeTest {
    @Test fun anonymousProfileMatchesProtocolOnEmulatorHostProxy() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSourceProbe") == "true")
        PicacgClient(NetworkEngine(NetworkProfile.HttpProxy("10.0.2.2", 7897))).probe()
    }
}
