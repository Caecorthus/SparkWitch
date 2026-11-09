package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkfactionapi.api.FactionInstinctPolicy;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Specter client-side presentation: the always-on sanity outline. The server resolves the bands in
 * {@link SpecterPlayerComponent}; this policy only reads the owner-private synced map, so other viewers are unaffected.
 * 幽魂的客户端表现：常驻理智描边。区间由服务端在 {@link SpecterPlayerComponent} 中判定；本策略只读取仅本人同步的映射，
 * 其他观察者不受影响。
 */
public final class SpecterFeatureService {
    private static boolean registered;

    private SpecterFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionApi.registerInstinctPolicy(SpecterFeatureService::instinctHighlight);
    }

    private static FactionInstinctPolicy.InstinctResult instinctHighlight(
            PlayerEntity viewer,
            Entity target,
            GameWorldComponent gameComponent
    ) {
        if (!(target instanceof PlayerEntity targetPlayer) || targetPlayer == viewer) {
            return null;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(viewer)
                || GameFunctions.isPlayerSpectatingOrCreative(viewer)
                || !GameFunctions.isPlayerPlayingAndAlive(targetPlayer)
                || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)) {
            return null;
        }
        // Low-sanity real-mood players see the invisible Specter through walls in the role colour.
        // 低理智的真实理智玩家可以穿墙看到隐身的幽魂，使用职业色描边。
        if (SpecterRules.isSpecter(gameComponent.getRole(targetPlayer))
                && targetPlayer.isInvisible()
                && isLowSanityViewer(viewer, gameComponent)) {
            return FactionInstinctPolicy.InstinctResult.show(
                    SpecterRules.COLOR,
                    false,
                    SpecterRules.LOW_SANITY_SPECTER_OUTLINE_PRIORITY
            );
        }
        if (!SpecterRules.isSpecter(gameComponent.getRole(viewer))) {
            return null;
        }
        SpecterPlayerComponent component = SpecterPlayerComponent.KEY.get(viewer);
        // The active mark outranks every sanity band: the marked body is always the role colour.
        // 生效中的标记优先于所有理智区间：被标记的身体始终使用职业色。
        if (component.isMarked(targetPlayer.getUuid())) {
            return FactionInstinctPolicy.InstinctResult.show(
                    SpecterRules.COLOR,
                    false,
                    SpecterRules.MARK_OUTLINE_PRIORITY
            );
        }
        // Cowardice reads as a valid low-sanity target: blue-purple, above the normal sanity bands.
        // 怯懦按有效的低理智目标显示：蓝紫色，优先级高于普通理智区间。
        if (SpecterCowardiceComponent.KEY.get(targetPlayer).isCoward()) {
            return FactionInstinctPolicy.InstinctResult.show(
                    SpecterRules.COWARDICE_COLOR,
                    false,
                    SpecterRules.COWARDICE_OUTLINE_PRIORITY
            );
        }
        Integer color = component.highlightColor(targetPlayer.getUuid());
        if (color == null) {
            return null;
        }
        // requiresKeybind=false: the outline is always on, through walls, per the role design.
        // requiresKeybind=false：按职业设计，描边常驻且可穿墙。
        return FactionInstinctPolicy.InstinctResult.show(color, false, SpecterRules.OUTLINE_PRIORITY);
    }

    /** Real-mood player at or below 25 sanity, or a cowardly player of any mood type. / 真实理智不高于 25，或任何理智类型的怯懦者。 */
    private static boolean isLowSanityViewer(PlayerEntity viewer, GameWorldComponent gameComponent) {
        if (SpecterCowardiceComponent.KEY.get(viewer).isCoward()) {
            return true;
        }
        Role role = gameComponent.getRole(viewer);
        if (role == null || role.getMoodType() != Role.MoodType.REAL) {
            return false;
        }
        return PlayerMoodComponent.KEY.get(viewer).getMood() * 100.0F <= SpecterRules.LOW_SANITY_THRESHOLD;
    }
}
