package com.android.purebilibili.feature.anime4k.gl

/** GLES 3.0 版本的移动端 Anime4K 简化管线。 */
internal object Anime4KShaders {
    const val VERTEX = """
        #version 300 es
        in vec2 aPosition;
        in vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        uniform vec2 uFlip;
        out vec2 vTexCoord;
        void main() {
            vec2 texCoord = aTexCoord;
            if (uFlip.x > 0.5) texCoord.x = 1.0 - texCoord.x;
            if (uFlip.y > 0.5) texCoord.y = 1.0 - texCoord.y;
            vTexCoord = (uTexMatrix * vec4(texCoord, 0.0, 1.0)).xy;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """

    const val EXTERNAL_COPY = """
        #version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision mediump float;
        uniform samplerExternalOES uTexture;
        in vec2 vTexCoord;
        out vec4 outColor;
        void main() {
            outColor = texture(uTexture, vTexCoord);
        }
    """

    const val LUMINANCE = """
        #version 300 es
        precision mediump float;
        uniform sampler2D uColor;
        in vec2 vTexCoord;
        out vec4 outColor;
        void main() {
            float luma = dot(texture(uColor, vTexCoord).rgb, vec3(0.2126, 0.7152, 0.0722));
            outColor = vec4(luma, luma, luma, 1.0);
        }
    """

    const val PUSH = """
        #version 300 es
        precision mediump float;
        uniform sampler2D uColor;
        uniform sampler2D uLuma;
        uniform vec2 uTexelSize;
        uniform int uUseLuma;
        in vec2 vTexCoord;
        out vec4 outColor;

        float sampleLuma(vec2 uv) {
            if (uUseLuma == 1) return texture(uLuma, uv).r;
            return dot(texture(uColor, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
        }

        void main() {
            vec3 center = texture(uColor, vTexCoord).rgb;
            vec2 dx = vec2(uTexelSize.x, 0.0);
            vec2 dy = vec2(0.0, uTexelSize.y);
            float c = sampleLuma(vTexCoord);
            float l = sampleLuma(vTexCoord - dx);
            float r = sampleLuma(vTexCoord + dx);
            float u = sampleLuma(vTexCoord + dy);
            float d = sampleLuma(vTexCoord - dy);
            vec2 direction = vec2(r - l, u - d);
            float edge = clamp(length(direction) * 1.8, 0.0, 1.0);
            vec2 offset = normalize(direction + vec2(0.00001)) * uTexelSize * 0.65;
            vec3 pushed = 0.5 * (texture(uColor, vTexCoord - offset).rgb + texture(uColor, vTexCoord + offset).rgb);
            vec3 result = mix(center, pushed, edge * 0.42);
            outColor = vec4(result, 1.0);
        }
    """

    const val GRADIENT = """
        #version 300 es
        precision highp float;
        uniform sampler2D uColor;
        uniform vec2 uTexelSize;
        in vec2 vTexCoord;
        out vec4 outColor;
        float luma(vec2 uv) {
            return dot(texture(uColor, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
        }
        void main() {
            vec2 dx = vec2(uTexelSize.x, 0.0);
            vec2 dy = vec2(0.0, uTexelSize.y);
            float gx = luma(vTexCoord + dx) - luma(vTexCoord - dx);
            float gy = luma(vTexCoord + dy) - luma(vTexCoord - dy);
            float edge = clamp(length(vec2(gx, gy)) * 2.4, 0.0, 1.0);
            outColor = vec4(edge, edge, edge, 1.0);
        }
    """

    const val REFINE = """
        #version 300 es
        precision mediump float;
        uniform sampler2D uColor;
        uniform sampler2D uGradient;
        uniform vec2 uTexelSize;
        uniform float uStrength;
        uniform int uUseGradient;
        in vec2 vTexCoord;
        out vec4 outColor;
        void main() {
            vec3 center = texture(uColor, vTexCoord).rgb;
            vec2 dx = vec2(uTexelSize.x, 0.0);
            vec2 dy = vec2(0.0, uTexelSize.y);
            vec3 blur = (
                texture(uColor, vTexCoord - dx).rgb + texture(uColor, vTexCoord + dx).rgb +
                texture(uColor, vTexCoord - dy).rgb + texture(uColor, vTexCoord + dy).rgb
            ) * 0.25;
            float edge = uUseGradient == 1
                ? texture(uGradient, vTexCoord).r
                : clamp(length(center - blur) * 3.0, 0.0, 1.0);
            vec3 refined = center + (center - blur) * (uStrength * edge);
            outColor = vec4(clamp(refined, 0.0, 1.0), 1.0);
        }
    """
}
