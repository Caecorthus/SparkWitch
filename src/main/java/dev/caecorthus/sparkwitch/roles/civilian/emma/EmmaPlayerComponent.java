package dev.caecorthus.sparkwitch.roles.civilian.emma;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/** Recruitment knowledge is owner-only and belongs to one round, including after reconnect.
 * 招募反制情报仅同步本人，并绑定单个回合，包括重连后。 */
public final class EmmaPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<EmmaPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("emma_player"), EmmaPlayerComponent.class);
    private final PlayerEntity player;
    private UUID roundId;
    private final Set<UUID> revealed = new HashSet<>();
    private boolean speedUnlocked;

    public EmmaPlayerComponent(PlayerEntity player) { this.player = player; }
    public Set<UUID> getRevealedGrandWitches() { return Set.copyOf(revealed); }
    public boolean hasRevealedGrandWitch(UUID target) { return revealed.contains(target); }
    public boolean speedUnlocked() { return speedUnlocked; }

    public void joinRound(UUID id) {
        if (!java.util.Objects.equals(roundId, id)) { clear(); roundId = id; }
    }
    public void reveal(UUID target) { if (revealed.add(target)) sync(); }
    public void unlockSpeed() { if (!speedUnlocked) { speedUnlocked = true; sync(); } }
    public void clear() {
        roundId = null; revealed.clear(); speedUnlocked = false; sync();
    }
    public void sync() { if (!player.getWorld().isClient) KEY.sync(player); }

    @Override public boolean shouldSyncWith(ServerPlayerEntity recipient) { return recipient == player; }
    private boolean authorized() {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return game.isRunning() && EmmaRules.isEmma(game.getRole(player)) && GameFunctions.isPlayerPlayingAndAlive(player);
    }
    @Override public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean valid = recipient == player && authorized();
        buf.writeBoolean(valid && speedUnlocked);
        buf.writeVarInt(valid ? revealed.size() : 0);
        if (valid) revealed.forEach(buf::writeUuid);
    }
    @Override public void applySyncPacket(RegistryByteBuf buf) {
        speedUnlocked = buf.readBoolean(); revealed.clear();
        int size = buf.readVarInt();
        if (size < 0 || size > buf.readableBytes() / 16) throw new IllegalArgumentException("Invalid Emma view");
        for (int i = 0; i < size; i++) revealed.add(buf.readUuid());
    }
    @Override public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        if (roundId != null) tag.putUuid("RoundId", roundId);
        tag.putBoolean("SpeedUnlocked", speedUnlocked);
        NbtList list = new NbtList();
        revealed.forEach(id -> { NbtCompound row = new NbtCompound(); row.putUuid("Target", id); list.add(row); });
        tag.put("Revealed", list);
    }
    @Override public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        roundId = tag.containsUuid("RoundId") ? tag.getUuid("RoundId") : null;
        speedUnlocked = tag.getBoolean("SpeedUnlocked"); revealed.clear();
        NbtList list = tag.getList("Revealed", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).containsUuid("Target")) {
            revealed.add(list.getCompound(i).getUuid("Target"));
        }
    }
}
