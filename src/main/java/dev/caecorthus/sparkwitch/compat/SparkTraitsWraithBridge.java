package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional Wraith integration through SparkTraits' public, generic facade only.
 * 冤魂仅通过 SparkTraits 公共通用门面进行可选集成。
 */
public final class SparkTraitsWraithBridge {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final Identifier CAUTIOUS = Identifier.of("sparktraits", "cautious");

    private static volatile ApiMethods methods;
    private static volatile boolean lookupFailed;

    private SparkTraitsWraithBridge() {
    }

    public static TraitSnapshot capture(PlayerEntity player) {
        ApiMethods api = methods();
        if (api == null || player == null) {
            return TraitSnapshot.unavailable();
        }
        try {
            return new TraitSnapshot(
                    identifiers(api.getActiveTraitIds().invoke(null, player)),
                    identifiers(api.getRevealedTraitIds().invoke(null, player)),
                    true
            );
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return TraitSnapshot.unavailable();
        }
    }

    public static void restoreWithCautious(ServerPlayerEntity player, TraitSnapshot snapshot) {
        ApiMethods api = methods();
        if (api == null || player == null || snapshot == null || !snapshot.available()) {
            return;
        }
        LinkedHashSet<Identifier> active = new LinkedHashSet<>(snapshot.activeTraitIds());
        LinkedHashSet<Identifier> revealed = new LinkedHashSet<>(snapshot.revealedTraitIds());
        active.add(CAUTIOUS);
        revealed.add(CAUTIOUS);
        invokeRestore(api, player, active, revealed);
    }

    public static void clearRuntimeTraits(ServerPlayerEntity player) {
        ApiMethods api = methods();
        if (api != null && player != null) {
            invokeRestore(api, player, List.of(), Set.of());
        }
    }

    /**
     * Checks whether Last Stand approved or is still processing this exact death attempt.
     * 检查背水一战是否已批准或仍在处理本次精确死亡尝试。
     */
    public static boolean isLastStandDeathIntercepted(PlayerEntity player) {
        ApiMethods api = methods();
        if (player == null) {
            return false;
        }
        if (api == null) {
            return FabricLoader.getInstance().isModLoaded("sparktraits");
        }
        try {
            return Boolean.TRUE.equals(api.isLastStandDeathIntercepted().invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return FabricLoader.getInstance().isModLoaded("sparktraits");
        }
    }

    private static void invokeRestore(
            ApiMethods api,
            ServerPlayerEntity player,
            Collection<Identifier> active,
            Collection<Identifier> revealed
    ) {
        try {
            api.restoreActiveTraitsForRuntime().invoke(null, player, active, revealed);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            // SparkTraits is optional; an incompatible build must not break Wraith gameplay.
            // SparkTraits 是可选依赖；不兼容版本不得破坏冤魂玩法。
        }
    }

    private static List<Identifier> identifiers(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream().filter(Identifier.class::isInstance).map(Identifier.class::cast).toList();
    }

    private static ApiMethods methods() {
        if (lookupFailed) {
            return null;
        }
        ApiMethods cached = methods;
        if (cached != null) {
            return cached;
        }
        synchronized (SparkTraitsWraithBridge.class) {
            if (methods != null || lookupFailed) {
                return methods;
            }
            try {
                Class<?> api = Class.forName(API_CLASS);
                methods = new ApiMethods(
                        api.getMethod("getActiveTraitIds", PlayerEntity.class),
                        api.getMethod("getRevealedTraitIds", PlayerEntity.class),
                        api.getMethod(
                                "restoreActiveTraitsForRuntime",
                                ServerPlayerEntity.class,
                                Collection.class,
                                Collection.class
                        ),
                        api.getMethod("isLastStandDeathIntercepted", PlayerEntity.class)
                );
                return methods;
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                lookupFailed = true;
                return null;
            }
        }
    }

    private record ApiMethods(
            Method getActiveTraitIds,
            Method getRevealedTraitIds,
            Method restoreActiveTraitsForRuntime,
            Method isLastStandDeathIntercepted
    ) {
    }

    public record TraitSnapshot(
            List<Identifier> activeTraitIds,
            List<Identifier> revealedTraitIds,
            boolean available
    ) {
        public TraitSnapshot {
            activeTraitIds = List.copyOf(activeTraitIds);
            revealedTraitIds = List.copyOf(revealedTraitIds);
        }

        public static TraitSnapshot unavailable() {
            return new TraitSnapshot(List.of(), List.of(), false);
        }
    }
}
