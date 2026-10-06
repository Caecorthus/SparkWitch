package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stable CCA contract {@code sparkwitch:recruitment_hold} (NEVER_COPY, never saved): the 5-second hold a freshly
 * recruited accomplice spends next to the Grand Witch. Server-authoritative; the remaining ticks are synced to every
 * client, because other clients hide the held player's items. Never written to NBT, so a relog or restart can never
 * strand a player in the hold. The anchor is server-only.
 * 稳定 CCA 契约 {@code sparkwitch:recruitment_hold}（NEVER_COPY，不存盘）：新招募的共犯在大魔女身旁度过的 5 秒定身。
 * 由服务端权威决定；剩余刻数同步给所有客户端，因为其他客户端需要隐藏被定身玩家的手持物。从不写入 NBT，
 * 因此重新登录或重启永远不会让玩家卡在定身中。锚点只存在于服务端。
 */
public final class RecruitmentHoldComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<RecruitmentHoldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("recruitment_hold"), RecruitmentHoldComponent.class);

    private final PlayerEntity player;
    private final RecruitmentHoldState state = new RecruitmentHoldState();

    public RecruitmentHoldComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isActive() {
        return state.isActive();
    }

    public int remainingTicks() {
        return state.remainingTicks();
    }

    /** Server only; max semantics, re-anchors at the given pose. / 仅服务端；取最大值，并以给定姿态重新取锚点。 */
    void start(int ticks, double x, double y, double z, float yaw, float pitch) {
        if (state.start(ticks, x, y, z, yaw, pitch)) {
            sync();
        }
    }

    public void clear() {
        if (state.clear()) {
            sync();
        }
    }

    @Override
    public void serverTick() {
        if (!state.isActive()) {
            return;
        }
        // Death, spectating, creative, an active Wraith, or a stopped game ends the hold at once.
        // 死亡、旁观、创造、激活冤魂或对局结束时立即结束定身。
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !ControlExpertTargeting.isParticipant(player)) {
            clear();
            return;
        }
        RecruitmentHold.keepAtAnchor(serverPlayer, state);
        if (state.serverTick()) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        state.clientTick();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
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

    private void sync() {
        if (!player.getWorld().isClient()) {
            KEY.sync(player);
        }
    }
}
