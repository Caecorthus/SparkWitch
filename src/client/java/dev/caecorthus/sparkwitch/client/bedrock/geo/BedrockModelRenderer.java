package dev.caecorthus.sparkwitch.client.bedrock.geo;

import dev.caecorthus.sparkwitch.client.bedrock.BedrockBounds;
import dev.caecorthus.sparkwitch.client.bedrock.BedrockPose;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a {@link BedrockGeometry} posed by a {@link BedrockPose}. Render frame contract: y up, the model faces -z
 * (Bedrock north), 1 unit = 1 block, and a Bedrock point (x, y, z) px of an unrotated root bone lands at
 * (-x / 16, y / 16, z / 16) — GeckoLib's convention, equivalent to vanilla {@code ModelPart} space
 * (x, 24 - y, z) px with (pitch, yaw, roll) = Bedrock (rx, ry, rz) under the entity renderer's scale(-1, -1, 1).
 * 绘制由 {@link BedrockPose} 摆姿的 {@link BedrockGeometry}。渲染坐标系约定：y 轴向上，模型朝向 -z（Bedrock 北），1 单位
 * = 1 格，未旋转根骨骼上的 Bedrock 点 (x, y, z) 像素落在 (-x / 16, y / 16, z / 16)——即 GeckoLib 的约定，等价于原版
 * {@code ModelPart} 空间 (x, 24 - y, z) 像素、(pitch, yaw, roll) = Bedrock (rx, ry, rz)，再经实体渲染器的
 * scale(-1, -1, 1)。
 */
public final class BedrockModelRenderer {
    private static final float DEG = (float) (Math.PI / 180.0);
    /** Per-thread bone matrices so neither render nor bounds allocates per frame. / 每线程骨骼矩阵，避免每帧分配。 */
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private BedrockModelRenderer() {
    }

    /**
     * Emits every visible face as entity-format quads; glow bones use full-bright light instead of {@code light}.
     * 以实体顶点格式输出所有可见面；发光骨骼使用全亮光照而非 {@code light}。
     */
    public static void render(BedrockGeometry geometry, BedrockPose pose, MatrixStack matrices,
                              VertexConsumer consumer, int light, int overlay, int argb) {
        MatrixStack.Entry entry = matrices.peek();
        Scratch scratch = SCRATCH.get();
        scratch.pose(geometry, pose, entry.getPositionMatrix(), entry.getNormalMatrix());
        float[] quads = geometry.quads;
        Vector3f position = scratch.position;
        Vector3f normal = scratch.normal;
        for (int bone = 0; bone < geometry.boneCount(); bone++) {
            int first = geometry.quadOffsets[bone];
            int end = geometry.quadOffsets[bone + 1];
            if (first == end) {
                continue;
            }
            Matrix4f model = scratch.models[bone];
            Matrix3f normals = scratch.normals[bone];
            int boneLight = geometry.boneGlows(bone) ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            for (int quad = first; quad < end; quad++) {
                int o = quad * BedrockGeometry.QUAD_STRIDE;
                normals.transform(quads[o], quads[o + 1], quads[o + 2], normal);
                float length = normal.length();
                if (length > 1.0E-12F) {
                    normal.div(length);
                } else {
                    normal.set(0.0F, 1.0F, 0.0F);
                }
                for (int v = o + 3; v < o + BedrockGeometry.QUAD_STRIDE; v += 5) {
                    model.transformPosition(quads[v], quads[v + 1], quads[v + 2], position);
                    consumer.vertex(position.x, position.y, position.z, argb, quads[v + 3], quads[v + 4], overlay,
                            boneLight, normal.x, normal.y, normal.z);
                }
            }
        }
    }

    /**
     * The box around every cube corner in the render frame; null pose = bind pose. {@link BedrockBounds#EMPTY} for a
     * model without cubes.
     * 渲染坐标系中包住所有方块角点的包围盒；pose 为 null 时取绑定姿态。没有方块的模型返回 {@link BedrockBounds#EMPTY}。
     */
    public static BedrockBounds bounds(BedrockGeometry geometry, @Nullable BedrockPose pose) {
        if (geometry.corners.length == 0) {
            return BedrockBounds.EMPTY;
        }
        Scratch scratch = SCRATCH.get();
        scratch.pose(geometry, pose, scratch.identityModel, scratch.identityNormal);
        float[] corners = geometry.corners;
        Vector3f position = scratch.position;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int bone = 0; bone < geometry.boneCount(); bone++) {
            int start = geometry.cornerOffsets[bone] * BedrockGeometry.CUBE_CORNER_STRIDE;
            int end = geometry.cornerOffsets[bone + 1] * BedrockGeometry.CUBE_CORNER_STRIDE;
            Matrix4f model = scratch.models[bone];
            for (int i = start; i < end; i += 3) {
                model.transformPosition(corners[i], corners[i + 1], corners[i + 2], position);
                minX = Math.min(minX, position.x);
                minY = Math.min(minY, position.y);
                minZ = Math.min(minZ, position.z);
                maxX = Math.max(maxX, position.x);
                maxY = Math.max(maxY, position.y);
                maxZ = Math.max(maxZ, position.z);
            }
        }
        return new BedrockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static final class Scratch {
        final Matrix4f identityModel = new Matrix4f();
        final Matrix3f identityNormal = new Matrix3f();
        final Vector3f position = new Vector3f();
        final Vector3f normal = new Vector3f();
        Matrix4f[] models = new Matrix4f[0];
        Matrix3f[] normals = new Matrix3f[0];

        /**
         * Bone b = parent (or base) · T(pose offset + pivot) · Rz·Ry·Rx · S(pose scale) · T(-pivot), the GeckoLib
         * {@code RenderUtil.prepMatrixForBone} order; normals use the inverse scale like {@code MatrixStack#scale}.
         * 骨骼矩阵 = 父级（或基准）· T(姿态位移 + 枢轴) · Rz·Ry·Rx · S(姿态缩放) · T(-枢轴)，即 GeckoLib
         * {@code RenderUtil.prepMatrixForBone} 的顺序；法线按 {@code MatrixStack#scale} 使用逆缩放。
         */
        void pose(BedrockGeometry geometry, @Nullable BedrockPose pose, Matrix4f baseModel, Matrix3f baseNormal) {
            int count = geometry.boneCount();
            ensure(count);
            boolean posed = pose != null && pose.boneCount() == count;
            float[] pivots = geometry.bonePivots;
            float[] rotations = geometry.boneRotations;
            for (int bone = 0; bone < count; bone++) {
                int parent = geometry.boneParent(bone);
                Matrix4f model = models[bone].set(parent < 0 ? baseModel : models[parent]);
                Matrix3f normal = normals[bone].set(parent < 0 ? baseNormal : normals[parent]);
                int i = bone * 3;
                float pivotX = pivots[i];
                float pivotY = pivots[i + 1];
                float pivotZ = pivots[i + 2];
                float rx = rotations[i];
                float ry = rotations[i + 1];
                float rz = rotations[i + 2];
                float offsetX = 0.0F;
                float offsetY = 0.0F;
                float offsetZ = 0.0F;
                float scaleX = 1.0F;
                float scaleY = 1.0F;
                float scaleZ = 1.0F;
                if (posed) {
                    rx += pose.rotationX(bone);
                    ry += pose.rotationY(bone);
                    rz += pose.rotationZ(bone);
                    offsetX = -pose.positionX(bone) / 16.0F;
                    offsetY = pose.positionY(bone) / 16.0F;
                    offsetZ = pose.positionZ(bone) / 16.0F;
                    scaleX = pose.scaleX(bone);
                    scaleY = pose.scaleY(bone);
                    scaleZ = pose.scaleZ(bone);
                }
                model.translate(offsetX + pivotX, offsetY + pivotY, offsetZ + pivotZ);
                if (rx != 0.0F || ry != 0.0F || rz != 0.0F) {
                    // Bedrock x/y rotations flip sign in the render frame (x is mirrored). / x 轴镜像，x/y 旋转取反。
                    float angleX = -rx * DEG;
                    float angleY = -ry * DEG;
                    float angleZ = rz * DEG;
                    model.rotateZYX(angleZ, angleY, angleX);
                    normal.rotateZYX(angleZ, angleY, angleX);
                }
                if (scaleX != 1.0F || scaleY != 1.0F || scaleZ != 1.0F) {
                    model.scale(scaleX, scaleY, scaleZ);
                    if (scaleX != 0.0F && scaleY != 0.0F && scaleZ != 0.0F) {
                        normal.scale(1.0F / scaleX, 1.0F / scaleY, 1.0F / scaleZ);
                    } else {
                        normal.scale(scaleY * scaleZ, scaleX * scaleZ, scaleX * scaleY);
                    }
                }
                model.translate(-pivotX, -pivotY, -pivotZ);
            }
        }

        private void ensure(int count) {
            if (models.length >= count) {
                return;
            }
            Matrix4f[] grownModels = new Matrix4f[count];
            Matrix3f[] grownNormals = new Matrix3f[count];
            for (int i = 0; i < count; i++) {
                grownModels[i] = i < models.length ? models[i] : new Matrix4f();
                grownNormals[i] = i < normals.length ? normals[i] : new Matrix3f();
            }
            models = grownModels;
            normals = grownNormals;
        }
    }
}
