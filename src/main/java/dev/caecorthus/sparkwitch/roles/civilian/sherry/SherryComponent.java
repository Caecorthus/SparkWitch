package dev.caecorthus.sparkwitch.roles.civilian.sherry;

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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 橘雪莉's own ledger: every badge she handed out, and the deduction cooldown shown on her HUD.
 * <p>Only the owner receives the payload — the badge list is her private informant network.
 * 橘雪莉自己的记录：她送出的每一枚徽章，以及 HUD 上显示的推演冷却。
 * <p>只同步给本人——徽章名单是她的私人线人网络。
 */
public final class SherryComponent implements AutoSyncedComponent {
    public static final ComponentKey<SherryComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("sherry_player"),
            SherryComponent.class
    );

    private final PlayerEntity player;
    private final Set<UUID> badgeHolders = new HashSet<>();
    private int deductionCooldownSeconds;
    /** Mighty Force: seconds left on the window, and seconds until it can be used again. 巨力：窗口剩余秒数与冷却剩余秒数。 */
    private int mightyActiveSeconds;
    private int mightyCooldownSeconds;

    public SherryComponent(PlayerEntity player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(player);
    }

    public void reset() {
        badgeHolders.clear();
        deductionCooldownSeconds = 0;
        sync();
    }

    public boolean addBadgeHolder(UUID uuid) {
        if (uuid == null || !badgeHolders.add(uuid)) {
            return false;
        }
        sync();
        return true;
    }

    public boolean isBadgeHolder(UUID uuid) {
        return uuid != null && badgeHolders.contains(uuid);
    }

    public Set<UUID> badgeHolders() {
        return Set.copyOf(badgeHolders);
    }

    public void removeBadgeHolder(UUID uuid) {
        if (badgeHolders.remove(uuid)) {
            sync();
        }
    }

    public int getMightyActiveSeconds() {
        return mightyActiveSeconds;
    }

    public int getMightyCooldownSeconds() {
        return mightyCooldownSeconds;
    }

    public void setMightyState(int activeSeconds, int cooldownSeconds) {
        int a = Math.max(0, activeSeconds);
        int c = Math.max(0, cooldownSeconds);
        if (a != mightyActiveSeconds || c != mightyCooldownSeconds) {
            mightyActiveSeconds = a;
            mightyCooldownSeconds = c;
            sync();
        }
    }

    public int getDeductionCooldownSeconds() {
        return deductionCooldownSeconds;
    }

    /** Server-driven; syncs only when the displayed second changes. 服务端驱动；只在显示的秒数变化时同步。 */
    public void setDeductionCooldownSeconds(int seconds) {
        int clamped = Math.max(0, seconds);
        if (clamped != deductionCooldownSeconds) {
            deductionCooldownSeconds = clamped;
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeInt(deductionCooldownSeconds);
        buf.writeInt(mightyActiveSeconds);
        buf.writeInt(mightyCooldownSeconds);
        buf.writeInt(badgeHolders.size());
        for (UUID uuid : badgeHolders) {
            buf.writeUuid(uuid);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        deductionCooldownSeconds = buf.readInt();
        mightyActiveSeconds = buf.readInt();
        mightyCooldownSeconds = buf.readInt();
        badgeHolders.clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            badgeHolders.add(buf.readUuid());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        badgeHolders.clear();
        deductionCooldownSeconds = 0;
        mightyActiveSeconds = 0;
        mightyCooldownSeconds = 0;
        if (tag.contains("badgeHolders")) {
            NbtList list = tag.getList("badgeHolders", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                try {
                    badgeHolders.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Skip a malformed entry instead of losing the whole list. / 跳过损坏条目。
                }
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (UUID uuid : badgeHolders) {
            list.add(NbtString.of(uuid.toString()));
        }
        tag.put("badgeHolders", list);
    }
}
