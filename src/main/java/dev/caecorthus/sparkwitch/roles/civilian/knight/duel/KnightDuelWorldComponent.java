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
import java.util.Map;
import java.util.UUID;

/**
 * Stable contract: the single source of the active Knight duel on both sides. The public state (pair + remaining
 * time) is AutoSynced to every client; there is no separate duel packet or client mirror. Pre-duel effects, teams and
 * the sealed items' pre-duel cooldowns are server-only bookkeeping for cleanup and are not persisted.
 * 稳定契约：双端当前骑士决斗的唯一来源。公开状态（双方 + 剩余时间）自动同步给所有客户端；不存在单独的决斗数据包或
 * 客户端镜像。决斗前效果、队伍与被封印物品的决斗前冷却仅服务端用于收尾，且不持久化。
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
    /** Pre-duel NoellesRoles no-collision effect per duelist. / 每名决斗者决斗前的 NoellesRoles 无碰撞效果。 */
    private final Map<UUID, StatusEffectInstance> previousNoCollision = new HashMap<>();
    private final Map<UUID, String> previousTeams = new HashMap<>();
    /** Per duelist: sealed item -> world tick its pre-duel cooldown would have ended. / 每名决斗者：被封印物品 -> 其决斗前冷却本应结束的世界刻。 */
    private final Map<UUID, Map<Item, Long>> sealedItems = new HashMap<>();

    public KnightDuelWorldComponent(World world) {
        this.world = world;
    }

    public boolean active() {
        return active;
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
        previousNoCollision.clear();
        previousTeams.clear();
        sealedItems.clear();
    }

    Map<UUID, StatusEffectInstance> previousGlowing() {
        return previousGlowing;
    }

    Map<UUID, StatusEffectInstance> previousSpeed() {
        return previousSpeed;
    }

    Map<UUID, StatusEffectInstance> previousNoCollision() {
        return previousNoCollision;
    }

    Map<UUID, String> previousTeams() {
        return previousTeams;
    }

    Map<UUID, Map<Item, Long>> sealedItems() {
        return sealedItems;
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
