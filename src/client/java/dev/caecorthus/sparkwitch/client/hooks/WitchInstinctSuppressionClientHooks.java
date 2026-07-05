package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
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
    private static final String SWALLOWED_PLAYER_COMPONENT_CLASS =
            "org.agmas.noellesroles.taotie.SwallowedPlayerComponent";
    private static Method swallowedPlayerCheckMethod;
    private static boolean swallowedPlayerCheckUnavailable;

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
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null) {
            return false;
        }

        World world = viewer.getWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        WitchWorldComponent witchWorld = WitchWorldComponent.KEY.get(world);
        Role viewerRole = gameComponent.getRole(viewer);
        return WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                witchWorld.getFearTicks() > 0,
                witchWorld.isInstinctObscured(),
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                GameFunctions.isPlayerSpectatingOrCreative(viewer),
                isSparkTraitsFinalMomentActive(world)
        );
    }

    public static boolean shouldSuppressSwallowedInstinctHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        if (isNoellesPlayerSwallowed(viewer)) {
            return true;
        }
        return target instanceof PlayerEntity targetPlayer && isNoellesPlayerSwallowed(targetPlayer);
    }

    private static boolean isNoellesPlayerSwallowed(PlayerEntity player) {
        if (player == null || swallowedPlayerCheckUnavailable) {
            return false;
        }
        try {
            Method method = swallowedPlayerCheckMethod;
            if (method == null) {
                Class<?> componentClass = Class.forName(SWALLOWED_PLAYER_COMPONENT_CLASS);
                method = componentClass.getMethod("isPlayerSwallowed", PlayerEntity.class);
                swallowedPlayerCheckMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(null, player));
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            swallowedPlayerCheckUnavailable = true;
            return false;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
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
