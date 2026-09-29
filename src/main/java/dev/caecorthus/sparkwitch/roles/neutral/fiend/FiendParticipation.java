package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen Fiend predicates, safe on both client and server: they read only Wathe's synced game component and the
 * all-player {@link FiendMomentWorldComponent}. The role is an exact-id gate, never inferred from faction or namespace.
 * 冻结的魔人判断，客户端与服务端均可安全调用：只读取 Wathe 已同步的对局组件与全员同步的 {@link FiendMomentWorldComponent}。
 * 职业按精确 id 判断，从不由阵营或命名空间推断。
 */
public final class FiendParticipation {
    private FiendParticipation() {
    }

    public static boolean isFiendRole(@Nullable Role role) {
        return role != null && FiendRules.ROLE_ID.equals(role.identifier());
    }

    public static boolean isFiend(@Nullable PlayerEntity player) {
        return player != null && isFiendRole(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Fiend role, playing and alive in Wathe, not the moment Fiend, and not spent (its moment ended by a Taotie
     * swallow): the immune, outcome-neutral state. The spent check is server-only; on the client the ledger is empty,
     * so no client code may rely on this predicate.
     * 拥有魔人职业、在 Wathe 中参与且存活、不是时刻中的魔人，且未耗尽（其时刻未因饕餮吞噬而结束）：即免疫且不影响胜负的状态。
     * 耗尽检查仅在服务端有效；客户端登记表为空，因此客户端代码不得依赖此判断。
     */
    public static boolean isDormantFiend(@Nullable PlayerEntity player) {
        return isFiend(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !FiendMomentWorldComponent.isMomentFiend(player)
                && !FiendMomentWorldComponent.isSpentFiend(player);
    }

    /** Fiend role and owner of the active moment. / 拥有魔人职业且为当前时刻的拥有者。 */
    public static boolean isMomentFiend(@Nullable PlayerEntity player) {
        return isFiend(player) && FiendMomentWorldComponent.isMomentFiend(player);
    }
}
