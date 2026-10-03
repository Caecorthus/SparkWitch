package dev.caecorthus.sparkwitch.client.riftwalker.swapper;

/**
 * Pure C7 rule for NoellesRoles' Swapper grid: a head whose player is missing from the client world may still be
 * picked when that player's tab-list entry is in SPECTATOR mode and Wathe still lists them alive (a gate occupant,
 * Taotie-swallowed, Last Stand, Depression fake death). The server decides: gate occupants crush the Swapper, every
 * other spectator is a silent no-op. Untracked non-spectators (simply out of range) stay unclickable, as stock.
 * NoellesRoles 交换网格的纯 C7 规则：客户端世界里不存在的玩家，若其玩家列表条目为旁观者模式且 Wathe 仍记为存活
 * （门内玩家、被饕餮吞、Last Stand、抑郁假死），仍可被点选。由服务端裁决：门内玩家会让交换者被夹死，其他旁观者静默
 * 无效。未被追踪的非旁观者（只是超出范围）保持不可点，与原版一致。
 */
public final class RiftSwapperPickRules {
    private RiftSwapperPickRules() {
    }

    public static boolean allowsUntrackedPick(boolean listedAsSpectator, boolean aliveInWathe) {
        return listedAsSpectator && aliveInWathe;
    }
}
