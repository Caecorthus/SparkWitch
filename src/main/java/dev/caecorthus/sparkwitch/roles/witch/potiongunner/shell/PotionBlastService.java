package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import net.minecraft.util.math.Vec3d;

/**
 * Detonates a shell: grenade-style sound and particles, Seeker device breaks, target resolution, the shell effect
 * (inside Judge attribution), and the per-hit gold reward.
 * 引爆炮弹：手雷式音效与粒子、搜寻者设备破坏、目标判定、炮弹效果（在审判官归因内执行）以及每人奖励金币。
 */
public final class PotionBlastService {
    private static boolean registered;

    private PotionBlastService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // WP2 implements. / 由 WP2 实现。
    }

    public static void detonate(PotionShellEntity shell, Vec3d center) {
        // WP2 implements. / 由 WP2 实现。
    }
}
