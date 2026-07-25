package com.android.purebilibili.feature.anime4k

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Anime4K 的用户可持久化配置。启用状态由 PluginManager 单独管理。 */
@Serializable
data class Anime4KConfig(
    val preset: Anime4KPreset = Anime4KPreset.BALANCED
)

@Serializable
enum class Anime4KPreset {
    FAST,
    BALANCED,
    QUALITY
}

/** 渲染器使用的非持久化预设参数。 */
data class Anime4KRenderProfile(
    val internalScale: Float,
    val maxLongEdgePx: Int,
    val usesLuminancePass: Boolean,
    val usesGradientPass: Boolean,
    val sharpenStrength: Float
)

fun resolveAnime4KRenderProfile(preset: Anime4KPreset): Anime4KRenderProfile {
    return when (preset) {
        Anime4KPreset.FAST -> Anime4KRenderProfile(
            internalScale = 0.5f,
            maxLongEdgePx = 1080,
            usesLuminancePass = false,
            usesGradientPass = false,
            sharpenStrength = 0.35f
        )

        Anime4KPreset.BALANCED -> Anime4KRenderProfile(
            internalScale = 0.75f,
            maxLongEdgePx = 1440,
            usesLuminancePass = true,
            usesGradientPass = false,
            sharpenStrength = 0.5f
        )

        Anime4KPreset.QUALITY -> Anime4KRenderProfile(
            internalScale = 1f,
            maxLongEdgePx = 2160,
            usesLuminancePass = true,
            usesGradientPass = true,
            sharpenStrength = 0.68f
        )
    }
}

/**
 * 计算中间 FBO 尺寸。长边上限是稳定的显存预算，而不是 GL_MAX_TEXTURE_SIZE 的替代品。
 */
fun resolveAnime4KProcessingSize(
    outputWidth: Int,
    outputHeight: Int,
    profile: Anime4KRenderProfile,
    glMaxTextureSize: Int
): Pair<Int, Int> {
    if (outputWidth <= 0 || outputHeight <= 0 || glMaxTextureSize <= 0) return 1 to 1
    val safeLongEdge = minOf(profile.maxLongEdgePx, glMaxTextureSize)
    val scaledWidth = (outputWidth * profile.internalScale).roundToInt().coerceAtLeast(1)
    val scaledHeight = (outputHeight * profile.internalScale).roundToInt().coerceAtLeast(1)
    val longEdge = maxOf(scaledWidth, scaledHeight)
    if (longEdge <= safeLongEdge) return scaledWidth to scaledHeight

    val clampScale = safeLongEdge.toFloat() / longEdge.toFloat()
    return (scaledWidth * clampScale).roundToInt().coerceAtLeast(1) to
        (scaledHeight * clampScale).roundToInt().coerceAtLeast(1)
}
