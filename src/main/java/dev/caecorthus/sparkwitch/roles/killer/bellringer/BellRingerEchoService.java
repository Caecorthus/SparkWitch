package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Echo skill handler reached through the shared {@code sparkwitch:use_skill} path (so Grand Witch Fear
 * already blocks it). Cooldown starts at cast via a plain success result.
 * 通过共享 {@code sparkwitch:use_skill} 路径到达的回响技能处理器（因此大魔女恐惧已可拦截）；
 * 冷却通过普通成功结果在施放时开始。
 */
public final class BellRingerEchoService {
    private static final float TOLL_VOLUME = 5.0F;
    private static final float TOLL_PITCH = 0.6F;
    private static final PlayerMoodComponent.Task[] TASKS = PlayerMoodComponent.Task.values();

    private BellRingerEchoService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity caster = context.player();
        if (!BellRingerRules.isBellRinger(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        // SparkWitch packets are not covered by SparkTraits' own packet gate, so ask the facade here.
        // SparkWitch 自有数据包不在 SparkTraits 的数据包拦截范围内，因此在此主动查询公共门面。
        if (SparkTraitsKillerBridge.isRoleSkillBlocked(caster) || caster.isSpectator()) {
            return WitchSkillUseResult.fail("message.sparkwitch.bell_echo.blocked");
        }
        GameWorldComponent game = context.gameComponent();
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(caster)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }
        ServerWorld world = context.world();
        GameTimeComponent time = GameTimeComponent.KEY.get(world);
        if (!BellRingerRules.canAffordEcho(time.getTime())) {
            return WitchSkillUseResult.fail("message.sparkwitch.bell_echo.not_enough_time");
        }

        // Owner decision: Echo is paid with Wathe round time; the bell, tasks and 60 s deadline start now.
        // 所有者决定：回响以 Wathe 对局时间支付；钟声、任务与 60 秒期限均从此刻开始。
        time.addTime(-BellRingerRules.ECHO_TIME_COST_TICKS);
        broadcastToll(world);
        long now = world.getTime();
        UUID match = BellRingerMatch.currentId();
        for (ServerPlayerEntity player : List.copyOf(world.getPlayers())) {
            BellEchoPlayerComponent component = BellEchoPlayerComponent.KEY.get(player);
            switch (BellRingerEchoTargeting.audience(caster, player, game)) {
                // Already-affected players are skipped: no stacking and no deadline refresh (owner decision 12).
                // 已受影响的玩家直接跳过：不叠加、不刷新期限（所有者决定 12）。
                case AFFECTED -> {
                    if (!component.hasEchoTask()) {
                        assignEchoTask(player, component, now, match);
                    }
                }
                case HEARER -> component.startHeard(now + BellRingerRules.ECHO_HINT_TICKS, match);
                case NONE -> {
                }
            }
        }
        return WitchSkillUseResult.success(BellRingerRules.ECHO_COOLDOWN_TICKS, "message.sparkwitch.bell_echo.cast");
    }

    /**
     * Sends the toll to every player of {@code world} at their own position, like Saint's karma bell.
     * 仿照圣人业力钟声，向 {@code world} 内每名玩家在其自身位置发送钟声。
     */
    static void broadcastToll(ServerWorld world) {
        if (SparkWitchSounds.BELL_RINGER_TOLL == null) {
            return;
        }
        RegistryEntry<SoundEvent> sound = RegistryEntry.of(SparkWitchSounds.BELL_RINGER_TOLL);
        for (ServerPlayerEntity player : world.getPlayers()) {
            player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                    sound,
                    SoundCategory.PLAYERS,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    TOLL_VOLUME,
                    TOLL_PITCH,
                    world.random.nextLong()
            ));
        }
    }

    /**
     * Replaces every Wathe task with one uniformly random Echo task. {@code timesGotten} is left untouched
     * so later normal task odds do not change; the marker is synced before the mood so the owner's HUD
     * never shows the Echo task as a normal line.
     * 以一个均匀随机的回响任务替换全部 Wathe 任务。不改动 {@code timesGotten}，以免影响之后常规任务的概率；
     * 标记先于情绪组件同步，使拥有者 HUD 不会把回响任务显示为普通任务。
     */
    private static void assignEchoTask(
            ServerPlayerEntity player,
            BellEchoPlayerComponent component,
            long now,
            @Nullable UUID match
    ) {
        PlayerMoodComponent.Task type = TASKS[player.getRandom().nextInt(TASKS.length)];
        component.startEcho(type, now + BellRingerRules.ECHO_DEADLINE_TICKS, match);
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(player);
        mood.tasks.clear();
        mood.tasks.put(type, createTask(type));
        mood.sync();
    }

    private static PlayerMoodComponent.TrainTask createTask(PlayerMoodComponent.Task type) {
        return switch (type) {
            case SLEEP -> new PlayerMoodComponent.SleepTask(GameConstants.SLEEP_TASK_DURATION);
            case OUTSIDE -> new PlayerMoodComponent.OutsideTask(GameConstants.OUTSIDE_TASK_DURATION);
            case EAT -> new PlayerMoodComponent.EatTask();
            case DRINK -> new PlayerMoodComponent.DrinkTask();
        };
    }
}
