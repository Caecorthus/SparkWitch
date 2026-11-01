package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player Ferryman state ({@code sparkwitch:ferryman}): the ability cooldown, the pending pardon window (killer and
 * death reason held only until the window closes or the key is pressed), the attackers the Ferryman may see through
 * walls (瞥视) and the bodies already ferried (怜悯). The server owns every field; the owner's client receives the
 * remaining cooldown and both id sets (never the death reason or the server time) and renders the marks from them.
 * Transient: a round reset or a respawn copy never carries it (the Wathe round resets the role itself).
 * 每名玩家的摆渡人状态（{@code sparkwitch:ferryman}）：技能冷却、待决的宽恕窗口（仅保存到窗口关闭或按键为止的击杀者与
 * 死亡原因）、可被摆渡人透视的攻击者（瞥视）与已摆渡的遗体（怜悯）。全部字段由服务端拥有；拥有者客户端只接收剩余
 * 冷却与两组 id（绝不含死亡原因或服务端时间），并据此渲染标记。瞬时状态：换局或重生不会带走它。
 */
public final class FerrymanPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<FerrymanPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ferryman"),
            FerrymanPlayerComponent.class
    );

    private final PlayerEntity player;
    private final Set<UUID> gazeTargets = new LinkedHashSet<>();
    private final Set<UUID> ferriedBodies = new LinkedHashSet<>();

    private int cooldownTicks;
    private boolean pardoning;
    private @Nullable UUID pendingKiller;
    private @Nullable Identifier pendingDeathReason;
    private long pardonDeadlineTick;

    public FerrymanPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public static FerrymanPlayerComponent get(PlayerEntity player) {
        return KEY.get(player);
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** Server: arms the ability for {@code ticks}; 0 clears it. / 服务端：把技能冷却设为 ticks，0 为就绪。 */
    public void setCooldown(int ticks) {
        int next = Math.max(0, ticks);
        if (cooldownTicks == next) {
            return;
        }
        cooldownTicks = next;
        syncOwner();
    }

    /** Server: one tick of cooldown. Returns true when it became ready this tick. / 服务端：推进一格冷却；本 tick 变为就绪时返回 true。 */
    public boolean tickCooldown() {
        if (cooldownTicks <= 0) {
            return false;
        }
        cooldownTicks--;
        if (cooldownTicks == 0) {
            syncOwner();
            return true;
        }
        return false;
    }

    // ---- Pardon window (server only) / 宽恕窗口（仅服务端） ----

    public boolean isPardoning() {
        return pardoning;
    }

    public void openPardon(UUID killer, @Nullable Identifier deathReason, long deadlineTick) {
        pardoning = true;
        pendingKiller = killer;
        pendingDeathReason = deathReason;
        pardonDeadlineTick = deadlineTick;
    }

    public long pardonDeadlineTick() {
        return pardonDeadlineTick;
    }

    public @Nullable UUID pendingKiller() {
        return pendingKiller;
    }

    public @Nullable Identifier pendingDeathReason() {
        return pendingDeathReason;
    }

    public void closePardon() {
        pardoning = false;
        pendingKiller = null;
        pendingDeathReason = null;
        pardonDeadlineTick = 0L;
    }

    // ---- Marks / 标记 ----

    public boolean hasGaze(UUID target) {
        return gazeTargets.contains(target);
    }

    public boolean addGaze(UUID target) {
        if (!gazeTargets.add(target)) {
            return false;
        }
        syncOwner();
        return true;
    }

    public Set<UUID> gazeTargets() {
        return Set.copyOf(gazeTargets);
    }

    public boolean hasFerried(UUID bodyOwner) {
        return ferriedBodies.contains(bodyOwner);
    }

    public boolean addFerried(UUID bodyOwner) {
        if (!ferriedBodies.add(bodyOwner)) {
            return false;
        }
        syncOwner();
        return true;
    }

    /** Server: drops one 怜悯 mark once its body is gone (paid or not). / 服务端：遗体消失后移除对应「怜悯」标记。 */
    public boolean removeFerried(UUID bodyOwner) {
        if (!ferriedBodies.remove(bodyOwner)) {
            return false;
        }
        syncOwner();
        return true;
    }

    public Set<UUID> ferriedBodies() {
        return Set.copyOf(ferriedBodies);
    }

    /** Server: drops every round-scoped field; the cooldown goes back to the round-start value. */
    /** 服务端：清除全部本局字段；冷却回到开局值。 */
    public void clearRoundState() {
        boolean changed = pardoning || !gazeTargets.isEmpty() || !ferriedBodies.isEmpty() || cooldownTicks != 0;
        gazesClear();
        ferriedBodies.clear();
        closePardon();
        cooldownTicks = FerrymanRules.INITIAL_COOLDOWN_TICKS;
        if (changed) {
            syncOwner();
        }
    }

    private void gazesClear() {
        gazeTargets.clear();
    }

    // ---- Sync / 同步 ----

    public void syncOwner() {
        if (!player.getWorld().isClient) {
            KEY.sync(player);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(cooldownTicks);
        buf.writeVarInt(gazeTargets.size());
        for (UUID target : gazeTargets) {
            buf.writeUuid(target);
        }
        buf.writeVarInt(ferriedBodies.size());
        for (UUID body : ferriedBodies) {
            buf.writeUuid(body);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownTicks = buf.readVarInt();
        int gazeCount = buf.readVarInt();
        gazeTargets.clear();
        for (int i = 0; i < gazeCount; i++) {
            gazeTargets.add(buf.readUuid());
        }
        int bodyCount = buf.readVarInt();
        ferriedBodies.clear();
        for (int i = 0; i < bodyCount; i++) {
            ferriedBodies.add(buf.readUuid());
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Transient by design: the round resets the role and the marks with it.
        // 刻意保持瞬时：换局时职业与标记一同重置。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        closePardon();
        gazeTargets.clear();
        ferriedBodies.clear();
        cooldownTicks = 0;
    }

    @Override
    public String toString() {
        return "FerrymanPlayerComponent{" + Objects.toString(player.getUuid()) + "}";
    }
}
