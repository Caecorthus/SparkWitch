package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.math.random.Random;

/**
 * Apprentice Witch dealing (owner, 2026-11-11): no player minimum and no quota. Wathe's {@code assignCivilians} first
 * gives every eligible special civilian one seat; each seat it would then fill with the plain Civilian is drawn
 * uniformly from {Civilian, Apprentice Witch} while the Apprentice is enabled, so she can appear more than once but
 * never before the special civilians run out. Her appearance condition is always false, which keeps her out of
 * Wathe's once-each special pick. Forced roles never reach this seat. Called from
 * {@code mixin/ApprenticeCivilianPoolMixin}.
 * 预备魔女发放（所有者 2026-11-11）：无人数门槛、无名额。Wathe 的 {@code assignCivilians} 先给每个可出现的特殊平民各一个
 * 席位；之后原本填普通平民的每个席位，在预备魔女启用时从{普通平民, 预备魔女}中均匀随机抽取，因此她可能出现多个，但不会早于
 * 特殊平民分完。她的出现条件恒为 false，从而不进入 Wathe 每职业一次的特殊抽取。强制指定的职业不会走到这个席位。
 * 由 {@code mixin/ApprenticeCivilianPoolMixin} 调用。
 */
public final class ApprenticeCivilianPool {
    private ApprenticeCivilianPool() {
    }

    /** The role for a seat Wathe is about to fill; only the plain-Civilian fallback can change. / 席位最终职业。 */
    public static Role chooseSeatRole(GameWorldComponent game, Role wathePick, Random random) {
        if (wathePick != WatheRoles.CIVILIAN) {
            return wathePick;
        }
        Role apprentice = SparkWitchRoles.apprenticeWitch();
        if (apprentice == null || !game.isRoleEnabled(apprentice)) {
            // A disabled Apprentice draws nothing, so Wathe's random stream is unchanged. / 未启用时不消耗随机数。
            return wathePick;
        }
        return choose(wathePick, apprentice, true, random.nextBoolean());
    }

    /** Pure seat choice; the coin is only consulted for a plain-Civilian pick. / 纯席位选择。 */
    public static Role choose(Role wathePick, Role apprentice, boolean apprenticeEnabled, boolean coin) {
        if (wathePick != WatheRoles.CIVILIAN || !apprenticeEnabled || apprentice == null) {
            return wathePick;
        }
        return coin ? apprentice : wathePick;
    }
}
