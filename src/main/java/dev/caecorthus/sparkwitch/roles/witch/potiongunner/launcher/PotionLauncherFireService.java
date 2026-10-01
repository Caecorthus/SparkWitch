package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.net.FirePotionLauncherC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-authoritative launcher fire. The client only states intent and aim; this service validates the shooter,
 * the held and loaded launcher, cooldown, stun/session locks, and SparkTraits weapon blocks before spawning a shell.
 * 服务端权威的炮筒发射。客户端只表达意图与朝向；本服务复核射手、手持且已装填的炮筒、冷却、眩晕/遥控锁定与
 * SparkTraits 武器封锁后才生成炮弹。
 */
public final class PotionLauncherFireService {
    private PotionLauncherFireService() {
    }

    public static void fire(ServerPlayerEntity player, FirePotionLauncherC2SPacket payload) {
        // WP1 implements. / 由 WP1 实现。
    }
}
