package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDamageRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * External seam: SparkStrength M67 blasts break Seeker devices via {@code ServerEntityEvents.ENTITY_UNLOAD} and two
 * cached, fail-closed reflective public getters; inert when SparkStrength is absent. SparkStrength exposes no blast
 * event, so a detonation is recognised heuristically: an {@code sparkstrength:m67} entity unloaded with reason
 * DISCARDED whose {@code getDetonateAt()} is set and already reached, and whose {@code getThrowerUuid()} player is
 * online (M67 kills players first, then discards itself). Only a match grenade breaks devices
 * ({@link #breaksDevices}, the {@link dev.caecorthus.sparkwitch.util.OffMatchUse} rule): the Wathe status is exactly
 * ACTIVE and the thrower holds a match role. SparkStrength discards every in-flight M67 on a phase change, so a
 * detonation during STOPPING was thrown during STOPPING; that grenade, like a non-participant's, is presentation-only.
 * The blast then breaks devices in a 3.5-block sphere with line of sight to the grenade centre, attributed to the
 * thrower; the same blast ends Magician puppets in the M67's own kill radius ({@code MagicianPuppetHits.onM67Blast}).
 * Only this class may name SparkStrength implementation classes, and only those two getters (pinned by a
 * source test); any lookup failure disables the seam for the session, never throwing into entity unloading.
 * 外部接缝：SparkStrength M67 爆炸经 {@code ServerEntityEvents.ENTITY_UNLOAD} 与两个带缓存、失败即关闭的反射 public getter
 * 打坏搜寻者设备；未安装 SparkStrength 时不生效。SparkStrength 没有爆炸事件，因此以启发式识别引爆：
 * {@code sparkstrength:m67} 实体以 DISCARDED 原因卸载、{@code getDetonateAt()} 已设置且已到时、
 * {@code getThrowerUuid()} 对应玩家在线（M67 先击杀玩家再移除自身）。只有对局手雷会打坏设备
 * （{@link #breaksDevices}，即 {@code OffMatchUse} 规则）：Wathe 状态恰为 ACTIVE 且投掷者持有对局职业。SparkStrength
 * 在阶段切换时会移除所有飞行中的 M67，因此 STOPPING 期间的引爆必定是 STOPPING 期间投出的；它与非参与者的手雷一样
 * 仅作表现。随后打坏以手雷中心为球心、半径 3.5、有视线的设备，归属于投掷者；同一次爆炸按 M67 自身的击杀半径结束魔术师皮套
 * （{@code MagicianPuppetHits.onM67Blast}）。只有本类可以提及 SparkStrength 实现类且
 * 只限这两个 getter（由源码测试固定）；任何查找失败都会在本次会话中关闭该接缝，绝不向实体卸载流程抛出异常。
 */
public final class SparkStrengthM67Compat {
    public static final String MOD_ID = "sparkstrength";
    public static final Identifier M67_ENTITY_ID = Identifier.of(MOD_ID, "m67");
    /** Pinned SparkStrength implementation class and public getters. / 固定的 SparkStrength 实现类与 public getter。 */
    static final String M67_CLASS = "annina.sparkstrength.entity.M67GrenadeEntity";
    static final String DETONATE_AT_GETTER = "getDetonateAt";
    static final String THROWER_GETTER = "getThrowerUuid";

    private static boolean registered;
    private static volatile boolean disabled;
    @Nullable
    private static volatile Accessors accessors;

    private SparkStrengthM67Compat() {
    }

    /** The two cached getters of one resolved class. / 同一已解析类的两个缓存 getter。 */
    private record Accessors(Class<?> type, Method detonateAt, Method thrower) {
    }

    public static synchronized void register() {
        if (registered || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        registered = true;
        ServerEntityEvents.ENTITY_UNLOAD.register(SparkStrengthM67Compat::onUnload);
    }

    private static void onUnload(Entity entity, ServerWorld world) {
        if (disabled || entity == null || world == null
                || entity.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || !M67_ENTITY_ID.equals(Registries.ENTITY_TYPE.getId(entity.getType()))) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean roundActive = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        if (!roundActive) {
            return;
        }
        Accessors resolved = accessorsFor(entity.getClass());
        if (resolved == null) {
            return;
        }
        long detonateAt;
        UUID throwerUuid;
        try {
            Object rawDetonateAt = resolved.detonateAt().invoke(entity);
            Object rawThrower = resolved.thrower().invoke(entity);
            if (!(rawDetonateAt instanceof Long value) || !(rawThrower instanceof UUID uuid)) {
                return;
            }
            detonateAt = value;
            throwerUuid = uuid;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disabled = true;
            return;
        }
        if (!breaksDevices(roundActive, game.hasAnyRole(throwerUuid), detonateAt, world.getTime())) {
            return;
        }
        ServerPlayerEntity thrower = world.getServer().getPlayerManager().getPlayer(throwerUuid);
        if (thrower == null) {
            return;
        }
        SeekerDeviceHits.onBlast(world, entity.getBoundingBox().getCenter(), SeekerDamageRules.M67_RADIUS, thrower,
                SeekerBreakSource.M67);
        // Magician seam: the same match blast ends puppets in the M67's own kill radius.
        // 魔术师接缝：同一次对局爆炸按 M67 自身的击杀半径结束皮套。
        MagicianPuppetHits.onM67Blast(world, entity.getBoundingBox().getCenter(), thrower);
    }

    /**
     * A match grenade whose fuse ran out: an ACTIVE round and a thrower with a match role.
     * 引信已到时的对局手雷：对局 ACTIVE 且投掷者持有对局职业。
     */
    static boolean breaksDevices(boolean roundActive, boolean throwerIsParticipant, long detonateAt, long worldTime) {
        return roundActive && throwerIsParticipant && detonated(detonateAt, worldTime);
    }

    /** A set fuse that has already run out. / 引信已设置且已到时。 */
    static boolean detonated(long detonateAt, long worldTime) {
        return detonateAt >= 0L && worldTime >= detonateAt;
    }

    @Nullable
    private static Accessors accessorsFor(Class<?> type) {
        Accessors cached = accessors;
        if (cached != null && cached.type() == type) {
            return cached;
        }
        if (!M67_CLASS.equals(type.getName())) {
            // A different class under the pinned id: SparkStrength changed; stay inert. / 固定 id 下的类已变化：保持不生效。
            disabled = true;
            return null;
        }
        try {
            Method detonateAt = type.getMethod(DETONATE_AT_GETTER);
            Method thrower = type.getMethod(THROWER_GETTER);
            if (detonateAt.getReturnType() != long.class || thrower.getReturnType() != UUID.class) {
                disabled = true;
                return null;
            }
            Accessors resolved = new Accessors(type, detonateAt, thrower);
            accessors = resolved;
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disabled = true;
            return null;
        }
    }
}
