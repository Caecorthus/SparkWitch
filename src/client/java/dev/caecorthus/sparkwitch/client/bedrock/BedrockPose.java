package dev.caecorthus.sparkwitch.client.bedrock;

import java.util.Arrays;

/**
 * One frame's pose on top of a Bedrock geometry's own bone rotations, in Bedrock units: rotation in degrees (Bedrock
 * sign convention, added to the bone's geometry rotation), position offset in model pixels (Bedrock axes), and a scale
 * multiplier about the bone pivot. Animations and the Mini Wither's head aim write into it; the model renderer and the
 * bounds pass read it. A bone index of -1 (a name the geometry lacks) is ignored by every writer.
 * 在 Bedrock 几何自带骨骼旋转之上的单帧姿态，使用 Bedrock 单位：旋转为角度（Bedrock 符号约定，叠加到几何旋转上），位移为
 * 模型像素（Bedrock 坐标轴），缩放为绕骨骼枢轴的倍率。动画与小凋灵的头部瞄准写入它，模型渲染与包围盒计算读取它。骨骼索引
 * 为 -1（几何中不存在的名字）时所有写入方法都会忽略。
 */
public final class BedrockPose {
    private final float[] rotation;
    private final float[] position;
    private final float[] scale;

    public BedrockPose(int boneCount) {
        if (boneCount < 0) {
            throw new IllegalArgumentException("boneCount < 0: " + boneCount);
        }
        rotation = new float[boneCount * 3];
        position = new float[boneCount * 3];
        scale = new float[boneCount * 3];
        Arrays.fill(scale, 1.0F);
    }

    public int boneCount() {
        return rotation.length / 3;
    }

    /** Back to the bind pose. / 恢复到绑定姿态。 */
    public void reset() {
        Arrays.fill(rotation, 0.0F);
        Arrays.fill(position, 0.0F);
        Arrays.fill(scale, 1.0F);
    }

    /** Drops what earlier layers wrote to one bone (Bedrock {@code override_previous_animation}). / 清除某骨骼已有的叠加。 */
    public void resetBone(int bone) {
        if (!valid(bone)) {
            return;
        }
        int i = bone * 3;
        Arrays.fill(rotation, i, i + 3, 0.0F);
        Arrays.fill(position, i, i + 3, 0.0F);
        Arrays.fill(scale, i, i + 3, 1.0F);
    }

    public void addRotation(int bone, float x, float y, float z) {
        add(rotation, bone, x, y, z);
    }

    public void addPosition(int bone, float x, float y, float z) {
        add(position, bone, x, y, z);
    }

    public void multiplyScale(int bone, float x, float y, float z) {
        if (!valid(bone) || !finite(x, y, z)) {
            return;
        }
        int i = bone * 3;
        scale[i] *= x;
        scale[i + 1] *= y;
        scale[i + 2] *= z;
    }

    public float rotationX(int bone) {
        return rotation[bone * 3];
    }

    public float rotationY(int bone) {
        return rotation[bone * 3 + 1];
    }

    public float rotationZ(int bone) {
        return rotation[bone * 3 + 2];
    }

    public float positionX(int bone) {
        return position[bone * 3];
    }

    public float positionY(int bone) {
        return position[bone * 3 + 1];
    }

    public float positionZ(int bone) {
        return position[bone * 3 + 2];
    }

    public float scaleX(int bone) {
        return scale[bone * 3];
    }

    public float scaleY(int bone) {
        return scale[bone * 3 + 1];
    }

    public float scaleZ(int bone) {
        return scale[bone * 3 + 2];
    }

    private void add(float[] target, int bone, float x, float y, float z) {
        if (!valid(bone) || !finite(x, y, z)) {
            return;
        }
        int i = bone * 3;
        target[i] += x;
        target[i + 1] += y;
        target[i + 2] += z;
    }

    private boolean valid(int bone) {
        return bone >= 0 && bone < boneCount();
    }

    /** A NaN or infinite channel (a broken Molang result) is dropped, never drawn. / 丢弃 NaN 或无穷值，绝不绘制。 */
    private static boolean finite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }
}
