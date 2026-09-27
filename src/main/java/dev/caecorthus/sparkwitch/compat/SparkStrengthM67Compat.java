package dev.caecorthus.sparkwitch.compat;

/**
 * External seam: SparkStrength M67 blasts break Seeker devices via {@code ServerEntityEvents.ENTITY_UNLOAD} and two
 * cached, fail-closed reflective public getters; inert when SparkStrength is absent.
 * TODO(WP-04): implement. / 待 WP-04 实现。
 * 外部接缝：SparkStrength M67 爆炸经 {@code ServerEntityEvents.ENTITY_UNLOAD} 与两个带缓存、失败即关闭的反射 public getter
 * 打坏搜寻者设备；未安装 SparkStrength 时不生效。
 */
public final class SparkStrengthM67Compat {
    private SparkStrengthM67Compat() {
    }

    public static void register() {
        // TODO(WP-04) / 待 WP-04 实现
    }
}
