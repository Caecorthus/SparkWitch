package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * SparkStrength 软兼容桥。
 *
 * <p>SparkWitch 不能硬依赖 SparkStrength；这里仅在模组实际加载时用反射询问
 * “验尸官是否正伪装为 SparkWitch 绑架者”。SparkStrength 不存在或旧版本无该方法时，
 * 迷药会自然只对真实绑架者生效；并为大魔女招募的假尸体写入验尸官读取的身份快照。</p>
 */
public final class SparkStrengthCoronerCompat {
    private static final String MOD_ID = "sparkstrength";
    private static final String CORONER_SERVICE = "annina.sparkstrength.role.coroner.CoronerService";
    /** SparkStrength's per-body role snapshot component and its public setter. / SparkStrength 尸体身份快照组件及其 public setter。 */
    static final Identifier BODY_SNAPSHOT_ID = Identifier.of(MOD_ID, "coroner_body_snapshot");
    static final String BODY_SNAPSHOT_SETTER = "setRoleId";

    private SparkStrengthCoronerCompat() {
    }

    /**
     * Writes the role SparkStrength's Coroner reads from a body SparkWitch spawned itself (the Grand Witch recruit's fake
     * corpse); SparkStrength only snapshots bodies made inside Wathe's kill. Looked up by component id, setter called
     * reflectively; absent, older or failing SparkStrength leaves the body as is.
     * 为 SparkWitch 自行生成的尸体（大魔女招募的假尸体）写入 SparkStrength 验尸官读取的身份；SparkStrength 只会快照 Wathe
     * 击杀流程中生成的尸体。按组件 id 查找、反射调用 setter；SparkStrength 缺失、过旧或失败时保持尸体不变。
     */
    public static void recordBodyRole(Entity body, Identifier roleId) {
        if (body == null || roleId == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        try {
            ComponentKey<?> key = ComponentRegistry.get(BODY_SNAPSHOT_ID);
            Object snapshot = key == null ? null : key.getNullable(body);
            if (snapshot != null) {
                snapshot.getClass().getMethod(BODY_SNAPSHOT_SETTER, Identifier.class).invoke(snapshot, roleId);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Fail closed: the Coroner falls back to its own lookup. / 失败即关闭：验尸官回落到其自身查找。
        }
    }

    public static boolean hasKidnapperDisguise(PlayerEntity player) {
        if (player == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return false;
        }
        try {
            Class<?> service = Class.forName(CORONER_SERVICE);
            Object result = service.getMethod("hasKidnapperDisguise", PlayerEntity.class).invoke(null, player);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
