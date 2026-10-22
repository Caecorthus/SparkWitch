package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.lang.reflect.Method;

/**
 * SparkStrength 软兼容桥。
 *
 * <p>SparkWitch 不能硬依赖 SparkStrength；这里仅在模组实际加载时用反射询问
 * “验尸官是否正伪装为 SparkWitch 绑架者”。SparkStrength 不存在或旧版本无该方法时，
 * 迷药会自然只对真实绑架者生效；并为魔人假死的假尸体与魔术师诱饵尸体写入验尸官读取的身份快照。</p>
 */
public final class SparkStrengthCoronerCompat {
    private static final String MOD_ID = "sparkstrength";
    private static final String CORONER_SERVICE = "annina.sparkstrength.role.coroner.CoronerService";
    /** SparkStrength's per-body role snapshot component and its public setter. / SparkStrength 尸体身份快照组件及其 public setter。 */
    static final Identifier BODY_SNAPSHOT_ID = Identifier.of(MOD_ID, "coroner_body_snapshot");
    static final String BODY_SNAPSHOT_SETTER = "setRoleId";
    private static volatile ComponentKey<?> snapshotKey;
    private static volatile SnapshotSetter snapshotSetter;

    private SparkStrengthCoronerCompat() {
    }

    /**
     * Writes the role SparkStrength's Coroner reads from a body SparkWitch spawned itself (the Fiend's feign-death
     * corpse, the Magician decoy); SparkStrength only snapshots bodies made inside Wathe's kill and otherwise falls back
     * to the owner's live role. Looked up by component id, setter called reflectively; absent, older or failing
     * SparkStrength leaves the body as is.
     * 为 SparkWitch 自行生成的尸体（魔人假死的假尸体、魔术师诱饵尸体）写入 SparkStrength 验尸官读取的身份；SparkStrength 只会快照 Wathe
     * 击杀流程中生成的尸体，否则回落到尸体主人的实时身份。按组件 id 查找、反射调用 setter；SparkStrength 缺失、过旧或失败时
     * 保持尸体不变。
     */
    public static void recordBodyRole(Entity body, Identifier roleId) {
        if (body == null || roleId == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        try {
            ComponentKey<?> key = snapshotKey();
            Object snapshot = key == null ? null : key.getNullable(body);
            if (snapshot != null) {
                Method setter = snapshotSetter(snapshot.getClass());
                if (setter != null) {
                    setter.invoke(snapshot, roleId);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Fail closed: the Coroner falls back to its own lookup. / 失败即关闭：验尸官回落到其自身查找。
        }
    }

    public static boolean hasKidnapperDisguise(PlayerEntity player) {
        if (player == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        Method disguise = DisguiseHolder.METHOD;
        if (disguise == null) {
            return false;
        }
        try {
            Object result = disguise.invoke(null, player);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * The snapshot key once registered; an absent key is not latched, so it is looked up again on the next call.
     * 快照组件键一经注册即缓存；缺失不会被锁定，下次调用会重新查找。
     */
    private static ComponentKey<?> snapshotKey() {
        ComponentKey<?> key = snapshotKey;
        if (key == null) {
            key = ComponentRegistry.get(BODY_SNAPSHOT_ID);
            snapshotKey = key;
        }
        return key;
    }

    /** The public setter of the snapshot's runtime class, cached per class (null when it has none). / 按快照运行时类缓存的 public setter（没有时为 null）。 */
    private static Method snapshotSetter(Class<?> type) {
        SnapshotSetter cached = snapshotSetter;
        if (cached == null || cached.type() != type) {
            Method setter;
            try {
                setter = type.getMethod(BODY_SNAPSHOT_SETTER, Identifier.class);
            } catch (NoSuchMethodException ignored) {
                setter = null;
            }
            cached = new SnapshotSetter(type, setter);
            snapshotSetter = cached;
        }
        return cached.setter();
    }

    private record SnapshotSetter(Class<?> type, Method setter) {
    }

    /**
     * Resolved once, after the mod-loaded guard (class init is thread-safe); SparkStrength's classes never change at
     * runtime, so a failed lookup is latched as null (not disguised).
     * 在模组加载守卫之后解析一次（类初始化线程安全）；SparkStrength 的类在运行期不会改变，因此查找失败锁定为 null（未伪装）。
     */
    private static final class DisguiseHolder {
        private static final Method METHOD = resolve();

        private static Method resolve() {
            try {
                return Class.forName(CORONER_SERVICE).getMethod("hasKidnapperDisguise", PlayerEntity.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
