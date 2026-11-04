package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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

/**
 * Owner-only Dominator state: remaining skill uses (2 at role assignment) and the 30 s mark target.
 * Marking itself consumes no use. Synced only to the owner, whose HUD and outline read it.
 * 支配者仅本人可见的状态：剩余技能次数（分配职业时为 2）和 30 秒标记目标。标记本身不消耗次数。
 * 只同步给本人，本人的 HUD 与描边读取它。
 */
public final class DominatorPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<DominatorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            DominatorRules.ROLE_ID,
            DominatorPlayerComponent.class
    );
    private static final String REMAINING_USES = "RemainingUses";
    private static final String MARKED_TARGET = "MarkedTarget";
    private static final String MARK_REMAINING_TICKS = "MarkRemainingTicks";
    private static final String COOLDOWN_TICKS = "CooldownTicks";

    private final PlayerEntity player;
    private int remainingUses;
    private @Nullable UUID markedTarget;
    private int markRemainingTicks;
    private int cooldownTicks;

    public DominatorPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getRemainingUses() {
        return remainingUses;
    }

    public boolean hasActiveMark() {
        return markedTarget != null && markRemainingTicks > 0;
    }

    public boolean isMarked(UUID target) {
        return hasActiveMark() && markedTarget.equals(target);
    }

    public @Nullable UUID getMarkedTarget() {
        return hasActiveMark() ? markedTarget : null;
    }

    public int getMarkRemainingTicks() {
        return Math.max(0, markRemainingTicks);
    }

    public int getCooldownTicks() {
        return Math.max(0, cooldownTicks);
    }

    public boolean isOnCooldown() {
        return cooldownTicks > 0;
    }

    /** Server only: resets to the role's initial state and syncs. / 仅服务端：重置为该职业初始状态并同步。 */
    public void assignForRole(@Nullable Role role) {
        boolean dominator = DominatorRules.isDominator(role);
        int nextUses = dominator ? DominatorRules.INITIAL_USES : 0;
        int nextCooldown = dominator ? DominatorRules.INITIAL_COOLDOWN_TICKS : 0;
        if (remainingUses != nextUses
                || markedTarget != null
                || markRemainingTicks != 0
                || cooldownTicks != nextCooldown) {
            remainingUses = nextUses;
            markedTarget = null;
            markRemainingTicks = 0;
            cooldownTicks = nextCooldown;
            sync();
        }
    }

    /** Server only: marks the target for 30 s. Does not consume a use. / 仅服务端：标记目标 30 秒，不消耗次数。 */
    public void mark(UUID target) {
        markedTarget = target;
        markRemainingTicks = DominatorRules.MARK_DURATION_TICKS;
        sync();
    }

    /** Server only: clears every field and syncs. / 仅服务端：清空全部字段并同步。 */
    public void clear() {
        if (remainingUses == 0 && markedTarget == null && markRemainingTicks == 0 && cooldownTicks == 0) {
            return;
        }
        remainingUses = 0;
        markedTarget = null;
        markRemainingTicks = 0;
        cooldownTicks = 0;
        sync();
    }

    /**
     * Server only: clears the mark, starts the G cooldown and syncs, leaving the remaining uses untouched.
     * 仅服务端：清除标记、开始 G 冷却并同步，不动剩余次数。
     */
    public void clearMark() {
        if (markedTarget == null && markRemainingTicks == 0) {
            return;
        }
        markedTarget = null;
        markRemainingTicks = 0;
        cooldownTicks = DominatorRules.MARK_END_COOLDOWN_TICKS;
        syncOwner();
    }

    /** Server only: one more use, syncing once. / 仅服务端：可用次数 +1 并同步一次。 */
    public void addUse() {
        if (remainingUses >= DominatorRules.MAX_USES) {
            return;
        }
        remainingUses = Math.min(DominatorRules.MAX_USES, remainingUses + 1);
        sync();
    }

    /** Server only: consumes one use, clears the mark, starts the G cooldown and syncs once.
     *  仅服务端：消耗一次次数、清除标记、开始 G 冷却并只同步一次。 */
    public void releaseMark() {
        if (remainingUses > 0) {
            remainingUses--;
        }
        markedTarget = null;
        markRemainingTicks = 0;
        cooldownTicks = DominatorRules.MARK_END_COOLDOWN_TICKS;
        sync();
    }

    @Override
    public void serverTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            // Once per second keeps the HUD accurate without syncing every tick; the client counts down locally too.
            // 每秒同步一次即可让 HUD 准确；客户端也会本地倒计时。
            if (cooldownTicks % 20 == 0) {
                syncOwner();
            }
        }
        if (markedTarget == null) {
            markRemainingTicks = 0;
            return;
        }
        if (player instanceof ServerPlayerEntity owner) {
            ServerPlayerEntity target = owner.getServer() == null
                    ? null
                    : owner.getServer().getPlayerManager().getPlayer(markedTarget);
            if (target == null
                    || target.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(target)) {
                clearMark();
                return;
            }
        }
        if (markRemainingTicks <= 0) {
            clearMark();
            return;
        }
        markRemainingTicks--;
        if (markRemainingTicks <= 0) {
            clearMark();
        } else if (markRemainingTicks % 20 == 0) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (markRemainingTicks <= 0) {
            return;
        }
        markRemainingTicks--;
        if (markRemainingTicks <= 0) {
            markedTarget = null;
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (remainingUses > 0) {
            tag.putInt(REMAINING_USES, remainingUses);
        }
        if (markedTarget != null) {
            tag.putUuid(MARKED_TARGET, markedTarget);
        }
        if (markRemainingTicks > 0) {
            tag.putInt(MARK_REMAINING_TICKS, markRemainingTicks);
        }
        if (cooldownTicks > 0) {
            tag.putInt(COOLDOWN_TICKS, cooldownTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        remainingUses = tag.contains(REMAINING_USES, NbtElement.NUMBER_TYPE)
                ? Math.min(DominatorRules.MAX_USES, Math.max(0, tag.getInt(REMAINING_USES)))
                : 0;
        markedTarget = tag.containsUuid(MARKED_TARGET) ? tag.getUuid(MARKED_TARGET) : null;
        markRemainingTicks = tag.contains(MARK_REMAINING_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(MARK_REMAINING_TICKS))
                : 0;
        cooldownTicks = tag.contains(COOLDOWN_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(COOLDOWN_TICKS))
                : 0;
        if (markedTarget == null || markRemainingTicks <= 0) {
            markedTarget = null;
            markRemainingTicks = 0;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(remainingUses);
        buf.writeVarInt(getCooldownTicks());
        boolean marked = hasActiveMark();
        buf.writeBoolean(marked);
        if (marked) {
            buf.writeUuid(markedTarget);
            buf.writeVarInt(markRemainingTicks);
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        remainingUses = Math.min(DominatorRules.MAX_USES, Math.max(0, buf.readVarInt()));
        cooldownTicks = Math.max(0, buf.readVarInt());
        if (buf.readBoolean()) {
            markedTarget = buf.readUuid();
            markRemainingTicks = Math.max(0, buf.readVarInt());
            if (markRemainingTicks <= 0) {
                markedTarget = null;
            }
        } else {
            markedTarget = null;
            markRemainingTicks = 0;
        }
    }

    private void sync() {
        if (player instanceof ServerPlayerEntity) {
            KEY.sync(player);
        }
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
