package com.android.purebilibili.feature.anime4k.gl

import android.opengl.GLES30

internal data class FboTarget(
    val framebuffer: Int,
    val texture: Int,
    val width: Int,
    val height: Int
)

/** 管理 Anime4K 乒乓渲染所需的 RGBA8 FBO。 */
internal class FboManager {
    private val targets = mutableMapOf<String, FboTarget>()

    fun obtain(name: String, width: Int, height: Int): FboTarget {
        targets[name]?.takeIf { it.width == width && it.height == height }?.let { return it }
        targets.remove(name)?.let(::delete)

        val textures = IntArray(1)
        val framebuffers = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )
        GLES30.glGenFramebuffers(1, framebuffers, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffers[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            textures[0],
            0
        )
        check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "Anime4K FBO 创建失败"
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return FboTarget(framebuffers[0], textures[0], width, height).also { targets[name] = it }
    }

    fun release() {
        targets.values.forEach(::delete)
        targets.clear()
    }

    private fun delete(target: FboTarget) {
        GLES30.glDeleteFramebuffers(1, intArrayOf(target.framebuffer), 0)
        GLES30.glDeleteTextures(1, intArrayOf(target.texture), 0)
    }
}
