package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * External seam: the Abyss Listener's own optional SparkTraits reads, through the public {@code SparkTraitsApi}
 * facade only ({@code isLastStandPending(PlayerEntity)} and {@code hasActiveTrait(PlayerEntity, Identifier)} for
 * Conscience and Impostor); never {@code sparktraits.impl} or {@code sparktraits.component}. Lookups are cached and a
 * wrong shape is never trusted. Absent SparkTraits: not pending, both traits FALSE. Present but a method missing or
 * failing: not pending (the spectator gate still excludes a pending Last Stand player), and trait queries read as
 * unknown ({@code null}) so the caller fails closed.
 * 外部接缝：聆渊者自有的可选 SparkTraits 读取，仅经由公共门面 {@code SparkTraitsApi}（{@code isLastStandPending} 与
 * 针对良知、内鬼的 {@code hasActiveTrait}），从不触及 {@code impl} 或 {@code component} 包。查询结果被缓存，形状不符的方法
 * 永不被信任。未安装：非待定，两个词条均为 FALSE。已安装但方法缺失或出错：非待定（旁观判定仍会排除背水一战待定的玩家），
 * 词条查询视为未知（{@code null}），由调用方失败关闭。
 */
public final class SparkTraitsAbyssListenerBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    static final Identifier CONSCIENCE = Identifier.of(MOD_ID, "conscience");
    static final Identifier IMPOSTOR = Identifier.of(MOD_ID, "impostor");

    private static volatile boolean pendingResolved;
    private static volatile @Nullable Method pendingMethod;
    private static volatile boolean traitResolved;
    private static volatile @Nullable Method traitMethod;

    private SparkTraitsAbyssListenerBridge() {
    }

    /**
     * Whether SparkTraits Last Stand currently holds this player's pending death transition; absent, older or
     * failing providers read as false.
     * SparkTraits 背水一战当前是否持有该玩家的待决死亡转换；提供方缺失、过旧或出错时为 false。
     */
    public static boolean isLastStandPending(@Nullable PlayerEntity player) {
        if (player == null || !isLoaded()) {
            return false;
        }
        Method method = pendingMethod();
        if (method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, player));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /** Conscience trait: FALSE when SparkTraits is absent, null when unknown. / 良知词条：未安装为 FALSE，未知为 null。 */
    public static @Nullable Boolean isConscienceActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, CONSCIENCE);
    }

    /** Impostor trait: FALSE when SparkTraits is absent, null when unknown. / 内鬼词条：未安装为 FALSE，未知为 null。 */
    public static @Nullable Boolean isImpostorActive(@Nullable PlayerEntity player) {
        return hasActiveTrait(player, IMPOSTOR);
    }

    private static @Nullable Boolean hasActiveTrait(@Nullable PlayerEntity player, Identifier traitId) {
        if (!isLoaded()) {
            return Boolean.FALSE;
        }
        if (player == null) {
            return null;
        }
        Method method = traitMethod();
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, player, traitId) instanceof Boolean active ? active : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded(MOD_ID);
        } catch (LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static @Nullable Method pendingMethod() {
        if (!pendingResolved) {
            synchronized (SparkTraitsAbyssListenerBridge.class) {
                if (!pendingResolved) {
                    pendingMethod = resolve("isLastStandPending", PlayerEntity.class);
                    pendingResolved = true;
                }
            }
        }
        return pendingMethod;
    }

    private static @Nullable Method traitMethod() {
        if (!traitResolved) {
            synchronized (SparkTraitsAbyssListenerBridge.class) {
                if (!traitResolved) {
                    traitMethod = resolve("hasActiveTrait", PlayerEntity.class, Identifier.class);
                    traitResolved = true;
                }
            }
        }
        return traitMethod;
    }

    private static @Nullable Method resolve(String name, Class<?>... parameters) {
        try {
            Method method = Class.forName(API_CLASS, false, SparkTraitsAbyssListenerBridge.class.getClassLoader())
                    .getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class ? method : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
