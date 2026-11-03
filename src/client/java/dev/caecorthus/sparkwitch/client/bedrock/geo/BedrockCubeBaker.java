package dev.caecorthus.sparkwitch.client.bedrock.geo;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Bakes one Bedrock cube into render-frame quads (see {@link BedrockGeometry#QUAD_STRIDE}).
 * Face winding, per-vertex UV corners and box-UV regions follow GeckoLib 1.21.1 {@code BakedModelFactory} /
 * {@code GeoQuad}, which equal vanilla {@code ModelPart.Cuboid} for box UV (proven by test). Box-UV sizes are floored
 * like Blockbench's Bedrock formats ({@code box_uv_float_size} unset). Mirror follows Blockbench: it only affects box
 * UV; per-face UV expresses mirroring through negative {@code uv_size}.
 * 将一个 Bedrock 方块烘焙为渲染坐标系四边形。面的绕序、顶点 UV 角与盒式 UV 区域遵循 GeckoLib 1.21.1
 * {@code BakedModelFactory}/{@code GeoQuad}，盒式 UV 与原版 {@code ModelPart.Cuboid} 一致（有测试证明）。盒式 UV 尺寸按
 * Blockbench 的 Bedrock 格式向下取整。镜像遵循 Blockbench：只影响盒式 UV；逐面 UV 用负的 {@code uv_size} 表达镜像。
 */
final class BedrockCubeBaker {
    static final int WEST = 0;
    static final int EAST = 1;
    static final int NORTH = 2;
    static final int SOUTH = 3;
    static final int UP = 4;
    static final int DOWN = 5;
    static final String[] FACE_NAMES = {"west", "east", "north", "south", "up", "down"};

    /** Corner selectors (x, y, z ∈ {0 = min, 1 = max}) per face, CCW from outside. / 每个面的角点选择，外侧逆时针。 */
    private static final int[][] FACE_CORNERS = {
            {0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1}, // west: TRB TLB BLB BRB
            {1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0}, // east: TLF TRF BRF BLF
            {0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0}, // north: TLB TLF BLF BLB
            {1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1}, // south: TRF TRB BRB BRF
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, // up: TRB TRF TLF TLB
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, // down: BLB BLF BRF BRB
    };
    private static final float[][] FACE_NORMALS = {
            {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, -1, 0},
    };
    private static final float EPSILON_AREA = 1.0E-9F;

    /**
     * A per-face UV rectangle in texels. NaN sizes mean "not given" (the box dimensions are used, as Bedrock
     * documents). {@code rotation} is clockwise quarter turns 0..3.
     * 逐面 UV 矩形（纹素）。尺寸为 NaN 表示未给出（按 Bedrock 文档使用方块尺寸）。rotation 为顺时针 0..3 个 90°。
     */
    record FaceUv(float u, float v, float uSize, float vSize, int rotation) {
    }

    private final float textureWidth;
    private final float textureHeight;
    private final FloatArrayList quads;
    private final FloatArrayList corners;
    private final float[][] point = new float[8][3];
    private final Matrix3f rotation = new Matrix3f();
    private final Vector3f scratch = new Vector3f();

    BedrockCubeBaker(int textureWidth, int textureHeight, FloatArrayList quads, FloatArrayList corners) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.quads = quads;
        this.corners = corners;
    }

    /**
     * @param origin   Bedrock px, the unrotated lowest corner / Bedrock 像素，未旋转的最小角
     * @param pivot    Bedrock px, null = the cube centre (Bedrock schema default) / null 为方块中心（Bedrock 规范默认）
     * @param rotation Bedrock degrees / Bedrock 角度
     * @param faces    per-face UV (6, null entries are not drawn), or null for box UV at (boxU, boxV)
     * @return quads emitted / 输出的四边形数
     */
    int bake(float[] origin, float[] size, float inflate, @Nullable float[] pivot, float[] rotationDegrees,
             @Nullable FaceUv[] faces, float boxU, float boxV, boolean mirror) {
        // Render frame in px: Bedrock x is negated (GeckoLib constructCube). / 渲染坐标系像素：Bedrock x 取反。
        float[] min = {-(origin[0] + size[0]) - inflate, origin[1] - inflate, origin[2] - inflate};
        float[] max = {-origin[0] + inflate, origin[1] + size[1] + inflate, origin[2] + size[2] + inflate};
        float extentX = max[0] - min[0];
        float extentY = max[1] - min[1];
        float extentZ = max[2] - min[2];

        boolean rotated = rotationDegrees[0] != 0.0F || rotationDegrees[1] != 0.0F || rotationDegrees[2] != 0.0F;
        float pivotX = 0.0F;
        float pivotY = 0.0F;
        float pivotZ = 0.0F;
        if (rotated) {
            if (pivot != null) {
                pivotX = -pivot[0];
                pivotY = pivot[1];
                pivotZ = pivot[2];
            } else {
                pivotX = -(origin[0] + size[0] * 0.5F);
                pivotY = origin[1] + size[1] * 0.5F;
                pivotZ = origin[2] + size[2] * 0.5F;
            }
            rotation.rotationZYX((float) Math.toRadians(rotationDegrees[2]),
                    (float) Math.toRadians(-rotationDegrees[1]), (float) Math.toRadians(-rotationDegrees[0]));
        }
        for (int c = 0; c < 8; c++) {
            float x = (c & 4) != 0 ? max[0] : min[0];
            float y = (c & 2) != 0 ? max[1] : min[1];
            float z = (c & 1) != 0 ? max[2] : min[2];
            if (rotated) {
                rotation.transform(x - pivotX, y - pivotY, z - pivotZ, scratch);
                x = scratch.x + pivotX;
                y = scratch.y + pivotY;
                z = scratch.z + pivotZ;
            }
            point[c][0] = x / 16.0F;
            point[c][1] = y / 16.0F;
            point[c][2] = z / 16.0F;
        }

        float boxX = 0.0F;
        float boxY = 0.0F;
        float boxZ = 0.0F;
        if (faces == null) {
            boxX = floorBoxSize(size[0]);
            boxY = floorBoxSize(size[1]);
            boxZ = floorBoxSize(size[2]);
        }
        int emitted = 0;
        for (int face = 0; face < 6; face++) {
            float area = switch (face) {
                case WEST, EAST -> extentZ * extentY;
                case NORTH, SOUTH -> extentX * extentY;
                default -> extentX * extentZ;
            };
            if (Math.abs(area) <= EPSILON_AREA) {
                continue; // a flat cube keeps only its two broad faces / 薄片方块只保留两个大面
            }
            float u;
            float v;
            float uSize;
            float vSize;
            int turns = 0;
            boolean flipU = false;
            int cornerFace = face;
            if (faces == null) {
                switch (face) {
                    case WEST -> {
                        u = boxU + boxZ + boxX; v = boxV + boxZ; uSize = boxZ; vSize = boxY;
                    }
                    case EAST -> {
                        u = boxU; v = boxV + boxZ; uSize = boxZ; vSize = boxY;
                    }
                    case NORTH -> {
                        u = boxU + boxZ; v = boxV + boxZ; uSize = boxX; vSize = boxY;
                    }
                    case SOUTH -> {
                        u = boxU + boxZ + boxX + boxZ; v = boxV + boxZ; uSize = boxX; vSize = boxY;
                    }
                    case UP -> {
                        u = boxU + boxZ; v = boxV; uSize = boxX; vSize = boxZ;
                    }
                    default -> {
                        u = boxU + boxZ + boxX; v = boxV + boxZ; uSize = boxX; vSize = -boxZ;
                    }
                }
                if (mirror) {
                    // GeckoLib box mirror: east/west swap places and every face's u runs backwards.
                    // GeckoLib 盒式镜像：东西面互换位置，所有面的 u 反向。
                    flipU = true;
                    if (face == WEST) {
                        cornerFace = EAST;
                    } else if (face == EAST) {
                        cornerFace = WEST;
                    }
                }
            } else {
                FaceUv faceUv = faces[face];
                if (faceUv == null) {
                    continue; // Bedrock: an omitted face is not drawn / Bedrock：省略的面不绘制
                }
                u = faceUv.u();
                v = faceUv.v();
                uSize = Float.isNaN(faceUv.uSize()) ? defaultUSize(face, size) : faceUv.uSize();
                vSize = Float.isNaN(faceUv.vSize()) ? defaultVSize(face, size) : faceUv.vSize();
                turns = faceUv.rotation();
            }
            emitQuad(cornerFace, u, v, uSize, vSize, turns, flipU, rotated);
            emitted++;
        }
        if (emitted > 0) {
            for (int c = 0; c < 8; c++) {
                corners.add(point[c][0]);
                corners.add(point[c][1]);
                corners.add(point[c][2]);
            }
        }
        return emitted;
    }

    private void emitQuad(int cornerFace, float u, float v, float uSize, float vSize, int turns, boolean flipU,
                          boolean rotated) {
        float[] normal = FACE_NORMALS[cornerFace];
        if (rotated) {
            rotation.transform(normal[0], normal[1], normal[2], scratch);
            quads.add(scratch.x);
            quads.add(scratch.y);
            quads.add(scratch.z);
        } else {
            quads.add(normal[0]);
            quads.add(normal[1]);
            quads.add(normal[2]);
        }
        // GeoQuad.build: unmirrored, vertex 0 takes the far u edge; FaceUV.Rotation turns the corners clockwise.
        // GeoQuad.build：未镜像时顶点 0 取 u 远端；FaceUV.Rotation 顺时针轮换角点。
        float uA = (flipU ? u : u + uSize) / textureWidth;
        float uB = (flipU ? u + uSize : u) / textureWidth;
        float vA = v / textureHeight;
        float vB = (v + vSize) / textureHeight;
        int[] selectors = FACE_CORNERS[cornerFace];
        for (int k = 0; k < 4; k++) {
            int c = selectors[k * 3] << 2 | selectors[k * 3 + 1] << 1 | selectors[k * 3 + 2];
            quads.add(point[c][0]);
            quads.add(point[c][1]);
            quads.add(point[c][2]);
            switch ((k + turns) & 3) {
                case 0 -> {
                    quads.add(uA);
                    quads.add(vA);
                }
                case 1 -> {
                    quads.add(uB);
                    quads.add(vA);
                }
                case 2 -> {
                    quads.add(uB);
                    quads.add(vB);
                }
                default -> {
                    quads.add(uA);
                    quads.add(vB);
                }
            }
        }
    }

    /** Blockbench {@code Cube#size(axis, 'box_uv')} for Bedrock: floor with a tiny epsilon. / 向下取整（带极小容差）。 */
    private static float floorBoxSize(float size) {
        return (float) Math.floor(size + 1.0E-7);
    }

    private static float defaultUSize(int face, float[] size) {
        return face == WEST || face == EAST ? size[2] : size[0];
    }

    private static float defaultVSize(int face, float[] size) {
        return face == UP || face == DOWN ? size[2] : size[1];
    }
}
