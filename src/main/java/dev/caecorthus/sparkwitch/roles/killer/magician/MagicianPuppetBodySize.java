package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.OptionalStaticMethod;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * A puppet is as big as the player it copies (owner decision D6, forum T-0013, 2026-10-25). The only body-size rule
 * today is SparkTraits' Childish trait: a {@code generic.scale} modifier ({@code sparktraits:childish_scale}, -0.25
 * multiplied total, so 0.75) that vanilla applies to the model, shadow, hitbox and eye height. The server copies it onto
 * the puppet once at spawn, so every viewer (copied player loaded or not) and every puppet hit get that size through
 * vanilla's tracked-attribute sync; no packet, tracked data or NBT is added.
 * <ul>
 *     <li>copied player online, playing and alive: their live modifier, verbatim (no modifier, no shrink);</li>
 *     <li>otherwise (dead, spectating, offline): SparkTraits' round-end traits through the public facade (the death
 *     snapshot, else the assignment snapshot), so a dead Childish player's puppet stays small and the size never
 *     reveals a death (D7).</li>
 * </ul>
 * Fails closed: without SparkTraits or without an answer the puppet keeps the normal size.
 * 皮套与被复制玩家一样大（所有者决定 D6，论坛 T-0013，2026-10-25）。目前唯一的体型规则是 SparkTraits 的幼稚词条：一个
 * {@code generic.scale} 修饰符（{@code sparktraits:childish_scale}，-0.25 乘算总值，即 0.75），原版会把它应用到模型、阴影、
 * 碰撞箱与视线高度。服务端在生成时把它复制到皮套上一次，因此所有观察者（无论是否加载了被复制玩家）和所有皮套命中判定都经由
 * 原版的追踪属性同步得到该体型；不新增数据包、追踪数据或 NBT。被复制玩家在线、参与且存活时：原样复制其实时修饰符（没有则
 * 不缩小）；否则（已死亡、旁观、离线）：经公共门面读取 SparkTraits 本局最终词条（死亡快照，否则为分配快照），使已死亡幼稚
 * 玩家的皮套仍然偏小，体型永不暴露死亡（D7）。失败即关闭：没有 SparkTraits 或得不到答案时皮套保持正常体型。
 */
public final class MagicianPuppetBodySize {
    static final Identifier CHILDISH_TRAIT = Identifier.of("sparktraits", "childish");
    static final Identifier CHILDISH_SCALE = Identifier.of("sparktraits", "childish_scale");
    // SparkTraits' own Childish modifier (GlobalTraitService), which its facade does not expose; used only when the
    // copied player no longer carries it. / SparkTraits 自身的幼稚修饰符（GlobalTraitService），门面未公开；仅在被复制
    // 玩家身上已不再带有该修饰符时使用。
    static final EntityAttributeModifier CHILDISH_SNAPSHOT_SCALE = new EntityAttributeModifier(CHILDISH_SCALE, -0.25,
            EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static final OptionalStaticMethod ROUND_END_TRAIT_IDS = OptionalStaticMethod.of("sparktraits",
                    "dev.caecorthus.sparktraits.api.SparkTraitsApi", "getRoundEndTraitIds",
                    ServerWorld.class, UUID.class)
            .returning(List.class)
            .warnOnce(error -> SparkWitch.LOGGER.warn(
                    "Magician puppets cannot read SparkTraits round-end traits; they keep the normal size", error));

    private MagicianPuppetBodySize() {
    }

    /** Server only, before the puppet spawns. / 仅服务端，在皮套生成之前调用。 */
    public static void copyOnto(MagicianPlaybackEntity puppet, ServerWorld world, @Nullable UUID copied) {
        EntityAttributeInstance scale = puppet.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale == null || copied == null) {
            return;
        }
        EntityAttributeModifier childish = childishScale(world, copied);
        if (childish != null && !scale.hasModifier(childish.id())) {
            scale.addTemporaryModifier(childish);
            puppet.calculateDimensions();
        }
    }

    private static @Nullable EntityAttributeModifier childishScale(ServerWorld world, UUID copied) {
        ServerPlayerEntity live = world.getServer().getPlayerManager().getPlayer(copied);
        if (live != null && GameFunctions.isPlayerPlayingAndAlive(live)) {
            EntityAttributeInstance liveScale = live.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
            return choose(true, liveScale == null ? null : liveScale.getModifier(CHILDISH_SCALE), null);
        }
        return choose(false, null, ROUND_END_TRAIT_IDS.callOrNull(world, copied));
    }

    /**
     * The modifier a puppet takes: a playing, alive copied player's live one; else the snapshot one when SparkTraits'
     * round-end traits list Childish. / 皮套采用的修饰符：参与且存活的被复制玩家用其实时修饰符；否则仅当 SparkTraits 本局
     * 最终词条含幼稚时使用快照修饰符。
     */
    static @Nullable EntityAttributeModifier choose(boolean playingAlive, @Nullable EntityAttributeModifier live,
                                                    @Nullable Object roundEndTraits) {
        if (playingAlive) {
            return live;
        }
        return roundEndTraits instanceof Collection<?> traits && traits.contains(CHILDISH_TRAIT)
                ? CHILDISH_SNAPSHOT_SCALE
                : null;
    }
}
