package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Amendment fix 1: a disguised Raven keeps Wathe's killer-teammate colors. Acting-role highlights returned through
 * {@code GetInstinctHighlight.EVENT} (NoellesRoles and SparkTraits event listeners at PRIORITY_DEFAULT) would
 * otherwise repaint teammates, so this listener re-states Wathe 1.5.6's own killer branch of
 * {@code WatheClient.getInstinctHighlight} for raw killer-team targets, above PRIORITY_DEFAULT but below explicit
 * skips (PRIORITY_HIGH) and the Feather mark (PRIORITY_HIGH + 1).
 * It outranks only event results. SparkTraits' HEAD-cancel mixin {@code client/mixin/WatheClientMixin
 * #getInstinctHighlight} (Toxicologist, Serial Killer, Morphling and trait branches) returns before the event runs,
 * so no listener priority can beat it. Batch 2 gate: before {@code noellesroles:toxicologist} becomes selectable,
 * the teammate color must be placed ahead of that HEAD path (a higher-priority SparkWitch HEAD inject on
 * {@code WatheClient.getInstinctHighlight} or a SparkTraits public-facade check), with a test.
 * 修订 1：伪装中的黑羽鸦保留 Wathe 杀手队友颜色。通过 {@code GetInstinctHighlight.EVENT} 返回的扮演职业高亮
 * （诺艾尔与 SparkTraits 的默认优先级事件监听器）否则会覆盖队友颜色；此监听器按 Wathe 1.5.6
 * getInstinctHighlight 的杀手分支，对真实杀手阵营目标给出同样结果，优先级高于默认、低于显式跳过（PRIORITY_HIGH）
 * 与羽刃标记（PRIORITY_HIGH + 1）。它只能压过事件结果：SparkTraits 在 HEAD 取消的 mixin
 * （WatheClientMixin#getInstinctHighlight，含毒理学家、连环杀手、变形者与词条分支）在事件触发前就已返回，
 * 任何监听器优先级都无法胜过。批次二门槛：毒理学家可选之前，必须把队友颜色置于该 HEAD 路径之前
 * （更高优先级的 SparkWitch HEAD 注入或 SparkTraits 公共门面检查），并补充测试。
 */
public final class BlackRavenDisguiseInstinctClientHooks {
    /** Wathe's teammate red: {@code MathHelper.hsvToRgb(0F, 1.0F, 0.6F)}. / Wathe 杀手队友红色。 */
    public static final int KILLER_TEAMMATE_COLOR = MathHelper.hsvToRgb(0.0F, 1.0F, 0.6F);
    public static final int KILLER_TEAMMATE_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 50;
    private static boolean registered;

    private BlackRavenDisguiseInstinctClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(BlackRavenDisguiseInstinctClientHooks::killerTeammateHighlight);
    }

    static @Nullable GetInstinctHighlight.HighlightResult killerTeammateHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer() || !(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || targetPlayer == viewer
                || !BlackRavenDisguiseClientState.isDisguised(viewer)
                // Wathe's default path runs only while killer instinct is active (key held, raw killer viewer).
                // Wathe 默认逻辑只在杀手本能生效时运行（按住本能键且真实身份为杀手）。
                || !WatheClient.isInstinctEnabledAndIsKiller()
                || WatheClient.canSeeSpectatorInformation()
                || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)) {
            return null;
        }
        // Raw read on purpose: canUseKillerFeatures follows getRole, which the acting overlay never widens.
        // 有意读取真实身份：canUseKillerFeatures 基于 getRole，扮演覆盖层从不放宽它。
        if (!GameWorldComponent.KEY.get(viewer.getWorld()).canUseKillerFeatures(targetPlayer)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.withKeybind(KILLER_TEAMMATE_COLOR, KILLER_TEAMMATE_PRIORITY);
    }
}
