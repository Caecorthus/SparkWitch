package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

/**
 * Frozen contract: a temporary spherical hole that an explosion punches into every smoke cloud it overlaps. It stays
 * fully open, then closes from the edge inward during its last {@link SmokeTimeline#HOLE_CLOSE_TICKS} ticks
 * (owner 2026-11-05: closes after about 2.5 s). {@code startTime} is {@code World#getTime()} at the explosion.
 * 冻结契约：爆炸在所有相交烟团里炸开的临时球形空洞。先完全敞开，最后 {@link SmokeTimeline#HOLE_CLOSE_TICKS} tick
 * 从边缘向内合上（所有者 2026-11-05：约 2.5 秒后合上）。{@code startTime} 为爆炸时的 {@code World#getTime()}。
 */
public record SmokeHole(double x, double y, double z, float radius, long startTime, int durationTicks) {
    public SmokeHole {
        if (!(radius > 0.0F) || durationTicks <= 0) {
            throw new IllegalArgumentException("bad smoke hole");
        }
    }

    public boolean expired(long worldTime) {
        return worldTime - startTime >= durationTicks;
    }
}
