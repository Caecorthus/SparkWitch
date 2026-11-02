package dev.caecorthus.sparkwitch.client.witchmaiden.skin;

/**
 * What a skin animation is played for. {@link #IDLE} and {@link #MOVE} loop underneath; {@link #SUMMON},
 * {@link #SHOOT} and {@link #CRIMSON} play once on top (CRIMSON falls back to SHOOT).
 * 皮肤动画的播放时机。{@link #IDLE} 与 {@link #MOVE} 在底层循环；{@link #SUMMON}、{@link #SHOOT} 与 {@link #CRIMSON}
 * 在上层播放一次（CRIMSON 缺省时使用 SHOOT）。
 */
public enum MiniWitherAnimationState {
    IDLE("idle"),
    MOVE("move"),
    SUMMON("summon"),
    SHOOT("shoot"),
    CRIMSON("crimson");

    private final String key;

    MiniWitherAnimationState(String key) {
        this.key = key;
    }

    /** The manifest {@code states} key. / 清单中 {@code states} 的键名。 */
    public String key() {
        return key;
    }
}
