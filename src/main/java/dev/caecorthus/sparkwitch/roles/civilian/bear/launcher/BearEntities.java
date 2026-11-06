package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Frozen contract (WP0): registers the BEAR entity type {@code sparkwitch:smoke_round_40mm}. The type is created when
 * this class initialises, which {@link #register()} forces during mod init (after the sounds, before the items), like
 * {@code SparkWitchDataComponents}; never touch {@link #SMOKE_ROUND} from code that may run before mod init.
 * 冻结契约（WP0）：注册 BEAR 实体类型 {@code sparkwitch:smoke_round_40mm}。该类型在本类初始化时创建，{@link #register()} 在模组
 * 初始化期间（音效之后、物品之前）强制触发，做法与 {@code SparkWitchDataComponents} 相同；可能早于模组初始化运行的代码不得
 * 访问 {@link #SMOKE_ROUND}。
 */
public final class BearEntities {
    public static final EntityType<SmokeRoundEntity> SMOKE_ROUND = Registry.register(
            Registries.ENTITY_TYPE,
            BearRules.SMOKE_ROUND_ENTITY_ID,
            EntityType.Builder.<SmokeRoundEntity>create(SmokeRoundEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25F, 0.25F)
                    .maxTrackingRange(8)
                    .trackingTickInterval(10)
                    .build(BearRules.SMOKE_ROUND_ENTITY_ID.toString())
    );

    private BearEntities() {
    }

    /** Forces class initialisation during mod init. / 在模组初始化时强制加载本类。 */
    public static void register() {
    }
}
