#version 150

// Otherworld screen filter: saturation down, contrast up, red edge vignette.
// 里世界画面滤镜：降低饱和度、提高对比度、屏幕边缘泛红。
uniform sampler2D DiffuseSampler;
uniform float Saturation;
uniform float Contrast;
uniform float Vignette;

in vec2 texCoord;

out vec4 fragColor;

void main()
{
    vec4 color = texture(DiffuseSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 rgb = mix(vec3(luma), color.rgb, Saturation);
    rgb = (rgb - 0.5) * Contrast + 0.5;

    vec2 edgeUv = texCoord * 2.0 - 1.0;
    float edge = smoothstep(0.55, 1.0, length(edgeUv));
    rgb = mix(rgb, vec3(0.55, 0.0, 0.02), edge * Vignette);

    fragColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
}
