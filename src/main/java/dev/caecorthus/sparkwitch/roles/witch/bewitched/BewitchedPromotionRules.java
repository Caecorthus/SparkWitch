package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoll;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pure rules of the Bewitched promotion role choice (D3, D4, C4, C5) and of the forced accomplice-role lock command.
 * Server-authoritative callers feed live state in; nothing here reads the world.
 * 魔化使晋升身份选择（D3、D4、C4、C5）与强制共犯身份锁定命令的纯规则。服务端权威的调用方传入实时状态；这里不读取世界。
 */
public final class BewitchedPromotionRules {
    private BewitchedPromotionRules() {
    }

    /**
     * The role a lock id names: the plain Accomplice, a registered special accomplice, or null for anything else
     * (an unknown or no longer registered id).
     * 锁定 id 指向的职业：普通共犯、某个已注册的特殊共犯；其他情况（未知或已不再注册的 id）为 null。
     */
    public static @Nullable Role resolveLock(@Nullable Identifier lockId, Role plainAccomplice, List<Role> variants) {
        if (lockId == null) {
            return null;
        }
        if (lockId.equals(plainAccomplice.identifier())) {
            return plainAccomplice;
        }
        for (Role variant : variants) {
            if (variant != null && lockId.equals(variant.identifier())) {
                return variant;
            }
        }
        return null;
    }

    /**
     * One promotion's role. (a) This player's lock: the plain Accomplice is always honoured; a special accomplice not
     * used this round is honoured even when disabled; a used one (or an unknown lock) falls through. (b) The roll: a
     * uniform pick among enabled special accomplices that are neither used this round nor reserved by another living
     * Bewitched's lock, in registration order; the plain Accomplice only once none is left (D3).
     * 单次晋升的身份。(a) 本人的锁定：普通共犯总是生效；本局未使用的特殊共犯即使被禁用也生效；已使用的（或未知的锁定）
     * 则落入抽取。(b) 抽取：按注册顺序，在已启用、本局未使用、且未被其他存活魔化使锁定预留的特殊共犯中均匀抽取；
     * 全部用完时才是普通共犯（D3）。
     */
    public static Role choose(
            @Nullable Role lockedRole,
            Role plainAccomplice,
            List<Role> variants,
            Predicate<? super Role> enabled,
            Predicate<? super Role> usedThisRound,
            Predicate<? super Role> reservedByAnother,
            Random random
    ) {
        Objects.requireNonNull(plainAccomplice, "plainAccomplice");
        if (lockedRole != null) {
            if (lockedRole == plainAccomplice) {
                return plainAccomplice;
            }
            if (variants.contains(lockedRole) && !usedThisRound.test(lockedRole)) {
                return lockedRole;
            }
        }
        return AccompliceVariantRoll.pick(variants, enabled,
                role -> usedThisRound.test(role) || reservedByAnother.test(role), random);
    }

    /**
     * Live reservation (D4, C5): {@code candidate} is reserved for someone else when another UUID's lock names it and
     * that holder is still a living player whose real role is the Bewitched. A dead or promoted holder frees the seat.
     * 实时预留（D4、C5）：当另一名玩家的锁定指向 {@code candidate}，且该持有者仍是真实身份为魔化使的存活玩家时，
     * 此职业为他人预留。持有者死亡或已晋升时席位即释放。
     */
    public static boolean reservedByAnother(Role candidate, UUID self, Map<UUID, Identifier> locks,
                                            Predicate<UUID> livingBewitched) {
        if (candidate == null) {
            return false;
        }
        for (Map.Entry<UUID, Identifier> lock : locks.entrySet()) {
            if (!lock.getKey().equals(self) && candidate.identifier().equals(lock.getValue())
                    && livingBewitched.test(lock.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** Outcome of a {@code /sparkwitch:forceAccompliceRole} request. / 锁定命令请求的结果。 */
    public enum LockCheck {
        OK,
        /** A special accomplice was given more than one target. / 特殊共犯指定了多名目标。 */
        SPECIAL_SINGLE_TARGET,
        /** Another player already holds this special accomplice's lock. / 已有其他玩家持有该特殊共犯的锁定。 */
        ALREADY_LOCKED
    }

    /**
     * C5: the plain Accomplice may be locked to many players; a special accomplice to one player at a time.
     * C5：普通共犯可锁定给多名玩家；特殊共犯同一时间只能锁定给一名玩家。
     */
    public static LockCheck checkLock(boolean special, int targetCount, boolean heldByAnother) {
        if (!special) {
            return LockCheck.OK;
        }
        if (targetCount > 1) {
            return LockCheck.SPECIAL_SINGLE_TARGET;
        }
        return heldByAnother ? LockCheck.ALREADY_LOCKED : LockCheck.OK;
    }

    /**
     * Whether another holder's lock still blocks a new holder: always outside a round (it applies to the next round);
     * during a round only while that holder is a living, unpromoted Bewitched, the same test as the live reservation,
     * because a dead, promoted or never-dealt holder reserves nothing (D4).
     * 另一名持有者的锁定是否仍阻止新持有者：对局外总是阻止（作用于下一局）；对局中仅当该持有者仍是存活且未晋升的魔化使时
     * 才阻止，与实时预留的判定相同，因为已死亡、已晋升或从未被发放的持有者不预留任何席位（D4）。
     */
    public static boolean lockStillHeld(boolean roundRunning, boolean holderLivingBewitched) {
        return !roundRunning || holderLivingBewitched;
    }
}
