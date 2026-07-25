package com.android.purebilibili.feature.anime4k.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.android.purebilibili.feature.anime4k.Anime4KConfig
import com.android.purebilibili.feature.anime4k.Anime4KPreset

/** Anime4K 的可见输出 SurfaceView，解码器输入 Surface 由 renderer 异步提供。 */
class Anime4KGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    var onInputSurfaceChanged: (Surface?) -> Unit = {}
    var onFirstFrameRendered: () -> Unit = {}
    var onPipelineError: (Throwable) -> Unit = {}
    var onPresetDowngradeRequested: (Anime4KPreset) -> Unit = {}

    private val pipelineRenderer = Anime4KPipelineRenderer(
        onFrameAvailable = { requestRender() },
        onInputSurfaceChanged = { surface -> post { onInputSurfaceChanged(surface) } },
        onFirstFrameRendered = { post { onFirstFrameRendered() } },
        onPipelineError = { error -> post { onPipelineError(error) } },
        onPresetDowngradeRequested = { preset -> post { onPresetDowngradeRequested(preset) } }
    )

    init {
        setEGLContextClientVersion(3)
        setPreserveEGLContextOnPause(true)
        setRenderer(pipelineRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateConfig(config: Anime4KConfig) {
        queueEvent { pipelineRenderer.setConfig(config) }
        requestRender()
    }

    fun updateInputSize(width: Int, height: Int) {
        queueEvent { pipelineRenderer.setInputSize(width, height) }
    }

    fun updateFlip(horizontal: Boolean, vertical: Boolean) {
        queueEvent { pipelineRenderer.setFlip(horizontal, vertical) }
        requestRender()
    }

    override fun onResume() {
        super.onResume()
        queueEvent { pipelineRenderer.ensureInputSurface() }
    }

    override fun onPause() {
        queueEvent { pipelineRenderer.releaseInputSurface() }
        super.onPause()
    }

    override fun onDetachedFromWindow() {
        queueEvent { pipelineRenderer.release() }
        super.onDetachedFromWindow()
    }
}
