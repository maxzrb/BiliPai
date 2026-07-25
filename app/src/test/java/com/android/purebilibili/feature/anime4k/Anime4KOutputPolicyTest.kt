package com.android.purebilibili.feature.anime4k

import androidx.media3.common.C
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Anime4KOutputPolicyTest {

    @Test
    fun balancedProfile_usesExpectedPassesAndBudget() {
        val profile = resolveAnime4KRenderProfile(Anime4KPreset.BALANCED)

        assertEquals(0.75f, profile.internalScale)
        assertEquals(1440, profile.maxLongEdgePx)
        assertTrue(profile.usesLuminancePass)
        assertFalse(profile.usesGradientPass)
    }

    @Test
    fun processingSize_preservesAspectRatioWithinBudget() {
        assertEquals(
            2160 to 1215,
            resolveAnime4KProcessingSize(
                outputWidth = 3840,
                outputHeight = 2160,
                profile = resolveAnime4KRenderProfile(Anime4KPreset.QUALITY),
                glMaxTextureSize = 4096
            )
        )
    }

    @Test
    fun hdrContent_bypassesPipeline() {
        val decision = resolveAnime4KOutputDecision(
            pluginEnabled = true,
            glAvailable = true,
            colorTransfer = C.COLOR_TRANSFER_ST2084,
            sampleMimeType = "video/hevc",
            isInPipMode = false,
            isAudioOnly = false,
            hostLifecycleStarted = true
        )

        assertFalse(decision.shouldUsePipeline)
        assertEquals(Anime4KBypassReason.HDR_OR_DOLBY_VISION, decision.bypassReason)
    }

    @Test
    fun sdrForegroundPlayback_usesPipeline() {
        val decision = resolveAnime4KOutputDecision(
            pluginEnabled = true,
            glAvailable = true,
            colorTransfer = C.COLOR_TRANSFER_SDR,
            sampleMimeType = "video/avc",
            isInPipMode = false,
            isAudioOnly = false,
            hostLifecycleStarted = true
        )

        assertTrue(decision.shouldUsePipeline)
        assertEquals(Anime4KBypassReason.NONE, decision.bypassReason)
    }

    @Test
    fun lowerPerformancePreset_stopsAtFast() {
        assertEquals(Anime4KPreset.BALANCED, Anime4KPreset.QUALITY.lowerPerformancePreset())
        assertEquals(Anime4KPreset.FAST, Anime4KPreset.BALANCED.lowerPerformancePreset())
        assertEquals(null, Anime4KPreset.FAST.lowerPerformancePreset())
    }
}
