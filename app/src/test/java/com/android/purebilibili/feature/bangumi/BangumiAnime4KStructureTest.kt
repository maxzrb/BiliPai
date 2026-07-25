package com.android.purebilibili.feature.bangumi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BangumiAnime4KStructureTest {

    @Test
    fun `bangumi player routes video through Anime4K pipeline`() {
        val playerSource = sourceOf("ui/player/BangumiPlayerComponents.kt")
        val overlaySource = sourceOf("ui/player/BangumiPlayerOverlayHost.kt")

        assertTrue(playerSource.contains("VideoOutputRouter(exoPlayer)"))
        assertTrue(playerSource.contains("Anime4KGLSurfaceView("))
        assertTrue(playerSource.contains("resolveAnime4KOutputDecision("))
        assertTrue(playerSource.contains("updateDisplayScaleMode(currentAspectRatio.toAnime4KDisplayScaleMode())"))
        assertTrue(playerSource.contains("width = playerFrameViewport.width.toDp()"))
        assertTrue(playerSource.contains("height = playerFrameViewport.height.toDp()"))
        assertTrue(playerSource.contains("anime4kEnabled = anime4kPluginInfo?.enabled == true"))
        assertFalse(playerSource.contains("view.player = exoPlayer"))
        assertTrue(overlaySource.contains("anime4kBypassReason = anime4kBypassReason"))
        assertTrue(overlaySource.contains("onAnime4kPresetChange = onAnime4kPresetChange"))
    }

    private fun sourceOf(path: String): String =
        File("src/main/java/com/android/purebilibili/feature/bangumi/$path").readText()
}
