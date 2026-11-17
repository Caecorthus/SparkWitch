package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.entity.NinjaGrapplingHookEntity;
import dev.caecorthus.sparkwitch.entity.NinjaShurikenEntity;
import dev.caecorthus.sparkwitch.entity.SphinxHaoFistEntity;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
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
    public static final Identifier NINJA_GRAPPLING_HOOK_ID = SparkWitch.id("ninja_grappling_hook");
    public static final Identifier SPHINX_HAO_FIST_ID = SphinxHaoRules.FIST_ENTITY_ID;

    private static EntityType<NinjaShurikenEntity> ninjaShuriken;
    private static EntityType<MagicianPlaybackEntity> magicianPlayback;
    private static EntityType<NinjaGrapplingHookEntity> ninjaGrapplingHook;
    private static EntityType<SphinxHaoFistEntity> sphinxHaoFist;
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
        // A plain Entity, never a ProjectileEntity, so projectile-only hooks never see it; one hook cycle, never saved.
        // 普通 Entity 而非 ProjectileEntity，只针对投射物的钩子不会处理它；只存活一次钩爪循环，从不保存。
        ninjaGrapplingHook = Registry.register(
                Registries.ENTITY_TYPE,
                NINJA_GRAPPLING_HOOK_ID,
                EntityType.Builder.<NinjaGrapplingHookEntity>create(NinjaGrapplingHookEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(1)
                        .disableSaving()
                        // Only a thrown item owns a hook; /summon would create an ownerless one.
                        // 只有投出的物品拥有钩爪；/summon 会产生无主钩爪。
                        .disableSummon()
                        .build(NINJA_GRAPPLING_HOOK_ID.toString())
        );
        // Sphinx Hao spring fist: presentation only (the shot is a server hitscan), a plain Entity like the grappling
        // hook, one shot long, never saved; appended last.
        // 狮身豪的弹簧拳头：仅用于表现（射击是服务端即时射线），与钩爪一样是普通 Entity，只存在一次射击，从不保存；追加在最后。
        sphinxHaoFist = Registry.register(
                Registries.ENTITY_TYPE,
                SPHINX_HAO_FIST_ID,
                EntityType.Builder.<SphinxHaoFistEntity>create(SphinxHaoFistEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F)
                        .maxTrackingRange(4)
                        .trackingTickInterval(1)
                        .disableSaving()
                        // Only a fired gun owns a fist; /summon would create an ownerless one.
                        // 只有开火的手枪拥有拳头；/summon 会产生无主拳头。
                        .disableSummon()
                        .makeFireImmune()
                        .build(SPHINX_HAO_FIST_ID.toString())
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

    public static EntityType<NinjaGrapplingHookEntity> ninjaGrapplingHook() {
        if (ninjaGrapplingHook == null) {
            throw new IllegalStateException("SparkWitch entities are not registered yet");
        }
        return ninjaGrapplingHook;
    }

    public static EntityType<SphinxHaoFistEntity> sphinxHaoFist() {
        if (sphinxHaoFist == null) {
            throw new IllegalStateException("SparkWitch entities are not registered yet");
        }
        return sphinxHaoFist;
    }
}
