package dev.caecorthus.sparkwitch.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Weak bridge into the public SparkTraits API without a compile-time dependency.
 * 通过弱反射读取 SparkTraits 公共 API，避免编译期强依赖。
 */
public final class SparkTraitsLastStandBridge {
    private static final String MOD_ID = "sparktraits";
    private static final String SPARK_TRAITS_API_CLASS =
            "dev.caecorthus.sparktraits.api.SparkTraitsApi";

    // Shared by the Seeker and Abyss Listener bridges: gated on SparkTraits, static boolean shape, class not
    // initialised by the lookup; older SparkTraits builds lack this facade: silently "not pending".
    // 由搜寻者与聆渊者桥接共用：以 SparkTraits 已加载为门控、检查静态 boolean 形状、查找时不初始化类；
    // 旧版缺少此门面：静默视为非待定。
    private static final OptionalStaticMethod PENDING = OptionalStaticMethod.of(MOD_ID, SPARK_TRAITS_API_CLASS,
            "isLastStandPending", PlayerEntity.class).returning(boolean.class);

    private SparkTraitsLastStandBridge() {
    }

    public static boolean hasTriggeredThisRound(ServerWorld world, UUID playerUuid) {
        return world != null && playerUuid != null
                && Triggered.METHOD.callBoolean(false, false, false, world, playerUuid);
    }

    /**
     * Whether SparkTraits Last Stand currently holds this player's pending death transition; absent, older or
     * failing providers read as false. Both sides.
     * SparkTraits 背水一战当前是否持有该玩家的待决死亡转换；提供方缺失、过旧或出错时为 false。两端可用。
     */
    public static boolean isLastStandPending(@Nullable PlayerEntity player) {
        return player != null && PENDING.callBoolean(false, false, false, player);
    }

    /** Server-only query, held apart so a client reading {@link #isLastStandPending} never touches it.
     * 仅服务端的查询，单独持有，使读取 {@link #isLastStandPending} 的客户端从不触及它。 */
    private static final class Triggered {
        // No mod check, as before: an absent SparkTraits is a missing class, which reads false like any other failure.
        // 与此前一致不检查模组：缺失 SparkTraits 即类缺失，与其他失败一样返回 false。
        private static final OptionalStaticMethod METHOD = OptionalStaticMethod.ungated(SPARK_TRAITS_API_CLASS,
                "hasLastStandTriggeredThisRound", ServerWorld.class, UUID.class).initializingClass();
    }
}
