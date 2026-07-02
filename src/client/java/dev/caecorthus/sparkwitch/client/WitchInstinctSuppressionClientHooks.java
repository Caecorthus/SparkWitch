package dev.caecorthus.sparkwitch.client;

import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.impl.GrandWitchRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * High-priority client gate for Grand Witch area spells that must beat always-on role highlights.
 * 大魔女范围法术的高优先级客户端拦截，用来压过常驻角色高亮。
 */
public final class WitchInstinctSuppressionClientHooks {
    public static final int SUPPRESSION_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 2;
    private static final String SPARKTRAITS_MOD_ID = "sparktraits";
    private static final String TRAIT_WORLD_COMPONENT_CLASS =
            "dev.caecorthus.sparktraits.component.TraitWorldComponent";

    private WitchInstinctSuppressionClientHooks() {
    }

    public static void register() {
        GetInstinctHighlight.EVENT.register(WitchInstinctSuppressionClientHooks::suppressAffectedInstinctHighlight);
    }

    private static GetInstinctHighlight.HighlightResult suppressAffectedInstinctHighlight(Entity target) {
        if (!shouldSuppressInstinctHighlight()) {
            return null;
        }
        return new GetInstinctHighlight.HighlightResult(-1, false, SUPPRESSION_PRIORITY);
    }

    public static boolean shouldSuppressInstinctHighlight() {
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }

        World world = viewer.getWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        WitchWorldComponent witchWorld = WitchWorldComponent.KEY.get(world);
        Role viewerRole = gameComponent.getRole(viewer);
        return GrandWitchRules.shouldSuppressAffectedInstinctHighlight(
                witchWorld.getFearTicks() > 0,
                witchWorld.isInstinctObscured(),
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                isSparkTraitsFinalMomentActive(world)
        );
    }

    private static boolean isSparkTraitsFinalMomentActive(World world) {
        if (world == null || !FabricLoader.getInstance().isModLoaded(SPARKTRAITS_MOD_ID)) {
            return false;
        }
        try {
            Class<?> componentClass = Class.forName(TRAIT_WORLD_COMPONENT_CLASS);
            Field keyField = componentClass.getField("KEY");
            ComponentKey<?> key = (ComponentKey<?>) keyField.get(null);
            Object component = key.get(world);
            Method activeMethod = componentClass.getMethod("isFinalMomentActive");
            return Boolean.TRUE.equals(activeMethod.invoke(component));
        } catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
