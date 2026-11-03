package dev.caecorthus.sparkwitch.client.bedrock;

/**
 * An axis-aligned box in the model render frame (blocks, y up; see {@code BedrockModelRenderer}).
 * 模型渲染坐标系中的轴对齐包围盒（单位为格，y 轴向上；见 {@code BedrockModelRenderer}）。
 */
public record BedrockBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    /** No geometry at all (a model with no cubes). / 完全没有几何体（没有方块的模型）。 */
    public static final BedrockBounds EMPTY = new BedrockBounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public float sizeX() {
        return maxX - minX;
    }

    public float sizeY() {
        return maxY - minY;
    }

    public float sizeZ() {
        return maxZ - minZ;
    }

    public float centerX() {
        return (minX + maxX) * 0.5F;
    }

    public float centerY() {
        return (minY + maxY) * 0.5F;
    }

    public float centerZ() {
        return (minZ + maxZ) * 0.5F;
    }

    public boolean isEmpty() {
        return sizeX() <= 0.0F && sizeY() <= 0.0F && sizeZ() <= 0.0F;
    }
}
