#version 150

// Adapted from SparkTraits depression grayscale, itself adapted from StarRailExpress insanity.fsh.
// License: GPL-3.0-only. SparkWitch keeps only the configurable grayscale pass; Black Raven uses full grayscale at 0.85 luminance.
// 来源：SparkTraits 抑郁灰阶效果与 StarRailExpress insanity.fsh；这里只保留可配置灰阶，黑羽鸦使用全灰阶与 0.85 亮度。

uniform sampler2D DiffuseSampler;
uniform float DesaturateFactor;
uniform float LuminanceScale;
uniform float LumaRed;
uniform float LumaGreen;
uniform float LumaBlue;
uniform float SpreadFactor;
uniform float Brightness;

in vec2 texCoord;

out vec4 fragColor;

vec3 desaturate(vec3 color, float factor)
{
    float luminance = dot(color, vec3(LumaRed, LumaGreen, LumaBlue)) * LuminanceScale;
    vec3 gray = vec3(luminance);
    return mix(color, gray, factor);
}

void main()
{
    vec4 color = texture(DiffuseSampler, texCoord);
    // Identity branches retain the original Witch path exactly when Traits is not the winner.
    if (SpreadFactor != 0.0) {
        float mul = 1.0 + ((color.r + color.g + color.b) * 1.0 - 1.0) * SpreadFactor;
        color.rgb = color.rgb * mul;
    }
    color.rgb = desaturate(color.rgb, DesaturateFactor);
    if (Brightness != 1.0) {
        color = color * Brightness;
    }
    fragColor = color;
}
