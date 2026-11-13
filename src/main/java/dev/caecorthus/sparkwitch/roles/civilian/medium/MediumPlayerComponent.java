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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-private Medium state: the 通灵 and 幽灵的加护 cooldowns (招魂 has none), the 招魂-bound targets in the order
 * they were paid for, and the corpse entities the 尸体透视 blessing highlights. A paid use of either skill starts both
 * cooldowns ({@link #startCooldowns}); their lengths are fixed the moment they start (owner rule 2026-11-10: deaths
 * after that only shape the next cooldown); the prices are always read live from the current death count. Everything
 * is server-authoritative, synced to the owner once per second and counted down locally for the HUD; the client never
 * predicts a use.
 * 通灵师仅同步给本人的状态：通灵与幽灵的加护两个冷却（招魂无冷却）、按付费先后排序的招魂绑定名单，以及尸体透视加护高亮的
 * 尸体实体。任一技能的付费使用都会同时开始两个冷却（{@link #startCooldowns}）；冷却长度在进入冷却那一刻固定
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
    private static final String BLESSING_COOLDOWN_TICKS = "BlessingCooldownTicks";
    private static final String BOUND_TARGETS = "BoundTargets";
    private static final String CORPSE_SIGHT = "CorpseSight";
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final PlayerEntity player;
    private int seanceCooldownTicks;
    private int blessingCooldownTicks;
    private final List<UUID> boundTargets = new ArrayList<>();
    /** Entity UUIDs of the corpses the 尸体透视 blessing shows; unioned over repeated grants.
     * 尸体透视加护显示的尸体实体 UUID；多次获得时取并集。 */
    private final Set<UUID> corpseSightBodies = new LinkedHashSet<>();
    private int syncTicks;

    public MediumPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getSeanceCooldownTicks() {
        return Math.max(0, seanceCooldownTicks);
    }

    public int getBlessingCooldownTicks() {
        return Math.max(0, blessingCooldownTicks);
    }

    /** The 招魂-bound targets in binding order; a defensive copy. / 按绑定先后排序的名单；返回防御性副本。 */
    public List<UUID> getBoundTargets() {
        return List.copyOf(boundTargets);
    }

    public boolean isBound(@Nullable UUID uuid) {
        return uuid != null && boundTargets.contains(uuid);
    }

    /**
     * Entity UUIDs (not player UUIDs) of the Wathe corpses the 尸体透视 blessing highlights for the owner; a defensive
     * copy. Kept until round end or the Medium's death ({@link #clear()}); a corpse removed from the world simply stops
     * resolving on the client.
     * 尸体透视加护为本人高亮的 Wathe 尸体的实体 UUID（不是玩家 UUID）；返回防御性副本。保留到回合结束或通灵师死亡
     * （{@link #clear()}）；从世界中移除的尸体在客户端自然解析不到。
     */
    public Set<UUID> getCorpseSightBodies() {
        return Set.copyOf(corpseSightBodies);
    }

    /** Whether the corpse entity with this UUID is highlighted for the owner. / 该实体 UUID 的尸体是否为本人高亮。 */
    public boolean seesCorpse(@Nullable UUID bodyUuid) {
        return bodyUuid != null && corpseSightBodies.contains(bodyUuid);
    }

    /**
     * Server only, from {@code RoleAssigned}: the opening cooldowns (通灵 180 s, 加护 300 s) for a fresh Medium, cleared
     * otherwise. The pre-round window keeps the countdown (the Specter second-skill precedent).
     * 仅服务端，来自 {@code RoleAssigned}：新通灵师进入开局冷却（通灵 180 秒、加护 300 秒），其余职业清空。回合开始前不清
     * 倒计时（幽魂二技能先例）。
     */
    public void assignForRole(@Nullable Role role) {
        if (!MediumRules.isMedium(role)) {
            clear();
            return;
        }
        int seanceOpening = MediumRules.CD_OPENING_TICKS;
        int blessingOpening = MediumRules.BLESSING_CD_OPENING_TICKS;
        if (seanceCooldownTicks == seanceOpening && blessingCooldownTicks == blessingOpening
                && boundTargets.isEmpty() && corpseSightBodies.isEmpty()) {
            return;
        }
        seanceCooldownTicks = seanceOpening;
        blessingCooldownTicks = blessingOpening;
        boundTargets.clear();
        corpseSightBodies.clear();
        syncTicks = 0;
        syncOwner();
    }

    /**
     * Server only: a paid 通灵 or 加护 was just used at this death tier, so BOTH cooldowns enter now (通灵 60 s − 10 s
     * per tier, 加护 that + 120 s). The single entry both services use.
     * 仅服务端：刚以该死亡档位付费使用了通灵或加护，两个冷却此刻同时开始（通灵 60 秒 − 每档 10 秒，加护在此基础上 +120 秒）。
     * 两个服务共用的唯一入口。
     * A timer that is already longer is kept: in normal play neither can be (加护 is never ready before 通灵), so this
     * only stops a use of one skill from shortening a cooldown that was force-raised on the other skill's store.
     * 已经更长的计时保持不变：正常对局中不会出现（加护不会先于通灵就绪），这只是防止使用其中一个技能时，
     * 把另一个技能存储上被强制拉长的冷却缩短。
     */
    public void startCooldowns(int tier) {
        seanceCooldownTicks = Math.max(seanceCooldownTicks, MediumRules.seanceCooldownAfterUse(tier));
        blessingCooldownTicks = Math.max(blessingCooldownTicks, MediumRules.blessingCooldownAfterUse(tier));
        syncTicks = 0;
        syncOwner();
    }

    /**
     * Server only: exact write of the 通灵 cooldown alone (floored at 0), for the forced-cooldown store; a real use
     * goes through {@link #startCooldowns}.
     * 仅服务端：只精确写入通灵冷却（下限为 0），供强制冷却存储使用；真实使用走 {@link #startCooldowns}。
     */
    public void startSeanceCooldown(int ticks) {
        seanceCooldownTicks = Math.max(0, ticks);
        syncTicks = 0;
        syncOwner();
    }

    /**
     * Server only: exact write of the 加护 cooldown alone (floored at 0), for the forced-cooldown store.
     * 仅服务端：只精确写入加护冷却（下限为 0），供强制冷却存储使用。
     */
    public void startBlessingCooldown(int ticks) {
        blessingCooldownTicks = Math.max(0, ticks);
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

    /**
     * Server only: the 尸体透视 grant adds this snapshot of corpse entity UUIDs to the highlighted set.
     * 仅服务端：尸体透视加护把这份尸体实体 UUID 快照并入高亮集合。
     */
    public void addCorpseSightBodies(@Nullable Collection<UUID> bodyUuids) {
        if (bodyUuids == null) {
            return;
        }
        boolean changed = false;
        for (UUID uuid : bodyUuids) {
            changed |= uuid != null && corpseSightBodies.add(uuid);
        }
        if (changed) {
            syncOwner();
        }
    }

    /** Clears on death, reset, round end or role change. / 死亡、重置、回合结束或职业变更时清空。 */
    public void clear() {
        if (seanceCooldownTicks == 0 && blessingCooldownTicks == 0 && boundTargets.isEmpty()
                && corpseSightBodies.isEmpty()) {
            return;
        }
        // A live window this player paid for keeps its 招魂 weights even if the caster dies or changes role before
        // the tally. / 该玩家付费开启的进行中窗口，即使施放者在计票前死亡或转职，也保留其招魂票权。
        MediumSeanceComponent seance = MediumSeanceComponent.KEY.get(player.getWorld());
        if (player.getUuid().equals(seance.getCasterUuid())) {
            seance.retainBoundTargets(boundTargets);
        }
        seanceCooldownTicks = 0;
        blessingCooldownTicks = 0;
        boundTargets.clear();
        corpseSightBodies.clear();
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
        if (blessingCooldownTicks > 0) {
            blessingCooldownTicks--;
        }
        if (++syncTicks < SYNC_INTERVAL_TICKS) {
            return;
        }
        syncTicks = 0;
        if (seanceCooldownTicks > 0 || blessingCooldownTicks > 0) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        if (seanceCooldownTicks > 0) {
            seanceCooldownTicks--;
        }
        if (blessingCooldownTicks > 0) {
            blessingCooldownTicks--;
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (seanceCooldownTicks > 0) {
            tag.putInt(SEANCE_COOLDOWN_TICKS, seanceCooldownTicks);
        }
        if (blessingCooldownTicks > 0) {
            tag.putInt(BLESSING_COOLDOWN_TICKS, blessingCooldownTicks);
        }
        if (!boundTargets.isEmpty()) {
            tag.put(BOUND_TARGETS, writeUuids(boundTargets));
        }
        if (!corpseSightBodies.isEmpty()) {
            tag.put(CORPSE_SIGHT, writeUuids(corpseSightBodies));
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        seanceCooldownTicks = readTicks(tag, SEANCE_COOLDOWN_TICKS);
        blessingCooldownTicks = readTicks(tag, BLESSING_COOLDOWN_TICKS);
        boundTargets.clear();
        readUuids(tag, BOUND_TARGETS, boundTargets);
        corpseSightBodies.clear();
        readUuids(tag, CORPSE_SIGHT, corpseSightBodies);
    }

    private static NbtList writeUuids(Collection<UUID> uuids) {
        NbtList list = new NbtList();
        for (UUID uuid : uuids) {
            list.add(NbtString.of(uuid.toString()));
        }
        return list;
    }

    private static void readUuids(NbtCompound tag, String key, Collection<UUID> into) {
        if (!tag.contains(key, NbtElement.LIST_TYPE)) {
            return;
        }
        for (NbtElement element : tag.getList(key, NbtElement.STRING_TYPE)) {
            try {
                UUID uuid = UUID.fromString(element.asString());
                if (!into.contains(uuid)) {
                    into.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {
                // A corrupted entry is dropped, never fatal. / 损坏条目直接丢弃，绝不致命。
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
        // Layout: 通灵 cooldown, 加护 cooldown, bound targets (count + UUIDs), corpse-sight bodies (count + UUIDs).
        // 布局：通灵冷却、加护冷却、招魂绑定（数量 + UUID）、尸体透视尸体（数量 + UUID）。
        buf.writeVarInt(getSeanceCooldownTicks());
        buf.writeVarInt(getBlessingCooldownTicks());
        buf.writeVarInt(boundTargets.size());
        for (UUID uuid : boundTargets) {
            buf.writeUuid(uuid);
        }
        buf.writeVarInt(corpseSightBodies.size());
        for (UUID uuid : corpseSightBodies) {
            buf.writeUuid(uuid);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        seanceCooldownTicks = Math.max(0, buf.readVarInt());
        blessingCooldownTicks = Math.max(0, buf.readVarInt());
        boundTargets.clear();
        int count = Math.max(0, buf.readVarInt());
        for (int i = 0; i < count; i++) {
            boundTargets.add(buf.readUuid());
        }
        corpseSightBodies.clear();
        int bodies = Math.max(0, buf.readVarInt());
        for (int i = 0; i < bodies; i++) {
            corpseSightBodies.add(buf.readUuid());
        }
    }

    private void syncOwner() {
        // The handler check is CCA's own recipient rule for the owner. / 对网络处理器的判断即 CCA 自身为本人选择接收者的规则。
        if (player instanceof ServerPlayerEntity owner && owner.networkHandler != null) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
