package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMarkPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the local Black Raven's Feather marks and its held-instinct x-ray (owner 2026-10-21): while the instinct key
 * is held, every living perceived player within 8 blocks of the camera entity is outlined in the single
 * {@link BlackRavenRules#XRAY_COLOR}. The x-ray only adds outlines (an unperceived player is never answered here), shows
 * no role, gates on the RAW role so it also works while disguised, and stays off during Perception's own window.
 * 裁决本地黑羽鸦的羽刃标记与按住本能键时的透视（所有者 2026-10-21）：按住本能键时，镜头实体 8 格内每名存活的已感知玩家
 * 都以统一的 {@link BlackRavenRules#XRAY_COLOR} 描边。透视只会增加描边（未感知的玩家此处从不作答），不显示职业，按原始职业
 * 判定因此伪装期间同样生效，并在感知技能窗口期间关闭。
 */
public final class BlackRavenInstinctClientHooks {
    private static final int FEATHER_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 1;
    private static boolean registered;

    private BlackRavenInstinctClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(BlackRavenInstinctClientHooks::featherHighlight);
    }

    /** RETURN resolver ({@code BlackRavenInstinctResolverMixin}). / RETURN 阶段裁决器。 */
    public static int resolveHighlight(int originalColor, Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer != null && target instanceof PlayerEntity targetPlayer) {
            Integer vendettaHighlight = VendettaClientPresentation.highlight(viewer, targetPlayer);
            if (vendettaHighlight != null) {
                return vendettaHighlight;
            }
        }
        if (viewer == null || !BlackRavenClientState.isEligible(viewer)) {
            return originalColor;
        }

        boolean marked = isMarkedForLocalRaven(target);
        if (BlackRavenClientState.isPerceptionActive(viewer)) {
            return marked ? BlackRavenRules.COLOR : -1;
        }
        if (marked) {
            return BlackRavenRules.COLOR;
        }
        Integer xray = xrayHighlight(viewer, target);
        return xray != null ? xray : originalColor;
    }

    /**
     * Frozen reflective seam: SparkTraits' {@code SparkWitchBlackRavenBridge} calls this exact
     * {@code public static Integer resolvePrioritySensedHighlight(Entity)} at its {@code getInstinctHighlight} HEAD, before
     * its Conscience branch, and {@code BlackRavenInstinctPriorityMixin} calls it at HEAD priority 2000. {@code null}
     * means "not answered here"; a colour is the final x-ray answer. Keep the name and signature.
     * 冻结的反射接缝：SparkTraits 的 {@code SparkWitchBlackRavenBridge} 在其 {@code getInstinctHighlight} HEAD、善良分支之前
     * 调用这一精确签名，{@code BlackRavenInstinctPriorityMixin} 也在 HEAD 优先级 2000 调用。{@code null} 表示此处不作答；
     * 返回颜色即透视的最终结果。名称与签名必须保持不变。
     */
    public static @Nullable Integer resolvePrioritySensedHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !BlackRavenClientState.isEligible(viewer)) {
            return null;
        }
        return xrayHighlight(viewer, target);
    }

    /**
     * The x-ray answer for one target, or {@code null}. {@code WatheClient.isInstinctEnabled()} is the held key, so every
     * gate that closes it (Perception, Grand Witch Fear, the USEC cloak) also closes the x-ray; an explicit {@code skip}
     * from any listener and the hard hides in {@link #isPubliclyVisible} still win. A Feather-marked target keeps the
     * mark colour.
     * 单个目标的透视结果，或 {@code null}。{@code WatheClient.isInstinctEnabled()} 即按键状态，因此关闭它的门禁（感知、
     * 大魔女恐惧、USEC 隐蔽）同样关闭透视；任何监听器的显式 {@code skip} 与 {@link #isPubliclyVisible} 中的硬隐藏仍然优先。
     * 被羽刃标记的目标保持标记颜色。
     */
    private static @Nullable Integer xrayHighlight(ClientPlayerEntity viewer, Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer)
                || BlackRavenClientState.isPerceptionActive(viewer)
                || !WatheClient.isInstinctEnabled()
                || !isPubliclyVisible(viewer, targetPlayer)
                || BlackRavenPerceptionPlayerComponent.KEY.get(viewer).snapshot(targetPlayer.getUuid()) == null
                || !BlackRavenRules.isWithinXrayRadius(xrayCenter(viewer).squaredDistanceTo(targetPlayer))) {
            return null;
        }
        GetInstinctHighlight.HighlightResult eventResult = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        if (eventResult != null && eventResult.isSkip()) {
            return null;
        }
        return isMarkedForLocalRaven(target) ? BlackRavenRules.COLOR : BlackRavenRules.XRAY_COLOR;
    }

    /**
     * The camera entity, not the player: a later camera (the crow) moves the x-ray centre with it.
     * 以镜头实体而非玩家为中心：之后镜头切换（乌鸦）时透视中心随之移动。
     */
    private static Entity xrayCenter(ClientPlayerEntity viewer) {
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        return camera != null ? camera : viewer;
    }

    public static boolean isPubliclyVisible(PlayerEntity viewer, PlayerEntity target) {
        return target != viewer
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && !GameFunctions.isPlayerSpectatingOrCreative(target)
                && !target.isInvisibleTo(viewer)
                && !SparkTraitsInstinctVisibilityBridge.isHidden(viewer, target);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult featherHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || !BlackRavenClientState.isEligible(viewer)
                || !isMarkedForLocalRaven(target)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(BlackRavenRules.COLOR, FEATHER_PRIORITY);
    }

    private static boolean isMarkedForLocalRaven(Entity target) {
        return target instanceof PlayerEntity targetPlayer
                && BlackRavenMarkPlayerComponent.KEY.get(targetPlayer).isMarkedForLocalRaven();
    }
}
