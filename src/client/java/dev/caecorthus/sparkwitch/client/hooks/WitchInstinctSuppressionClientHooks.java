package dev.caecorthus.sparkwitch.client.hooks;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.civilian.apprentice.ApprenticeFearExemption;
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

import java.lang.reflect.Method;

/**
 * High-priority client gate for Grand Witch area spells that must beat always-on role highlights.
 * 大魔女范围法术的高优先级客户端拦截，用来压过常驻角色高亮。
 */
public final class WitchInstinctSuppressionClientHooks {
    public static final int SUPPRESSION_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 2;
    private static final String SPARKTRAITS_MOD_ID = "sparktraits";
    private static final String SPARK_TRAITS_API_CLASS =
            "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    // Resolved once: Fabric's mod set and classpath are fixed after launch, so a missing mod, class or method stays
    // missing (latched) and a found facade method stays valid. Render thread only; a race just resolves twice.
    // 只解析一次：Fabric 启动后模组集合与类路径固定，缺失的模组、类或方法会一直缺失（锁存），找到的门面方法也一直有效。
    // 仅渲染线程使用；并发最多导致重复解析。
    private static Method finalMomentActiveMethod;
    private static boolean finalMomentActiveUnavailable;

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
        // The Apprentice Witch is spell-immune and a Healing ward lifts Fear (owner 2026-10-06 D4/D8); her own skill
        // outlines then answer before Obscure inside WitchInstinctPolicy.
        // 预备魔女不受法术影响，疗愈庇护解除恐惧（所有者 2026-10-06 D4/D8）；她自己的技能描边随后在 WitchInstinctPolicy
        // 中先于障眼作答。
        boolean apprentice = viewerRole == SparkWitchRoles.apprenticeWitch();
        // An active Final Moment only ever lifts a suppression (the rule returns false first), so it is passed as
        // false and the reflective SparkTraits read runs only when Fear or Obscure would suppress: same answer.
        // 激活的最终时刻只会解除压制（规则会先返回 false），因此此处传入 false，仅当恐惧或障眼会生效时才反射读取
        // SparkTraits：结果不变。
        return WitchFactionRules.shouldSuppressAffectedInstinctHighlight(
                witchWorld.getFearTicks() > 0 && !ApprenticeFearExemption.isExempt(viewer),
                witchWorld.isInstinctObscured() && !apprentice,
                viewerRole,
                GameFunctions.isPlayerPlayingAndAlive(viewer),
                false
        ) && !isSparkTraitsFinalMomentActive(world);
    }

    public static boolean shouldSuppressSwallowedInstinctHighlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        // NoellesRoles is a hard dependency: read its synced swallowed flag directly (a missing component reads false).
        // NoellesRoles 是硬依赖：直接读取其同步的被吞标记（组件缺失时视为 false）。
        if (NoellesTaotieSeekerBridge.isSwallowed(viewer)) {
            return true;
        }
        return target instanceof PlayerEntity targetPlayer && NoellesTaotieSeekerBridge.isSwallowed(targetPlayer);
    }

    private static boolean isSparkTraitsFinalMomentActive(World world) {
        if (world == null) {
            return false;
        }
        Method activeMethod = finalMomentActiveMethod;
        if (activeMethod == null) {
            activeMethod = resolveFinalMomentActiveMethod();
            if (activeMethod == null) {
                return false;
            }
        }
        try {
            return Boolean.TRUE.equals(activeMethod.invoke(null, world));
        } catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Method resolveFinalMomentActiveMethod() {
        if (finalMomentActiveUnavailable) {
            return null;
        }
        if (!FabricLoader.getInstance().isModLoaded(SPARKTRAITS_MOD_ID)) {
            finalMomentActiveUnavailable = true;
            return null;
        }
        try {
            // Reflect only the stable public facade; missing or incompatible Traits fails closed.
            // 只反射稳定公共门面；Traits 缺失或不兼容时按 false 安全回退。
            Class<?> apiClass = Class.forName(SPARK_TRAITS_API_CLASS);
            Method activeMethod = apiClass.getMethod("isFinalMomentActive", World.class);
            finalMomentActiveMethod = activeMethod;
            return activeMethod;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            finalMomentActiveUnavailable = true;
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
