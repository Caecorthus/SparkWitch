package dev.caecorthus.sparkwitch.client.bear.smoke.occlusion;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeClientState;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeOcclusion;
import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeVolumes;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/**
 * Client-only: Wathe's crosshair labels (a player's name and role, a corpse's name, role and death info) are not
 * shown for a target hidden by BEAR smoke, matching the outline mask. "Hidden" means the CPU optical depth from the
 * viewer's eye to the point the crosshair ray hits the target's 0.3-expanded box (the margin {@code getCollision} uses;
 * the box centre when the eye is inside it) reaches {@link SmokeOcclusion#HIDDEN_OPTICAL_DEPTH}, so aiming at a head
 * above the smoke still reads the name. Narrowing the raycast predicate lets the ray pass through a hidden target to a
 * visible one behind it. Notes are left alone, and this never touches server-side line of sight.
 * 纯客户端：被 BEAR 烟雾挡住的目标不显示 Wathe 准星标签（玩家的名字与职业、尸体的名字、职业与死亡信息），与描边遮罩一致。
 * “挡住”指从观察者眼睛到准星射线命中目标外扩 0.3 的碰撞箱处（{@code getCollision} 使用的外扩；眼睛在箱内时取箱中心）的
 * CPU 光学厚度达到 {@link SmokeOcclusion#HIDDEN_OPTICAL_DEPTH}，因此瞄准烟上方露出的头仍能看到名字。收窄射线判定使射线
 * 能穿过被挡住的目标命中其后可见的目标。纸条不受影响，也从不改动服务端视线判定。
 */
public final class SmokeLabelOcclusion {
    /** Same entity margin as {@code ProjectileUtil.getCollision(Entity, Predicate, double)}. / 与其相同的实体外扩。 */
    private static final double TARGET_MARGIN = 0.3;

    private SmokeLabelOcclusion() {
    }

    /**
     * Wraps one {@code RoleNameRenderer} crosshair predicate; returns it unchanged when no smoke exists.
     * 包装一个 {@code RoleNameRenderer} 准星判定；不存在烟时原样返回。
     */
    public static Predicate<Entity> filterLabelTarget(Entity viewer, Predicate<Entity> predicate, double range) {
        if (SmokeClientState.isEmpty()) {
            return predicate;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || SmokeVolumes.prepare(world).isEmpty()) {
            return predicate;
        }
        Vec3d eye = viewer.getEyePos();
        Vec3d end = eye.add(viewer.getRotationVec(1.0F).multiply(range));
        return entity -> predicate.test(entity) && !(isLabelled(entity) && hiddenBySmoke(eye, end, entity));
    }

    private static boolean isLabelled(Entity entity) {
        return entity instanceof PlayerEntity || entity instanceof PlayerBodyEntity;
    }

    private static boolean hiddenBySmoke(Vec3d eye, Vec3d end, Entity entity) {
        Box box = entity.getBoundingBox().expand(TARGET_MARGIN);
        Vec3d aim = box.raycast(eye, end).orElseGet(box::getCenter);
        return SmokeVolumes.opticalDepth(eye.x, eye.y, eye.z, aim.x, aim.y, aim.z)
                >= SmokeOcclusion.HIDDEN_OPTICAL_DEPTH;
    }
}
