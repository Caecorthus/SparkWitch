package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Weak, fail-closed body query through SparkTraits' public API only.
 * 仅通过 SparkTraits 公共 API 弱反射查询尸体，并在兼容失败时拒绝拖动。
 */
public final class SparkTraitsBodyDragBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final OptionalStaticMethod IS_FAKE_DEATH_BODY = OptionalStaticMethod.of(
            MOD_ID, API_CLASS, "isFakeDeathBody", Entity.class).initializingClass();

    private SparkTraitsBodyDragBridge() {
    }

    /** Absent SparkTraits drags any body; a missing or failing facade (null) never does.
     * 未安装 SparkTraits 时可拖动任意尸体；门面缺失或出错（null）时一律不可拖动。 */
    public static boolean canDragBody(Entity body) {
        return canDragFromQuery(IS_FAKE_DEATH_BODY.isModLoaded(),
                IS_FAKE_DEATH_BODY.callNullableBoolean(null, null, null, body));
    }

    /**
     * True only when SparkTraits positively reports a Depression fake-death body; absent or broken API reads as real,
     * so Death Sense keeps working without SparkTraits.
     * 仅当 SparkTraits 明确报告为抑郁假死尸体时返回 true；API 缺失或异常视为真尸体，保证无 SparkTraits 时死亡感知照常工作。
     */
    public static boolean isConfirmedFakeDeathBody(Entity body) {
        return IS_FAKE_DEATH_BODY.callBoolean(false, false, false, body);
    }

    static boolean canDragFromQuery(boolean loaded, @Nullable Boolean fakeDeathBody) {
        return !loaded || Boolean.FALSE.equals(fakeDeathBody);
    }
}
