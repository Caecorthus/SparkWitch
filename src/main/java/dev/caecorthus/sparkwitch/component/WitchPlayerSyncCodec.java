package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class WitchPlayerSyncCodec {
    private WitchPlayerSyncCodec() {
    }

    static void write(WitchPlayerComponent component, RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean visible = recipient == component.player || GameFunctions.isPlayerSpectatingOrCreative(recipient);
        boolean ownerVisible = recipient == component.player;
        writeOptionalIdentifier(buf, visible ? component.activeSkillId : null);
        buf.writeVarInt(visible ? component.cooldownTicks : 0);
        buf.writeBoolean(visible && component.manaEnabled);
        buf.writeVarInt(visible && component.manaEnabled ? component.mana : 0);
        buf.writeVarInt(visible ? component.ceremonialSwordTicks : 0);
        buf.writeVarInt(visible ? component.grandWitchCeremonialSwordTasks : 0);
        buf.writeVarInt(ownerVisible ? component.mightyForceTicks : 0);
        buf.writeVarInt(ownerVisible ? component.swiftStepTicks : 0);
        buf.writeVarInt(ownerVisible ? component.murderSenseTicks : 0);
        buf.writeVarInt(ownerVisible ? component.healingTicks : 0);
        buf.writeVarInt(ownerVisible ? component.clairvoyanceSelfTicks : 0);
        buf.writeVarInt(ownerVisible ? component.clairvoyanceOthersTicks : 0);
        buf.writeVarInt(ownerVisible ? component.deferredCooldownTicks : 0);
        buf.writeVarInt(ownerVisible ? component.pigChaseFreezeTicks : 0);
        buf.writeVarInt(ownerVisible ? component.pigChaseQueuedTicks : 0);
        buf.writeVarInt(ownerVisible ? component.pigChaseTicks : 0);
        buf.writeVarInt(ownerVisible ? component.deathRayTicks : 0);
        buf.writeVarInt(ownerVisible ? component.deathRayCharges : 0);
    }

    static void read(WitchPlayerComponent component, RegistryByteBuf buf) {
        component.activeSkillId = readOptionalIdentifier(buf);
        component.cooldownTicks = Math.max(0, buf.readVarInt());
        component.manaEnabled = buf.readBoolean();
        int syncedMana = Math.max(0, buf.readVarInt());
        component.mana = component.manaEnabled ? syncedMana : 0;
        component.manaRegenerationTicks = 0;
        component.ceremonialSwordTicks = Math.max(0, buf.readVarInt());
        if (component.ceremonialSwordTicks == 0) {
            component.ceremonialSwordSlot = -1;
        }
        component.grandWitchCeremonialSwordTasks =
                GrandWitchRules.clampCeremonialSwordTaskProgress(buf.readVarInt());
        component.mightyForceTicks = Math.max(0, buf.readVarInt());
        component.swiftStepTicks = Math.max(0, buf.readVarInt());
        component.murderSenseTicks = Math.max(0, buf.readVarInt());
        component.healingTicks = Math.max(0, buf.readVarInt());
        component.healingPulseTicks = 0;
        component.clairvoyanceSelfTicks = Math.max(0, buf.readVarInt());
        component.clairvoyanceOthersTicks = Math.max(0, buf.readVarInt());
        component.deferredCooldownTicks = Math.max(0, buf.readVarInt());
        component.pigChaseFreezeTicks = Math.max(0, buf.readVarInt());
        component.pigChaseQueuedTicks = Math.max(0, buf.readVarInt());
        component.pigChaseTicks = Math.max(0, buf.readVarInt());
        component.pigChaseOwnsPsycho = false;
        component.deathRayTicks = Math.max(0, buf.readVarInt());
        component.deathRayCharges = Math.max(0, buf.readVarInt());
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
