package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.caecorthus.sparkwitch.SparkWitch;
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
 * Owner-private Sabotage cooldown, independent from Witch skills and Wathe blackout cooldowns.
 * 仅拥有者可见的破坏技能冷却，独立于魔女技能与 Wathe 熄灯共享冷却。
 */
public final class SaboteurPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SaboteurPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("saboteur_player"),
            SaboteurPlayerComponent.class
    );

    private final PlayerEntity player;
    private final SaboteurCooldownState state = new SaboteurCooldownState();
    // This records the promotion grant, not item possession: dropped, consumed, or moved radios never re-grant.
    private boolean promotionWalkieGranted;

    public SaboteurPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return state.cooldownTicks();
    }

    public boolean isReady() {
        return state.cooldownTicks() <= 0;
    }

    public void setCooldownTicks(int ticks) {
        if (state.setCooldownTicks(ticks)) {
            syncOwner();
        }
    }

    public void clear() {
        setCooldownTicks(0);
        promotionWalkieGranted = false;
    }

    /** Returns true exactly once per active promotion lifecycle. */
    public boolean claimPromotionWalkieGrant() {
        if (promotionWalkieGranted) {
            return false;
        }
        promotionWalkieGranted = true;
        syncOwner();
        return true;
    }

    @Override
    public void serverTick() {
        if (!state.tick()) {
            return;
        }
        int remaining = state.cooldownTicks();
        if (remaining == 0 || remaining % 20 == 0) {
            syncOwner();
        }
    }

    @Override
    public void clientTick() {
        // Stop at one tick until the authoritative zero arrives, so the HUD never advertises early readiness.
        // 在服务端同步零之前停在一 tick，避免 HUD 提前显示可用。
        if (state.cooldownTicks() > 1) {
            state.tick();
        }
    }

    public void syncOwner() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(state.cooldownTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        state.setCooldownTicks(buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("CooldownTicks", state.cooldownTicks());
        tag.putBoolean("PromotionWalkieGranted", promotionWalkieGranted);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        state.setCooldownTicks(tag.getInt("CooldownTicks"));
        promotionWalkieGranted = tag.getBoolean("PromotionWalkieGranted");
    }
}
