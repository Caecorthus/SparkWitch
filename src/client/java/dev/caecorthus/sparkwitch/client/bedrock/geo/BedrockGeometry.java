package dev.caecorthus.sparkwitch.client.bedrock.geo;

/**
 * A parsed, immutable Bedrock geometry (format 1.12+ {@code minecraft:geometry} or legacy 1.8 {@code geometry.*}),
 * baked for {@link BedrockModelRenderer}. Bones are ordered parents first. Built only by
 * {@link BedrockGeometryParser}.
 * 已解析、不可变的 Bedrock 几何（1.12+ 的 {@code minecraft:geometry} 或旧版 1.8 的 {@code geometry.*}），已为
 * {@link BedrockModelRenderer} 烘焙。骨骼按父级在前排列。只能由 {@link BedrockGeometryParser} 构建。
 */
public final class BedrockGeometry {
    /**
     * One baked quad: outward normal (3), then 4 vertices of x, y, z, u, v in the bone's bind frame (render frame,
     * blocks, cube rotation already applied), wound counter-clockwise seen from outside.
     * 一个烘焙四边形：外法线（3），再是 4 个顶点的 x、y、z、u、v，位于骨骼绑定坐标系（渲染坐标系、单位为格，已含方块旋转），
     * 从外侧看为逆时针。
     */
    static final int QUAD_STRIDE = 3 + 4 * 5;
    /** 8 corners × xyz of one drawn cube, bone bind frame. / 一个绘制方块的 8 个角点坐标，骨骼绑定坐标系。 */
    static final int CUBE_CORNER_STRIDE = 8 * 3;

    private final String identifier;
    private final int textureWidth;
    private final int textureHeight;
    private final String[] boneNames;
    private final int[] boneParents;
    private final boolean[] boneGlows;
    private final int[] boneCubeCounts;
    private final int cubeCount;
    /** Render-frame pivot in blocks, 3 per bone. / 渲染坐标系枢轴（格），每骨骼 3 个。 */
    final float[] bonePivots;
    /** Geometry rotation in Bedrock degrees, 3 per bone. / 几何自带旋转（Bedrock 角度），每骨骼 3 个。 */
    final float[] boneRotations;
    /** Quad range of bone b is [quadOffsets[b], quadOffsets[b + 1]). / 骨骼 b 的四边形区间。 */
    final int[] quadOffsets;
    final float[] quads;
    /** Drawn-cube range of bone b is [cornerOffsets[b], cornerOffsets[b + 1]). / 骨骼 b 的绘制方块区间。 */
    final int[] cornerOffsets;
    final float[] corners;

    BedrockGeometry(String identifier, int textureWidth, int textureHeight, String[] boneNames, int[] boneParents,
                    boolean[] boneGlows, int[] boneCubeCounts, float[] bonePivots, float[] boneRotations,
                    int[] quadOffsets, float[] quads, int[] cornerOffsets, float[] corners) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.boneNames = boneNames;
        this.boneParents = boneParents;
        this.boneGlows = boneGlows;
        this.boneCubeCounts = boneCubeCounts;
        int cubes = 0;
        for (int count : boneCubeCounts) {
            cubes += count;
        }
        this.cubeCount = cubes;
        this.bonePivots = bonePivots;
        this.boneRotations = boneRotations;
        this.quadOffsets = quadOffsets;
        this.quads = quads;
        this.cornerOffsets = cornerOffsets;
        this.corners = corners;
    }

    public String identifier() {
        return identifier;
    }

    public int textureWidth() {
        return textureWidth;
    }

    public int textureHeight() {
        return textureHeight;
    }

    public int boneCount() {
        return boneNames.length;
    }

    /** Every cube in the file, drawn or not. / 文件中的全部方块（无论是否绘制）。 */
    public int cubeCount() {
        return cubeCount;
    }

    public String boneName(int bone) {
        return boneNames[bone];
    }

    /** Exact name first, then ignoring case; -1 when absent. / 先精确匹配，再忽略大小写；不存在时为 -1。 */
    public int boneIndex(String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(name)) {
                return i;
            }
        }
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** Parent bone index (always lower than {@code bone}), or -1 for a root. / 父骨骼索引（总小于 bone），根为 -1。 */
    public int boneParent(int bone) {
        return boneParents[bone];
    }

    /** Whether the bone draws full-bright (glow prefix on itself or an ancestor). / 该骨骼是否全亮绘制。 */
    public boolean boneGlows(int bone) {
        return boneGlows[bone];
    }

    /** Cubes the file declares on this bone, drawn or not. / 文件在此骨骼上声明的方块数（无论是否绘制）。 */
    public int boneCubeCount(int bone) {
        return boneCubeCounts[bone];
    }

    /** Faces that are actually drawn (missing, zero-area and {@code neverRender} faces excluded). / 实际绘制的面数。 */
    public int quadCount() {
        return quads.length / QUAD_STRIDE;
    }
}
