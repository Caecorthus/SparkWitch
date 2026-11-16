package dev.caecorthus.sparkwitch.roles.civilian.apprentice;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Apprentice-owned per-player state ({@code sparkwitch:apprentice_player}, {@code NEVER_COPY}) for the 2026-10-06
 * buff, kept out of the shared {@code sparkwitch:player} packet whose field order is frozen. Every field is
 * owner-private except the Clairvoyance exposure timer, which every client needs to draw the exposure outline (D9).
 * The Fear ward lives here because the Healing aura grants it to any innocent player (D8). Apprentice-only fields
 * (tasks, Purify cooldown, Swift Step recharge, Mighty Force forfeit) clear themselves once the player stops being the
 * Apprentice.
 * 预备魔女 2026-10-06 增强的每玩家状态（{@code sparkwitch:apprentice_player}，{@code NEVER_COPY}），不放进字段顺序已冻结的
 * 共享 {@code sparkwitch:player} 同步包。除千里眼暴露计时（所有客户端都需要它来绘制暴露描边，D9）外，其余字段只同步给本人。
 * 恐惧庇护也存放在这里，因为疗愈光环会把它给予任意好人（D8）。仅属于预备魔女的字段（任务数、净化冷却、滑步充能、巨力失效）
 * 在玩家不再是预备魔女时自行清空。
 * <p>Sync scope: a write to an owner-private field goes to the owner alone ({@link #syncPrivate()}); only the exposure
 * timer's own changes are broadcast ({@link #sync()}). A broadcast packet tells every tracking client that something
 * changed on this player even though it carries zeros for them, so a private write must not produce one: it would
 * mark the Apprentice (tasks, Purify, Swift Step, Mighty Force) and every innocent her Healing aura wards.
 * <p>同步范围：本人私有字段的写入只发给本人（{@link #syncPrivate()}）；只有暴露计时自身的变化才广播（{@link #sync()}）。
 * 广播包即使对他人只携带零值，也会让所有追踪客户端得知该玩家身上发生了变化，因此私有写入不得产生广播：否则会暴露预备魔女
 * （任务、净化、滑步、巨力）以及被其疗愈光环庇护的每一名好人。
 */
public final class ApprenticePlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<ApprenticePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("apprentice_player"), ApprenticePlayerComponent.class);

    private static final int SYNC_INTERVAL_TICKS = 20;

    private final PlayerEntity player;
    private int completedTasks;
    private int purifyCooldownTicks;
    private int swiftStepRechargeA;
    private int swiftStepRechargeB;
    private boolean mightyForceForfeited;
    private int fearWardTicks;
    private int exposureTicks;

    public ApprenticePlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public boolean isGraduated() {
        return ApprenticeRules.isGraduated(completedTasks);
    }

    /** Counts one task; returns true when this task graduates her. / 记一个任务；本次出师时返回 true。 */
    public boolean recordCompletedTask() {
        int before = completedTasks;
        completedTasks = Math.min(ApprenticeRules.GRADUATION_TASKS, completedTasks + 1);
        if (completedTasks != before) {
            syncPrivate();
        }
        return ApprenticeRules.graduatesOn(before, completedTasks);
    }

    public int getPurifyCooldownTicks() {
        return purifyCooldownTicks;
    }

    /** Only ever lengthens the Purify cooldown, then resyncs. / 只会延长净化冷却，并重新同步。 */
    public void raisePurifyCooldown(int ticks) {
        if (ticks > purifyCooldownTicks) {
            purifyCooldownTicks = ticks;
            syncPrivate();
        }
    }

    public int getSwiftStepCharges() {
        return ApprenticeRules.swiftStepCharges(swiftStepRechargeA, swiftStepRechargeB);
    }

    public int getTicksUntilSwiftStepCharge() {
        return ApprenticeRules.ticksUntilSwiftStepCharge(swiftStepRechargeA, swiftStepRechargeB);
    }

    /** Spends one ready charge and starts its own recharge timer. / 消耗一层可用充能并启动其独立恢复计时。 */
    public boolean consumeSwiftStepCharge(int rechargeTicks) {
        if (swiftStepRechargeA <= 0) {
            swiftStepRechargeA = Math.max(1, rechargeTicks);
        } else if (swiftStepRechargeB <= 0) {
            swiftStepRechargeB = Math.max(1, rechargeTicks);
        } else {
            return false;
        }
        syncPrivate();
        return true;
    }

    public boolean isMightyForceForfeited() {
        return mightyForceForfeited;
    }

    public void forfeitMightyForce() {
        if (!mightyForceForfeited) {
            mightyForceForfeited = true;
            syncPrivate();
        }
    }

    public boolean hasFearWard() {
        return fearWardTicks > 0;
    }

    /** Refreshed by every Healing pulse; syncs only when the ward starts. / 每次疗愈脉冲刷新；只在庇护开始时同步。 */
    public void refreshFearWard(int ticks) {
        boolean wasActive = fearWardTicks > 0;
        fearWardTicks = Math.max(fearWardTicks, ticks);
        if (!wasActive && fearWardTicks > 0) {
            syncPrivate();
        }
    }

    public int getExposureTicks() {
        return exposureTicks;
    }

    public void beginExposure(int ticks) {
        exposureTicks = Math.max(0, ticks);
        sync();
    }

    private boolean hasApprenticeOnlyState() {
        return completedTasks > 0 || purifyCooldownTicks > 0 || swiftStepRechargeA > 0 || swiftStepRechargeB > 0
                || mightyForceForfeited;
    }

    private boolean isEmpty() {
        return !hasApprenticeOnlyState() && fearWardTicks == 0 && exposureTicks == 0;
    }

    private void clearApprenticeOnlyState() {
        completedTasks = 0;
        purifyCooldownTicks = 0;
        swiftStepRechargeA = 0;
        swiftStepRechargeB = 0;
        mightyForceForfeited = false;
    }

    public void clear() {
        if (isEmpty()) {
            return;
        }
        // Non-owners hold only the exposure timer, so they are told only when one was running.
        // 非本人只持有暴露计时，因此只有在暴露进行中时才需要通知他们。
        boolean wasExposed = exposureTicks > 0;
        clearApprenticeOnlyState();
        fearWardTicks = 0;
        exposureTicks = 0;
        if (wasExposed) {
            sync();
        } else {
            syncPrivate();
        }
    }

    /** Broadcast: for changes of the public exposure timer. / 广播：用于公开的暴露计时发生变化时。 */
    public void sync() {
        KEY.sync(player);
    }

    /**
     * Sync after a write that changed only owner-private fields: to the owner alone. While an exposure runs it still
     * goes to everyone, as the countdown checkpoints always did (their copy of the timer is refreshed and the player
     * is publicly exposed anyway). The network-handler check mirrors CCA's own recipient rule for the owner.
     * 只改变本人私有字段的写入之后的同步：只发给本人。暴露进行中时仍发给所有人，与倒计时校准一贯的做法相同（刷新他们的计时
     * 副本，且该玩家此时本就公开暴露）。对网络处理器的判断与 CCA 自身为本人选择接收者的规则一致。
     */
    private void syncPrivate() {
        if (exposureTicks > 0) {
            sync();
        } else if (player instanceof ServerPlayerEntity owner && owner.networkHandler != null) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone receives the exposure timer; the rest is written only for the owner below.
        // 所有人都接收暴露计时；其余字段在下方只写给本人。
        return true;
    }

    @Override
    public void serverTick() {
        if (isEmpty()) {
            return;
        }
        // sync: the public exposure timer changed (broadcast). ownerOnly: only owner-private fields changed.
        // sync：公开的暴露计时发生变化（广播）。ownerOnly：只有本人私有字段发生变化。
        boolean sync = false;
        boolean ownerOnly = false;
        if (hasApprenticeOnlyState()
                && GameWorldComponent.KEY.get(player.getWorld()).getRole(player) != SparkWitchRoles.apprenticeWitch()) {
            clearApprenticeOnlyState();
            ownerOnly = true;
        }
        if (purifyCooldownTicks > 0) {
            purifyCooldownTicks--;
            ownerOnly |= purifyCooldownTicks % SYNC_INTERVAL_TICKS == 0;
        }
        if (swiftStepRechargeA > 0) {
            swiftStepRechargeA--;
            ownerOnly |= swiftStepRechargeA % SYNC_INTERVAL_TICKS == 0;
        }
        if (swiftStepRechargeB > 0) {
            swiftStepRechargeB--;
            ownerOnly |= swiftStepRechargeB % SYNC_INTERVAL_TICKS == 0;
        }
        if (fearWardTicks > 0) {
            fearWardTicks--;
            ownerOnly |= fearWardTicks == 0;
        }
        if (exposureTicks > 0) {
            exposureTicks--;
            sync |= exposureTicks == 0;
        }
        // A countdown checkpoint, the ward's end and the role-loss clear change only owner-private fields; non-owners get
        // the exposure timer, so these still go to everyone while an exposure runs (their copy of the timer is refreshed
        // as before) and to the owner alone otherwise.
        // 倒计时校准、庇护结束与失去职业时的清空都只改变本人私有字段；非本人会收到暴露计时，因此暴露进行中仍发给所有人
        // （照旧刷新他们的计时副本），否则只发给本人。
        if (sync) {
            sync();
        } else if (ownerOnly) {
            syncPrivate();
        }
    }

    @Override
    public void clientTick() {
        if (purifyCooldownTicks > 0) {
            purifyCooldownTicks--;
        }
        if (swiftStepRechargeA > 0) {
            swiftStepRechargeA--;
        }
        if (swiftStepRechargeB > 0) {
            swiftStepRechargeB--;
        }
        // The fear ward is not counted down here: Healing pulses refresh it on the server without a sync, and the
        // server syncs both its start and its end. / 恐惧庇护不在此倒计时：疗愈脉冲在服务端刷新它且不同步，服务端会同步
        // 其开始与结束。
        if (exposureTicks > 0) {
            exposureTicks--;
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean owner = recipient == player;
        buf.writeVarInt(exposureTicks);
        buf.writeVarInt(owner ? completedTasks : 0);
        buf.writeVarInt(owner ? purifyCooldownTicks : 0);
        buf.writeVarInt(owner ? swiftStepRechargeA : 0);
        buf.writeVarInt(owner ? swiftStepRechargeB : 0);
        buf.writeBoolean(owner && mightyForceForfeited);
        buf.writeVarInt(owner ? fearWardTicks : 0);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        exposureTicks = Math.max(0, buf.readVarInt());
        completedTasks = Math.max(0, buf.readVarInt());
        purifyCooldownTicks = Math.max(0, buf.readVarInt());
        swiftStepRechargeA = Math.max(0, buf.readVarInt());
        swiftStepRechargeB = Math.max(0, buf.readVarInt());
        mightyForceForfeited = buf.readBoolean();
        fearWardTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt("CompletedTasks", completedTasks);
        tag.putInt("PurifyCooldown", purifyCooldownTicks);
        tag.putInt("SwiftStepRechargeA", swiftStepRechargeA);
        tag.putInt("SwiftStepRechargeB", swiftStepRechargeB);
        tag.putBoolean("MightyForceForfeited", mightyForceForfeited);
        tag.putInt("FearWard", fearWardTicks);
        tag.putInt("Exposure", exposureTicks);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        completedTasks = Math.max(0, tag.getInt("CompletedTasks"));
        purifyCooldownTicks = Math.max(0, tag.getInt("PurifyCooldown"));
        swiftStepRechargeA = Math.max(0, tag.getInt("SwiftStepRechargeA"));
        swiftStepRechargeB = Math.max(0, tag.getInt("SwiftStepRechargeB"));
        mightyForceForfeited = tag.getBoolean("MightyForceForfeited");
        fearWardTicks = Math.max(0, tag.getInt("FearWard"));
        exposureTicks = Math.max(0, tag.getInt("Exposure"));
    }
}
