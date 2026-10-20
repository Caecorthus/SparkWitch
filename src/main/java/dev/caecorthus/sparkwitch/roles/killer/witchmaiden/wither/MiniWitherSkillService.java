package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Key 2: summons or dismisses the Mini Wither. Both directions start the shared 10 s cooldown (owner rule). Summoning
 * never happens on unlock; the Wither appears only on a press.
 * 技能键 2：召唤或收回小凋灵。两个方向都会开始共用的 10 秒冷却（所有者规则）。解锁时不会自动召唤，只有按键才会出现。
 */
public final class MiniWitherSkillService {
    private static final String SUMMONED_KEY = "summoned";

    private MiniWitherSkillService() {
    }

    public static void registerReplayFormatter() {
        ReplayRegistry.registerSkillFormatter(MiniWitherRules.SKILL_ID, MiniWitherSkillService::formatSkillUse);
    }

    /** Handles the empty toggle request; the server re-checks everything. / 处理空的切换请求；服务端重新校验全部条件。 */
    public static void toggle(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player) || player.isSpectator()
                || !WitchMaidenRules.isWitchMaiden(game.getRole(player))) {
            return;
        }
        // Same silent refusals as the other dedicated role skills (Apprentice Purify, Blind Attune).
        // 与其他自有流程职业技能（预备魔女净化、盲人凝神）相同的静默拒绝。
        if (SparkTraitsKillerBridge.isRoleSkillBlocked(player) || NoellesTaotieSeekerBridge.isSwallowed(player)) {
            return;
        }
        MiniWitherPlayerComponent wither = MiniWitherPlayerComponent.KEY.get(player);
        if (!wither.isUnlocked()) {
            send(player, Text.translatable("message.sparkwitch.mini_wither.locked",
                    wither.getCompletedTasks(), MiniWitherRules.UNLOCK_TASKS));
            return;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(player)) {
            return;
        }
        if (wither.getToggleCooldownTicks() > 0) {
            send(player, Text.translatable("message.sparkwitch.skill.cooldown",
                    (wither.getToggleCooldownTicks() + 19) / 20));
            return;
        }
        boolean summon = !wither.isActive();
        if (summon) {
            // Until its first shot it stares where she looked when summoning. / 第一次射击前，它盯着召唤时她看的方向。
            wither.setAim(player.getYaw(), player.getPitch());
        }
        wither.setActive(summon);
        wither.raiseToggleCooldown(MiniWitherRules.TOGGLE_COOLDOWN_TICKS);
        present(player, summon);
        NbtCompound extra = new NbtCompound();
        extra.putBoolean(SUMMONED_KEY, summon);
        GameRecordManager.recordSkillUse(player, MiniWitherRules.SKILL_ID, null, extra);
    }

    private static void present(ServerPlayerEntity player, boolean summon) {
        ServerWorld world = player.getServerWorld();
        Vec3d at = MiniWitherAnchor.position(player);
        if (summon) {
            world.spawnParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 12, 0.25, 0.3, 0.25, 0.02);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.3F, 1.8F);
        } else {
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.2, 0.25, 0.2, 0.01);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.2F,
                    2.0F);
        }
    }

    private static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(text, true);
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
        return Text.translatable(
                data.getBoolean(SUMMONED_KEY)
                        ? "replay.skill.sparkwitch.mini_wither.summon"
                        : "replay.skill.sparkwitch.mini_wither.dismiss",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo)
        );
    }
}
