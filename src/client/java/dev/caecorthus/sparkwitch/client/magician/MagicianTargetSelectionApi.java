package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.SelectMagicianTargetC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import java.util.List;
import java.util.UUID;

/** 魔术师目标选择公开 API，扩展职业可直接复用同一按钮选择与网络行为。 */
public final class MagicianTargetSelectionApi {
    private MagicianTargetSelectionApi() {}
    public static void select(UUID target) { if (target != null) ClientPlayNetworking.send(new SelectMagicianTargetC2SPacket(target)); }
    public static UUID selected(ClientPlayerEntity player) { return MagicianPlayerComponent.KEY.get(player).selectedTarget(); }
    public static boolean isSelected(ClientPlayerEntity player, UUID target) { return selected(player).equals(target); }
    /**
     * The round roster the server captured (owner-synced, the Magician first). It does not follow deaths or
     * disconnects, so the list never reveals who has died (owner decision 2026-10-07 D7).
     * 服务端记录的本局名单（只同步给本人，魔术师本人在前）。它不随死亡或断线变化，因此列表永远不会暴露谁已死亡
     * （所有者 2026-10-07 决定 D7）。
     */
    public static List<MagicianPlayerComponent.RosterEntry> roster(ClientPlayerEntity player) {
        return MagicianPlayerComponent.KEY.get(player).roster();
    }
}
