package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Owner-private Specter state: the skill-one mark/cooldown and the sanity-outline colour map.
 * <ul>
 *   <li>Mark: crosshair target within 3 blocks, 10 s, no extra cooldown when it simply expires.</li>
 *   <li>Cooldown: 45 s at role assignment and 45 s after a shriek; marking itself never starts it.</li>
 *   <li>Vision: mood is only synced to its owner in Wathe, so the server resolves the sanity bands and syncs the
 *       resulting outline colours to the Specter only.</li>
 * </ul>
 * 仅幽魂本人可见的状态：技能一的标记/冷却，以及理智描边颜色映射。标记 3 格内准星目标、持续 10 秒，自然过期不进冷却；
 * 开局与尖啸后各 45 秒冷却，标记本身不触发冷却。Wathe 的理智只同步给本人，因此由服务端判定区间并只把描边颜色同步给幽魂。
 */
public final class SpecterPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SpecterPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SpecterRules.ROLE_ID,
            SpecterPlayerComponent.class
    );
    private static final int REFRESH_INTERVAL_TICKS = 5;
    private static final String MARKED_TARGET = "MarkedTarget";
    private static final String MARK_REMAINING_TICKS = "MarkRemainingTicks";
    private static final String COOLDOWN_TICKS = "CooldownTicks";

    private final PlayerEntity player;
    private Map<UUID, Integer> highlightColors = Map.of();
    private int refreshTicks;
    private @Nullable UUID markedTarget;
    private int markRemainingTicks;
    private int cooldownTicks;

    public SpecterPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public @Nullable Integer highlightColor(UUID target) {
        return highlightColors.get(target);
    }

    public boolean hasActiveMark() {
        return markedTarget != null && markRemainingTicks > 0;
    }

    public @Nullable UUID getMarkedTarget() {
        return hasActiveMark() ? markedTarget : null;
    }

    public boolean isMarked(UUID target) {
        return hasActiveMark() && markedTarget.equals(target);
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

    /** Server only: resets the skill state for the assigned role and syncs. / 仅服务端：按分配的职业重置技能状态并同步。 */
    public void assignForRole(@Nullable Role role) {
        boolean specter = SpecterRules.isSpecter(role);
        int nextCooldown = specter ? SpecterRules.INITIAL_COOLDOWN_TICKS : 0;
        if (cooldownTicks == nextCooldown && markedTarget == null && markRemainingTicks == 0) {
            return;
        }
        cooldownTicks = nextCooldown;
        markedTarget = null;
        markRemainingTicks = 0;
        syncOwner();
    }

    /** Server only: marks one player for 10 s without starting the cooldown. / 仅服务端：标记一名玩家 10 秒，不触发冷却。 */
    public void mark(UUID target) {
        markedTarget = target;
        markRemainingTicks = SpecterRules.MARK_DURATION_TICKS;
        syncOwner();
    }

    /** Expired/invalid mark: clear it and leave the skill immediately ready. / 标记过期/失效：清除标记，技能立即回到可用状态。 */
    public void clearMark() {
        if (markedTarget == null && markRemainingTicks == 0) {
            return;
        }
        markedTarget = null;
        markRemainingTicks = 0;
        syncOwner();
    }

    /** Server only: the shriek consumed the mark and starts the 60 s cooldown. / 仅服务端：尖啸消耗标记并进入 60 秒冷却。 */
    public void consumeMarkForShriek() {
        markedTarget = null;
        markRemainingTicks = 0;
        cooldownTicks = SpecterRules.SHRIEK_COOLDOWN_TICKS;
        syncOwner();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayerEntity owner)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(owner.getWorld());
        boolean active = SpecterRules.isSpecter(game.getRole(owner))
                && GameFunctions.isPlayerPlayingAndAlive(owner)
                && !GameFunctions.isPlayerSpectatingOrCreative(owner);
        if (!active) {
            clearMark();
            clearHighlights();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            // Once per second is enough for future HUD use; the client also counts down locally.
            // 每秒同步一次即可供后续 HUD 使用；客户端也会本地倒计时。
            if (cooldownTicks % 20 == 0) {
                syncOwner();
            }
        }

        if (markedTarget != null) {
            ServerPlayerEntity target = owner.getServer() == null
                    ? null
                    : owner.getServer().getPlayerManager().getPlayer(markedTarget);
            if (target == null
                    || target.isSpectator()
                    || !GameFunctions.isPlayerPlayingAndAlive(target)) {
                clearMark();
            } else if (markRemainingTicks > 0) {
                markRemainingTicks--;
                if (markRemainingTicks <= 0) {
                    clearMark();
                }
            }
        }

        if (++refreshTicks < REFRESH_INTERVAL_TICKS) {
            return;
        }
        refreshTicks = 0;
        refreshHighlights(owner, game);
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (markedTarget == null) {
            return;
        }
        if (markRemainingTicks > 0) {
            markRemainingTicks--;
        }
        if (markRemainingTicks <= 0) {
            markedTarget = null;
            markRemainingTicks = 0;
        }
    }

    private void refreshHighlights(ServerPlayerEntity owner, GameWorldComponent game) {
        Map<UUID, Integer> next = new HashMap<>();
        for (ServerPlayerEntity target : owner.getServerWorld().getPlayers()) {
            if (target == owner
                    || target.isSpectator()
                    || GameFunctions.isPlayerSpectatingOrCreative(target)
                    || !GameFunctions.isPlayerPlayingAndAlive(target)) {
                continue;
            }
            Role role = game.getRole(target);
            // Only real mood bars carry a sanity value; FAKE/NONE roles stay at full mood and never highlight.
            // 只有真实理智条带有理智值；FAKE/NONE 身份始终满理智，永不参与高亮。
            if (role == null || role.getMoodType() != Role.MoodType.REAL) {
                continue;
            }
            float sanity = MathHelper.clamp(PlayerMoodComponent.KEY.get(target).getMood() * 100.0F, 0.0F, 100.0F);
            Integer color = SpecterRules.sanityHighlightColor(sanity);
            if (color != null) {
                next.put(target.getUuid(), color);
            }
        }
        if (!highlightColors.equals(next)) {
            highlightColors = Map.copyOf(next);
            syncOwner();
        }
    }

    private void clearHighlights() {
        refreshTicks = 0;
        if (highlightColors.isEmpty()) {
            return;
        }
        highlightColors = Map.of();
        syncOwner();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
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
        buf.writeVarInt(highlightColors.size());
        for (Map.Entry<UUID, Integer> entry : highlightColors.entrySet()) {
            buf.writeUuid(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
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
        int size = buf.readVarInt();
        Map<UUID, Integer> next = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            next.put(buf.readUuid(), buf.readVarInt());
        }
        highlightColors = Map.copyOf(next);
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

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
