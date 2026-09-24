package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaGunService;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Only the provider's public facade is inspected; missing protocols never invent a kill.
 * 仅访问提供方公共门面；缺失协议时不得猜测确认击杀。 */
public final class SparkTraitsGunBridge {
    private static final String API = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static boolean initialized;
    private static boolean cycleProtocol;
    private static Method intercepted;
    private static Method roleSkillBlocked;
    private static Method exactCooldown;
    private static Method cooldownTick;
    private static Method terminalReason;

    private SparkTraitsGunBridge() { }

    public static synchronized void register() {
        if (initialized) return;
        initialized = true;
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) return;
        try {
            Class<?> api = Class.forName(API);
            intercepted = api.getMethod("isLastStandDeathIntercepted", PlayerEntity.class);
            roleSkillBlocked = api.getMethod("isRoleSkillBlocked", PlayerEntity.class);
            terminalReason = api.getMethod("registerTerminalDeathReason", Identifier.class);
            terminalReason.invoke(null, SparkWitch.id("factor_backlash"));
            exactCooldown = api.getMethod("setExactItemCooldownRemaining", ServerPlayerEntity.class, Item.class, int.class);
            cooldownTick = api.getMethod("getItemCooldownTick", PlayerEntity.class);
            Class<?> listener = Class.forName(API + "$GunShotCycleListener");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener},
                    (object, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "SparkWitchEmmaGunListener";
                                case "hashCode" -> System.identityHashCode(object);
                                case "equals" -> object == args[0];
                                default -> null;
                            };
                        }
                        return switch (method.getName()) {
                            case "cycleStarted" -> {
                                EmmaGunService.cycleStarted((ServerPlayerEntity) args[0], (UUID) args[1], (Item) args[2]);
                                yield null;
                            }
                            case "beforeTargetKill" -> EmmaGunService.beforeTargetKill(
                                    (ServerPlayerEntity) args[0], (UUID) args[1], (ServerPlayerEntity) args[2]);
                            case "afterTargetKill" -> {
                                EmmaGunService.afterTargetKill((ServerPlayerEntity) args[0], (UUID) args[1],
                                        (ServerPlayerEntity) args[2], args[3]);
                                yield null;
                            }
                            case "initialCooldownEstablished" -> {
                                EmmaGunService.initialCooldownEstablished((ServerPlayerEntity) args[0], (UUID) args[1],
                                        (Item) args[2], (int) args[3], (int) args[4], (int) args[5]);
                                yield null;
                            }
                            case "cycleClosed" -> {
                                EmmaGunService.cycleClosed((ServerPlayerEntity) args[0], (UUID) args[1]);
                                yield null;
                            }
                            default -> null;
                        };
                    });
            api.getMethod("registerGunShotCycleListener", listener).invoke(null, proxy);
            cycleProtocol = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            SparkWitch.LOGGER.warn("SparkTraits does not expose the complete Emma protocol; provider-dependent Emma abilities are disabled.", exception);
        }
    }

    public static boolean isRoleSkillBlocked(PlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) return false;
        if (roleSkillBlocked == null) return true;
        try {
            return Boolean.TRUE.equals(roleSkillBlocked.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return true;
        }
    }

    public static boolean hasCycleProtocol() {
        return cycleProtocol;
    }

    public static boolean deathWasIntercepted(PlayerEntity target) {
        if (!FabricLoader.getInstance().isModLoaded("sparktraits")) return false;
        if (intercepted == null) return true;
        try {
            return Boolean.TRUE.equals(intercepted.invoke(null, target));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return true;
        }
    }

    public static boolean setExactCooldown(ServerPlayerEntity player, Item item, int ticks) {
        if (exactCooldown == null) return false;
        try {
            exactCooldown.invoke(null, player, item, Math.max(0, ticks));
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    public static int cooldownTick(ServerPlayerEntity player, int fallback) {
        if (cooldownTick == null) return fallback;
        try {
            return (int) cooldownTick.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return fallback;
        }
    }
}
