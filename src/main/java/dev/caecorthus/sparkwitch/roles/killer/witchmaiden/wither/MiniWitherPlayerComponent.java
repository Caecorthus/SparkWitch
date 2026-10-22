package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
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
 * Mini Wither state ({@code sparkwitch:mini_wither}, {@code NEVER_COPY}, appended last), kept out of the shared
 * {@code sparkwitch:player} packet whose field order is frozen. Every client receives whether the Wither is out (it is
 * drawn on its owner, owner Q1); the task count and the toggle cooldown go to the owner only. The auto-fire and ambient
 * timers are server-only. Everything clears once the player stops being the Witch Maiden.
 * 小凋灵状态（{@code sparkwitch:mini_wither}，{@code NEVER_COPY}，追加在最后），不放进字段顺序已冻结的共享
 * {@code sparkwitch:player} 同步包。所有客户端都会收到小凋灵是否在场（它绘制在主人身上，所有者 Q1）；任务数与切换冷却只同步给
 * 本人。自动射击与环境音计时只在服务端。玩家不再是巫女时全部清空。
 */
public final class MiniWitherPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<MiniWitherPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("mini_wither"), MiniWitherPlayerComponent.class);

    private static final int SYNC_INTERVAL_TICKS = 20;

    private final PlayerEntity player;
    private int completedTasks;
    private boolean active;
    private int toggleCooldownTicks;
    /** World-space direction of the last shot; the heads keep facing it. / 上一次射击的世界方向；三个头一直朝向它。 */
    private float aimYaw;
    private float aimPitch;
    private int autoFireTicks;
    private int ambientTicks;

    public MiniWitherPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public boolean isUnlocked() {
        return MiniWitherRules.isUnlocked(completedTasks);
    }

    /** Counts one task; true when this task unlocks the Wither. / 记一个任务；本次解锁时返回 true。 */
    public boolean recordCompletedTask() {
        int before = completedTasks;
        completedTasks = Math.min(MiniWitherRules.UNLOCK_TASKS, completedTasks + 1);
        if (completedTasks != before) {
            sync();
        }
        return MiniWitherRules.unlocksOn(before, completedTasks);
    }

    public boolean isActive() {
        return active;
    }

    public int getToggleCooldownTicks() {
        return toggleCooldownTicks;
    }

    /** Only ever lengthens the toggle cooldown, then resyncs. / 只会延长切换冷却，并重新同步。 */
    public void raiseToggleCooldown(int ticks) {
        if (ticks > toggleCooldownTicks) {
            toggleCooldownTicks = ticks;
            sync();
        }
    }

    /** Sets the toggle cooldown exactly (forced-cooldown store). / 精确设定切换冷却（强制冷却存储使用）。 */
    public void setToggleCooldown(int ticks) {
        int normalized = Math.max(0, ticks);
        if (normalized != toggleCooldownTicks) {
            toggleCooldownTicks = normalized;
            sync();
        }
    }

    /**
     * Summons or dismisses the Wither. A summon starts the 5 s auto-fire timer, so the first skull flies 5 s later.
     * 召唤或收回小凋灵。召唤会启动 5 秒自动射击计时，第一枚头颅在 5 秒后射出。
     */
    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        autoFireTicks = active ? MiniWitherRules.AUTO_FIRE_INTERVAL_TICKS : 0;
        ambientTicks = active ? MiniWitherRules.ambientDelay(player.getRandom().nextDouble()) : 0;
        sync();
    }

    public float getAimYaw() {
        return aimYaw;
    }

    public float getAimPitch() {
        return aimPitch;
    }

    /**
     * Turns the heads to a new world-space direction at once (every shot, and the owner's look on a summon).
     * 立即把三个头转向新的世界方向（每次射击时，以及召唤时的主人视线）。
     */
    public void setAim(float yaw, float pitch) {
        if (yaw == aimYaw && pitch == aimPitch) {
            return;
        }
        aimYaw = yaw;
        aimPitch = pitch;
        sync();
    }

    /** Server: counts the auto-fire timer down, never below zero. / 服务端：倒数自动射击计时，不低于 0。 */
    int tickAutoFire() {
        if (autoFireTicks > 0) {
            autoFireTicks--;
        }
        return autoFireTicks;
    }

    void restartAutoFire() {
        autoFireTicks = MiniWitherRules.AUTO_FIRE_INTERVAL_TICKS;
    }

    /** Server: true when an ambient call is due, then schedules the next. / 服务端：到了叫声时间返回 true 并安排下一次。 */
    boolean tickAmbient() {
        if (--ambientTicks > 0) {
            return false;
        }
        ambientTicks = MiniWitherRules.ambientDelay(player.getRandom().nextDouble());
        return true;
    }

    private boolean isEmpty() {
        return completedTasks == 0 && !active && toggleCooldownTicks == 0 && autoFireTicks == 0 && ambientTicks == 0;
    }

    public void clear() {
        if (isEmpty()) {
            return;
        }
        completedTasks = 0;
        active = false;
        toggleCooldownTicks = 0;
        autoFireTicks = 0;
        ambientTicks = 0;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        // Everyone receives the active flag and the heads' aim; the rest is written only for the owner below.
        // 所有人都接收在场标记与三个头的朝向；其余字段在下方只写给本人。
        return true;
    }

    @Override
    public void serverTick() {
        if (isEmpty()) {
            return;
        }
        if (!WitchMaidenRules.isWitchMaiden(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            clear();
            return;
        }
        if (toggleCooldownTicks > 0) {
            toggleCooldownTicks--;
            if (toggleCooldownTicks % SYNC_INTERVAL_TICKS == 0 && player instanceof ServerPlayerEntity owner) {
                // Non-owners only read the active flag and aim, which every write already synced to all; the countdown
                // checkpoint goes to the owner alone. / 非本人只读取在场标记与朝向，而每次写入都已同步给所有人；倒计时校准只发给本人。
                KEY.syncWith(owner, (ComponentProvider) owner);
            }
        }
        if (active && player instanceof ServerPlayerEntity serverPlayer) {
            MiniWitherRuntime.tick(serverPlayer, this);
        }
    }

    @Override
    public void clientTick() {
        if (toggleCooldownTicks > 0) {
            toggleCooldownTicks--;
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean owner = recipient == player;
        buf.writeBoolean(active);
        buf.writeFloat(aimYaw);
        buf.writeFloat(aimPitch);
        buf.writeVarInt(owner ? completedTasks : 0);
        buf.writeVarInt(owner ? toggleCooldownTicks : 0);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        active = buf.readBoolean();
        aimYaw = buf.readFloat();
        aimPitch = buf.readFloat();
        completedTasks = Math.max(0, buf.readVarInt());
        toggleCooldownTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        tag.putInt("CompletedTasks", completedTasks);
        tag.putBoolean("Active", active);
        tag.putInt("ToggleCooldown", toggleCooldownTicks);
        tag.putFloat("AimYaw", aimYaw);
        tag.putFloat("AimPitch", aimPitch);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        completedTasks = Math.max(0, tag.getInt("CompletedTasks"));
        active = tag.getBoolean("Active");
        toggleCooldownTicks = Math.max(0, tag.getInt("ToggleCooldown"));
        aimYaw = tag.getFloat("AimYaw");
        aimPitch = tag.getFloat("AimPitch");
        autoFireTicks = active ? MiniWitherRules.AUTO_FIRE_INTERVAL_TICKS : 0;
        ambientTicks = active ? MiniWitherRules.AMBIENT_MIN_INTERVAL_TICKS : 0;
    }
}
