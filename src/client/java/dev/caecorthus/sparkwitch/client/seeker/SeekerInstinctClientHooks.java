package dev.caecorthus.sparkwitch.client.seeker;

import dev.caecorthus.sparkwitch.client.hooks.WitchInstinctSuppressionClientHooks;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client seam: Wathe {@link GetInstinctHighlight} listener for Seeker devices only (plan §3.15). Priority
 * {@code SUPPRESSION_PRIORITY - 1}, the same slot as Hunter traps, so fear/obscure suppression still wins. The owner
 * sees their own car with an always-on dim outline; instinct viewers get a key-gated outline, which keeps the CE
 * Disruptor gate and the SparkAssist instinct toggle in force because both act on Wathe's keyed instinct check.
 * Cameras are explicitly never outlined. Ownership is read from the owner-only synced car entity id, never from the
 * entity (device owners are server-only).
 * 客户端接缝：仅针对搜寻者设备的 Wathe {@link GetInstinctHighlight} 监听器（计划 §3.15）。优先级为
 * {@code SUPPRESSION_PRIORITY - 1}，与猎人陷阱相同，因此恐惧/遮蔽压制仍然优先。拥有者对自己的小车看到常亮暗色描边；
 * 有本能的观察者得到按键描边，控场专家干扰器与 SparkAssist 本能切换都作用于 Wathe 的按键本能判定，因此依旧生效。
 * 摄像头明确从不描边。归属只读取仅同步给拥有者的小车实体 id，从不读取实体本身（设备拥有者仅存在于服务端）。
 */
public final class SeekerInstinctClientHooks {
    public static final int HIGHLIGHT_PRIORITY = WitchInstinctSuppressionClientHooks.SUPPRESSION_PRIORITY - 1;
    private static boolean registered;

    private SeekerInstinctClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(SeekerInstinctClientHooks::sparkwitch$deviceHighlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult sparkwitch$deviceHighlight(Entity target) {
        if (!(target instanceof SeekerDeviceEntity device) || !SparkWitchServerConnection.isConfirmedServer()) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return null;
        }
        boolean own = SeekerInstinctRules.isOwnCar(SeekerClientState.carEntityId(), device.getId());
        return switch (SeekerInstinctRules.deviceOutline(device.kind(), own, !own && hasInstinct(viewer))) {
            case OWN -> GetInstinctHighlight.HighlightResult.always(SeekerRules.OWN_DEVICE_COLOR, HIGHLIGHT_PRIORITY);
            case INSTINCT -> GetInstinctHighlight.HighlightResult.withKeybind(SeekerRules.CAR_INSTINCT_COLOR,
                    HIGHLIGHT_PRIORITY);
            case HIDDEN -> new GetInstinctHighlight.HighlightResult(-1, false, HIGHLIGHT_PRIORITY);
            case NONE -> null;
        };
    }

    private static boolean hasInstinct(ClientPlayerEntity viewer) {
        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        // isKiller() already counts SparkTraits Impostors (it reads canUseKillerFeatures).
        // isKiller() 已包含 SparkTraits 内鬼（它读取 canUseKillerFeatures）。
        return SeekerInstinctRules.hasInstinct(WatheClient.isPlayerPlayingAndAlive(), WatheClient.isKiller(),
                WatheClient.canSeeSpectatorInformation(), role == null ? null : role.identifier());
    }
}
