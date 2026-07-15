package dev.caecorthus.sparkwitch.roles.civilian.orthopedist;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
 * Independent Orthopedist state: owner-private cooldown plus viewer-safe Bone Setting visibility.
 * 骨科大夫的独立状态：本人私有冷却，以及可安全同步给骨科大夫观察者的正骨标记。
 */
public final class OrthopedistPlayerComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<OrthopedistPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            OrthopedistRules.ROLE_ID,
            OrthopedistPlayerComponent.class
    );

    private final PlayerEntity player;
    private int cooldownTicks;
    private boolean boneSettingActive;

    public OrthopedistPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int ticks) {
        cooldownTicks = Math.max(0, ticks);
        sync();
    }

    public boolean hasBoneSettingActive() {
        return boneSettingActive;
    }

    public void assignForRole(Role role) {
        setCooldownTicks(isOrthopedist(role) ? OrthopedistRules.INITIAL_COOLDOWN_TICKS : 0);
    }

    public void refreshBoneSettingState() {
        updateBoneSettingState(player.hasStatusEffect(OrthopedistEffects.boneSetting()));
    }

    public void clear() {
        cooldownTicks = 0;
        boneSettingActive = false;
        player.removeStatusEffect(OrthopedistEffects.boneSetting());
        sync();
    }

    @Override
    public void serverTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
                sync();
            }
        }
        refreshBoneSettingState();
    }

    @Override
    public void clientTick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        if (recipient == player) {
            return true;
        }
        Role recipientRole = GameWorldComponent.KEY.get(recipient.getWorld()).getRole(recipient);
        return isOrthopedist(recipientRole);
    }

    /**
     * Only the component owner receives cooldown data; Orthopedist observers receive the public effect bit only.
     * 只有组件本人会收到冷却；其他骨科大夫观察者仅收到公开的正骨标记。
     */
    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean includeCooldown = recipient == player;
        buf.writeBoolean(includeCooldown);
        if (includeCooldown) {
            buf.writeVarInt(cooldownTicks);
        }
        buf.writeBoolean(boneSettingActive);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        boolean includesCooldown = buf.readBoolean();
        cooldownTicks = includesCooldown ? Math.max(0, buf.readVarInt()) : 0;
        boneSettingActive = buf.readBoolean();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (cooldownTicks > 0) {
            tag.putInt("CooldownTicks", cooldownTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cooldownTicks = tag.contains("CooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CooldownTicks"))
                : 0;
        boneSettingActive = false;
    }

    private void updateBoneSettingState(boolean active) {
        if (active == boneSettingActive) {
            return;
        }
        boneSettingActive = active;
        sync();
    }

    private void sync() {
        KEY.sync(player);
    }

    private static boolean isOrthopedist(Role role) {
        return role != null && OrthopedistRules.ROLE_ID.equals(role.identifier());
    }
}
