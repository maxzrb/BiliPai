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

enum class Anime4KShaderChain {
    KAZUMI_EFFICIENCY,
    KAZUMI_QUALITY
}

/** 渲染器使用的非持久化 CNN 预设参数。 */
data class Anime4KRenderProfile(
    val shaderChain: Anime4KShaderChain,
    val maxInputLongEdgePx: Int
)

fun resolveAnime4KRenderProfile(preset: Anime4KPreset): Anime4KRenderProfile {
    return when (preset) {
        Anime4KPreset.FAST -> Anime4KRenderProfile(
            shaderChain = Anime4KShaderChain.KAZUMI_EFFICIENCY,
            maxInputLongEdgePx = 720
        )

        Anime4KPreset.BALANCED -> Anime4KRenderProfile(
            shaderChain = Anime4KShaderChain.KAZUMI_EFFICIENCY,
            maxInputLongEdgePx = 1440
        )

        Anime4KPreset.QUALITY -> Anime4KRenderProfile(
            shaderChain = Anime4KShaderChain.KAZUMI_QUALITY,
            maxInputLongEdgePx = 2160
        )
    }
}

/**
 * 计算进入 CNN 链的输入尺寸。长边上限用于控制命名中间纹理的显存占用。
 */
fun resolveAnime4KInputSize(
    inputWidth: Int,
    inputHeight: Int,
    profile: Anime4KRenderProfile,
    glMaxTextureSize: Int
): Pair<Int, Int> {
    if (inputWidth <= 0 || inputHeight <= 0 || glMaxTextureSize <= 0) return 1 to 1
    val safeLongEdge = minOf(profile.maxInputLongEdgePx, glMaxTextureSize)
    val longEdge = maxOf(inputWidth, inputHeight)
    if (longEdge <= safeLongEdge) return inputWidth to inputHeight

    val clampScale = safeLongEdge.toFloat() / longEdge.toFloat()
    return (inputWidth * clampScale).roundToInt().coerceAtLeast(1) to
        (inputHeight * clampScale).roundToInt().coerceAtLeast(1)
}
