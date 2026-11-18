package dev.caecorthus.sparkwitch.client.m1903a4;

/**
 * Pure gates for the M1903A4 first-person pose. Both mixins key on the stack, player and hand vanilla's
 * {@code renderFirstPersonItem} is drawing, never on the local player's own hands, so the other hand and any other
 * rendered player keep vanilla's pose.
 * M1903A4 第一人称姿势的纯逻辑判定。两个 mixin 都按原版 {@code renderFirstPersonItem} 正在绘制的物品、玩家与手判断，
 * 而不是按本地玩家自己的手，因此另一只手以及任何其他被渲染的玩家都保持原版姿势。
 */
public final class M1903A4FirstPersonRules {
    /** Vanilla bow pull time: the draw is full from here on. / 原版弓的拉满时间：此后视为蓄力已满。 */
    public static final int FULL_CHARGE_TICKS = 20;

    private M1903A4FirstPersonRules() {
    }

    /**
     * The rendered stack is the rifle the player is charging in this very hand. / 正在渲染的物品就是玩家正用这只手蓄力的步枪。
     */
    public static boolean chargingRenderedRifle(boolean renderedRifle, boolean usingItem, boolean activeHandIsRendered) {
        return renderedRifle && usingItem && activeHandIsRendered;
    }

    /**
     * The aimed pose: the rendered rifle is being charged in the main hand (the rifle fires from the main hand only).
     * 瞄准姿势：正在渲染的步枪正在主手蓄力（步枪只能主手开火）。
     */
    public static boolean aimPose(boolean renderedRifle, boolean renderedMainHand, boolean usingItem,
                                  boolean activeHandIsRendered) {
        return renderedMainHand && chargingRenderedRifle(renderedRifle, usingItem, activeHandIsRendered);
    }

    /**
     * The bow sway is dropped once the rendered rifle's charge is full; while it is still drawing, vanilla's sway stays.
     * 正在渲染的步枪蓄力已满时取消弓的摆动；仍在拉弓时保留原版摆动。
     */
    public static boolean holdsSteady(boolean renderedRifle, boolean usingItem, boolean activeHandIsRendered,
                                      int useTicks) {
        return chargingRenderedRifle(renderedRifle, usingItem, activeHandIsRendered) && useTicks >= FULL_CHARGE_TICKS;
    }
}
