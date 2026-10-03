package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Entry point of the Perception Ledger item and the single seam that reads the owner's perception snapshots: the
 * Perceived tab only ever sees completed snapshots, never in-flight perception state.
 * 感知册物品的入口，也是读取拥有者感知快照的唯一接缝：“已感知身份”页只会看到已完成的快照，从不包含进行中的感知状态。
 */
public final class BlackRavenLedgerScreen {
    private BlackRavenLedgerScreen() {
    }

    /**
     * Opens the two-tab ledger on the Perceived tab; the Absent tab is read-only from here (no mask session).
     * 在“已感知身份”页打开双页感知册；从这里打开时“未登场的善良职业”页只读（无面具会话）。
     */
    public static void open(MinecraftClient client) {
        BlackRavenLedgerBookScreen.open(client, BlackRavenLedgerBookScreen.Tab.PERCEIVED, 0);
    }

    /**
     * Tab A entries in synced order, built only from completed owner snapshots (leak guard: the book screen never
     * reads the component itself).
     * Tab A 条目（按同步顺序），仅由已完成的拥有者快照组成（防泄露：感知册界面从不直接读取该组件）。
     */
    static List<BlackRavenIdentitySnapshot> perceivedSnapshots(PlayerEntity player) {
        return List.copyOf(BlackRavenPerceptionPlayerComponent.KEY.get(player).completedSnapshots());
    }
}
