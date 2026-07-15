package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.client.hunter.HunterTrapVisibilityHelper;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import dev.caecorthus.sparkwitch.roles.neutral.murderouswitch.MurderousWitchRules.MurderousWitchRules;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Registers Hunter trap model loading and the Wathe instinct-outline bridge.
 * 注册猎人捕兽夹模型加载与 Wathe 本能描边桥接。
 */
public final class HunterTrapClientHooks {
    public static final Identifier HUNTER_TRAP_PLACED_MODEL_ID = SparkWitch.id("item/hunter_trap_placed");
    public static final int HIGHLIGHT_PRIORITY =
            WitchInstinctSuppressionClientHooks.SUPPRESSION_PRIORITY - 1;
    private static boolean registered;

    private HunterTrapClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ModelLoadingPlugin.register(context -> context.addModels(HUNTER_TRAP_PLACED_MODEL_ID));
        GetInstinctHighlight.EVENT.register(HunterTrapClientHooks::trapHighlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult trapHighlight(Entity target) {
        if (!(target instanceof HunterTrapEntity trap)) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || HunterTrapVisibilityHelper.visibilityFor(trap, viewer)
                != HunterRules.TrapVisibility.THROUGH_WALL) {
            return null;
        }

        if (HunterTrapVisibilityHelper.usesAlwaysOnOutline(viewer)) {
            return GetInstinctHighlight.HighlightResult.always(HunterRules.COLOR, HIGHLIGHT_PRIORITY);
        }

        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        if (role == null) {
            return null;
        }
        Identifier roleId = role.identifier();
        if (HunterRules.MURDEROUS_WITCH_ROLE_ID.equals(roleId)) {
            return GetInstinctHighlight.HighlightResult.withKeybind(
                    MurderousWitchRules.INSTINCT_COLOR,
                    HIGHLIGHT_PRIORITY
            );
        }
        if (HunterRules.GRAND_WITCH_ROLE_ID.equals(roleId)
                || HunterRules.ACCOMPLICE_ROLE_ID.equals(roleId)) {
            return GetInstinctHighlight.HighlightResult.withKeybind(
                    WitchFactionRules.NON_WITCH_INSTINCT_COLOR,
                    HIGHLIGHT_PRIORITY
            );
        }
        return null;
    }
}
