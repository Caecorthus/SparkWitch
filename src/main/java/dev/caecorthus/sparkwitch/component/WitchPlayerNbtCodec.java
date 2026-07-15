package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.skill.WitchForcedSkillState;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

final class WitchPlayerNbtCodec {
    private WitchPlayerNbtCodec() {
    }

    static void write(WitchPlayerComponent component, NbtCompound tag) {
        if (component.activeSkillId != null) {
            tag.putString("ActiveSkill", component.activeSkillId.toString());
        }
        WitchForcedSkillState.writeToNbt(tag, component.forcedSkillId);
        if (component.cooldownTicks > 0) {
            tag.putInt("CooldownTicks", component.cooldownTicks);
        }
        if (component.manaEnabled) {
            tag.putBoolean("ManaEnabled", true);
            tag.putInt("Mana", component.mana);
            if (component.manaRegenerationTicks > 0) {
                tag.putInt("ManaRegenerationTicks", component.manaRegenerationTicks);
            }
        }
        if (component.ceremonialSwordTicks > 0) {
            tag.putInt("CeremonialSwordTicks", component.ceremonialSwordTicks);
            tag.putInt("CeremonialSwordSlot", component.ceremonialSwordSlot);
        }
        if (component.grandWitchCeremonialSwordTasks > 0) {
            tag.putInt("GrandWitchCeremonialSwordTasks", component.grandWitchCeremonialSwordTasks);
        }
        if (component.mightyForceTicks > 0) {
            tag.putInt("MightyForceTicks", component.mightyForceTicks);
        }
        if (component.swiftStepTicks > 0) {
            tag.putInt("SwiftStepTicks", component.swiftStepTicks);
        }
        if (component.murderSenseTicks > 0) {
            tag.putInt("MurderSenseTicks", component.murderSenseTicks);
        }
        if (component.healingTicks > 0) {
            tag.putInt("HealingTicks", component.healingTicks);
            if (component.healingPulseTicks > 0) {
                tag.putInt("HealingPulseTicks", component.healingPulseTicks);
            }
        }
        if (component.clairvoyanceSelfTicks > 0) {
            tag.putInt("ClairvoyanceSelfTicks", component.clairvoyanceSelfTicks);
        }
        if (component.clairvoyanceOthersTicks > 0) {
            tag.putInt("ClairvoyanceOthersTicks", component.clairvoyanceOthersTicks);
        }
        if (component.deferredCooldownTicks > 0) {
            tag.putInt("DeferredCooldownTicks", component.deferredCooldownTicks);
        }
        if (component.pigChaseFreezeTicks > 0) {
            tag.putInt("PigChaseFreezeTicks", component.pigChaseFreezeTicks);
            tag.putInt("PigChaseQueuedTicks", component.pigChaseQueuedTicks);
            tag.putDouble("PigChaseFreezeX", component.pigChaseFreezeX);
            tag.putDouble("PigChaseFreezeY", component.pigChaseFreezeY);
            tag.putDouble("PigChaseFreezeZ", component.pigChaseFreezeZ);
        }
        if (component.pigChaseTicks > 0) {
            tag.putInt("PigChaseTicks", component.pigChaseTicks);
            tag.putBoolean("PigChaseOwnsPsycho", component.pigChaseOwnsPsycho);
        }
        if (component.deathRayTicks > 0) {
            tag.putInt("DeathRayTicks", component.deathRayTicks);
            tag.putInt("DeathRayCharges", component.deathRayCharges);
        }
        component.getSaintState().writeNbt(tag);
    }

    static void read(WitchPlayerComponent component, NbtCompound tag) {
        component.activeSkillId = tag.contains("ActiveSkill", NbtElement.STRING_TYPE)
                ? Identifier.tryParse(tag.getString("ActiveSkill"))
                : null;
        component.forcedSkillId = WitchForcedSkillState.readFromNbt(tag);
        component.cooldownTicks = tag.contains("CooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CooldownTicks"))
                : 0;
        component.manaEnabled = tag.getBoolean("ManaEnabled");
        component.mana = component.manaEnabled && tag.contains("Mana", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("Mana"))
                : 0;
        component.manaRegenerationTicks = component.manaEnabled
                && tag.contains("ManaRegenerationTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("ManaRegenerationTicks"))
                : 0;
        component.ceremonialSwordTicks = tag.contains("CeremonialSwordTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CeremonialSwordTicks"))
                : 0;
        component.ceremonialSwordSlot = component.ceremonialSwordTicks > 0
                && tag.contains("CeremonialSwordSlot", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("CeremonialSwordSlot"))
                : -1;
        component.grandWitchCeremonialSwordTasks = tag.contains(
                "GrandWitchCeremonialSwordTasks",
                NbtElement.NUMBER_TYPE
        )
                ? GrandWitchRules.clampCeremonialSwordTaskProgress(
                tag.getInt("GrandWitchCeremonialSwordTasks"))
                : 0;
        component.mightyForceTicks = tag.contains("MightyForceTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("MightyForceTicks"))
                : 0;
        component.swiftStepTicks = tag.contains("SwiftStepTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("SwiftStepTicks"))
                : 0;
        component.murderSenseTicks = tag.contains("MurderSenseTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("MurderSenseTicks"))
                : 0;
        component.healingTicks = tag.contains("HealingTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("HealingTicks"))
                : 0;
        component.healingPulseTicks = component.healingTicks > 0
                && tag.contains("HealingPulseTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("HealingPulseTicks"))
                : 0;
        component.clairvoyanceSelfTicks = tag.contains("ClairvoyanceSelfTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("ClairvoyanceSelfTicks"))
                : 0;
        component.clairvoyanceOthersTicks = tag.contains("ClairvoyanceOthersTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("ClairvoyanceOthersTicks"))
                : 0;
        component.deferredCooldownTicks = tag.contains("DeferredCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("DeferredCooldownTicks"))
                : 0;
        component.pigChaseFreezeTicks = tag.contains("PigChaseFreezeTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("PigChaseFreezeTicks"))
                : 0;
        component.pigChaseQueuedTicks = tag.contains("PigChaseQueuedTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("PigChaseQueuedTicks"))
                : 0;
        component.pigChaseTicks = tag.contains("PigChaseTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("PigChaseTicks"))
                : 0;
        component.pigChaseFreezeX = tag.contains("PigChaseFreezeX", NbtElement.NUMBER_TYPE)
                ? tag.getDouble("PigChaseFreezeX")
                : 0.0;
        component.pigChaseFreezeY = tag.contains("PigChaseFreezeY", NbtElement.NUMBER_TYPE)
                ? tag.getDouble("PigChaseFreezeY")
                : 0.0;
        component.pigChaseFreezeZ = tag.contains("PigChaseFreezeZ", NbtElement.NUMBER_TYPE)
                ? tag.getDouble("PigChaseFreezeZ")
                : 0.0;
        component.pigChaseOwnsPsycho = component.pigChaseTicks > 0 && tag.getBoolean("PigChaseOwnsPsycho");
        component.deathRayTicks = tag.contains("DeathRayTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("DeathRayTicks"))
                : 0;
        component.deathRayCharges = component.deathRayTicks > 0
                && tag.contains("DeathRayCharges", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("DeathRayCharges"))
                : 0;
        component.getSaintState().readNbt(tag);
    }
}
