package dev.caecorthus.sparkwitch.client.riftwalker.gate;

import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateEntity;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam (D9): Wathe {@link GetInstinctHighlight} listener for Rift Gates only. A live witch-faction viewer
 * (Grand Witch, plain Accomplice or any special accomplice, read from the RAW {@code getRole}, never the Black Raven
 * acting role) holding instinct sees every gate through walls in the Riftwalker colour; everyone else gets
 * {@code null}, so Wathe's default (which never outlines a gate) applies. Key-gated, so the Control Expert Disruptor and
 * the SparkAssist instinct toggle keep working; priority {@code SUPPRESSION_PRIORITY - 1}, the Hunter-trap / Seeker
 * slot, so fear/obscure/Final Moment suppression still wins. Presentation only: gates are visible to everyone anyway.
 * 客户端接缝（D9）：仅针对裂隙门的 Wathe {@link GetInstinctHighlight} 监听器。存活的魔女阵营观察者（大魔女、普通共犯或
 * 任一特殊共犯，读取真实 {@code getRole}，从不读取黑羽鸦扮演职业）按住本能时以隙行者颜色隔墙看到所有门；其他人返回
 * {@code null}，交给 Wathe 默认逻辑（从不描边门）。需按键，因此控场专家干扰器与 SparkAssist 本能开关仍然生效；优先级为
 * {@code SUPPRESSION_PRIORITY - 1}（与猎人陷阱、搜寻者相同），恐惧/遮蔽/最终时刻压制仍然优先。仅为表现：门本来对所有人可见。
 */
public final class RiftGateInstinctHooks {
    public static final int HIGHLIGHT_PRIORITY = WitchInstinctSuppressionClientHooks.SUPPRESSION_PRIORITY - 1;
    private static boolean registered;

    private RiftGateInstinctHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(RiftGateInstinctHooks::gateHighlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult gateHighlight(Entity target) {
        if (!(target instanceof RiftGateEntity)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        boolean outlines = RiftGatePresentationRules.outlinesGate(SparkWitchServerConnection.isConfirmedServer(),
                GameFunctions.isPlayerPlayingAndAlive(viewer), WitchFactionRules.isGrandWitch(role),
                WitchFactionRules.isAccompliceLike(role));
        return outlines
                ? GetInstinctHighlight.HighlightResult.withKeybind(RiftGatePresentationRules.OUTLINE_COLOR,
                        HIGHLIGHT_PRIORITY)
                : null;
    }
}
