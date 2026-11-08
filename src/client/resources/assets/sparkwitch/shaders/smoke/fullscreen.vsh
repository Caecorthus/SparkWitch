#version 150

// Attribute-less fullscreen triangle for the BEAR smoke occlusion passes (raw GL, drawn with glDrawArrays(3)).
// BEAR 烟雾遮挡各步骤用的无属性全屏三角形（原生 GL，glDrawArrays 画 3 个顶点）。
void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
