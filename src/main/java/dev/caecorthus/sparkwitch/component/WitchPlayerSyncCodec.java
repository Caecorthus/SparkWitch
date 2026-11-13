package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

final class WitchPlayerSyncCodec {
    private WitchPlayerSyncCodec() {
    }

    /**
     * Who receives a player's shared private state (active skill, cooldown, mana, sword progress): the owner, a
     * creative player, and a spectator who is not a living round participant. A living player is only put into
     * spectator mode by a mechanic (inside a Rift Gate, Taotie-swallowed, feigning death); treating that as a dead
     * observer handed them every tracked player's mana and cooldowns to keep after they came back. The one predicate
     * serves both {@code shouldSyncWith} and {@link #write}.
     * 谁能收到玩家的共享私有状态（当前技能、冷却、魔力、仪式剑进度）：本人、创造模式玩家，以及不是本局存活参与者的旁观者。
     * 存活玩家只会因某个机制被切成旁观模式（在裂隙门内、被饕餮吞下、假死）；把这种情况当作死亡观察者，会让他们拿到所有被追踪
     * 玩家的魔力与冷却，并在恢复后继续持有。{@code shouldSyncWith} 与 {@link #write} 共用这一个判定。
     */
    static boolean seesPrivateState(@Nullable PlayerEntity owner, ServerPlayerEntity recipient) {
        return recipient == owner
                || isInformationObserver(recipient.isCreative(), recipient.isSpectator(),
                        GameFunctions.isPlayerPlayingAndAlive(recipient));
    }

    /** Pure: creative always; a spectator only when not playing and alive. / 纯函数：创造模式始终可见；旁观者仅在并非存活在局时可见。 */
    static boolean isInformationObserver(boolean creative, boolean spectator, boolean playingAndAlive) {
        return creative || spectator && !playingAndAlive;
    }

    static void write(WitchPlayerComponent component, RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean visible = seesPrivateState(component.player, recipient);
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
        component.getSaintState().writeSync(buf, ownerVisible);
        buf.writeVarInt(ownerVisible ? component.ninjaParryTicks : 0);
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
        component.getSaintState().readSync(buf);
        component.ninjaParryTicks = Math.max(0, buf.readVarInt());
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
