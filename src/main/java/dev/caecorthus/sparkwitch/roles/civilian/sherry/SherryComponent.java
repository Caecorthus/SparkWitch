package dev.caecorthus.sparkwitch.roles.civilian.sherry;

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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 橘雪莉's owner-private state: the two skill cooldowns, her own 巨力魔法 window, the badge holders and the reveal
 * trackers. Everything is server-authoritative and synced to the owner only (her informants and her suspects are
 * private); the client counts the timers down locally between syncs and never predicts a use. Every round timer lives
 * here, so nothing leaks across rounds through static maps.
 * 橘雪莉仅同步给本人的状态：两个技能冷却、她自己的巨力魔法窗口、徽章持有者与揭示追踪。全部由服务端权威、只同步给本人
 * （线人与嫌疑人都是她的私密信息）；两次同步之间客户端本地倒计时，从不预判使用。所有回合计时都保存在这里，
 * 不通过静态表跨回合泄漏。
 */
public final class SherryComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SherryComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("sherry"),
            SherryComponent.class
    );
    private static final String DEDUCTION_COOLDOWN_TICKS = "DeductionCooldownTicks";
    private static final String MIGHTY_WINDOW_TICKS = "MightyWindowTicks";
    private static final String MIGHTY_COOLDOWN_TICKS = "MightyCooldownTicks";
    private static final String MIGHTY_FORFEITED = "MightyForfeited";
    private static final String BADGE_HOLDERS = "BadgeHolders";
    private static final String TRACKERS = "Trackers";
    private static final String TRACKER_TARGET = "Target";
    private static final String TRACKER_INTERVAL = "Interval";
    private static final String TRACKER_NEXT = "Next";
    private static final String TRACKER_REVEAL = "Reveal";
    private static final int SYNC_INTERVAL_TICKS = 20;

    /**
     * One periodically revealed player. {@code nextTicks} counts down to the next window, {@code revealTicks} is what is
     * left of the current one.
     * 一个被周期性揭示的玩家。{@code nextTicks} 倒数到下一个窗口，{@code revealTicks} 是当前窗口的剩余时间。
     */
    public static final class Tracker {
        private final UUID target;
        private int intervalTicks;
        private int nextTicks;
        private int revealTicks;

        Tracker(UUID target, int intervalTicks, int nextTicks, int revealTicks) {
            this.target = target;
            // Never shorter than one window, also for saved or synced data: a missing or corrupt interval must not
            // turn into a permanent reveal. / 绝不短于一个窗口，存档与同步数据同样如此：缺失或损坏的周期不得变成常亮揭示。
            this.intervalTicks = Math.max(SherryRules.REVEAL_TICKS, intervalTicks);
            this.nextTicks = Math.max(0, nextTicks);
            this.revealTicks = Math.max(0, revealTicks);
        }

        public UUID target() {
            return target;
        }

        public int intervalTicks() {
            return intervalTicks;
        }

        public int nextTicks() {
            return nextTicks;
        }

        public int revealTicks() {
            return revealTicks;
        }
    }

    private final PlayerEntity player;
    private int deductionCooldownTicks;
    private int mightyWindowTicks;
    private int mightyCooldownTicks;
    private boolean mightyForfeited;
    private final Set<UUID> badgeHolders = new LinkedHashSet<>();
    private final List<Tracker> trackers = new ArrayList<>();
    /** Server only, never synced or saved. / 仅服务端，不同步也不存档。 */
    private int incomeTicks;
    private int syncTicks;

    public SherryComponent(PlayerEntity player) {
        this.player = player;
    }

    // ---- Reads ----

    public int getDeductionCooldownTicks() {
        return Math.max(0, deductionCooldownTicks);
    }

    public int getMightyWindowTicks() {
        return Math.max(0, mightyWindowTicks);
    }

    public int getMightyCooldownTicks() {
        return Math.max(0, mightyCooldownTicks);
    }

    public boolean hasMightyWindow() {
        return mightyWindowTicks > 0;
    }

    /** A misfire under Wathe's PREVENT_GUN_PICKUP setting took her Mighty Force away for the round. */
    /** 在 Wathe 的禁止拾枪设置下误杀好人后，她的巨力本局失效。 */
    public boolean isMightyForfeited() {
        return mightyForfeited;
    }

    public boolean isBadgeHolder(@Nullable UUID uuid) {
        return uuid != null && badgeHolders.contains(uuid);
    }

    /** A defensive copy, in hand-out order. / 按赠出先后排序的防御性副本。 */
    public Set<UUID> getBadgeHolders() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(badgeHolders));
    }

    public List<Tracker> getTrackers() {
        return List.copyOf(trackers);
    }

    public boolean isTracked(@Nullable UUID uuid) {
        return findTracker(uuid) != null;
    }

    /** The tracked player's reveal interval, or 0 when not tracked. / 被追踪玩家的揭示周期；未被追踪时为 0。 */
    public int getTrackedIntervalTicks(@Nullable UUID uuid) {
        Tracker tracker = findTracker(uuid);
        return tracker == null ? 0 : tracker.intervalTicks;
    }

    /** Whether this player is inside a live reveal window right now. / 该玩家此刻是否处于揭示窗口内。 */
    public boolean isRevealed(@Nullable UUID uuid) {
        Tracker tracker = findTracker(uuid);
        return tracker != null && tracker.revealTicks > 0;
    }

    private @Nullable Tracker findTracker(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Tracker tracker : trackers) {
            if (tracker.target.equals(uuid)) {
                return tracker;
            }
        }
        return null;
    }

    // ---- Server writes ----

    /**
     * Server only, from {@code RoleAssigned}: a fresh Sherry starts both skills on the opening cooldown with an empty
     * ledger; every other role is cleared. The pre-round window keeps the countdown (Medium precedent).
     * 仅服务端，来自 {@code RoleAssigned}：新的雪莉两个技能都进入开局冷却、记录清空；其余职业一律清空。
     * 回合开始前不清倒计时（通灵师先例）。
     */
    public void assignForRole(@Nullable Role role) {
        if (!SherryRules.isSherry(role)) {
            clear();
            return;
        }
        deductionCooldownTicks = SherryRules.OPENING_COOLDOWN_TICKS;
        mightyCooldownTicks = SherryRules.OPENING_COOLDOWN_TICKS;
        mightyWindowTicks = 0;
        mightyForfeited = false;
        badgeHolders.clear();
        trackers.clear();
        incomeTicks = 0;
        syncTicks = 0;
        syncOwner();
    }

    public void startDeductionCooldown(int ticks) {
        deductionCooldownTicks = Math.max(0, ticks);
        syncTicks = 0;
        syncOwner();
    }

    /** Exact write for the forced-cooldown store. / 强制冷却存储使用的精确写入。 */
    public void setMightyCooldownTicks(int ticks) {
        mightyCooldownTicks = Math.max(0, ticks);
        syncTicks = 0;
        syncOwner();
    }

    public void beginMightyWindow(int ticks) {
        mightyWindowTicks = Math.max(0, ticks);
        syncTicks = 0;
        syncOwner();
    }

    /**
     * Ends the window (a strike landed, or it ran out) and starts the cooldown from that moment, the Apprentice's
     * "cooldown after the active window" rule. A no-op without a live window.
     * 结束窗口（命中，或自然结束）并从此刻开始冷却，与预备魔女“窗口结束后才进入冷却”的规则一致。没有窗口时不做任何事。
     */
    public void endMightyWindow(int cooldownTicks) {
        if (mightyWindowTicks <= 0) {
            return;
        }
        mightyWindowTicks = 0;
        mightyCooldownTicks = Math.max(mightyCooldownTicks, Math.max(0, cooldownTicks));
        syncTicks = 0;
        syncOwner();
    }

    public void forfeitMighty() {
        if (mightyForfeited && mightyWindowTicks == 0) {
            return;
        }
        mightyForfeited = true;
        mightyWindowTicks = 0;
        syncOwner();
    }

    public boolean addBadgeHolder(@Nullable UUID uuid) {
        if (uuid == null || !badgeHolders.add(uuid)) {
            return false;
        }
        syncOwner();
        return true;
    }

    public boolean removeBadgeHolder(@Nullable UUID uuid) {
        if (uuid == null || !badgeHolders.remove(uuid)) {
            return false;
        }
        syncOwner();
        return true;
    }

    /**
     * Starts (or speeds up) the periodic reveal of one player; the first window opens at once. A player tracked twice
     * keeps the shorter interval, so a deduced killer who later kills a badge holder stays on the 20 s cycle.
     * 开始（或加快）对一名玩家的周期性揭示；第一个窗口立即开启。被重复追踪的玩家保留较短的周期，因此先被推演出、后来又杀死
     * 徽章持有者的杀手仍按 20 秒周期高亮。
     *
     * @return false when the tracker list is full / 追踪列表已满时返回 false
     */
    public boolean track(@Nullable UUID target, int intervalTicks) {
        if (target == null || target.equals(player.getUuid())) {
            return false;
        }
        int interval = Math.max(SherryRules.REVEAL_TICKS, intervalTicks);
        Tracker existing = findTracker(target);
        if (existing != null) {
            existing.intervalTicks = Math.min(existing.intervalTicks, interval);
            existing.nextTicks = existing.intervalTicks;
            existing.revealTicks = SherryRules.REVEAL_TICKS;
        } else {
            if (trackers.size() >= SherryRules.MAX_TRACKED) {
                return false;
            }
            trackers.add(new Tracker(target, interval, interval, SherryRules.REVEAL_TICKS));
        }
        syncOwner();
        return true;
    }

    /** Clears on death, reset, round end or role change. / 死亡、重置、回合结束或职业变更时清空。 */
    public void clear() {
        if (deductionCooldownTicks == 0 && mightyWindowTicks == 0 && mightyCooldownTicks == 0 && !mightyForfeited
                && badgeHolders.isEmpty() && trackers.isEmpty()) {
            incomeTicks = 0;
            return;
        }
        deductionCooldownTicks = 0;
        mightyWindowTicks = 0;
        mightyCooldownTicks = 0;
        mightyForfeited = false;
        badgeHolders.clear();
        trackers.clear();
        incomeTicks = 0;
        syncTicks = 0;
        syncOwner();
    }

    // ---- Ticking ----

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        if (!SherryRules.isSherry(game.getRole(owner))) {
            clear();
            return;
        }
        // Roles are dealt before the round is RUNNING; keep the opening cooldowns through that window.
        // 职业在回合 RUNNING 之前发放；这段时间保留开局冷却。
        if (!game.isRunning()) {
            return;
        }
        // Only a Wathe death clears; a Last Stand hold and an alive spectator (Taotie-swallowed, Rift occupant) keep
        // the state counting (Medium / Dominator precedent).
        // 只有 Wathe 判定的死亡才清空；背水一战保持期与存活旁观者（被饕餮吞下、裂隙门内）期间状态照常倒数（通灵师、支配者先例）。
        if (!SparkTraitsAbyssListenerBridge.isLastStandPending(owner)
                && !GameFunctions.isPlayerPlayingAndAlive(owner)) {
            clear();
            return;
        }
        boolean changed = false;
        if (deductionCooldownTicks > 0) {
            deductionCooldownTicks--;
        }
        if (mightyWindowTicks > 0) {
            if (--mightyWindowTicks == 0) {
                // The window ran out unused: the cooldown starts now. / 窗口未使用而结束：此刻进入冷却。
                mightyCooldownTicks = Math.max(mightyCooldownTicks, SherryMightyForceService.cooldownTicks());
                changed = true;
            }
        } else if (mightyCooldownTicks > 0) {
            mightyCooldownTicks--;
        }
        changed |= tickTrackers(game);
        if (++incomeTicks >= SherryRules.PASSIVE_INTERVAL_TICKS) {
            incomeTicks = 0;
            SherryEconomyService.payPassiveIncome(owner);
        }
        if (changed) {
            syncTicks = 0;
            syncOwner();
            return;
        }
        if (++syncTicks < SYNC_INTERVAL_TICKS) {
            return;
        }
        syncTicks = 0;
        if (deductionCooldownTicks > 0 || mightyWindowTicks > 0 || mightyCooldownTicks > 0 || !trackers.isEmpty()) {
            syncOwner();
        }
    }

    /**
     * Counts every tracker; returns true when a window opened or a tracker was dropped, so the owner is told at once.
     * A dead target stops being tracked.
     * 推进所有追踪；有窗口开启或追踪被移除时返回 true，以便立即告知本人。目标死亡后不再追踪。
     */
    private boolean tickTrackers(GameWorldComponent game) {
        boolean changed = false;
        for (Iterator<Tracker> iterator = trackers.iterator(); iterator.hasNext(); ) {
            Tracker tracker = iterator.next();
            if (game.isPlayerDead(tracker.target) || !game.hasAnyRole(tracker.target)) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (tracker.revealTicks > 0) {
                tracker.revealTicks--;
            }
            if (--tracker.nextTicks <= 0) {
                tracker.nextTicks = tracker.intervalTicks;
                tracker.revealTicks = SherryRules.REVEAL_TICKS;
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clientTick() {
        if (deductionCooldownTicks > 0) {
            deductionCooldownTicks--;
        }
        if (mightyWindowTicks > 0) {
            mightyWindowTicks--;
        } else if (mightyCooldownTicks > 0) {
            mightyCooldownTicks--;
        }
        // Windows only close locally; the server opens the next one. / 本地只负责让窗口到期；下一个窗口由服务端开启。
        for (Tracker tracker : trackers) {
            if (tracker.revealTicks > 0) {
                tracker.revealTicks--;
            }
        }
    }

    // ---- Persistence ----

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (deductionCooldownTicks > 0) {
            tag.putInt(DEDUCTION_COOLDOWN_TICKS, deductionCooldownTicks);
        }
        if (mightyWindowTicks > 0) {
            tag.putInt(MIGHTY_WINDOW_TICKS, mightyWindowTicks);
        }
        if (mightyCooldownTicks > 0) {
            tag.putInt(MIGHTY_COOLDOWN_TICKS, mightyCooldownTicks);
        }
        if (mightyForfeited) {
            tag.putBoolean(MIGHTY_FORFEITED, true);
        }
        if (!badgeHolders.isEmpty()) {
            NbtList list = new NbtList();
            for (UUID uuid : badgeHolders) {
                list.add(NbtString.of(uuid.toString()));
            }
            tag.put(BADGE_HOLDERS, list);
        }
        if (!trackers.isEmpty()) {
            NbtList list = new NbtList();
            for (Tracker tracker : trackers) {
                NbtCompound entry = new NbtCompound();
                entry.putString(TRACKER_TARGET, tracker.target.toString());
                entry.putInt(TRACKER_INTERVAL, tracker.intervalTicks);
                entry.putInt(TRACKER_NEXT, tracker.nextTicks);
                entry.putInt(TRACKER_REVEAL, tracker.revealTicks);
                list.add(entry);
            }
            tag.put(TRACKERS, list);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        deductionCooldownTicks = readTicks(tag, DEDUCTION_COOLDOWN_TICKS);
        mightyWindowTicks = readTicks(tag, MIGHTY_WINDOW_TICKS);
        mightyCooldownTicks = readTicks(tag, MIGHTY_COOLDOWN_TICKS);
        mightyForfeited = tag.getBoolean(MIGHTY_FORFEITED);
        badgeHolders.clear();
        if (tag.contains(BADGE_HOLDERS, NbtElement.LIST_TYPE)) {
            for (NbtElement element : tag.getList(BADGE_HOLDERS, NbtElement.STRING_TYPE)) {
                try {
                    badgeHolders.add(UUID.fromString(element.asString()));
                } catch (IllegalArgumentException ignored) {
                    // A corrupted entry is dropped, never fatal. / 损坏条目直接丢弃，绝不致命。
                }
            }
        }
        trackers.clear();
        if (tag.contains(TRACKERS, NbtElement.LIST_TYPE)) {
            NbtList list = tag.getList(TRACKERS, NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size() && trackers.size() < SherryRules.MAX_TRACKED; i++) {
                NbtCompound entry = list.getCompound(i);
                try {
                    UUID target = UUID.fromString(entry.getString(TRACKER_TARGET));
                    if (findTracker(target) == null) {
                        trackers.add(new Tracker(target, entry.getInt(TRACKER_INTERVAL), entry.getInt(TRACKER_NEXT),
                                entry.getInt(TRACKER_REVEAL)));
                    }
                } catch (IllegalArgumentException ignored) {
                    // A corrupted entry is dropped, never fatal. / 损坏条目直接丢弃，绝不致命。
                }
            }
        }
    }

    private static int readTicks(NbtCompound tag, String key) {
        return tag.contains(key, NbtElement.NUMBER_TYPE) ? Math.max(0, tag.getInt(key)) : 0;
    }

    // ---- Sync (owner only) ----

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getDeductionCooldownTicks());
        buf.writeVarInt(getMightyWindowTicks());
        buf.writeVarInt(getMightyCooldownTicks());
        buf.writeBoolean(mightyForfeited);
        buf.writeVarInt(badgeHolders.size());
        for (UUID uuid : badgeHolders) {
            buf.writeUuid(uuid);
        }
        buf.writeVarInt(trackers.size());
        for (Tracker tracker : trackers) {
            buf.writeUuid(tracker.target);
            buf.writeVarInt(tracker.intervalTicks);
            buf.writeVarInt(tracker.nextTicks);
            buf.writeVarInt(tracker.revealTicks);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        deductionCooldownTicks = Math.max(0, buf.readVarInt());
        mightyWindowTicks = Math.max(0, buf.readVarInt());
        mightyCooldownTicks = Math.max(0, buf.readVarInt());
        mightyForfeited = buf.readBoolean();
        badgeHolders.clear();
        int holders = Math.max(0, buf.readVarInt());
        for (int i = 0; i < holders; i++) {
            badgeHolders.add(buf.readUuid());
        }
        trackers.clear();
        int count = Math.max(0, buf.readVarInt());
        for (int i = 0; i < count; i++) {
            UUID target = buf.readUuid();
            int interval = buf.readVarInt();
            int next = buf.readVarInt();
            int reveal = buf.readVarInt();
            // Always drain the buffer; keep at most the documented bound. / 始终读完缓冲区；最多保留文档规定的上限。
            if (trackers.size() < SherryRules.MAX_TRACKED) {
                trackers.add(new Tracker(target, interval, next, reveal));
            }
        }
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
