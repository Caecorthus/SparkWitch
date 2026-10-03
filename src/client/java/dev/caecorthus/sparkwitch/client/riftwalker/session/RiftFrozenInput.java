package dev.caecorthus.sparkwitch.client.riftwalker.session;

import net.minecraft.client.input.Input;

/**
 * Zero-movement input installed on the local body while inside a gate (copy of the Seeker pattern, never shared with
 * it). It never sneaks, so the server never sees Shift (no sneak-based effects) and the spectator body never flies
 * down; it never jumps or moves, so it never flies up or through walls. It extends plain {@link Input}, not
 * {@code KeyboardInput}, so hooks on {@code KeyboardInput#tick} (footsteps) never see it.
 * 在门内时安装在本地身体上的零移动输入（照抄 Seeker 的写法，不与其共用）。它从不潜行，因此服务端看不到 Shift（不触发基于潜行的效果），
 * 旁观者身体也不会下降；它从不跳跃或移动，因此不会上升或穿墙。它继承普通的 {@link Input} 而非 {@code KeyboardInput}，
 * 因此 {@code KeyboardInput#tick} 上的钩子（脚步声）不会作用于它。
 */
public final class RiftFrozenInput extends Input {
    public RiftFrozenInput() {
        clear();
    }

    @Override
    public void tick(boolean slowDown, float slowDownFactor) {
        clear();
    }

    private void clear() {
        movementForward = 0.0F;
        movementSideways = 0.0F;
        pressingForward = false;
        pressingBack = false;
        pressingLeft = false;
        pressingRight = false;
        jumping = false;
        sneaking = false;
    }
}
