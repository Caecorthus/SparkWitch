package dev.caecorthus.sparkwitch.client.hunter;

import dev.caecorthus.sparkwitch.client.bear.smoke.SmokeTargetOcclusion;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.killer.hunter.DoubleBarrelShotgunItem;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The two 15x15 crosshairs a loaded buckshot shows: the target one once the next shot would kill — at least the
 * {@link HunterRules#buckshotKillCount} threshold of pellet rays enter a player (or a live Magician puppet as its
 * copied player) — and the idle one otherwise. Only the buckshot state is taken over; a slug keeps Wathe's own target
 * crosshair, and an empty gun or another item keeps Wathe's 3x3. The 4-tick follow-up cooldown keeps the live sprite
 * (owner decision 2026-11-02) so a held trigger does not flicker between shots; any longer cooldown (seal, aura,
 * forced raise) shows the idle one.
 * 已装填霰弹时的两枚 15x15 准星：下一枪能打死人时——至少有 {@link HunterRules#buckshotKillCount} 门槛数量的弹丸射线
 * 进入玩家（或视同其复制玩家的存活魔术师皮套）——用目标准星，否则用空准星。只接管霰弹状态；独头弹保持 Wathe 自己的
 * 目标准星，空枪与其他物品保持 Wathe 的 3x3 准星。4 tick 连发冷却期间保留实时贴图（所有者 2026-11-02 决定），按住右键
 * 连发时不会在两次射击之间闪烁；更长的冷却（封印、光环、强制延长）显示空准星。
 */
public final class HunterShotgunCrosshair {
    public static final Identifier IDLE = Identifier.of("sparkwitch", "hud/double_barrel_crosshair_idle");
    public static final Identifier TARGET = Identifier.of("sparkwitch", "hud/double_barrel_crosshair_target");
    /** Sprite edge at the auto GUI scale, in GUI pixels. / 自动界面尺寸下的贴图边长（GUI 像素）。 */
    public static final int SIZE = 15;

    /** The shotgun pick's player box growth ({@code TARGET_BOX_EXPANSION}). / 猎枪选取玩家时的箱体扩大量。 */
    private static final double PLAYER_BOX_EXPANSION = 0.1D;

    private HunterShotgunCrosshair() {
    }

    /**
     * Matrix scale that draws the {@link #SIZE} sprite at its size under the window's automatic GUI scale whatever GUI
     * scale is chosen, so a smaller manual scale shrinks the rest of the HUD but not these two crosshairs. It is exact
     * in framebuffer pixels ({@code SIZE * auto}).
     * 使 {@link #SIZE} 贴图在任何界面尺寸下都按窗口自动界面尺寸的大小绘制的矩阵缩放：手动调小界面尺寸时其他 HUD 变小，
     * 这两枚准星不变；按帧缓冲像素精确（{@code SIZE * 自动尺寸}）。
     */
    public static float scaleFor(MinecraftClient client) {
        int autoScale = client.getWindow().calculateScaleFactor(0,
                client.options.getForceUnicodeFont().getValue());
        double currentScale = Math.max(1.0D, client.getWindow().getScaleFactor());
        return (float) (autoScale / currentScale);
    }

    /**
     * The custom sprite a loaded buckshot should draw right now, or {@code null} when the main hand is not a shotgun
     * whose next shell is a buckshot (empty and slug guns included).
     * 当前应绘制的自定义准星；主手不是下一发为霰弹的猎枪时（含空枪与独头弹）为 {@code null}。
     */
    @Nullable
    public static Identifier spriteFor(ClientPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof DoubleBarrelShotgunItem shotgun)
                || DoubleBarrelShotgunItem.peekShell(stack) != HunterRules.SHELL_BUCKSHOT) {
            return null;
        }
        return readyOrFollowUp(player, shotgun) && hasTarget(player, stack) ? TARGET : IDLE;
    }

    /**
     * No cooldown, or only the follow-up between two shells: a running cooldown no longer than
     * {@link HunterRules#FOLLOW_UP_COOLDOWN_TICKS} in total.
     * 没有冷却，或只是两发之间的连发冷却：当前冷却总时长不超过 {@link HunterRules#FOLLOW_UP_COOLDOWN_TICKS}。
     */
    private static boolean readyOrFollowUp(ClientPlayerEntity player, Item shotgun) {
        if (!player.getItemCooldownManager().isCoolingDown(shotgun)) {
            return true;
        }
        ItemCooldownManagerAccessor manager = (ItemCooldownManagerAccessor) player.getItemCooldownManager();
        return manager.sparkwitch$getEntries().get(shotgun) instanceof ItemCooldownEntryAccessor cooldown
                && cooldown.sparkwitch$getEndTick() - cooldown.sparkwitch$getStartTick()
                <= HunterRules.FOLLOW_UP_COOLDOWN_TICKS;
    }

    /**
     * The next shot would kill: a slug's single ray, or enough buckshot rays for
     * {@link HunterRules#buckshotKillCount} to kill one, enter a target. Rays are counted like the server's
     * {@code hit_rays}, once per ray however many targets it pierces. A puppet counts exactly as a player so the hint
     * never tells a puppet from its copied player; the server still decides every hit.
     * 下一枪能打死人：独头弹的单条射线，或足以让 {@link HunterRules#buckshotKillCount} 击杀一人的霰弹射线进入目标。射线
     * 按服务端 {@code hit_rays} 的方式计数，每条射线无论穿过几个目标都只算一次。皮套与玩家完全同样计数，因此提示不会
     * 区分皮套与其复制的玩家；是否命中仍由服务端决定。
     */
    public static boolean hasTarget(ClientPlayerEntity player, ItemStack stack) {
        int rays = raysOnTarget(player, stack);
        return DoubleBarrelShotgunItem.peekShell(stack) == HunterRules.SHELL_SLUG
                ? rays > 0
                : HunterRules.buckshotKillCount(rays, 1) > 0;
    }

    /**
     * Next-shot rays ({@link DoubleBarrelShotgunItem#shotAims}, block-clipped) that enter a visible player or a live
     * Magician puppet, both boxes grown like the shotgun pick. Puppets are tested on every ray, as the server does
     * ({@code MagicianPuppetHits}).
     * 下一发射线（{@link DoubleBarrelShotgunItem#shotAims}，按方块截断）中进入可见玩家或存活魔术师皮套的条数，两者箱体都与
     * 猎枪选取同样扩大。与服务端（{@code MagicianPuppetHits}）一样，每条射线都检查皮套。
     */
    private static int raysOnTarget(ClientPlayerEntity player, ItemStack stack) {
        List<DoubleBarrelShotgunItem.Aim> aims = DoubleBarrelShotgunItem.shotAims(player, stack);
        List<Box> targets = new ArrayList<>();
        // A candidate hidden by BEAR smoke does not light the hint, puppets included (hint only; always true with
        // no smoke). / 被 BEAR 烟雾挡住的候选者（含皮套）不点亮提示（仅提示；没有烟时恒为真）。
        Predicate<Entity> shown = SmokeTargetOcclusion.shownTo(player);
        for (AbstractClientPlayerEntity candidate : player.clientWorld.getPlayers()) {
            if (candidate != player && isVisibleCandidate(candidate) && shown.test(candidate)) {
                targets.add(candidate.getBoundingBox().expand(PLAYER_BOX_EXPANSION));
            }
        }
        Box reach = new Box(player.getEyePos(), player.getEyePos());
        for (DoubleBarrelShotgunItem.Aim aim : aims) {
            reach = reach.union(new Box(aim.eye(), aim.end()));
        }
        for (MagicianPlaybackEntity puppet : player.getWorld().getEntitiesByClass(MagicianPlaybackEntity.class,
                reach.expand(1.0D),
                candidate -> candidate.isAlive() && !candidate.isRemoved() && shown.test(candidate))) {
            targets.add(puppet.getBoundingBox().expand(PLAYER_BOX_EXPANSION));
        }
        if (targets.isEmpty()) {
            return 0;
        }
        int rays = 0;
        for (DoubleBarrelShotgunItem.Aim aim : aims) {
            if (HitscanLagRules.entryDistanceSquared(aim.eye(), aim.end(), targets) >= 0.0D) {
                rays++;
            }
        }
        return rays;
    }

    /**
     * Public state only, the same filter as Wathe's own gun crosshair: alive, not removed, in survival/adventure and
     * not invisible, so an invisible player never lights it. In a running round this matches the server's eligibility
     * (Wathe keeps every non-playing player in spectator); outside a round it may light where the server refuses.
     * 只看公开状态，与 Wathe 自身枪械准星相同：存活、未移除、处于生存/冒险且未隐身，因此隐身玩家永远不会点亮准星。
     * 对局进行中与服务端资格一致（Wathe 让所有非参与者保持旁观）；对局外可能点亮但服务端不会命中。
     */
    private static boolean isVisibleCandidate(AbstractClientPlayerEntity candidate) {
        return candidate.isAlive() && !candidate.isRemoved()
                && GameFunctions.isPlayerAliveAndSurvival(candidate)
                && !candidate.isInvisible();
    }
}
