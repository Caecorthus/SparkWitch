package dev.caecorthus.sparkwitch.roles.killer.specter;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Specter skill one, server-authoritative: first press marks the crosshair target within 3 blocks; the next press makes
 * the marked player shriek (no distance limit). The mark expires after 10 s without a cooldown; a shriek costs 45 s.
 * 幽魂技能一，服务端权威：第一次按标记 3 格内准星目标；第二次按让被标记者尖啸（无距离限制）。
 * 标记 10 秒自然过期不进冷却；尖啸后 45 秒冷却。
 */
public final class SpecterSkillService {
    private static boolean registered;

    private SpecterSkillService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(SpecterRules.MARK_SKILL_ID, SpecterSkillService::formatMark);
        ReplayRegistry.registerSkillFormatter(SpecterRules.SHRIEK_SKILL_ID, SpecterSkillService::formatShriek);
        ReplayRegistry.registerSkillFormatter(SpecterRules.OTHERWORLD_SKILL_ID, SpecterSkillService::formatOtherworld);
    }

    public static void use(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        if (!SpecterRules.isSpecter(game.getRole(caster))
                || !GameFunctions.isPlayerPlayingAndAlive(caster)
                || GameFunctions.isPlayerSpectatingOrCreative(caster)) {
            return;
        }
        SpecterPlayerComponent component = SpecterPlayerComponent.KEY.get(caster);
        if (component.isOnCooldown()) {
            return;
        }
        if (component.hasActiveMark()) {
            shriek(caster, component);
            return;
        }

        PlayerEntity target = SpecterRules.findMarkTarget(caster);
        if (target == null) {
            return;
        }
        component.mark(target.getUuid());
        if (target instanceof ServerPlayerEntity serverTarget) {
            GameRecordManager.recordSkillUse(caster, SpecterRules.MARK_SKILL_ID, serverTarget, null);
        }
    }

    private static void shriek(ServerPlayerEntity caster, SpecterPlayerComponent component) {
        UUID targetUuid = component.getMarkedTarget();
        ServerPlayerEntity target = targetUuid == null || caster.getServer() == null
                ? null
                : caster.getServer().getPlayerManager().getPlayer(targetUuid);
        if (target == null || target.isSpectator() || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            // An invalid mark just clears; the skill stays available. / 标记失效只清除，技能保持可用。
            component.clearMark();
            return;
        }

        component.consumeMarkForShriek();
        // Owner-specified sound: the sculk shrieker's shriek. / 所有者指定音效：幽匿尖啸体的尖啸声。
        target.getWorld().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );
        // White action-bar line for 3 s (the vanilla overlay-message lifetime). / 白色动作栏小字，3 秒。
        target.networkHandler.sendPacket(new OverlayMessageS2CPacket(
                Text.translatable("message.sparkwitch.specter.shriek").formatted(Formatting.WHITE)
        ));
        GameRecordManager.recordSkillUse(caster, SpecterRules.SHRIEK_SKILL_ID, target, null);
        applyFear(target);
    }

    /** Players with a real mood bar within 5 blocks gain one level; the marked target gains one extra.
     *  5 格内有真实理智条的玩家 +1 层，被标记者额外 +1 层。 */
    private static void applyFear(ServerPlayerEntity markedTarget) {
        ServerWorld world = markedTarget.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        double radiusSquared = SpecterRules.FEAR_SHRIEK_RADIUS * SpecterRules.FEAR_SHRIEK_RADIUS;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()
                    || GameFunctions.isPlayerSpectatingOrCreative(player)
                    || !GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            var role = game.getRole(player);
            if (role == null || role.getMoodType() != Role.MoodType.REAL) {
                continue;
            }
            if (player.squaredDistanceTo(markedTarget) > radiusSquared) {
                continue;
            }
            int levels = player.getUuid().equals(markedTarget.getUuid()) ? 2 : 1;
            SpecterFearComponent.KEY.get(player).addLevel(levels);
        }
    }

    private static @Nullable Text formatMark(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        return formatTarget(event, match, "replay.skill.sparkwitch.specter_mark.target");
    }

    private static @Nullable Text formatShriek(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        return formatTarget(event, match, "replay.skill.sparkwitch.specter_shriek.target");
    }

    private static @Nullable Text formatOtherworld(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        return Text.translatable(
                "replay.skill.sparkwitch.specter_otherworld_skill",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match))
        );
    }

    private static @Nullable Text formatTarget(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            String translationKey
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(
                translationKey,
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo)
        );
    }
}
