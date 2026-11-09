package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Per-player Specter fear levels (not the Grand Witch's cast lock).
 * <ul>
 *   <li>Level 1/2 drain 5/10 sanity every 10 s, level 3 drains 20 every 5 s; sanity never drops below 5, and reaching
 *       that floor clears every fear level.</li>
 *   <li>One level decays every 60 s; gaining a level refreshes that timer and restarts the drain interval.</li>
 *   <li>Only players with a real mood bar can gain levels (filtered by the shriek service).</li>
 *   <li>The level is synced to its owner for a future HUD; nothing is displayed yet.</li>
 * </ul>
 * 幽魂恐惧层数（与女巫的施法封锁无关）。1/2 层每 10 秒扣 5/10 san，3 层每 5 秒扣 20 san，最低扣到 5；
 * 理智到 5 就清空全部恐惧层数；每 60 秒掉 1 层，加层会重置掉级计时与扣 san 间隔；仅真实理智条玩家可获得层数；
 * 层数只同步给本人，暂不显示。
 */
public final class SpecterFearComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<SpecterFearComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("specter_fear"),
            SpecterFearComponent.class
    );
    private static final String LEVEL = "FearLevel";
    private static final String DECAY_TICKS = "FearDecayTicks";
    private static final String DRAIN_TICKS = "FearDrainTicks";

    private final PlayerEntity player;
    private int level;
    private int decayTicks;
    private int drainTicks;

    public SpecterFearComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getLevel() {
        return level;
    }

    /** Server only: adds levels (capped), refreshing both timers when the level actually rises. / 仅服务端：加层（封顶），实际升层时重置两个计时。 */
    public void addLevel(int amount) {
        if (amount <= 0 || level >= SpecterRules.FEAR_MAX_LEVEL) {
            return;
        }
        level = Math.min(SpecterRules.FEAR_MAX_LEVEL, level + amount);
        decayTicks = SpecterRules.FEAR_DECAY_TICKS;
        drainTicks = SpecterRules.fearDrainIntervalTicks(level);
        syncOwner();
    }

    /** Clears fear on death, reset or round end. / 死亡、重置或回合结束时清空恐惧。 */
    public void clear() {
        if (level == 0 && decayTicks == 0 && drainTicks == 0) {
            return;
        }
        level = 0;
        decayTicks = 0;
        drainTicks = 0;
        syncOwner();
    }

    @Override
    public void serverTick() {
        if (level <= 0) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity owner)
                || !GameFunctions.isPlayerPlayingAndAlive(owner)
                || GameFunctions.isPlayerSpectatingOrCreative(owner)) {
            clear();
            return;
        }

        // Reaching the sanity floor clears all fear, whether the last point came from our drain or another source.
        // 理智到 5 就清空全部恐惧，不管最后一滴是不是恐惧扣的。
        if (PlayerMoodComponent.KEY.get(player).getMood() * 100.0F <= SpecterRules.FEAR_MIN_SANITY) {
            clear();
            return;
        }

        if (--decayTicks <= 0) {
            level--;
            if (level <= 0) {
                clear();
                return;
            }
            decayTicks = SpecterRules.FEAR_DECAY_TICKS;
            drainTicks = SpecterRules.fearDrainIntervalTicks(level);
            syncOwner();
            return;
        }

        if (--drainTicks <= 0) {
            if (drainSanity(level)) {
                return;
            }
            drainTicks = SpecterRules.fearDrainIntervalTicks(level);
        }
    }

    /** @return true when sanity hit the floor and all fear was cleared. / 掉到 5 并清空恐惧时返回 true。 */
    private boolean drainSanity(int fearLevel) {
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        float sanity = mood.getMood() * 100.0F;
        if (sanity <= SpecterRules.FEAR_MIN_SANITY) {
            clear();
            return true;
        }
        float next = Math.max(SpecterRules.FEAR_MIN_SANITY, sanity - SpecterRules.fearDrainSanity(fearLevel));
        mood.setMood(next / 100.0F);
        if (next <= SpecterRules.FEAR_MIN_SANITY) {
            clear();
            return true;
        }
        return false;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (level <= 0) {
            return;
        }
        tag.putInt(LEVEL, level);
        tag.putInt(DECAY_TICKS, Math.max(0, decayTicks));
        tag.putInt(DRAIN_TICKS, Math.max(0, drainTicks));
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        level = tag.contains(LEVEL, NbtElement.NUMBER_TYPE)
                ? Math.clamp(tag.getInt(LEVEL), 0, SpecterRules.FEAR_MAX_LEVEL)
                : 0;
        decayTicks = tag.contains(DECAY_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(DECAY_TICKS))
                : 0;
        drainTicks = tag.contains(DRAIN_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(DRAIN_TICKS))
                : 0;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(level);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        level = Math.clamp(buf.readVarInt(), 0, SpecterRules.FEAR_MAX_LEVEL);
    }

    private void syncOwner() {
        if (player instanceof ServerPlayerEntity owner) {
            KEY.syncWith(owner, (ComponentProvider) owner);
        }
    }
}
