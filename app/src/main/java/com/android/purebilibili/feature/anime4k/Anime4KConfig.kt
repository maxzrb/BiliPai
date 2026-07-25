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

/** 面向播放页展示的增强强度名称。 */
fun resolveAnime4KPresetLabel(preset: Anime4KPreset): String {
    return when (preset) {
        Anime4KPreset.FAST -> "效率"
        Anime4KPreset.BALANCED -> "增强"
        Anime4KPreset.QUALITY -> "强力"
    }
}

fun Anime4KPreset.lowerPerformancePreset(): Anime4KPreset? {
    return when (this) {
        Anime4KPreset.QUALITY -> Anime4KPreset.BALANCED
        Anime4KPreset.BALANCED -> Anime4KPreset.FAST
        Anime4KPreset.FAST -> null
    }
}

/** 渲染器使用的非持久化预设参数。 */
data class Anime4KRenderProfile(
    val internalScale: Float,
    val maxLongEdgePx: Int,
    val usesLuminancePass: Boolean,
    val usesGradientPass: Boolean,
    val pushStrength: Float,
    val sharpenStrength: Float,
    val edgeThreshold: Float,
    val detailClamp: Float
)

fun resolveAnime4KRenderProfile(preset: Anime4KPreset): Anime4KRenderProfile {
    return when (preset) {
        Anime4KPreset.FAST -> Anime4KRenderProfile(
            internalScale = 0.6f,
            maxLongEdgePx = 1080,
            usesLuminancePass = false,
            usesGradientPass = false,
            pushStrength = 0.45f,
            sharpenStrength = 0.7f,
            edgeThreshold = 0.035f,
            detailClamp = 0.1f
        )

        Anime4KPreset.BALANCED -> Anime4KRenderProfile(
            internalScale = 0.85f,
            maxLongEdgePx = 1440,
            usesLuminancePass = true,
            usesGradientPass = true,
            pushStrength = 0.7f,
            sharpenStrength = 1.1f,
            edgeThreshold = 0.018f,
            detailClamp = 0.18f
        )

        Anime4KPreset.QUALITY -> Anime4KRenderProfile(
            internalScale = 1f,
            maxLongEdgePx = 2160,
            usesLuminancePass = true,
            usesGradientPass = true,
            pushStrength = 1.05f,
            sharpenStrength = 1.45f,
            edgeThreshold = 0.012f,
            detailClamp = 0.24f
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
