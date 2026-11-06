package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.doctor4t.wathe.api.Role;
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
 * Owner-private Savior skill state: the G cooldown and the remaining domain duration.
 * 救世主仅本人可见的技能状态：G 冷却与领域剩余时间。
 */
public final class SaviorPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SaviorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SaviorRules.ROLE_ID,
            SaviorPlayerComponent.class
    );
    private static final String COOLDOWN_TICKS = "CooldownTicks";
    private static final String DOMAIN_TICKS = "DomainTicks";

    private final PlayerEntity player;
    private int cooldownTicks;
    private int domainTicks;

    public SaviorPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return Math.max(0, cooldownTicks);
    }

    public int getDomainTicks() {
        return Math.max(0, domainTicks);
    }

    public boolean isDomainActive() {
        return domainTicks > 0;
    }

    /** Server only: resets to the Savior's initial state and syncs. / 仅服务端：重置为救世主初始状态并同步。 */
    public void assignForRole(@Nullable Role role) {
        int nextCooldown = SaviorRules.isSavior(role) ? SaviorRules.DOMAIN_INITIAL_COOLDOWN_TICKS : 0;
        if (cooldownTicks != nextCooldown || domainTicks != 0) {
            cooldownTicks = nextCooldown;
            domainTicks = 0;
            sync();
        }
    }

    /** Server only: starts the 10 s domain and its 60 s cooldown, syncing once. / 仅服务端：展开 10 秒领域并进入 60 秒冷却，同步一次。 */
    public void startDomain() {
        domainTicks = SaviorRules.DOMAIN_DURATION_TICKS;
        cooldownTicks = SaviorRules.DOMAIN_COOLDOWN_TICKS;
        sync();
    }

    /** Server only: ends an active domain early (death, role change, round end). / 仅服务端：提前结束领域。 */
    public void stopDomain() {
        if (domainTicks == 0) {
            return;
        }
        domainTicks = 0;
        syncOwner();
    }

    /** Server only: clears both fields and syncs. / 仅服务端：清空两个字段并同步。 */
    public void clear() {
        if (cooldownTicks == 0 && domainTicks == 0) {
            return;
        }
        cooldownTicks = 0;
        domainTicks = 0;
        sync();
    }

    @Override
    public void serverTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
                syncOwner();
            }
        }
        if (domainTicks > 0) {
            domainTicks--;
            if (domainTicks == 0 || domainTicks % 20 == 0) {
                syncOwner();
            }
        }
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (domainTicks > 0) {
            domainTicks--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeVarInt(getCooldownTicks());
        buf.writeVarInt(getDomainTicks());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        cooldownTicks = Math.max(0, buf.readVarInt());
        domainTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (cooldownTicks > 0) {
            tag.putInt(COOLDOWN_TICKS, cooldownTicks);
        }
        if (domainTicks > 0) {
            tag.putInt(DOMAIN_TICKS, domainTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cooldownTicks = tag.contains(COOLDOWN_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(COOLDOWN_TICKS))
                : 0;
        domainTicks = tag.contains(DOMAIN_TICKS, NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt(DOMAIN_TICKS))
                : 0;
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
