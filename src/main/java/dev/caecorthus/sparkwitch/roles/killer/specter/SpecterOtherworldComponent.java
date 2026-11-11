package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * World-level Otherworld window. It is started by the Specter's skill two and keeps running for the full duration even
 * if the Specter dies; only the timer or the end of the round stops it. Synced to every client so the screen filter and
 * soul-flame particles can run for all living participants.
 * 世界级里世界窗口。由幽魂二技能开启，即使幽魂死亡也会跑满整个时长；只有计时结束或回合结束才关闭。
 * 同步给所有客户端，供所有存活参与者运行画面滤镜与灵魂火把粒子。
 */
public final class SpecterOtherworldComponent implements AutoSyncedComponent, ServerTickingComponent,
        ClientTickingComponent {
    public static final ComponentKey<SpecterOtherworldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("specter_otherworld"),
            SpecterOtherworldComponent.class
    );
    private static final String ACTIVE_TICKS = "ActiveTicks";
    private static final String BGM_VARIANT = "BgmVariant";
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int BGM_VARIANTS = 3;

    private final World world;
    private int activeTicks;
    private int bgmVariant;
    private int syncTicks;

    public SpecterOtherworldComponent(World world) {
        this.world = world;
    }

    public boolean isActive() {
        return activeTicks > 0;
    }

    public int getActiveTicks() {
        return Math.max(0, activeTicks);
    }

    /** Elapsed ticks since the window opened, clamped to the configured duration. / 窗口开启以来的刻数。 */
    public int getElapsedTicks() {
        return Math.max(0, SpecterRules.SECOND_SKILL_DURATION_TICKS - getActiveTicks());
    }

    /** Server only: opens (or refreshes) the world window and fixes the BGM track for everyone. / 仅服务端：开启/刷新窗口并选定全服 BGM。 */
    public void start(int ticks) {
        activeTicks = Math.max(0, ticks);
        bgmVariant = world.getRandom().nextInt(BGM_VARIANTS);
        syncTicks = 0;
        sync();
    }

    /** Chosen BGM variant, identical on every client. / 全客户端一致的 BGM 变体。 */
    public int getBgmVariant() {
        return Math.floorMod(bgmVariant, BGM_VARIANTS);
    }

    public void clear() {
        if (activeTicks == 0) {
            return;
        }
        activeTicks = 0;
        syncTicks = 0;
        if (world instanceof ServerWorld serverWorld) {
            SpecterPeachwoodService.refresh(serverWorld);
        }
        sync();
    }

    @Override
    public void serverTick() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!GameWorldComponent.KEY.get(serverWorld).isRunning()) {
            clear();
            return;
        }
        if (activeTicks <= 0) {
            return;
        }
        activeTicks--;
        if (activeTicks <= 0) {
            activeTicks = 0;
            // The window is over: recall low-sanity swords immediately; slowness expires on its own within its margin.
            // 窗口结束：立即收回低理智桃木剑；缓慢会在自身余量内自然过期。
            SpecterPeachwoodService.refresh(serverWorld);
            SpecterOtherworldService.refresh(serverWorld);
            sync();
            return;
        }
        if (++syncTicks >= SYNC_INTERVAL_TICKS) {
            syncTicks = 0;
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (activeTicks > 0) {
            activeTicks--;
        }
    }

    private void sync() {
        if (world instanceof ServerWorld) {
            KEY.sync(world);
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (activeTicks > 0) {
            tag.putInt(ACTIVE_TICKS, activeTicks);
            tag.putInt(BGM_VARIANT, bgmVariant);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        activeTicks = Math.max(0, tag.getInt(ACTIVE_TICKS));
        bgmVariant = Math.floorMod(tag.getInt(BGM_VARIANT), BGM_VARIANTS);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getActiveTicks());
        buf.writeVarInt(getBgmVariant());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        activeTicks = Math.max(0, buf.readVarInt());
        bgmVariant = Math.floorMod(buf.readVarInt(), BGM_VARIANTS);
    }
}
