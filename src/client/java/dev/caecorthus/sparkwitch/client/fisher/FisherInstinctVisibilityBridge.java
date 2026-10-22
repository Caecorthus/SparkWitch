package dev.caecorthus.sparkwitch.client.fisher;

import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import net.minecraft.entity.player.PlayerEntity;

/**
 * External seam (optional SparkTraits): the Angler's own copy of the public {@code SparkTraitsApi
 * .isInstinctHidden(viewer, target)} facade read, deliberately not another role's bridge. Fail-closed means "not
 * hidden": an absent SparkTraits, an older build without the method, a wrong return type or a throwing call all hide
 * nothing and never break the client. Reflection never targets SparkTraits internals.
 * 外部接缝（可选 SparkTraits）：钓鱼佬自有的一份公共 {@code SparkTraitsApi.isInstinctHidden(viewer, target)}
 * 门面读取，刻意不复用其他职业的桥。失败关闭即“不隐藏”：SparkTraits 缺失、旧版本缺少该方法、返回类型不符或调用抛异常时
 * 都不隐藏任何人，也绝不会让客户端崩溃。反射从不触及 SparkTraits 内部实现。
 */
public final class FisherInstinctVisibilityBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    // Static boolean only: a non-static or mistyped variant hides nothing, exactly as a failing call did.
    // 仅接受静态 boolean：非静态或类型不符的变体不隐藏任何人，与调用失败时完全一致。
    private static final OptionalStaticMethod IS_INSTINCT_HIDDEN = OptionalStaticMethod.of(MOD_ID, API_CLASS,
            "isInstinctHidden", PlayerEntity.class, PlayerEntity.class).returning(boolean.class);

    private FisherInstinctVisibilityBridge() {
    }

    public static boolean isHidden(PlayerEntity viewer, PlayerEntity target) {
        return viewer != null && target != null && IS_INSTINCT_HIDDEN.callBoolean(false, false, false, viewer, target);
    }
}
