package dev.caecorthus.sparkwitch.client.blind.gate;

import dev.caecorthus.sparkwitch.client.blind.BlindPerceptionClientState;
import dev.caecorthus.sparkwitch.client.blind.BlindView;
import dev.caecorthus.sparkwitch.client.render.WraithClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Per-call client hooks behind the {@code BlindGate*} mixins. Every answer re-reads {@link BlindView#isActive}, so the
 * gates switch off the same frame the view does. Cheap checks (is it another player?) run first.
 * {@code BlindGate*} mixin 背后的逐次调用客户端钩子。每次都重新读取 {@link BlindView#isActive}，因此闸门与视图在同一帧关闭。
 * 先执行开销小的判断（是否为其他玩家）。
 */
public final class BlindClientGates {
    private BlindClientGates() {
    }

    /** The view is active: hide HUD leaks, outlines, the hand and the block outline. / 视图生效：隐藏 HUD 泄露、描边、手与方块框。 */
    public static boolean viewActive() {
        return BlindView.isActive(MinecraftClient.getInstance());
    }

    /**
     * D2: an unperceived other player, or a spectator, is not drawn at all; neither is a fishing bobber whose owner is
     * hidden, because its line is drawn to the owner's hand.
     * D2：未被感知的其他玩家或旁观者完全不画；主人被隐藏的浮漂也不画，因为钓线会连到主人手上。
     */
    public static boolean hidesEntity(@Nullable Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity instanceof FishingBobberEntity bobber) {
            return hidesPlayer(client, bobber.getPlayerOwner());
        }
        return hidesPlayer(client, entity);
    }

    /**
     * D4: a perceived other player draws its body even when invisible. Spectators (dead, swallowed, Last Stand pending)
     * would show only a floating head and active Wraiths are never perceivable (D4), so neither is forced.
     * D4：被感知的其他玩家即使隐身也画出身体。旁观者（死亡、被吞、最后一搏待定）只会显示漂浮的头，
     * 活跃的冤魂永远不可被感知（D4），因此两者都不强制显示。
     */
    public static boolean forcesVisible(@Nullable Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isOtherPlayer(client, entity) || !BlindView.isActive(client)) {
            return false;
        }
        return BlindGateRules.forcesVisible(true, true, isPerceived(entity), entity.isSpectator(),
                WraithClientState.isActive((PlayerEntity) entity));
    }

    /**
     * No held item, armor, cape or mod feature on other players, and only the empty arm pose (no gun, bat or knife
     * stance).
     * 其他玩家不画手持物、护甲、披风与模组附加层，手臂只用空手姿势（无持枪、持棒或持刀姿势）。
     */
    public static boolean suppressesFeatures(@Nullable Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isOtherPlayer(client, entity)) {
            return false;
        }
        return BlindGateRules.suppressesFeatures(BlindView.isActive(client), true);
    }

    private static boolean hidesPlayer(MinecraftClient client, @Nullable Entity entity) {
        if (!isOtherPlayer(client, entity) || !BlindView.isActive(client)) {
            return false;
        }
        return BlindGateRules.hidesEntity(true, true, isPerceived(entity), entity.isSpectator());
    }

    private static boolean isOtherPlayer(MinecraftClient client, @Nullable Entity entity) {
        return entity instanceof PlayerEntity && client.player != null && entity.getId() != client.player.getId();
    }

    private static boolean isPerceived(Entity entity) {
        return BlindPerceptionClientState.get().isPerceived(entity.getId(), Util.getMeasuringTimeNano());
    }
}
