package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.entity.NinjaShurikenEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkWitchEntities {
    public static final Identifier NINJA_SHURIKEN_ID = SparkWitch.id("ninja_shuriken");
    public static final Identifier MAGICIAN_PLAYBACK_ID = SparkWitch.id("magician_playback");

    private static EntityType<NinjaShurikenEntity> ninjaShuriken;
    private static EntityType<MagicianPlaybackEntity> magicianPlayback;
    private static boolean registered;

    private SparkWitchEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ninjaShuriken = Registry.register(
                Registries.ENTITY_TYPE,
                NINJA_SHURIKEN_ID,
                EntityType.Builder.<NinjaShurikenEntity>create(NinjaShurikenEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(1)
                        .build(NINJA_SHURIKEN_ID.toString())
        );
        // 播放体是可追踪的非生物实体；服务端只用它承载轨迹，伤害由魔术师管理器显式处理。
        magicianPlayback = Registry.register(
                Registries.ENTITY_TYPE,
                MAGICIAN_PLAYBACK_ID,
                EntityType.Builder.<MagicianPlaybackEntity>create(MagicianPlaybackEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6F, 1.8F)
                        .maxTrackingRange(92)
                        .trackingTickInterval(1)
                        // 皮套是 LivingEntity 子类，不应被存档持久化；播放结束后由管理器负责清理。
                        .disableSaving()
                        // Only the playback manager spawns puppets; /summon would create an ownerless puppet.
                        // 只有回放管理器生成皮套；/summon 会产生无主皮套。
                        .disableSummon()
                        .build(MAGICIAN_PLAYBACK_ID.toString())
        );
        /*
         * 自定义 LivingEntity 必须有默认属性容器。自改版 NoellesRoles 在实体注册后
         * 显式执行了同样的注册；若缺失，开始播放时实体初始化会失败，客户端表现为
         * 点击播放没有任何反应，录制阶段则不受影响。
         */
        FabricDefaultAttributeRegistry.register(
                magicianPlayback,
                MagicianPlaybackEntity.createAttributes()
        );
        registered = true;
    }

    public static EntityType<NinjaShurikenEntity> ninjaShuriken() {
        if (ninjaShuriken == null) {
            throw new IllegalStateException("SparkWitch entities are not registered yet");
        }
        return ninjaShuriken;
    }

    public static EntityType<MagicianPlaybackEntity> magicianPlayback() {
        if (magicianPlayback == null) throw new IllegalStateException("SparkWitch entities are not registered yet");
        return magicianPlayback;
    }
}
