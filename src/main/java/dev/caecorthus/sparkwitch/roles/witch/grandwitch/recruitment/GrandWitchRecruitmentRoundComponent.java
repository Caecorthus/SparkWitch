package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Durable, world-owned opening population and cumulative successful recruit ledger.
 * 世界持久化开局人数和累计成功记录；死亡、断线、转职不会补回名额。 */
public final class GrandWitchRecruitmentRoundComponent implements Component {
    public static final ComponentKey<GrandWitchRecruitmentRoundComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("grand_witch_recruitment_round"), GrandWitchRecruitmentRoundComponent.class);
    private int participants;
    private boolean active;
    private final List<UUID> recruited = new ArrayList<>();
    // Pending offline releases outlive quota resets; JOIN checks live game participation before release.
    // 离线释放记录跨越名额重置；重连时先检查当前对局参与状态，避免复活旁观者。
    private final Map<UUID, Vec3d> pendingReleases = new HashMap<>();
    private boolean conversionInProgress;

    public GrandWitchRecruitmentRoundComponent(World world) { }

    public void beginRound(int openingParticipants) {
        participants = Math.max(0, openingParticipants);
        recruited.clear();
        active = true;
        conversionInProgress = false;
    }

    public void clearRound() {
        participants = 0;
        recruited.clear();
        active = false;
        conversionInProgress = false;
    }

    public int getParticipants() { return participants; }
    public int getLimit() { return active ? GrandWitchRecruitmentRules.limit(participants) : 0; }
    public int getRemaining() { return Math.max(0, getLimit() - recruited.size()); }
    public boolean wasRecruited(UUID target) { return recruited.contains(target); }

    public boolean tryBeginConversion() {
        if (conversionInProgress || getRemaining() <= 0) return false;
        conversionInProgress = true;
        return true;
    }

    public void finishConversion() { conversionInProgress = false; }
    public void recordSuccess(UUID target) { recruited.add(target); }

    public void queueRelease(UUID swallowed, Vec3d position) { pendingReleases.put(swallowed, position); }
    public @Nullable Vec3d takeRelease(UUID swallowed) { return pendingReleases.remove(swallowed); }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt("OpeningParticipants", participants);
        tag.putBoolean("Active", active);
        NbtList list = new NbtList();
        for (UUID uuid : recruited) {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Player", uuid);
            list.add(entry);
        }
        tag.put("Recruited", list);
        NbtList releases = new NbtList();
        pendingReleases.forEach((uuid, position) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Player", uuid);
            entry.putDouble("X", position.x);
            entry.putDouble("Y", position.y);
            entry.putDouble("Z", position.z);
            releases.add(entry);
        });
        tag.put("PendingReleases", releases);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        participants = Math.max(0, tag.getInt("OpeningParticipants"));
        active = tag.getBoolean("Active");
        recruited.clear();
        for (NbtElement element : tag.getList("Recruited", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("Player")) recruited.add(entry.getUuid("Player"));
        }
        pendingReleases.clear();
        for (NbtElement element : tag.getList("PendingReleases", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("Player")) {
                pendingReleases.put(entry.getUuid("Player"), new Vec3d(
                        entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")));
            }
        }
        conversionInProgress = false;
    }
}
