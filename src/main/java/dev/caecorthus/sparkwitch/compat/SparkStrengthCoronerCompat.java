package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/**
 * SparkStrength 软兼容桥。
 *
 * <p>SparkWitch 不能硬依赖 SparkStrength；这里仅在模组实际加载时用反射询问
 * “验尸官是否正伪装为 SparkWitch 绑架者”。SparkStrength 不存在或旧版本无该方法时，
 * 迷药会自然只对真实绑架者生效。</p>
 */
public final class SparkStrengthCoronerCompat {
    private static final String MOD_ID = "sparkstrength";
    private static final String CORONER_SERVICE = "annina.sparkstrength.role.coroner.CoronerService";

    private SparkStrengthCoronerCompat() {
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
