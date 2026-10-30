package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The active Knight duel. Public state (pair + remaining time) syncs to clients; pre-duel effects, teams and the
 * applied cooldown set are server-only bookkeeping for cleanup.
 * 当前骑士决斗。公开状态（双方 + 剩余时间）同步给客户端；决斗前效果、队伍与已施加冷却集合仅服务端用于收尾。
 */
public final class KnightDuelWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<KnightDuelWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("knight_duel"), KnightDuelWorldComponent.class);
    private final World world;
    private boolean active;
    private UUID knight;
    private UUID target;
    private long endTick;
    private final Map<UUID, StatusEffectInstance> previousGlowing = new HashMap<>();
    private final Map<UUID, StatusEffectInstance> previousSpeed = new HashMap<>();
    private final Map<UUID, String> previousTeams = new HashMap<>();
    private final Set<Item> cooledItems = new HashSet<>();

    public KnightDuelWorldComponent(World world) {
        this.world = world;
    }

    public boolean active() {
        return active;
    }

    /** 1 while a duel is running, 0 otherwise. / 决斗进行中为 1，否则为 0。 */
    public int duelVariable() {
        return active ? 1 : 0;
    }

    public @Nullable UUID knight() {
        return knight;
    }

    public @Nullable UUID target() {
        return target;
    }

    public boolean isDuelist(@Nullable UUID player) {
        return active && player != null && (player.equals(knight) || player.equals(target));
    }

    public boolean isKnight(@Nullable UUID player) {
        return active && player != null && player.equals(knight);
    }

    public boolean isTarget(@Nullable UUID player) {
        return active && player != null && player.equals(target);
    }

    public int remainingTicks() {
        if (!active) {
            return 0;
        }
        long remaining = endTick - world.getTime();
        return remaining <= 0 ? 0 : (int) Math.min(KnightDuelRules.DUEL_TICKS, remaining);
    }

    void begin(UUID knight, UUID target, long endTick) {
        this.active = true;
        this.knight = knight;
        this.target = target;
        this.endTick = endTick;
        sync();
    }

    void clearState() {
        active = false;
        knight = null;
        target = null;
        endTick = 0;
        previousGlowing.clear();
        previousSpeed.clear();
        previousTeams.clear();
        cooledItems.clear();
    }

    Map<UUID, StatusEffectInstance> previousGlowing() {
        return previousGlowing;
    }

    Map<UUID, StatusEffectInstance> previousSpeed() {
        return previousSpeed;
    }

    Map<UUID, String> previousTeams() {
        return previousTeams;
    }

    Set<Item> cooledItems() {
        return cooledItems;
    }

    public void sync() {
        if (!world.isClient) {
            KEY.sync(world);
        }
    }

    @Override
    public void serverTick() {
        if (active && world instanceof ServerWorld serverWorld) {
            KnightDuelService.tick(serverWorld, this);
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(active);
        if (!active) {
            return;
        }
        buf.writeUuid(knight);
        buf.writeUuid(target);
        buf.writeVarInt(remainingTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        boolean incomingActive = buf.readBoolean();
        if (!incomingActive) {
            clearState();
            return;
        }
        knight = buf.readUuid();
        target = buf.readUuid();
        endTick = world.getTime() + buf.readVarInt();
        active = true;
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putBoolean("Active", active);
        if (active && knight != null && target != null) {
            tag.putUuid("Knight", knight);
            tag.putUuid("Target", target);
            tag.putLong("EndTick", endTick);
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        clearState();
        if (!tag.getBoolean("Active") || !tag.containsUuid("Knight") || !tag.containsUuid("Target")) {
            return;
        }
        active = true;
        knight = tag.getUuid("Knight");
        target = tag.getUuid("Target");
        endTick = tag.getLong("EndTick");
    }
}
