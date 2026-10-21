package dev.caecorthus.sparkwitch.client.blackraven.crow;

import net.minecraft.client.input.Input;

/**
 * Zero-movement input installed on the Raven's body while it flies the crow: the body never walks, jumps or sneaks. It
 * extends plain {@link Input}, not {@code KeyboardInput}, so footstep hooks on {@code KeyboardInput#tick} never see it.
 * 黑羽鸦操控乌鸦期间安装在本体上的零移动输入：本体从不行走、跳跃或潜行。它继承普通的 {@link Input} 而非
 * {@code KeyboardInput}，因此 {@code KeyboardInput#tick} 上的脚步声钩子不会作用于它。
 */
public final class BlackRavenCrowFrozenInput extends Input {
    public BlackRavenCrowFrozenInput() {
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
