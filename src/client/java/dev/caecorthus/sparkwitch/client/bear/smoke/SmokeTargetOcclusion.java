package dev.caecorthus.sparkwitch.client.bear.smoke;

import dev.caecorthus.sparkwitch.client.bear.smoke.occlusion.SmokeLabelOcclusion;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Client-only, stable contract (owner 2026-11-10): the one answer to "does BEAR smoke hide this target from the local
 * viewer?" for every HUD target hint (Wathe's gun, knife and bat crosshair, SparkWitch's own weapon crosshairs) and for
 * the label raycasts SparkWitch adds. Players and corpses are answered by {@link SmokeLabelOcclusion} itself, so a hint
 * and a name label can never disagree. The targets it leaves alone get its rule on the same primitives: a Magician
 * puppet (it must read exactly as its copied player), a Raven Form crow, a Seeker device. With no smoke every method
 * returns its input and does no work. Presentation only: ask this for what the HUD shows, never for what a shot or
 * stab sends; packets and the server's line of sight are untouched. Render thread only.
 * 纯客户端稳定契约（所有者 2026-11-10）：所有 HUD 目标提示（Wathe 的枪、刀、球棒准星，SparkWitch 自己的武器准星）以及
 * SparkWitch 自行发出的标签射线，对“BEAR 烟雾是否把该目标挡在本地观察者视线外”只有这一个答案。玩家与尸体由
 * {@link SmokeLabelOcclusion} 本身回答，因此提示与名字标签不会不一致；它不处理的目标按同一规则、同一组基础函数判断：
 * 魔术师皮套（必须与被复制的玩家表现完全一致）、化鸦乌鸦、搜寻者设备。没有烟时所有方法原样返回且不做任何计算。
 * 仅用于展示：只用来决定 HUD 显示什么，绝不用来决定开枪或刺击发送什么；数据包与服务端视线判定不变。仅限渲染线程。
 */
public final class SmokeTargetOcclusion {
    private static final Predicate<Entity> ANY = entity -> true;
    /** Longer than any hint reach (AXMC AP 260), so the look ray always reaches an aimed target. / 长于任何提示射程。 */
    private static final double HINT_REACH = 512.0;
    /** {@link SmokeLabelOcclusion}'s entity margin. / 与 {@link SmokeLabelOcclusion} 相同的实体外扩。 */
    private static final double TARGET_MARGIN = 0.3;

    private SmokeTargetOcclusion() {
    }

    /**
     * The targets smoke does not hide from {@code viewer} this frame; the same always-true instance whenever no smoke
     * exists. Build it once per query and test each candidate.
     * 本帧未被烟雾挡住的目标；不存在烟时始终返回同一个恒真实例。每次查询构建一次，再逐个测试候选者。
     */
    public static Predicate<Entity> shownTo(Entity viewer) {
        Predicate<Entity> labelRule = SmokeLabelOcclusion.filterLabelTarget(viewer, ANY, HINT_REACH);
        if (labelRule == ANY) {
            return ANY;
        }
        Vec3d eye = viewer.getEyePos();
        Vec3d end = eye.add(viewer.getRotationVec(1.0F).multiply(HINT_REACH));
        return entity -> labelRule.test(entity)
                && (entity instanceof PlayerEntity || !hiddenBySameRule(eye, end, entity));
    }

    /** Whether smoke hides {@code target} from {@code viewer}; false for {@code null}. / 烟雾是否挡住该目标。 */
    public static boolean hides(Entity viewer, @Nullable Entity target) {
        return target != null && !shownTo(viewer).test(target);
    }

    /**
     * A hint raycast result: a hit on a hidden entity reads as a miss, anything else is returned unchanged.
     * 提示射线的结果：命中被挡住的实体时视为未命中，其余原样返回。
     */
    public static HitResult visibleHit(Entity viewer, HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && hides(viewer, entityHit.getEntity())) {
            Vec3d pos = hit.getPos();
            return BlockHitResult.createMissed(pos, Direction.UP, BlockPos.ofFloored(pos));
        }
        return hit;
    }

    /**
     * A label raycast predicate: {@link SmokeLabelOcclusion}'s filter, which also drops a hidden Magician puppet (it is
     * labelled as its copied player, who would be hidden there). Apply it outside the puppet widening.
     * 标签射线判定：{@link SmokeLabelOcclusion} 的过滤，并额外去掉被挡住的魔术师皮套（皮套以被复制玩家的名义显示标签，
     * 而该玩家在同一位置会被挡住）。须套在皮套放宽之外。
     */
    public static Predicate<Entity> filterLabelTarget(Entity viewer, Predicate<Entity> predicate, double range) {
        Predicate<Entity> filtered = SmokeLabelOcclusion.filterLabelTarget(viewer, predicate, range);
        if (filtered == predicate) {
            return predicate;
        }
        Vec3d eye = viewer.getEyePos();
        Vec3d end = eye.add(viewer.getRotationVec(1.0F).multiply(range));
        return entity -> filtered.test(entity)
                && !(entity instanceof MagicianPlaybackEntity && hiddenBySameRule(eye, end, entity));
    }

    /**
     * {@link SmokeLabelOcclusion}'s rule for an entity it does not answer for: optical depth from the eye to where the
     * look ray enters the grown box (its centre when the ray misses it). Keep the two in step.
     * {@link SmokeLabelOcclusion} 对其不处理的实体所用的同一规则：从眼睛到视线进入外扩碰撞箱处（未进入时取箱中心）的光学厚度。
     * 两处须保持一致。
     */
    private static boolean hiddenBySameRule(Vec3d eye, Vec3d end, Entity entity) {
        Box box = entity.getBoundingBox().expand(TARGET_MARGIN);
        Vec3d aim = box.raycast(eye, end).orElseGet(box::getCenter);
        return SmokeVolumes.opticalDepth(eye.x, eye.y, eye.z, aim.x, aim.y, aim.z)
                >= SmokeOcclusion.HIDDEN_OPTICAL_DEPTH;
    }
}
