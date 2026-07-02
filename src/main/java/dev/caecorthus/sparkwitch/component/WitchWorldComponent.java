package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.impl.GrandWitchSpellService;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores world-level witch skill toggles.
 * 保存世界级魔女技能配置，例如禁用列表。
 */
public final class WitchWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("world"),
            WitchWorldComponent.class
    );

    private final World world;
    private final LinkedHashSet<Identifier> disabledSkills = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, UUID> criminologistKillers = new LinkedHashMap<>();
    private final GrandWitchCeremonialSwordBgmSources grandWitchCeremonialSwordBgmSources =
            new GrandWitchCeremonialSwordBgmSources();
    private int instinctObscureTicks;
    private int obscureActionbarTicks;
    private int fearTicks;
    private int syncedGrandWitchCeremonialSwordBgmSources;

    public WitchWorldComponent(World world) {
        this.world = world;
    }

    public boolean isSkillEnabled(Identifier skillId) {
        return !disabledSkills.contains(skillId);
    }

    public void setSkillEnabled(Identifier skillId, boolean enabled) {
        boolean changed = enabled ? disabledSkills.remove(skillId) : disabledSkills.add(skillId);
        if (changed) {
            sync();
        }
    }

    public Set<Identifier> getDisabledSkillIds() {
        return Set.copyOf(disabledSkills);
    }

    public boolean isInstinctObscured() {
        return instinctObscureTicks > 0;
    }

    public int getInstinctObscureTicks() {
        return instinctObscureTicks;
    }

    public int getFearTicks() {
        return fearTicks;
    }

    public boolean hasGrandWitchCeremonialSwordBgm() {
        return grandWitchCeremonialSwordBgmSourceCount() > 0;
    }

    public int grandWitchCeremonialSwordBgmSourceCount() {
        return usesLocalGrandWitchCeremonialSwordBgmSources()
                ? grandWitchCeremonialSwordBgmSources.size()
                : syncedGrandWitchCeremonialSwordBgmSources;
    }

    public void startInstinctObscure(int durationTicks) {
        instinctObscureTicks = Math.max(0, durationTicks);
        obscureActionbarTicks = 0;
        sync();
    }

    public void startFear(int durationTicks) {
        fearTicks = Math.max(0, durationTicks);
        sync();
    }

    /**
     * Tracks Grand Witch skill BGM by player UUID so overlapping casts keep the ambience alive.
     * 按玩家 UUID 记录大魔女技能 BGM 来源，多个技能窗口重叠时不会误停全场环境音。
     */
    public void startGrandWitchCeremonialSwordBgm(UUID playerUuid) {
        if (!grandWitchCeremonialSwordBgmSources.start(playerUuid)) {
            return;
        }
        syncedGrandWitchCeremonialSwordBgmSources = grandWitchCeremonialSwordBgmSources.size();
        sync();
    }

    public void stopGrandWitchCeremonialSwordBgm(UUID playerUuid) {
        if (!grandWitchCeremonialSwordBgmSources.stop(playerUuid)) {
            return;
        }
        syncedGrandWitchCeremonialSwordBgmSources = grandWitchCeremonialSwordBgmSources.size();
        sync();
    }

    public void recordCriminologistKill(UUID victimUuid, UUID killerUuid) {
        if (victimUuid == null || killerUuid == null || victimUuid.equals(killerUuid)) {
            return;
        }
        criminologistKillers.put(victimUuid, killerUuid);
    }

    public Optional<UUID> getCriminologistKiller(UUID victimUuid) {
        return Optional.ofNullable(criminologistKillers.get(victimUuid));
    }

    public void clearRoundState() {
        instinctObscureTicks = 0;
        obscureActionbarTicks = 0;
        fearTicks = 0;
        grandWitchCeremonialSwordBgmSources.clear();
        syncedGrandWitchCeremonialSwordBgmSources = 0;
        criminologistKillers.clear();
        sync();
    }

    public void sync() {
        if (world != null) {
            KEY.sync(world);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        boolean shouldSync = false;
        if (instinctObscureTicks > 0) {
            if (obscureActionbarTicks <= 0) {
                GrandWitchSpellService.sendObscureActionbars(serverWorld, instinctObscureTicks);
                obscureActionbarTicks = 20;
            }
            instinctObscureTicks--;
            obscureActionbarTicks--;
            shouldSync = instinctObscureTicks == 0 || instinctObscureTicks % 20 == 0;
        } else {
            obscureActionbarTicks = 0;
        }

        if (fearTicks > 0) {
            GrandWitchSpellService.tickFear(serverWorld, fearTicks);
            fearTicks--;
            shouldSync = shouldSync || fearTicks == 0;
        }

        if (shouldSync) {
            sync();
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        writeIdentifierSet(buf, disabledSkills);
        buf.writeVarInt(instinctObscureTicks);
        buf.writeVarInt(fearTicks);
        buf.writeVarInt(grandWitchCeremonialSwordBgmSources.size());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        readIdentifierSet(buf, disabledSkills);
        instinctObscureTicks = Math.max(0, buf.readVarInt());
        fearTicks = Math.max(0, buf.readVarInt());
        syncedGrandWitchCeremonialSwordBgmSources = Math.max(0, buf.readVarInt());
        obscureActionbarTicks = 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.put("DisabledSkills", toNbt(disabledSkills));
        if (instinctObscureTicks > 0) {
            tag.putInt("InstinctObscureTicks", instinctObscureTicks);
        }
        if (fearTicks > 0) {
            tag.putInt("FearTicks", fearTicks);
        }
        if (!criminologistKillers.isEmpty()) {
            NbtList records = new NbtList();
            for (Map.Entry<UUID, UUID> entry : criminologistKillers.entrySet()) {
                NbtCompound record = new NbtCompound();
                record.putUuid("Victim", entry.getKey());
                record.putUuid("Killer", entry.getValue());
                records.add(record);
            }
            tag.put("CriminologistKillers", records);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        disabledSkills.clear();
        criminologistKillers.clear();
        grandWitchCeremonialSwordBgmSources.clear();
        syncedGrandWitchCeremonialSwordBgmSources = 0;
        fromNbt(tag.getList("DisabledSkills", NbtElement.STRING_TYPE), disabledSkills);
        instinctObscureTicks = tag.contains("InstinctObscureTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("InstinctObscureTicks"))
                : 0;
        fearTicks = tag.contains("FearTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("FearTicks"))
                : 0;
        NbtList records = tag.getList("CriminologistKillers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < records.size(); i++) {
            NbtCompound record = records.getCompound(i);
            if (record.containsUuid("Victim") && record.containsUuid("Killer")) {
                criminologistKillers.put(record.getUuid("Victim"), record.getUuid("Killer"));
            }
        }
        obscureActionbarTicks = 0;
    }

    private static NbtList toNbt(Collection<Identifier> ids) {
        NbtList list = new NbtList();
        for (Identifier id : ids) {
            list.add(NbtString.of(id.toString()));
        }
        return list;
    }

    private static void fromNbt(NbtList list, Set<Identifier> ids) {
        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
    }

    private static void writeIdentifierSet(RegistryByteBuf buf, Collection<Identifier> ids) {
        buf.writeVarInt(ids.size());
        for (Identifier id : ids) {
            buf.writeString(id.toString());
        }
    }

    private static void readIdentifierSet(RegistryByteBuf buf, Set<Identifier> ids) {
        ids.clear();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            Identifier id = Identifier.tryParse(buf.readString());
            if (id != null) {
                ids.add(id);
            }
        }
    }

    private boolean usesLocalGrandWitchCeremonialSwordBgmSources() {
        return world == null || world instanceof ServerWorld;
    }
}
