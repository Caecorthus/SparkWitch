package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Owner-private 死亡回归 (Return by Death) state of 二阶堂希罗, component {@code sparkwitch:nikaido_hiro_return}:
 * <ul>
 *   <li>the skill cooldown as counting-down ticks (Specter pattern: the server counts and syncs once per second, the
 *       client counts locally in between); it is the SparkFactionAPI forced-cooldown store
 *       {@code sparkwitch:nikaido_hiro_return};</li>
 *   <li>the window as an absolute world-time end tick ({@code world.getTime()}), 0 when closed;</li>
 *   <li>the record point taken when the window opened, null when there is none.</li>
 * </ul>
 * Synced to its owner only. The component only stores state: opening and settling the window, the mana spend, the
 * victims and the teleport belong to {@link HiroReturnService}.
 * 二阶堂希罗仅本人可见的「死亡回归」状态，组件 {@code sparkwitch:nikaido_hiro_return}：技能冷却为倒计时刻数（幽魂模式：服务端
 * 计数并每秒同步一次，客户端在两次同步之间本地倒计时），也是 SparkFactionAPI 强制冷却存储
 * {@code sparkwitch:nikaido_hiro_return}；窗口以世界时间（{@code world.getTime()}）的绝对结束刻保存，关闭时为 0；记录点在窗口
 * 开启时写入，没有时为 null。只同步给本人。组件只保存状态：窗口的开启与结算、魔力消耗、受害者与传送都属于
 * {@link HiroReturnService}。
 */
public final class HiroReturnComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<HiroReturnComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("nikaido_hiro_return"),
            HiroReturnComponent.class
    );
    private static final String COOLDOWN_TICKS = "CooldownTicks";
    private static final String WINDOW_END_TICK = "WindowEndTick";
    private static final String RECORD_POINT = "RecordPoint";

    private final PlayerEntity player;
    private int cooldownTicks;
    private long windowEndTick;
    private @Nullable RecordPoint recordPoint;

    public HiroReturnComponent(PlayerEntity player) {
        this.player = player;
    }

    /**
     * Where 死亡回归 was cast: position, facing and the dimension id ({@code world.getRegistryKey().getValue()}).
     * 死亡回归的施放位置：坐标、朝向与维度 id（{@code world.getRegistryKey().getValue()}）。
     */
    public record RecordPoint(double x, double y, double z, float yaw, float pitch, Identifier dimension) {
        /** The player's current position, facing and dimension. / 玩家当前的位置、朝向与维度。 */
        public static RecordPoint of(PlayerEntity player) {
            return new RecordPoint(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(),
                    player.getWorld().getRegistryKey().getValue());
        }
    }

    public int getCooldownTicks() {
        return Math.max(0, cooldownTicks);
    }

    public boolean isOnCooldown() {
        return cooldownTicks > 0;
    }

    /** Absolute world-time tick at which the window ends; 0 when closed. / 窗口结束的世界时间绝对刻；关闭时为 0。 */
    public long windowEndTick() {
        return windowEndTick;
    }

    /** {@code now} is {@code world.getTime()} of the owner's world. / {@code now} 为本人所在世界的 {@code world.getTime()}。 */
    public boolean isWindowActive(long now) {
        return windowEndTick > now;
    }

    public int windowRemainingTicks(long now) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, windowEndTick - now));
    }

    public @Nullable RecordPoint recordPoint() {
        return recordPoint;
    }

    /**
     * Server only: resets the skill for the assigned role and syncs. Hiro starts with the opening cooldown, everyone
     * else with nothing; a window and record point never survive a role assignment.
     * 仅服务端：按分配的职业重置技能并同步。希罗从开局冷却开始，其他职业清零；窗口与记录点不会跨越职业分配保留。
     */
    public void assignForRole(@Nullable Role role) {
        int nextCooldown = HiroRules.isHiro(role) ? HiroRules.OPENING_COOLDOWN_TICKS : 0;
        if (cooldownTicks == nextCooldown && windowEndTick == 0L && recordPoint == null) {
            return;
        }
        cooldownTicks = nextCooldown;
        windowEndTick = 0L;
        recordPoint = null;
        syncOwner();
    }

    /**
     * Server only: opens the window until {@code endTick} (absolute world time) with its record point and syncs.
     * 仅服务端：开启窗口至 {@code endTick}（世界时间绝对刻）并写入记录点，然后同步。
     */
    public void openWindow(long endTick, RecordPoint point) {
        windowEndTick = Math.max(0L, endTick);
        recordPoint = point;
        syncOwner();
    }

    /**
     * Server only: closes the window, drops the record point and starts {@code cooldownTicks} (the skill's cooldown
     * counts from the moment it ends). Syncs.
     * 仅服务端：关闭窗口、清除记录点并进入 {@code cooldownTicks} 冷却（冷却从技能结束时开始计算）。会同步。
     */
    public void closeWindow(int cooldownTicks) {
        windowEndTick = 0L;
        recordPoint = null;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        syncOwner();
    }

    /**
     * Server only, the SparkFactionAPI forced-cooldown write path (store {@code sparkwitch:nikaido_hiro_return}): sets
     * the cooldown exactly (floored at 0) and syncs the owner. The store's gate already excludes a running window.
     * 仅服务端，SparkFactionAPI 强制冷却的写入路径（存储 {@code sparkwitch:nikaido_hiro_return}）：精确设置冷却（下限为 0）并同步
     * 给本人。存储门控已排除进行中的窗口。
     */
    public void setCooldownTicks(int ticks) {
        int next = Math.max(0, ticks);
        if (cooldownTicks == next) {
            return;
        }
        cooldownTicks = next;
        syncOwner();
    }

    /** Server only: drops cooldown, window and record point (round reset). / 仅服务端：清除冷却、窗口与记录点（回合重置）。 */
    public void clear() {
        if (cooldownTicks == 0 && windowEndTick == 0L && recordPoint == null) {
            return;
        }
        cooldownTicks = 0;
        windowEndTick = 0L;
        recordPoint = null;
        syncOwner();
    }

    @Override
    public void serverTick() {
        if (cooldownTicks <= 0) {
            return;
        }
        cooldownTicks--;
        // Once per second is enough for the HUD; the client also counts down locally.
        // 每秒同步一次即可满足 HUD；客户端也会本地倒计时。
        if (cooldownTicks % 20 == 0) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (cooldownTicks > 0) {
            tag.putInt(COOLDOWN_TICKS, cooldownTicks);
        }
        if (windowEndTick > 0L) {
            tag.putLong(WINDOW_END_TICK, windowEndTick);
        }
        if (recordPoint != null) {
            NbtCompound point = new NbtCompound();
            point.putDouble("X", recordPoint.x());
            point.putDouble("Y", recordPoint.y());
            point.putDouble("Z", recordPoint.z());
            point.putFloat("Yaw", recordPoint.yaw());
            point.putFloat("Pitch", recordPoint.pitch());
            point.putString("Dimension", recordPoint.dimension().toString());
            tag.put(RECORD_POINT, point);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cooldownTicks = tag.contains(COOLDOWN_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(COOLDOWN_TICKS))
                : 0;
        windowEndTick = tag.contains(WINDOW_END_TICK, NbtElement.NUMBER_TYPE)
                ? Math.max(0L, tag.getLong(WINDOW_END_TICK))
                : 0L;
        recordPoint = null;
        if (tag.contains(RECORD_POINT, NbtElement.COMPOUND_TYPE)) {
            NbtCompound point = tag.getCompound(RECORD_POINT);
            Identifier dimension = Identifier.tryParse(point.getString("Dimension"));
            if (dimension != null) {
                recordPoint = new RecordPoint(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z"),
                        point.getFloat("Yaw"), point.getFloat("Pitch"), dimension);
            }
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getCooldownTicks());
        buf.writeVarLong(Math.max(0L, windowEndTick));
        buf.writeBoolean(recordPoint != null);
        if (recordPoint != null) {
            buf.writeDouble(recordPoint.x());
            buf.writeDouble(recordPoint.y());
            buf.writeDouble(recordPoint.z());
            buf.writeFloat(recordPoint.yaw());
            buf.writeFloat(recordPoint.pitch());
            buf.writeIdentifier(recordPoint.dimension());
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownTicks = Math.max(0, buf.readVarInt());
        windowEndTick = Math.max(0L, buf.readVarLong());
        recordPoint = buf.readBoolean()
                ? new RecordPoint(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                        buf.readFloat(), buf.readIdentifier())
                : null;
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
