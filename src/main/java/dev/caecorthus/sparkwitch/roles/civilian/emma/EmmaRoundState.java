package dev.caecorthus.sparkwitch.roles.civilian.emma;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

/** Persistent round state, independent of component registration. / 不依赖组件注册的持久化回合状态。 */
public class EmmaRoundState {
    private boolean active;
    private UUID roundId;
    private UUID claimant;
    private boolean loadoutGranted;
    private final Map<UUID, Penalty> penalties = new HashMap<>();
    private final Set<UUID> terminalDeaths = new HashSet<>();
    public record Penalty(long deathAt, long moodAt) { }

    public boolean active() { return active; }
    public UUID roundId() { return roundId; }
    public boolean claimed() { return claimant != null; }
    public Map<UUID, Penalty> penalties() { return penalties; }
    public boolean died(UUID player) { return terminalDeaths.contains(player); }
    public void begin() { clear(); active = true; roundId = UUID.randomUUID(); }
    public void clear() {
        active = false; roundId = null; claimant = null; loadoutGranted = false;
        penalties.clear(); terminalDeaths.clear();
    }
    public boolean claim(UUID player, boolean alreadyEmma) {
        if (!active || died(player)) return false;
        if (claimant == null) { claimant = player; return true; }
        return claimant.equals(player) && alreadyEmma && !died(player);
    }
    public boolean grantLoadout(UUID player) {
        if (!active || !player.equals(claimant) || loadoutGranted) return false;
        loadoutGranted = true;
        return true;
    }
    public void onTerminalDeath(UUID player) { terminalDeaths.add(player); penalties.remove(player); }
    public void schedule(UUID player, long deadline, boolean fatal) {
        Penalty current = penalties.getOrDefault(player, new Penalty(-1, -1));
        penalties.put(player, fatal ? new Penalty(earliest(current.deathAt(), deadline), current.moodAt())
                : new Penalty(current.deathAt(), earliest(current.moodAt(), deadline)));
    }
    private static long earliest(long old, long next) { return old < 0 ? next : Math.min(old, next); }

    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putBoolean("Active", active);
        if (roundId != null) tag.putUuid("RoundId", roundId);
        if (claimant != null) tag.putUuid("Claimant", claimant);
        tag.putBoolean("LoadoutGranted", loadoutGranted);
        NbtList pending = new NbtList();
        penalties.forEach((id, penalty) -> {
            NbtCompound row = new NbtCompound();
            row.putUuid("Player", id); row.putLong("DeathAt", penalty.deathAt()); row.putLong("MoodAt", penalty.moodAt());
            pending.add(row);
        });
        tag.put("Penalties", pending);
        NbtList deaths = new NbtList();
        terminalDeaths.forEach(id -> { NbtCompound row = new NbtCompound(); row.putUuid("Player", id); deaths.add(row); });
        tag.put("TerminalDeaths", deaths);
    }
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        clear();
        if (!tag.getBoolean("Active") || !tag.containsUuid("RoundId")) return;
        active = true; roundId = tag.getUuid("RoundId");
        claimant = tag.containsUuid("Claimant") ? tag.getUuid("Claimant") : null;
        loadoutGranted = tag.getBoolean("LoadoutGranted");
        NbtList pending = tag.getList("Penalties", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < pending.size(); i++) {
            NbtCompound row = pending.getCompound(i);
            if (row.containsUuid("Player")) penalties.put(row.getUuid("Player"), new Penalty(row.getLong("DeathAt"), row.getLong("MoodAt")));
        }
        NbtList deaths = tag.getList("TerminalDeaths", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < deaths.size(); i++) if (deaths.getCompound(i).containsUuid("Player")) {
            terminalDeaths.add(deaths.getCompound(i).getUuid("Player"));
        }
    }
}
