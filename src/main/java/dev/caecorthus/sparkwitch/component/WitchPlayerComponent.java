package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.impl.GrandWitchActiveSkillService;
import dev.caecorthus.sparkwitch.impl.WitchManaRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
 * Stores per-round SparkWitch player state.
 * 保存玩家本局 SparkWitch 运行态，包括主动技能、冷却和魔力值。
 */
public final class WitchPlayerComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<WitchPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("player"),
            WitchPlayerComponent.class
    );

    private final PlayerEntity player;
    private Identifier activeSkillId;
    private int cooldownTicks;
    private boolean manaEnabled;
    private int mana;
    private int manaRegenerationTicks;
    private int ceremonialSwordTicks;
    private int ceremonialSwordSlot = -1;

    public WitchPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public @Nullable Identifier getActiveSkillId() {
        return activeSkillId;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public boolean hasManaSystem() {
        return manaEnabled;
    }

    public int getMana() {
        return mana;
    }

    public int getCeremonialSwordTicks() {
        return ceremonialSwordTicks;
    }

    public int getCeremonialSwordSlot() {
        return ceremonialSwordSlot;
    }

    public boolean hasSkill() {
        return activeSkillId != null;
    }

    public boolean hasActiveCeremonialSword() {
        return ceremonialSwordTicks > 0 && ceremonialSwordSlot >= 0;
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

    public void initializeMana() {
        boolean changed = !manaEnabled || mana != WitchManaRules.INITIAL_MANA || manaRegenerationTicks != 0;
        manaEnabled = true;
        mana = WitchManaRules.INITIAL_MANA;
        manaRegenerationTicks = 0;
        if (changed) {
            sync();
        }
    }

    public void clearMana() {
        if (!manaEnabled && mana == 0 && manaRegenerationTicks == 0) {
            return;
        }
        manaEnabled = false;
        mana = 0;
        manaRegenerationTicks = 0;
        sync();
    }

    public void addMana(int amount) {
        if (!manaEnabled || amount <= 0) {
            return;
        }
        int normalized = Math.max(0, mana + amount);
        if (mana == normalized) {
            return;
        }
        mana = normalized;
        sync();
    }

    public boolean spendMana(int amount) {
        if (!manaEnabled || amount <= 0 || mana < amount) {
            return false;
        }
        mana -= amount;
        sync();
        return true;
    }

    public void beginCeremonialSwordWindow(int slot, int durationTicks) {
        int normalizedDuration = Math.max(0, durationTicks);
        int normalizedSlot = normalizedDuration > 0 ? Math.max(0, slot) : -1;
        if (ceremonialSwordTicks == normalizedDuration && ceremonialSwordSlot == normalizedSlot) {
            return;
        }
        ceremonialSwordTicks = normalizedDuration;
        ceremonialSwordSlot = normalizedSlot;
        sync();
    }

    public void completeCeremonialSwordWindow(int cooldownTicks) {
        ceremonialSwordTicks = 0;
        ceremonialSwordSlot = -1;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        sync();
    }

    public void clearCeremonialSwordWindow() {
        if (ceremonialSwordTicks == 0 && ceremonialSwordSlot < 0) {
            return;
        }
        ceremonialSwordTicks = 0;
        ceremonialSwordSlot = -1;
        sync();
    }

    public void clear() {
        if (activeSkillId == null
                && cooldownTicks <= 0
                && !manaEnabled
                && mana == 0
                && manaRegenerationTicks == 0
                && ceremonialSwordTicks == 0
                && ceremonialSwordSlot < 0) {
            return;
        }
        activeSkillId = null;
        cooldownTicks = 0;
        manaEnabled = false;
        mana = 0;
        manaRegenerationTicks = 0;
        ceremonialSwordTicks = 0;
        ceremonialSwordSlot = -1;
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
        tickCeremonialSwordWindow();
        tickCooldown();
        tickManaRegeneration();
    }

    private void tickCeremonialSwordWindow() {
        if (ceremonialSwordTicks <= 0) {
            return;
        }
        ceremonialSwordTicks--;
        if (ceremonialSwordTicks == 0 && player instanceof ServerPlayerEntity serverPlayer) {
            GrandWitchActiveSkillService.finishCeremonialSwordWindow(serverPlayer, this);
            return;
        }
        if (ceremonialSwordTicks % 20 == 0) {
            sync();
        }
    }

    private void tickCooldown() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (cooldownTicks == 0 || cooldownTicks % 20 == 0) {
                sync();
            }
        }
    }

    private void tickManaRegeneration() {
        if (!manaEnabled || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!WitchManaRules.isManaRole(role)) {
            clearMana();
            return;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(serverPlayer) || !WitchManaRules.canRegenerateNaturally(role)) {
            manaRegenerationTicks = 0;
            return;
        }
        if (mana >= WitchManaRules.naturalCap(role)) {
            manaRegenerationTicks = 0;
            return;
        }

        manaRegenerationTicks++;
        if (manaRegenerationTicks >= WitchManaRules.REGENERATION_INTERVAL_TICKS) {
            manaRegenerationTicks = 0;
            int regenerated = WitchManaRules.applyNaturalRegeneration(mana, role);
            if (regenerated != mana) {
                mana = regenerated;
                sync();
            }
        }
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean visible = recipient == player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
        writeOptionalIdentifier(buf, visible ? activeSkillId : null);
        buf.writeVarInt(visible ? cooldownTicks : 0);
        buf.writeBoolean(visible && manaEnabled);
        buf.writeVarInt(visible && manaEnabled ? mana : 0);
        buf.writeVarInt(visible ? ceremonialSwordTicks : 0);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        activeSkillId = readOptionalIdentifier(buf);
        cooldownTicks = Math.max(0, buf.readVarInt());
        manaEnabled = buf.readBoolean();
        int syncedMana = Math.max(0, buf.readVarInt());
        mana = manaEnabled ? syncedMana : 0;
        manaRegenerationTicks = 0;
        ceremonialSwordTicks = Math.max(0, buf.readVarInt());
        if (ceremonialSwordTicks == 0) {
            ceremonialSwordSlot = -1;
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (activeSkillId != null) {
            tag.putString("ActiveSkill", activeSkillId.toString());
        }
        if (cooldownTicks > 0) {
            tag.putInt("CooldownTicks", cooldownTicks);
        }
        if (manaEnabled) {
            tag.putBoolean("ManaEnabled", true);
            tag.putInt("Mana", mana);
            if (manaRegenerationTicks > 0) {
                tag.putInt("ManaRegenerationTicks", manaRegenerationTicks);
            }
        }
        if (ceremonialSwordTicks > 0) {
            tag.putInt("CeremonialSwordTicks", ceremonialSwordTicks);
            tag.putInt("CeremonialSwordSlot", ceremonialSwordSlot);
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
        manaEnabled = tag.getBoolean("ManaEnabled");
        mana = manaEnabled && tag.contains("Mana", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("Mana"))
                : 0;
        manaRegenerationTicks = manaEnabled && tag.contains("ManaRegenerationTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("ManaRegenerationTicks"))
                : 0;
        ceremonialSwordTicks = tag.contains("CeremonialSwordTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CeremonialSwordTicks"))
                : 0;
        ceremonialSwordSlot = ceremonialSwordTicks > 0 && tag.contains("CeremonialSwordSlot", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CeremonialSwordSlot"))
                : -1;
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
