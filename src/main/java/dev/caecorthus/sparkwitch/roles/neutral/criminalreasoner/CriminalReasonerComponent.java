package dev.caecorthus.sparkwitch.roles.neutral.criminalreasoner;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player reasoning ledger: the true killer of every death this reasoner has seen, the victims already solved,
 * the win progress, and the invisibility charges.
 * <p>Only its own owner receives the sync payload — the victim→killer map is the answer key and must never reach
 * another client.
 * 每名推理学家的记录：他见过的每一起死亡的真实凶手、已解出的死者、胜利进度与隐身充能。
 * <p>同步只发给本人——凶手映射就是答案，绝不能下发给其他客户端。
 */
public final class CriminalReasonerComponent implements AutoSyncedComponent {
    public static final ComponentKey<CriminalReasonerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("criminal_reasoner_player"),
            CriminalReasonerComponent.class
    );

    private final PlayerEntity player;
    private final Map<UUID, UUID> victimToKiller = new HashMap<>();
    private final Set<UUID> solvedVictims = new HashSet<>();
    private int successfulReasoningCount;
    private int requiredReasoningCount;
    private int invisibilityCharges;
    /** Remaining reasoning cooldown in seconds, mirrored to the owner's HUD. 推理冷却剩余秒数，同步给本人 HUD。 */
    private int cooldownSeconds;

    public CriminalReasonerComponent(PlayerEntity player) {
        this.player = player;
    }

    public void reset() {
        victimToKiller.clear();
        solvedVictims.clear();
        successfulReasoningCount = 0;
        requiredReasoningCount = 0;
        invisibilityCharges = 0;
        cooldownSeconds = 0;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    public void recordReasoningTarget(UUID victimUuid, UUID killerUuid) {
        if (victimUuid == null || killerUuid == null) {
            return;
        }
        victimToKiller.put(victimUuid, killerUuid);
        sync();
    }

    /** True when the suspected killer is the recorded one. 嫌疑人是否就是记录在案的凶手。 */
    public boolean isCorrectReasoning(UUID victimUuid, UUID suspectUuid) {
        if (victimUuid == null || suspectUuid == null) {
            return false;
        }
        UUID actualKiller = victimToKiller.get(victimUuid);
        return suspectUuid.equals(actualKiller);
    }

    /** Marks a victim solved once; a repeat solve of the same victim grants nothing. 同一名死者只计一次。 */
    public boolean recordSuccessfulReasoning(UUID victimUuid) {
        if (victimUuid == null || !solvedVictims.add(victimUuid)) {
            return false;
        }
        successfulReasoningCount++;
        invisibilityCharges += CriminalReasonerRules.CHARGES_PER_SUCCESS;
        sync();
        return true;
    }

    public boolean hasSolvedVictim(UUID victimUuid) {
        return victimUuid != null && solvedVictims.contains(victimUuid);
    }

    public int getSuccessfulReasoningCount() {
        return successfulReasoningCount;
    }

    public int getRequiredReasoningCount() {
        return requiredReasoningCount;
    }

    public void setRequiredReasoningCount(int requiredReasoningCount) {
        this.requiredReasoningCount = requiredReasoningCount;
        sync();
    }

    public int getInvisibilityCharges() {
        return invisibilityCharges;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    /** Server-driven; syncs only when the displayed second changes. 服务端驱动；只在显示的秒数变化时同步。 */
    public void setCooldownSeconds(int seconds) {
        int clamped = Math.max(0, seconds);
        if (clamped != cooldownSeconds) {
            cooldownSeconds = clamped;
            sync();
        }
    }

    public boolean consumeInvisibilityCharge() {
        if (invisibilityCharges <= 0) {
            return false;
        }
        invisibilityCharges--;
        sync();
        return true;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(successfulReasoningCount);
        buf.writeInt(requiredReasoningCount);
        buf.writeInt(invisibilityCharges);
        buf.writeInt(cooldownSeconds);
        buf.writeInt(solvedVictims.size());
        for (UUID solved : solvedVictims) {
            buf.writeUuid(solved);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        successfulReasoningCount = buf.readInt();
        requiredReasoningCount = buf.readInt();
        invisibilityCharges = buf.readInt();
        cooldownSeconds = buf.readInt();
        solvedVictims.clear();
        int solvedCount = buf.readInt();
        for (int i = 0; i < solvedCount; i++) {
            solvedVictims.add(buf.readUuid());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        victimToKiller.clear();
        solvedVictims.clear();
        successfulReasoningCount = tag.getInt("successfulReasoningCount");
        requiredReasoningCount = tag.contains("requiredReasoningCount") ? tag.getInt("requiredReasoningCount") : 0;
        invisibilityCharges = tag.contains("invisibilityCharges") ? tag.getInt("invisibilityCharges") : 0;
        cooldownSeconds = 0;
        if (tag.contains("victimToKiller")) {
            NbtCompound mapping = tag.getCompound("victimToKiller");
            for (String key : mapping.getKeys()) {
                try {
                    victimToKiller.put(UUID.fromString(key), mapping.getUuid(key));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed entries instead of losing the whole ledger.
                    // 跳过损坏条目，不因此丢掉整本记录。
                }
            }
        }
        if (tag.contains("solvedVictims")) {
            NbtList list = tag.getList("solvedVictims", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                try {
                    solvedVictims.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Same as above. 同上。
                }
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound mapping = new NbtCompound();
        victimToKiller.forEach((victim, killer) -> mapping.putUuid(victim.toString(), killer));
        tag.put("victimToKiller", mapping);

        NbtList solved = new NbtList();
        for (UUID id : solvedVictims) {
            solved.add(NbtString.of(id.toString()));
        }
        tag.put("solvedVictims", solved);
        tag.putInt("successfulReasoningCount", successfulReasoningCount);
        tag.putInt("requiredReasoningCount", requiredReasoningCount);
        tag.putInt("invisibilityCharges", invisibilityCharges);
    }
}
