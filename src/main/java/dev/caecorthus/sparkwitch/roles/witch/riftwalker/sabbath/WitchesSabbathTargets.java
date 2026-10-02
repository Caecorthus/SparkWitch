package dev.caecorthus.sparkwitch.roles.witch.riftwalker.sabbath;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit.FisherSpiritComponent;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerInventoryRules;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftGateUsers;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerStatusProbes;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Server adapters that feed {@link WitchesSabbathRules}' probes from live state (research/04 §1.2–§1.3). Roles are
 * RAW ({@code GameWorldComponent#getRole}) and the faction is SparkFactionAPI's effective faction through
 * {@link RiftGateUsers#isWitchFaction}, so a disguised Black Raven, an active Wraith or the promoted Curser is never
 * pulled. Other modules are only read through their public queries or SparkWitch's fail-safe compat bridges; the
 * per-tick position holders (Taotie stomach, Last Stand, Knockout control) are skipped because they would undo — or,
 * for Knockout control, break — the pull. Control-Expert-stunned, Hunter-rooted and capture-stunned teammates are pulled
 * and stay held at the landing spot (D19): the stun and the root never anchor a position, and the capture stun's lock
 * point is moved there after the teleport; a capture stun whose lock cannot move is skipped (fail closed).
 * 以实时状态为 {@link WitchesSabbathRules} 的探针提供输入的服务端适配器（research/04 §1.2–§1.3）。职业取原始职业
 * （{@code GameWorldComponent#getRole}），阵营取经 {@link RiftGateUsers#isWitchFaction} 的 SparkFactionAPI 有效阵营，
 * 因此伪装中的黑羽鸦、激活冤魂与晋升诅咒者永远不会被召集。其他模块只通过其公共查询或 SparkWitch 的安全兼容桥读取；
 * 每 tick 固定位置的状态（饕餮胃、背水一战、迷药控制）会被跳过，因为它们会抵消召集——迷药控制甚至会被召集打断。
 * 被控场专家眩晕、被捕兽夹定住或被捕捉装置眩晕的队友会被召集，并在落点保持原状态（D19）：眩晕与定身从不锚定位置，
 * 捕捉眩晕的锁定点会在传送后移到落点；锁定点无法移动的捕捉眩晕会被跳过（失败即关闭）。
 */
final class WitchesSabbathTargets {
    private WitchesSabbathTargets() {
    }

    /** Living, free, same-faction teammates of the caster, in world player order. / 施放者存活、自由的同阵营队友，按世界玩家顺序。 */
    static List<ServerPlayerEntity> collect(ServerWorld world, ServerPlayerEntity caster, GameWorldComponent game) {
        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            if (WitchesSabbathRules.targetSkip(target(caster, player, game)) == null) {
                targets.add(player);
            }
        }
        return targets;
    }

    static WitchesSabbathRules.CasterProbe caster(ServerPlayerEntity caster, Role role, GameWorldComponent game) {
        return new WitchesSabbathRules.CasterProbe() {
            @Override
            public boolean exactRiftwalker() {
                // RAW role from the shared path's context, never the Black Raven acting role. / 原始职业，从不使用伪装职业。
                return RiftwalkerRules.isRiftwalker(role) && RiftwalkerRules.isRiftwalker(game.getRole(caster));
            }

            @Override
            public boolean roundActive() {
                // STOPPING (post-win fade) still counts as running for Wathe: no pulls then. / 胜负后的淡出阶段不允许召集。
                return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
            }

            @Override
            public boolean participant() {
                return isRoundParticipant(caster, game);
            }

            @Override
            public boolean ownCamera() {
                return caster.getCameraEntity() == caster;
            }

            @Override
            public boolean swallowed() {
                return NoellesTaotieSeekerBridge.isSwallowed(caster);
            }

            @Override
            public boolean lastStandPending() {
                return SparkTraitsSeekerBridge.isLastStandPending(caster);
            }

            @Override
            public boolean lastEscape() {
                return SparkTraitsKillerBridge.isLastEscapeActive(caster);
            }

            @Override
            public boolean kidnapperControlled() {
                // A controlled caster would summon the whole team to the Kidnapper. / 被控制的施放者会把全队召到绑架者身边。
                return isKidnapperControlled(caster);
            }

            @Override
            public boolean captureStunned() {
                // SparkStrength locks the stunned player's input client-side only. / SparkStrength 只在客户端锁定输入。
                return RiftwalkerStatusProbes.isCaptureStunned(caster);
            }

            @Override
            public boolean roleSkillBlocked() {
                // SparkWitch packets are outside SparkTraits' packet gate, so ask the facade. / SparkWitch 数据包不在 Traits 拦截范围内。
                return SparkTraitsKillerBridge.isRoleSkillBlocked(caster);
            }

            @Override
            public boolean glimmering() {
                // A glimmering (Angler spirit) caster may stand inside a door. / 灵光状态的施放者可能站在门里。
                return FisherSpiritComponent.KEY.maybeGet(caster).map(FisherSpiritComponent::isActive).orElse(false);
            }
        };
    }

    static WitchesSabbathRules.TargetProbe target(ServerPlayerEntity caster, ServerPlayerEntity player,
                                                 GameWorldComponent game) {
        return new WitchesSabbathRules.TargetProbe() {
            @Override
            public boolean isCaster() {
                return player == caster || player.getUuid().equals(caster.getUuid());
            }

            @Override
            public boolean participant() {
                return isRoundParticipant(player, game);
            }

            @Override
            public boolean witchFaction() {
                // C6: effective faction exactly sparkwitch:witch; Apprentice/Murderous Witches are not pulled.
                // C6：有效阵营恰为 sparkwitch:witch；预备魔女与杀意魔女不会被召集。
                return RiftGateUsers.isWitchFaction(player);
            }

            @Override
            public boolean ownCamera() {
                return player.getCameraEntity() == player;
            }

            @Override
            public boolean swallowed() {
                return NoellesTaotieSeekerBridge.isSwallowed(player);
            }

            @Override
            public boolean lastStandPending() {
                return SparkTraitsSeekerBridge.isLastStandPending(player);
            }

            @Override
            public boolean lastEscape() {
                return SparkTraitsKillerBridge.isLastEscapeActive(player);
            }

            @Override
            public boolean kidnapperControlled() {
                return isKidnapperControlled(player);
            }

            @Override
            public boolean captureStunPinned() {
                // D19: pulled unless the lock point cannot follow (the pull re-locks it). / D19：锁定点无法跟随时才跳过。
                return RiftwalkerStatusProbes.isCaptureStunPinned(player);
            }

            @Override
            public boolean seekerSession() {
                // Read-only Seeker query; unreachable for witches today, kept as a cheap guard (research/04 §1.2).
                // 只读的搜寻者查询；魔女目前不可能处于会话中，作为廉价防护保留（research/04 §1.2）。
                return SeekerInventoryRules.isInSession(player);
            }

            @Override
            public boolean insideGate() {
                // D6: teammates inside a gate are skipped, never ejected. / D6：门内队友被跳过，不会被拉出。
                return RiftSessionService.isInside(player);
            }

            @Override
            public boolean factionAllows() {
                return SparkFactionApi.canAffectPlayer(caster, player, RiftwalkerRules.SABBATH_ACTION_ID, game);
            }
        };
    }

    /**
     * Online, playing and alive in Wathe, not spectating or creative, not an active Wraith, holding a role. Uses
     * {@code isPlayerPlayingAndAlive}, never {@code isPlayerAliveAndSurvival} (which passes active Wraiths).
     * 在线、在 Wathe 中参与且存活、非旁观/创造、非激活冤魂、拥有职业。使用 {@code isPlayerPlayingAndAlive}，
     * 从不使用 {@code isPlayerAliveAndSurvival}（后者会放过激活冤魂）。
     */
    static boolean isRoundParticipant(ServerPlayerEntity player, GameWorldComponent game) {
        return player != null
                && !player.isDisconnected()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && !player.isCreative()
                && !WraithStateService.isActive(player)
                && game.getRole(player) != null;
    }

    private static boolean isKidnapperControlled(PlayerEntity player) {
        return KidnapperControlComponent.KEY.maybeGet(player).map(KidnapperControlComponent::isControlled).orElse(false);
    }
}
