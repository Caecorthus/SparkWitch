package dev.caecorthus.sparkwitch.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Optional SparkStrength Partygoer seams (owner decision 2026-10-21): the Party Animal's summoned zombie
 * ({@code sparkstrength:partygoer}) dies in one hit to anything that can kill a player, and the corpse it leaves is not a
 * passenger's. SparkWitch never compiles against SparkStrength, so this is the only SparkWitch class that names its
 * frozen seams, all reached by reflection:
 * 可选的 SparkStrength 派对客接缝（所有者 2026-10-21 决定）：派对狂召唤的僵尸（{@code sparkstrength:partygoer}）会被任何能杀死
 * 玩家的东西一击打死，它留下的尸体不是乘客的尸体。SparkWitch 从不针对 SparkStrength 编译，因此只有本类点名其冻结接缝，全部经反射调用：
 * <ul>
 *   <li>{@code annina.sparkstrength.role.partyanimal.PartygoerService#killByWeapon(ServerPlayerEntity, Entity,
 *   Identifier)}: server; SparkStrength pays the reward, drops whoever it holds and leaves the corpse.
 *   / 服务端；SparkStrength 发放奖励、放下手中的人并留下尸体。</li>
 *   <li>{@code PartygoerService#isKillable(Entity)}: server; false while it is inside a Taotie. / 服务端；在饕餮肚里时为 false。</li>
 *   <li>{@code annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent#isPartygoerCorpse(Entity)}: both
 *   sides. / 双端可用。</li>
 * </ul>
 * Absent, older (no such method) or failing SparkStrength means there is no Partygoer and no such corpse.
 * SparkStrength 缺失、过旧（无此方法）或调用失败时，视为没有派对客，也没有这种尸体。
 */
public final class SparkStrengthPartygoerCompat {
    private static final String MOD_ID = "sparkstrength";
    /** The entity type, checked before any reflection. / 实体类型 id，在任何反射之前检查。 */
    public static final Identifier PARTYGOER_ID = Identifier.of(MOD_ID, "partygoer");
    static final String SERVICE = "annina.sparkstrength.role.partyanimal.PartygoerService";
    static final String CORPSE_COMPONENT = "annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent";
    static final String KILL_BY_WEAPON = "killByWeapon";
    static final String IS_KILLABLE = "isKillable";
    static final String IS_CORPSE = "isPartygoerCorpse";

    private SparkStrengthPartygoerCompat() {
    }

    /** Cheap type check, any side. / 廉价的类型判断，双端可用。 */
    public static boolean isPartygoer(@Nullable Entity entity) {
        return entity != null && PARTYGOER_ID.equals(Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    /**
     * Server: a Partygoer a weapon may hit right now (not inside a Taotie, not already dead).
     * 服务端：此刻可被武器命中的派对客（不在饕餮肚里、尚未死亡）。
     */
    public static boolean isKillable(@Nullable Entity entity) {
        // Without the kill seam nothing may treat it as a target (it would shield players and spend skills for nothing).
        // 没有击杀接缝时任何东西都不能把它当目标（否则它会白白挡住玩家、耗掉技能）。
        if (Methods.KILL_BY_WEAPON == null || !isPartygoer(entity) || entity.isRemoved() || entity.isInvisible()) {
            return false;
        }
        Object result = invoke(Methods.IS_KILLABLE, entity);
        return result instanceof Boolean killable && killable;
    }

    /**
     * Server: kills the Partygoer as {@code attacker}'s hit with SparkWitch's own death reason. The caller has already
     * validated its weapon (charge, cooldown, reach, aim); SparkStrength only checks the target. True iff it died.
     * 服务端：把这次命中当作 {@code attacker} 的武器击杀派对客，死因为 SparkWitch 自己的死因。调用方已校验武器（蓄力、冷却、距离、
     * 瞄准）；SparkStrength 只检查目标。仅当它确实死亡时返回 true。
     */
    public static boolean killByWeapon(@Nullable ServerPlayerEntity attacker, @Nullable Entity target,
                                       @Nullable Identifier deathReason) {
        if (!isKillable(target)) {
            return false;
        }
        Object result = invoke(Methods.KILL_BY_WEAPON, attacker, target, deathReason);
        return result instanceof Boolean killed && killed;
    }

    /** Any side: a corpse left by a killed Partygoer (not a passenger). / 双端：被打死的派对客留下的尸体（不是乘客）。 */
    public static boolean isPartygoerCorpse(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        Object result = invoke(Methods.IS_CORPSE, entity);
        return result instanceof Boolean corpse && corpse;
    }

    private static @Nullable Object invoke(@Nullable Method method, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Fail closed. / 失败即关闭。
            return null;
        }
    }

    /** Looked up once, on first use. / 首次使用时查找一次。 */
    private static final class Methods {
        static final @Nullable Method KILL_BY_WEAPON = find(SERVICE, SparkStrengthPartygoerCompat.KILL_BY_WEAPON,
                ServerPlayerEntity.class, Entity.class, Identifier.class);
        static final @Nullable Method IS_KILLABLE = find(SERVICE, SparkStrengthPartygoerCompat.IS_KILLABLE, Entity.class);
        static final @Nullable Method IS_CORPSE = find(CORPSE_COMPONENT, SparkStrengthPartygoerCompat.IS_CORPSE,
                Entity.class);

        private static @Nullable Method find(String owner, String name, Class<?>... parameters) {
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
                return null;
            }
            try {
                Method method = Class.forName(owner).getMethod(name, parameters);
                return method.getReturnType() == boolean.class ? method : null;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
