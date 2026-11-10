package dev.caecorthus.sparkwitch.roles.civilian.medium;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsAbyssListenerBridge;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owner-private Medium state: the 通灵 cooldown (招魂 has none) and the 招魂-bound targets in the order they were paid
 * for. The cooldown's length is fixed the moment it starts (owner rule 2026-11-10: deaths after that only shape the
 * next cooldown); the prices are always read live from the current death count. Everything is server-authoritative,
 * synced to the owner once per second and counted down locally for the HUD; the client never predicts a use.
 * 通灵师仅同步给本人的状态：通灵冷却（招魂无冷却）与按付费先后排序的招魂绑定名单。冷却长度在进入冷却那一刻固定
 * （用户规则 2026-11-10：之后的死亡只影响下一次进入的冷却）；价格始终按当前死亡数实时读取。全部由服务端权威，每秒同步给本人，
 * 客户端本地倒计时供 HUD 平滑显示；客户端从不预判使用。
 */
public final class MediumPlayerComponent implements AutoSyncedComponent, ServerTickingComponent,
        ClientTickingComponent {
    public static final ComponentKey<MediumPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("medium"),
            MediumPlayerComponent.class
    );
    private static final String SEANCE_COOLDOWN_TICKS = "SeanceCooldownTicks";
    private static final String BOUND_TARGETS = "BoundTargets";
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final PlayerEntity player;
    private int seanceCooldownTicks;
    private final List<UUID> boundTargets = new ArrayList<>();
    private int syncTicks;

    public MediumPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getSeanceCooldownTicks() {
        return Math.max(0, seanceCooldownTicks);
    }

    /** The 招魂-bound targets in binding order; a defensive copy. / 按绑定先后排序的名单；返回防御性副本。 */
    public List<UUID> getBoundTargets() {
        return List.copyOf(boundTargets);
    }

    public boolean isBound(@Nullable UUID uuid) {
        return uuid != null && boundTargets.contains(uuid);
    }

    /**
     * Server only, from {@code RoleAssigned}: the opening 通灵 cooldown (tier 0) for a fresh Medium, cleared otherwise.
     * The pre-round window keeps the countdown (the Specter second-skill precedent).
     * 仅服务端，来自 {@code RoleAssigned}：新通灵师进入通灵开局冷却（档位 0），其余职业清空。回合开始前不清倒计时
     * （幽魂二技能先例）。
     */
    public void assignForRole(@Nullable Role role) {
        if (!MediumRules.isMedium(role)) {
            clear();
            return;
        }
        int opening = MediumRules.cooldownTicks(MediumRules.CD_OPENING_TICKS, 0);
        if (seanceCooldownTicks == opening && boundTargets.isEmpty()) {
            return;
        }
        seanceCooldownTicks = opening;
        boundTargets.clear();
        syncTicks = 0;
        syncOwner();
    }

    /** Server only: the 通灵 cooldown enters now with this length. / 仅服务端：通灵冷却此刻以该时长开始。 */
    public void startSeanceCooldown(int ticks) {
        seanceCooldownTicks = Math.max(0, ticks);
        syncTicks = 0;
        syncOwner();
    }

    /** Server only: appends one 招魂 binding; repeats and nulls change nothing. / 仅服务端：追加一条招魂绑定；重复与空值不变。 */
    public void addBoundTarget(@Nullable UUID uuid) {
        if (uuid == null || boundTargets.contains(uuid)) {
            return;
        }
        boundTargets.add(uuid);
        syncOwner();
    }

    /** Clears on death, reset, round end or role change. / 死亡、重置、回合结束或职业变更时清空。 */
    public void clear() {
        if (seanceCooldownTicks == 0 && boundTargets.isEmpty()) {
            return;
        }
        // A live window this player paid for keeps its 招魂 weights even if the caster dies or changes role before
        // the tally. / 该玩家付费开启的进行中窗口，即使施放者在计票前死亡或转职，也保留其招魂票权。
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(player.getWorld());
        if (player.getUuid().equals(seance.getCasterUuid())) {
            seance.retainBoundTargets(boundTargets);
        }
        seanceCooldownTicks = 0;
        boundTargets.clear();
        syncTicks = 0;
        syncOwner();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        if (!MediumRules.isMedium(game.getRole(owner))) {
            clear();
            return;
        }
        // Roles are dealt before the round is RUNNING; keep the opening cooldown through that window.
        // 职业在回合 RUNNING 之前发放；这段时间保留开局冷却。
        if (!game.isRunning()) {
            return;
        }
        // A Last Stand hold keeps the state counting (Dominator / Time Stealer / Seeker precedent).
        // 背水一战的保持期内状态照常倒数（支配者、窃时者、搜寻者先例）。
        boolean lastStandPending = SparkTraitsAbyssListenerBridge.isLastStandPending(owner);
        // Only a Wathe death clears. An alive spectator (Taotie-swallowed, inside a Rift gate) is still playing: it
        // keeps the paid 招魂 bindings and the cooldown keeps counting, so being swallowed or hopping a gate can
        // neither wipe paid state nor zero the opening cooldown. It cannot cast meanwhile (mayUse refuses).
        // 只有 Wathe 判定的死亡才清空。存活旁观者（被饕餮吞下、身处裂隙门内）仍在对局中：保留已付费的招魂绑定，冷却照常
        // 倒数，因此被吞或钻门既不会抹掉已付费状态，也不会把开局冷却清零。其间无法施放（mayUse 会拒绝）。
        if (!lastStandPending && !GameFunctions.isPlayerPlayingAndAlive(owner)) {
            clear();
            return;
        }
        if (seanceCooldownTicks > 0) {
            seanceCooldownTicks--;
        }
        if (++syncTicks < SYNC_INTERVAL_TICKS) {
            return;
        }
        syncTicks = 0;
        if (seanceCooldownTicks > 0) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        if (seanceCooldownTicks > 0) {
            seanceCooldownTicks--;
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (seanceCooldownTicks > 0) {
            tag.putInt(SEANCE_COOLDOWN_TICKS, seanceCooldownTicks);
        }
        if (!boundTargets.isEmpty()) {
            NbtList list = new NbtList();
            for (UUID uuid : boundTargets) {
                list.add(NbtString.of(uuid.toString()));
            }
            tag.put(BOUND_TARGETS, list);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        seanceCooldownTicks = readTicks(tag, SEANCE_COOLDOWN_TICKS);
        boundTargets.clear();
        if (tag.contains(BOUND_TARGETS, NbtElement.LIST_TYPE)) {
            for (NbtElement element : tag.getList(BOUND_TARGETS, NbtElement.STRING_TYPE)) {
                try {
                    boundTargets.add(UUID.fromString(element.asString()));
                } catch (IllegalArgumentException ignored) {
                    // A corrupted entry is dropped, never fatal. / 损坏条目直接丢弃，绝不致命。
                }
            }
        }
    }

    private static int readTicks(NbtCompound tag, String key) {
        return tag.contains(key, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(key)) : 0;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getSeanceCooldownTicks());
        buf.writeVarInt(boundTargets.size());
        for (UUID uuid : boundTargets) {
            buf.writeUuid(uuid);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        seanceCooldownTicks = Math.max(0, buf.readVarInt());
        boundTargets.clear();
        int count = Math.max(0, buf.readVarInt());
        for (int i = 0; i < count; i++) {
            boundTargets.add(buf.readUuid());
        }
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
