package dev.caecorthus.sparkwitch.client.bedrock.geo;

/**
 * A parsed, immutable Bedrock geometry (format 1.12+ {@code minecraft:geometry} or legacy 1.8 {@code geometry.*}),
 * baked for {@link BedrockModelRenderer}. Bones are ordered parents first. Built only by
 * {@link BedrockGeometryParser}.
 * 已解析、不可变的 Bedrock 几何（1.12+ 的 {@code minecraft:geometry} 或旧版 1.8 的 {@code geometry.*}），已为
 * {@link BedrockModelRenderer} 烘焙。骨骼按父级在前排列。只能由 {@link BedrockGeometryParser} 构建。
 */
public final class BedrockGeometry {
    // CONTRACT SKELETON (2026-11-02): the geometry agent owns the fields and the package-private constructor.

    public String identifier() {
        throw new UnsupportedOperationException("TODO");
    }

    public int textureWidth() {
        throw new UnsupportedOperationException("TODO");
    }

    public int textureHeight() {
        throw new UnsupportedOperationException("TODO");
    }

    public int boneCount() {
        throw new UnsupportedOperationException("TODO");
    }

    public int cubeCount() {
        throw new UnsupportedOperationException("TODO");
    }

    public String boneName(int bone) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Exact name first, then ignoring case; -1 when absent. / 先精确匹配，再忽略大小写；不存在时为 -1。 */
    public int boneIndex(String name) {
        throw new UnsupportedOperationException("TODO");
    }
}
