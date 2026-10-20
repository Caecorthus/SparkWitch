package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Server tick of a summoned Mini Wither, driven by its owner's component: the quiet ambient call (owner rule: much
 * quieter than a real Wither) and the 5 s auto-fire. A timer that runs out with no target waits at zero and fires the
 * moment one appears.
 * 已召唤小凋灵的服务端逐刻逻辑，由主人的组件驱动：安静的环境叫声（所有者规则：比真正的凋灵小很多）与每 5 秒的自动射击。
 * 计时归零但没有目标时停在零，一出现目标立即射击。
 */
public final class MiniWitherRuntime {
    private static final float SHOOT_VOLUME = 0.35F;
    private static final float SHOOT_PITCH = 1.6F;
    private static final float PITCH_JITTER = 0.1F;

    private MiniWitherRuntime() {
    }

    static void tick(ServerPlayerEntity player, MiniWitherPlayerComponent component) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        Vec3d anchor = MiniWitherAnchor.position(player);
        if (component.tickAmbient()) {
            world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS, MiniWitherRules.AMBIENT_VOLUME, jitter(player, MiniWitherRules.AMBIENT_PITCH));
        }
        if (isSilenced(player) || component.tickAutoFire() > 0) {
            return;
        }
        MiniWitherRules.Mode mode = MiniWitherRules.mode(SparkFactionApi.resolveEffectiveFaction(player, game));
        MiniWitherTargeting.Target target = MiniWitherTargeting.find(player, anchor, mode, game);
        if (target == null) {
            return;
        }
        MiniWitherSkullEntity.Kind kind = mode == MiniWitherRules.Mode.CONSCIENCE
                ? MiniWitherSkullEntity.Kind.BLESSED : MiniWitherSkullEntity.Kind.NORMAL;
        if (MiniWitherSkullEntity.launch(player, kind, mode, anchor, target.aimPoint())) {
            component.restartAutoFire();
            Vec3d shot = target.aimPoint().subtract(anchor);
            component.setAim(MiniWitherRules.yawOf(shot), MiniWitherRules.pitchOf(shot));
            world.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS,
                    SHOOT_VOLUME, jitter(player, SHOOT_PITCH));
        }
    }

    /**
     * The states that refuse her skills also pause the auto-fire: spectating (inside a Rift Gate, swallowed), a Control
     * Expert stun, Fear, a SparkTraits skill block, or a Seeker remote session.
     * 拒绝她技能的状态同样暂停自动射击：旁观（裂隙门内、被吞下）、控场专家眩晕、恐惧、SparkTraits 技能封锁或搜寻者遥控会话。
     */
    static boolean isSilenced(ServerPlayerEntity player) {
        return player.isSpectator()
                || ControlExpertStun.isStunned(player)
                || GrandWitchFearService.isPlayerFeared(player)
                || SparkTraitsKillerBridge.isRoleSkillBlocked(player)
                || SeekerRemoteSessionService.isLocked(player);
    }

    private static float jitter(ServerPlayerEntity player, float pitch) {
        return pitch + (player.getRandom().nextFloat() - 0.5F) * 2.0F * PITCH_JITTER;
    }
}
