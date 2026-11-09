#version 150

// BEAR smoke under an Iris shader pack (SmokePackDepth): merges Iris's depths into the scene depth the march and the
// composite read after the pack's final pass. Opaque depth (before translucents), so smoke behind glass is marched;
// 0 (near plane, no smoke) wherever the first-person hand is, because the pack already drew it into the image.
// Iris 光影包下的 BEAR 烟雾（SmokePackDepth）：把 Iris 的几张深度合并为光影包最终处理之后步进与合成读取的场景深度。
// 取半透明之前的深度，使玻璃后的烟照样步进；第一人称手部所在处取 0（近平面，无烟），因为光影包已把手部画进画面。

uniform sampler2D DepthAll;
uniform sampler2D DepthOpaque;
uniform sampler2D DepthBeforeHand;
uniform float HandMaxDepth;

out vec4 OutDepth;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float full = texelFetch(DepthAll, px, 0).r;
    float opaque = texelFetch(DepthOpaque, px, 0).r;
    float beforeHand = texelFetch(DepthBeforeHand, px, 0).r;
    // Solid hand: it changed the depth between Iris's pre-hand and pre-translucent copies. Translucent held item: the
    // final depth sits in Iris's compressed hand range. / 实心手部：它改变了 Iris 手部前与半透明前两份副本之间的深度。
    // 半透明手持物品：最终深度落在 Iris 压缩后的手部区间内。
    bool hand = opaque < beforeHand || (full < opaque && full <= HandMaxDepth);
    OutDepth = vec4(hand ? 0.0 : opaque, 0.0, 0.0, 1.0);
}
