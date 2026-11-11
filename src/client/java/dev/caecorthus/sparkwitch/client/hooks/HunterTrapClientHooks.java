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
    // Class-1 dismantlers outrank every other outline while the channel runs (owner 2026-11-11).
    // 第一类拆除引导期间的高亮优先级高于其他描边（所有者 2026-11-11）。
    private static final int DISMANTLER_HIGHLIGHT_PRIORITY =
            GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 10;
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
        GetInstinctHighlight.EVENT.register(HunterTrapClientHooks::dismantlerHighlight);
    }

    /**
     * While a class-1 player is channelling a dismantle on a trap, the Hunter sees that player outlined in the
     * player's own role colour. The trap syncs its dismantler, so every client agrees on when the channel started and
     * the entity clears it once the presses stop.
     * 第一类玩家正在引导拆除时，猎人能看到该玩家以自身职业色描边。夹子同步了拆除者，因此每个客户端对引导何时开始是一致的，
     * 按键停止后由实体自行清除。
     */
    @Nullable
    private static GetInstinctHighlight.HighlightResult dismantlerHighlight(Entity target) {
        if (!(target instanceof PlayerEntity targetPlayer)) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || targetPlayer == viewer) {
            return null;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(viewer.getWorld());
        Role viewerRole = gameComponent.getRole(viewer);
        if (viewerRole == null || !HunterRules.ROLE_ID.equals(viewerRole.identifier())) {
            return null;
        }
        boolean dismantling = !viewer.getWorld().getEntitiesByClass(
                HunterTrapEntity.class,
                targetPlayer.getBoundingBox().expand(4.0D),
                candidate -> targetPlayer.getUuid().equals(candidate.getDismantlerUuid())
        ).isEmpty();
        if (!dismantling) {
            return null;
        }
        Role targetRole = gameComponent.getRole(targetPlayer);
        int color = targetRole == null ? 0xFFFFFF : targetRole.color() & 0xFFFFFF;
        return GetInstinctHighlight.HighlightResult.always(color, DISMANTLER_HIGHLIGHT_PRIORITY);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult trapHighlight(Entity target) {
        if (!(target instanceof HunterTrapEntity)) {
            return null;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        // Only THROUGH_WALL gets an outline and it never depends on line of sight, so this skips the trap raycast.
        // 只有 THROUGH_WALL 才描边，它与视线无关，因此这里跳过捕兽夹射线检测。
        if (viewer == null || !HunterTrapVisibilityHelper.isVisibleThroughWalls(viewer)) {
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
        // Every accomplice, special ones and the Bewitched included, shares the Grand Witch trap color.
        // 所有共犯（含特殊共犯与魔化使）与大魔女使用同一捕兽夹颜色。
        if (HunterRules.GRAND_WITCH_ROLE_ID.equals(roleId)
                || HunterRules.ACCOMPLICE_ROLE_ID.equals(roleId)
                || HunterRules.BEWITCHED_ROLE_ID.equals(roleId)
                || WitchFactionRules.isAccompliceVariantId(roleId)) {
            return GetInstinctHighlight.HighlightResult.withKeybind(
                    WitchFactionRules.NON_WITCH_INSTINCT_COLOR,
                    HIGHLIGHT_PRIORITY
            );
        }
        return null;
    }
}
