package dev.caecorthus.sparkwitch.client.seeker.remote;

import net.minecraft.client.input.Input;

/**
 * Zero-movement input installed on the real body while viewing. It never sneaks, so the body neither crouches nor
 * triggers sneak-based effects (SparkTraits Niko), and it never jumps. It extends plain {@link Input}, not
 * {@code KeyboardInput}, so hooks on {@code KeyboardInput#tick} (footsteps) never see it.
 * 观看期间安装在真实本体上的零移动输入。它从不潜行，因此本体既不会蹲下，也不会触发基于潜行的效果（SparkTraits Niko），
 * 并且从不跳跃。它继承普通的 {@link Input} 而非 {@code KeyboardInput}，因此 {@code KeyboardInput#tick} 上的钩子（脚步声）不会作用于它。
 */
public final class SeekerFrozenInput extends Input {
    public SeekerFrozenInput() {
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
