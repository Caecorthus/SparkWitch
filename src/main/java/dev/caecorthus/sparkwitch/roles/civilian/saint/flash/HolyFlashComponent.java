package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stable CCA contract {@code sparkwitch:holy_flash} (NEVER_COPY): each player's own Holy Flash blindness and
 * tinnitus timer. Server-authoritative and synced only to the flashed player; the client only draws the screen
 * mask and plays the ringing. It does not use vanilla Blindness, so blackout end, Gin and other cleanups that strip
 * Blindness never touch it. Never saved to NBT.
 * 稳定 CCA 契约 {@code sparkwitch:holy_flash}（NEVER_COPY）：每位玩家自己的圣光弹致盲与耳鸣计时。
 * 由服务端权威决定，仅同步给被闪的玩家本人；客户端只负责画遮罩和播放耳鸣。它不使用原版失明，
 * 因此黑灯结束、Gin 等清除失明的逻辑都不会影响它。从不写入 NBT。
 */
public final class HolyFlashComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<HolyFlashComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("holy_flash"), HolyFlashComponent.class);

    private final PlayerEntity player;
    private final HolyFlashState state = new HolyFlashState();

    public HolyFlashComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isActive() {
        return state.isActive();
    }

    public int totalTicks() {
        return state.totalTicks();
    }

    public int remainingTicks() {
        return state.remainingTicks();
    }

    /** Smooth elapsed ticks for rendering. / 供渲染使用的平滑经过刻数。 */
    public float elapsedTicks(float tickDelta) {
        return state.isActive() ? state.elapsedTicks() + tickDelta : 0.0F;
    }

    public Vec3d burstPos() {
        return new Vec3d(state.burstX(), state.burstY(), state.burstZ());
    }

    public boolean facedBurst() {
        return state.faced();
    }

    /** Server only; max semantics (see {@link HolyFlashState#flash}). / 仅服务端调用；取最大值。 */
    public void flash(int ticks, Vec3d burst, boolean faced) {
        if (state.flash(ticks, burst.x, burst.y, burst.z, faced)) {
            syncOwner();
        }
    }

    public void clear() {
        if (state.clear()) {
            syncOwner();
        }
    }

    @Override
    public void serverTick() {
        if (!state.isActive()) {
            return;
        }
        // Death, spectating, creative, active Wraith, or any phase other than ACTIVE ends the flash at once. A Rift
        // Gate occupant is the exception: an ALIVE spectator (Riftwalker D3), not a dead one, so entering a gate never
        // cleanses the flash; it keeps counting down inside.
        // 死亡、旁观、创造、激活冤魂，或不处于 ACTIVE 阶段时立即结束闪光。裂隙门内的玩家例外：它是存活旁观者
        // （隙行者 D3）而非死者，因此进门不会清除闪光，闪光在门内照常倒计时。
        if (!HolyFlashRules.isActivePhase(player.getWorld())
                || !(ControlExpertTargeting.isParticipant(player) || RiftSessionService.isInside(player))) {
            clear();
            return;
        }
        if (state.serverTick()) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        state.clientTick();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        state.writeSync(buf);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        state.readSync(buf);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }

    private void syncOwner() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }
}
