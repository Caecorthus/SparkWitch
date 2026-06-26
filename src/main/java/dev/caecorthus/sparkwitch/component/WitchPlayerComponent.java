package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.game.GameFunctions;
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
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Stores the one active witch skill owned by this player.
 * 保存玩家本局唯一的魔女主动技能与冷却。
 */
public final class WitchPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("player"),
            WitchPlayerComponent.class
    );

    private final PlayerEntity player;
    private Identifier activeSkillId;
    private int cooldownTicks;

    public WitchPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public @Nullable Identifier getActiveSkillId() {
        return activeSkillId;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean hasSkill() {
        return activeSkillId != null;
    }

    public void setActiveSkill(@Nullable Identifier activeSkillId) {
        if (this.activeSkillId == null ? activeSkillId == null : this.activeSkillId.equals(activeSkillId)) {
            return;
        }
        this.activeSkillId = activeSkillId;
        sync();
    }

    public void setCooldownTicks(int cooldownTicks) {
        int normalized = Math.max(0, cooldownTicks);
        if (this.cooldownTicks == normalized) {
            return;
        }
        this.cooldownTicks = normalized;
        sync();
    }

    public void clear() {
        if (activeSkillId == null && cooldownTicks <= 0) {
            return;
        }
        activeSkillId = null;
        cooldownTicks = 0;
        sync();
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
    }

    @Override
    public void serverTick() {
        if (cooldownTicks <= 0) {
            return;
        }
        cooldownTicks--;
        if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
            sync();
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean visible = recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
        writeOptionalIdentifier(buf, visible ? activeSkillId : null);
        buf.writeVarInt(visible ? cooldownTicks : 0);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        activeSkillId = readOptionalIdentifier(buf);
        cooldownTicks = Math.max(0, buf.readVarInt());
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (activeSkillId != null) {
            tag.putString("ActiveSkill", activeSkillId.toString());
        }
        if (cooldownTicks > 0) {
            tag.putInt("CooldownTicks", cooldownTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        activeSkillId = tag.contains("ActiveSkill", NbtElement.STRING_TYPE)
                ? Identifier.tryParse(tag.getString("ActiveSkill"))
                : null;
        cooldownTicks = tag.contains("CooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CooldownTicks"))
                : 0;
    }

    private static void writeOptionalIdentifier(RegistryByteBuf buf, @Nullable Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) {
            buf.writeString(id.toString());
        }
    }

    private static @Nullable Identifier readOptionalIdentifier(RegistryByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return Identifier.tryParse(buf.readString());
    }
}
