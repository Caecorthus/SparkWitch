package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import net.minecraft.util.math.MathHelper;

/**
 * Pure server-side fire decision and aim. Checks run in a fixed order and the first failing one wins; only
 * {@link Decision#NOT_LOADED} gives feedback (a dry click to the shooter), every other refusal is silent.
 * 纯服务端发射判定与朝向。按固定顺序检查，第一个不满足的条件即为结果；只有 {@link Decision#NOT_LOADED} 给出反馈
 * （仅射手可闻的空响），其余拒绝均为静默。
 */
public final class PotionLauncherFireRules {
    private static final float MAX_PITCH = 90.0F;

    private PotionLauncherFireRules() {
    }

    /** Outcome, in check order. / 判定结果，按检查顺序排列。 */
    public enum Decision {
        NOT_RUNNING,
        NOT_PLAYING,
        NOT_POTION_GUNNER,
        NOT_HOLDING,
        SPECTATOR,
        STUNNED,
        SESSION_LOCKED,
        WEAPON_BLOCKED,
        COOLING_DOWN,
        NOT_LOADED,
        FIRE
    }

    /**
     * Side-effect-free facts about one fire request. / 单次发射请求的无副作用事实。
     *
     * @param gameRunning      Wathe's round is running
     * @param playingAndAlive  Wathe {@code isPlayerPlayingAndAlive}
     * @param potionGunner     the shooter's raw role is exactly the Potion Gunner
     * @param holdingLauncher  the main hand holds a launcher
     * @param spectator        the shooter is in spectator mode (e.g. swallowed by the Taotie)
     * @param stunned          stunned by a Control Expert
     * @param sessionLocked    locked in a Seeker remote session
     * @param weaponBlocked    SparkTraits blocks a weapon action with the held launcher
     * @param coolingDown      the launcher's item cooldown is active
     * @param loaded           the held launcher carries a shell
     */
    public record Facts(boolean gameRunning, boolean playingAndAlive, boolean potionGunner, boolean holdingLauncher,
                        boolean spectator, boolean stunned, boolean sessionLocked, boolean weaponBlocked,
                        boolean coolingDown, boolean loaded) {
    }

    public static Decision decide(Facts facts) {
        if (!facts.gameRunning()) {
            return Decision.NOT_RUNNING;
        }
        if (!facts.playingAndAlive()) {
            return Decision.NOT_PLAYING;
        }
        if (!facts.potionGunner()) {
            return Decision.NOT_POTION_GUNNER;
        }
        if (!facts.holdingLauncher()) {
            return Decision.NOT_HOLDING;
        }
        if (facts.spectator()) {
            return Decision.SPECTATOR;
        }
        if (facts.stunned()) {
            return Decision.STUNNED;
        }
        if (facts.sessionLocked()) {
            return Decision.SESSION_LOCKED;
        }
        if (facts.weaponBlocked()) {
            return Decision.WEAPON_BLOCKED;
        }
        if (facts.coolingDown()) {
            return Decision.COOLING_DOWN;
        }
        if (!facts.loaded()) {
            return Decision.NOT_LOADED;
        }
        return Decision.FIRE;
    }

    /** Launch direction in degrees. / 发射方向（角度）。 */
    public record Aim(float yaw, float pitch) {
    }

    /**
     * The client's click-time aim when the payload carries a finite one, else the server rotation (Death Ray rule).
     * Yaw is wrapped to [-180, 180) and pitch clamped to [-90, 90]; the aim only picks the direction.
     * 载荷带有有限朝向时采用客户端点击瞬间的朝向，否则回退到服务端朝向（与死亡射线一致）。偏航角折回 [-180, 180)，
     * 俯仰角夹在 [-90, 90]；朝向只决定方向。
     */
    public static Aim aim(boolean payloadHasAim, float payloadYaw, float payloadPitch, float serverYaw,
                          float serverPitch) {
        float yaw = payloadHasAim ? payloadYaw : serverYaw;
        float pitch = payloadHasAim ? payloadPitch : serverPitch;
        if (!Float.isFinite(yaw)) {
            yaw = 0.0F;
        }
        if (!Float.isFinite(pitch)) {
            pitch = 0.0F;
        }
        return new Aim(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH));
    }
}
