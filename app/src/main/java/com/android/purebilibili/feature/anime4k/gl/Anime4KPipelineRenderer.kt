package com.android.purebilibili.feature.anime4k.gl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.android.purebilibili.feature.anime4k.Anime4KConfig
import com.android.purebilibili.feature.anime4k.resolveAnime4KProcessingSize
import com.android.purebilibili.feature.anime4k.resolveAnime4KRenderProfile

internal class Anime4KPipelineRenderer(
    private val onFrameAvailable: () -> Unit,
    private val onInputSurfaceChanged: (Surface?) -> Unit,
    private val onFirstFrameRendered: () -> Unit,
    private val onPipelineError: (Throwable) -> Unit
) : GLSurfaceView.Renderer {

    private val fboManager = FboManager()
    private val textureMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).apply {
        for (index in indices) this[index] = if (index % 5 == 0) 1f else 0f
    }
    private var externalTextureId = 0
    private var inputSurface: Anime4KInputSurface? = null
    private var outputWidth = 0
    private var outputHeight = 0
    private var inputWidth = 0
    private var inputHeight = 0
    private var flipHorizontal = false
    private var flipVertical = false
    private var config = Anime4KConfig()
    private var maxTextureSize = 1
    @Volatile
    private var frameAvailable = false
    private var hasLatchedFrame = false
    private var notifiedFirstFrame = false
    private var failed = false
    private var externalProgram = 0
    private var lumaProgram = 0
    private var pushProgram = 0
    private var gradientProgram = 0
    private var refineProgram = 0

    override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        failed = false
        hasLatchedFrame = false
        notifiedFirstFrame = false
        releaseGlResources(releaseInput = true)
        maxTextureSize = IntArray(1).also {
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, it, 0)
        }[0].coerceAtLeast(1)
        externalProgram = createProgram(Anime4KShaders.EXTERNAL_COPY)
        lumaProgram = createProgram(Anime4KShaders.LUMINANCE)
        pushProgram = createProgram(Anime4KShaders.PUSH)
        gradientProgram = createProgram(Anime4KShaders.GRADIENT)
        refineProgram = createProgram(Anime4KShaders.REFINE)
        ensureInputSurface()
        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(unused: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        outputWidth = width.coerceAtLeast(1)
        outputHeight = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
        if (failed) return
        try {
            if (frameAvailable) {
                frameAvailable = false
                inputSurface?.surfaceTexture?.updateTexImage()
                inputSurface?.surfaceTexture?.getTransformMatrix(textureMatrix)
                hasLatchedFrame = true
            }
            if (!hasLatchedFrame || outputWidth <= 0 || outputHeight <= 0) {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                return
            }
            renderFrame()
            if (!notifiedFirstFrame) {
                notifiedFirstFrame = true
                onFirstFrameRendered()
            }
        } catch (error: Throwable) {
            failed = true
            Log.e(TAG, "Anime4K 渲染失败", error)
            onPipelineError(error)
        }
    }

    fun setConfig(value: Anime4KConfig) {
        config = value
    }

    fun setInputSize(width: Int, height: Int) {
        inputWidth = width.coerceAtLeast(0)
        inputHeight = height.coerceAtLeast(0)
        inputSurface?.setDefaultBufferSize(inputWidth, inputHeight)
    }

    fun setFlip(horizontal: Boolean, vertical: Boolean) {
        flipHorizontal = horizontal
        flipVertical = vertical
    }

    fun ensureInputSurface() {
        if (inputSurface != null || failed) return
        externalTextureId = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        inputSurface = Anime4KInputSurface(externalTextureId) {
            frameAvailable = true
            onFrameAvailable()
        }.also { it.setDefaultBufferSize(inputWidth, inputHeight) }
        onInputSurfaceChanged(inputSurface?.surface)
    }

    fun releaseInputSurface() {
        inputSurface?.let {
            onInputSurfaceChanged(null)
            it.release()
        }
        inputSurface = null
        if (externalTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(externalTextureId), 0)
            externalTextureId = 0
        }
        hasLatchedFrame = false
        frameAvailable = false
    }

    fun release() {
        releaseGlResources(releaseInput = true)
    }

    private fun renderFrame() {
        val profile = resolveAnime4KRenderProfile(config.preset)
        val (processingWidth, processingHeight) = resolveAnime4KProcessingSize(
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            profile = profile,
            glMaxTextureSize = maxTextureSize
        )
        val color = fboManager.obtain("color", processingWidth, processingHeight)
        val push = fboManager.obtain("push", processingWidth, processingHeight)
        val luma = if (profile.usesLuminancePass) fboManager.obtain("luma", processingWidth, processingHeight) else null
        val gradient = if (profile.usesGradientPass) fboManager.obtain("gradient", processingWidth, processingHeight) else null

        drawExternal(target = color)
        if (luma != null) {
            drawLuminance(color, luma)
        }
        drawPush(color, luma ?: color, push, luma != null)
        if (gradient != null) {
            drawGradient(push, gradient)
        }
        drawRefine(push, gradient, profile.sharpenStrength)
    }

    private fun drawExternal(target: FboTarget) {
        bindTarget(target)
        GLES30.glUseProgram(externalProgram)
        bindTexture(externalProgram, "uTexture", GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId, 0)
        setVertexUniforms(externalProgram, textureMatrix, flipHorizontal, flipVertical)
        QuadRenderUtils.draw(externalProgram)
    }

    private fun drawLuminance(color: FboTarget, target: FboTarget) {
        bindTarget(target)
        GLES30.glUseProgram(lumaProgram)
        bindTexture(lumaProgram, "uColor", GLES30.GL_TEXTURE_2D, color.texture, 0)
        setVertexUniforms(lumaProgram, identityMatrix, false, false)
        QuadRenderUtils.draw(lumaProgram)
    }

    private fun drawPush(color: FboTarget, luma: FboTarget, target: FboTarget, usesLuma: Boolean) {
        bindTarget(target)
        GLES30.glUseProgram(pushProgram)
        bindTexture(pushProgram, "uColor", GLES30.GL_TEXTURE_2D, color.texture, 0)
        bindTexture(pushProgram, "uLuma", GLES30.GL_TEXTURE_2D, luma.texture, 1)
        setVec2(pushProgram, "uTexelSize", 1f / color.width, 1f / color.height)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pushProgram, "uUseLuma"), if (usesLuma) 1 else 0)
        setVertexUniforms(pushProgram, identityMatrix, false, false)
        QuadRenderUtils.draw(pushProgram)
    }

    private fun drawGradient(color: FboTarget, target: FboTarget) {
        bindTarget(target)
        GLES30.glUseProgram(gradientProgram)
        bindTexture(gradientProgram, "uColor", GLES30.GL_TEXTURE_2D, color.texture, 0)
        setVec2(gradientProgram, "uTexelSize", 1f / color.width, 1f / color.height)
        setVertexUniforms(gradientProgram, identityMatrix, false, false)
        QuadRenderUtils.draw(gradientProgram)
    }

    private fun drawRefine(color: FboTarget, gradient: FboTarget?, strength: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, outputWidth, outputHeight)
        GLES30.glUseProgram(refineProgram)
        bindTexture(refineProgram, "uColor", GLES30.GL_TEXTURE_2D, color.texture, 0)
        bindTexture(refineProgram, "uGradient", GLES30.GL_TEXTURE_2D, (gradient ?: color).texture, 1)
        setVec2(refineProgram, "uTexelSize", 1f / color.width, 1f / color.height)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(refineProgram, "uStrength"), strength)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(refineProgram, "uUseGradient"), if (gradient != null) 1 else 0)
        setVertexUniforms(refineProgram, identityMatrix, false, false)
        QuadRenderUtils.draw(refineProgram)
    }

    private fun bindTarget(target: FboTarget) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.framebuffer)
        GLES30.glViewport(0, 0, target.width, target.height)
    }

    private fun bindTexture(program: Int, uniform: String, target: Int, texture: Int, unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, uniform), unit)
    }

    private fun setVec2(program: Int, uniform: String, x: Float, y: Float) {
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, uniform), x, y)
    }

    private fun setVertexUniforms(program: Int, matrix: FloatArray, horizontalFlip: Boolean, verticalFlip: Boolean) {
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uTexMatrix"), 1, false, matrix, 0)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(program, "uFlip"),
            if (horizontalFlip) 1f else 0f,
            if (verticalFlip) 1f else 0f
        )
    }

    private fun createProgram(fragmentSource: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, Anime4KShaders.VERTEX)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES30.glCreateProgram().also { program ->
            GLES30.glAttachShader(program, vertex)
            GLES30.glAttachShader(program, fragment)
            GLES30.glLinkProgram(program)
            val linked = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0)
            check(linked[0] == GLES30.GL_TRUE) { "Anime4K program link failed: ${GLES30.glGetProgramInfoLog(program)}" }
            GLES30.glDeleteShader(vertex)
            GLES30.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            check(compiled[0] == GLES30.GL_TRUE) { "Anime4K shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}" }
        }
    }

    private fun releaseGlResources(releaseInput: Boolean) {
        fboManager.release()
        listOf(externalProgram, lumaProgram, pushProgram, gradientProgram, refineProgram)
            .filter { it != 0 }
            .forEach { GLES30.glDeleteProgram(it) }
        externalProgram = 0
        lumaProgram = 0
        pushProgram = 0
        gradientProgram = 0
        refineProgram = 0
        if (releaseInput) releaseInputSurface()
    }

    private companion object {
        const val TAG = "Anime4KRenderer"
    }
}
