package dev.caecorthus.sparkwitch.client.magician;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.SelectMagicianTargetC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 魔术师目标选择公开 API，扩展职业可直接复用同一按钮选择与网络行为。 */
public final class MagicianTargetSelectionApi {
    private MagicianTargetSelectionApi() {}
    public static void select(UUID target) { if (target != null) ClientPlayNetworking.send(new SelectMagicianTargetC2SPacket(target)); }
    public static UUID selected(ClientPlayerEntity player) { return MagicianPlayerComponent.KEY.get(player).selectedTarget(); }
    public static boolean isSelected(ClientPlayerEntity player, UUID target) { return selected(player).equals(target); }
    public static List<PlayerListEntry> onlinePlayers(ClientPlayerEntity player) {
        List<PlayerListEntry> entries = new ArrayList<>(player.networkHandler.getPlayerList());
        entries.sort(Comparator.comparingInt(e -> e.getProfile().getId().equals(player.getUuid()) ? 0 : 1));
        return entries;
    }
}
