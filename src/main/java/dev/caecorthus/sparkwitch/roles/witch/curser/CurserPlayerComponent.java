package dev.caecorthus.sparkwitch.roles.witch.curser;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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
 * Holds a promoted Curser's private cooldown and each player's private confusion timer.
 * 保存晋升诅咒师的私有冷却，以及每位玩家私有的混乱计时。
 */
public final class CurserPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<CurserPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("curser_player"), CurserPlayerComponent.class);

    private final PlayerEntity player;
    private final CurserState state = new CurserState();

    public CurserPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return state.cooldownTicks();
    }

    public int getConfusionTicks() {
        return state.confusionTicks();
    }

    public boolean isConfused() {
        return state.isConfused();
    }

    public void initializeForPromotion() {
        state.initializeForPromotion();
        syncOwner();
    }

    public boolean startCooldown() {
        if (!state.startCooldown()) {
            return false;
        }
        syncOwner();
        return true;
    }

    public void applyConfusion() {
        state.applyConfusion();
        syncOwner();
    }

    public void clear() {
        if (state.clear()) {
            syncOwner();
        }
    }

    @Override
    public void serverTick() {
        boolean cooldownChanged = state.tickCooldown();
        boolean confusionChanged = state.tickConfusion();
        if ((cooldownChanged && (state.cooldownTicks() == 0 || state.cooldownTicks() % 20 == 0))
                || (confusionChanged && (state.confusionTicks() == 0 || state.confusionTicks() % 20 == 0))) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        // Never predict readiness: await the server's zero sync. Confusion expires locally only after its last second.
        // 不预测冷却就绪：等待服务端同步零；混乱仅在最后一秒后本地结束。
        if (state.cooldownTicks() > 1) {
            state.tickCooldown();
        }
        if (state.confusionTicks() > 1) {
            state.tickConfusion();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(state.cooldownTicks());
        buf.writeVarInt(state.confusionTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        state.restore(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (state.cooldownTicks() > 0) {
            tag.putInt("CooldownTicks", state.cooldownTicks());
        }
        if (state.confusionTicks() > 0) {
            tag.putInt("ConfusionTicks", state.confusionTicks());
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.restore(
                tag.contains("CooldownTicks", NbtElement.NUMBER_TYPE) ? tag.getInt("CooldownTicks") : 0,
                tag.contains("ConfusionTicks", NbtElement.NUMBER_TYPE) ? tag.getInt("ConfusionTicks") : 0
        );
    }

    private void syncOwner() {
        KEY.sync(player);
    }
}
