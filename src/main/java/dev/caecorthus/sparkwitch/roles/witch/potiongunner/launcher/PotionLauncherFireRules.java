package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.util.OffMatchUse;
import net.minecraft.util.math.MathHelper;

/**
 * Pure server-side fire decision and aim. Checks run in a fixed order and the first failing one wins; only
 * {@link Decision#NOT_LOADED} gives feedback (a dry click to the shooter), every other refusal is silent. Use never
 * checks the role or the round (owner rule 2026-10-04, {@link OffMatchUse}): {@link Decision#FIRE} fires in the facts'
 * mode, a full match shot or a presentation-only one.
 * 纯服务端发射判定与朝向。按固定顺序检查，第一个不满足的条件即为结果；只有 {@link Decision#NOT_LOADED} 给出反馈
 * （仅射手可闻的空响），其余拒绝均为静默。使用从不检查职业或对局（所有者规则 2026-10-04，{@link OffMatchUse}）：
 * {@link Decision#FIRE} 按事实中的模式发射，即完整的对局射击或仅表现的射击。
 */
public final class PotionLauncherFireRules {
    private static final float MAX_PITCH = 90.0F;

    private PotionLauncherFireRules() {
    }

    /** Outcome, in check order. / 判定结果，按检查顺序排列。 */
    public enum Decision {
        /** {@link OffMatchUse.Mode#REFUSED}: a dead participant of an ACTIVE match. / ACTIVE 对局中已死亡的参与者。 */
        DEAD_PARTICIPANT,
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
     * @param mode            {@link OffMatchUse#mode(net.minecraft.entity.player.PlayerEntity)}: MATCH for a living
     *                        participant of an exactly {@code ACTIVE} round, PRESENTATION when the round is not
     *                        {@code ACTIVE} (none, {@code STARTING}, {@code STOPPING}) or the shooter has no role;
     *                        REFUSED (or null) never fires
     * @param holdingLauncher the main hand holds a launcher
     * @param spectator       the shooter is in spectator mode (e.g. swallowed by the Taotie)
     * @param stunned         stunned by a Control Expert
     * @param sessionLocked   locked in a Seeker remote session
     * @param weaponBlocked   SparkTraits blocks a weapon action with the held launcher
     * @param coolingDown     the launcher's item cooldown is active
     * @param loaded          the held launcher carries a shell
     */
    public record Facts(OffMatchUse.Mode mode, boolean holdingLauncher, boolean spectator, boolean stunned,
                        boolean sessionLocked, boolean weaponBlocked, boolean coolingDown, boolean loaded) {
    }

    public static Decision decide(Facts facts) {
        if (facts.mode() == null || facts.mode() == OffMatchUse.Mode.REFUSED) {
            return Decision.DEAD_PARTICIPANT;
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
