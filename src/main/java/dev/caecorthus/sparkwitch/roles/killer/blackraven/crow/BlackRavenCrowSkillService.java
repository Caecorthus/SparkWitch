package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerSessionMode;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMatch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenActingRole;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Key 2 (Role Skill 2 / N) for the RAW Black Raven: the two-task unlock and the toggle. A press during a session always
 * ends it (no refusal can trap the player in the crow); outside a session the start goes through
 * {@link BlackRavenCrowRules#toggleVerdict}, in the Mini Wither toggle's order.
 * RAW 黑羽鸦的技能键 2（职业技能 2 / N）：两个任务解锁与开关。会话中按下总是结束会话（任何拒绝都不会把玩家困在乌鸦里）；
 * 会话外的开始请求按小凋灵切换的顺序经 {@link BlackRavenCrowRules#toggleVerdict} 判定。
 */
public final class BlackRavenCrowSkillService {
    private BlackRavenCrowSkillService() {
    }

    public static void registerReplayFormatter() {
        ReplayRegistry.registerSkillFormatter(BlackRavenCrowRules.SKILL_ID, BlackRavenCrowSkillService::formatSkillUse);
    }

    /**
     * Counts the RAW Black Raven's own completed tasks in an ACTIVE round, disguised or not (plan §0: tasks done while
     * disguised count). Bell Ringer Echo tasks never fire {@code TaskComplete.EVENT}, so they never count here either.
     * 统计 RAW 黑羽鸦在 ACTIVE 回合中自己完成的任务，无论是否伪装（计划 §0：伪装期间的任务也算）。敲钟人的回响任务从不触发
     * {@code TaskComplete.EVENT}，因此这里同样不计。
     */
    static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !BlackRavenRules.isBlackRaven(game.getRole(player))) {
            return;
        }
        if (!BlackRavenCrowPlayerComponent.KEY.get(player).recordCompletedTask(BlackRavenMatch.currentId())) {
            return;
        }
        // A keybind text resolves to the owner's own binding of Role Skill 2 on their client.
        // 按键文本会在主人客户端上显示其自己绑定的职业技能 2 按键。
        player.sendMessage(Text.translatable("message.sparkwitch.black_raven_crow.unlocked",
                Text.keybind("key.sparkwitch.secondary_skill")).withColor(BlackRavenRules.COLOR), false);
        player.playSoundToPlayer(SoundEvents.ENTITY_PARROT_FLY, SoundCategory.PLAYERS, 0.7F, 0.8F);
        player.playSoundToPlayer(SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.5F, 0.7F);
    }

    /** Handles the empty toggle request; the server re-checks everything. / 处理空的开关请求；服务端重新校验全部条件。 */
    public static void toggle(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.get(player);
        if (component.isActive()) {
            BlackRavenCrowSessions.end(player, BlackRavenCrowEndReason.PLAYER);
            return;
        }
        BlackRavenCrowRules.ToggleVerdict verdict = BlackRavenCrowRules.toggleVerdict(facts(player, component));
        switch (verdict) {
            case START -> BlackRavenCrowSessions.start(player, component);
            case FEARED -> GrandWitchFearService.sendSkillBlocked(player);
            case LOCKED -> player.sendMessage(Text.translatable(verdict.messageKey(),
                    component.getCompletedTasks(), BlackRavenCrowRules.UNLOCK_TASKS), true);
            case COOLDOWN -> player.sendMessage(Text.translatable(verdict.messageKey(),
                    BlackRavenCrowRules.ceilSeconds(component.getCooldownTicks())), true);
            default -> {
                String key = verdict.messageKey();
                if (key != null) {
                    player.sendMessage(Text.translatable(key), true);
                }
            }
        }
    }

    static BlackRavenCrowRules.ToggleFacts facts(ServerPlayerEntity player, BlackRavenCrowPlayerComponent component) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        boolean eligible = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator()
                && BlackRavenRules.isBlackRaven(game.getRole(player));
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(player);
        SeekerStatusComponent seeker = SeekerStatusComponent.KEY.getNullable(player);
        return new BlackRavenCrowRules.ToggleFacts(
                eligible,
                component.isLandingHeld(player.getServerWorld().getTime()),
                SparkTraitsKillerBridge.isRoleSkillBlocked(player),
                NoellesTaotieSeekerBridge.isSwallowed(player),
                component.isUnlocked(),
                GrandWitchFearService.isPlayerFeared(player),
                ControlExpertStun.isStunned(player),
                component.getCooldownTicks(),
                BlackRavenActingRole.isDisguised(player),
                kidnap != null && kidnap.isControlled(),
                RiftSessionService.isInside(player),
                seeker != null && seeker.sessionMode() != SeekerSessionMode.NONE,
                player.isSleeping(),
                player.hasVehicle(),
                BlackRavenCrowRules.isBodyGrounded(player.isOnGround(), player.hasVehicle(), player.isTouchingWater()));
    }

    private static @Nullable Text formatSkillUse(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable("replay.skill.sparkwitch.black_raven_crow",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo));
    }
}
