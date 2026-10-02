package dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper;

/**
 * NoellesRoles Swapper vs. a Rift occupant (D13, C4, C7, C8; plan §16, research 06): a SparkWitch-owned HEAD inject on
 * the Swapper server handler cancels any swap that involves a player inside a gate and kills the Swapper with the
 * forced, terminal, killer-less death reason {@code RiftwalkerRules.PORTAL_CRUSHED_DEATH_REASON}, body at the
 * occupant's gate. Registers that reason as SparkTraits-terminal at {@code SERVER_STARTING}. Owned by P9 (with its
 * server mixin and the client Swapper widget patch).
 * NoellesRoles 交换者对门内玩家（D13、C4、C7、C8；plan §16，调研 06）：SparkWitch 自有的 HEAD 注入挂在交换者服务端
 * 处理器上，取消任何涉及门内玩家的交换，并以强制、终结、无凶手的死因 {@code RiftwalkerRules.PORTAL_CRUSHED_DEATH_REASON}
 * 处死交换者，尸体落在门内玩家所在的门前。在 {@code SERVER_STARTING} 时把该死因注册为 SparkTraits 终结死因。
 * 归属 P9（含其服务端 mixin 与客户端交换界面补丁）。
 */
public final class RiftSwapperCrushService {
    private static boolean registered;

    private RiftSwapperCrushService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // TODO(P9): terminal death reason registration, replay formatter. / TODO(P9)：终结死因注册、回放格式化器。
    }
}
