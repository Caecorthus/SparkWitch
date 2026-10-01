package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

/**
 * Bound-item lifecycle: strips the launcher and shells from anyone who is not a living Potion Gunner (role change,
 * terminal death, reset, finalize, periodic sweep) and discards shells still in flight when the round ends.
 * 绑定物品生命周期：从任何不是存活药炮手的玩家身上收走炮筒与炮弹（换职业、最终死亡、重置、收尾、定期清扫），
 * 并在对局结束时移除仍在飞行的炮弹。
 */
public final class PotionGunnerLifecycle {
    private static boolean registered;

    private PotionGunnerLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // WP1 implements. / 由 WP1 实现。
    }
}
