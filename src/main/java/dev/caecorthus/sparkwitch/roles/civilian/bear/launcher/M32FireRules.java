package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import dev.caecorthus.sparkwitch.util.OffMatchUse;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pure server-side M32A1 fire decision and aim. Checks run in a fixed order and the first failing one wins; only
 * {@link Decision#EMPTY} gives feedback (a dry click the shooter hears; no cooldown starts), every other refusal is
 * silent. Use never checks the role (owner 2026-11-05: anyone holding it may fire, the {@link OffMatchUse} rule of the
 * anti-tank launcher): {@link Decision#FIRE} fires in the facts' mode, a full match shot or a presentation-only one
 * (projectile and sound, never a cloud). The Rift occupant, Knight duel and Natsume locks drop the payload by id before
 * it reaches the service, so they are not facts here.
 * 纯服务端 M32A1 开火判定与朝向。按固定顺序检查，第一个不满足的条件即为结果；只有 {@link Decision#EMPTY} 给出反馈（射手可闻的
 * 空响；不开始冷却），其余拒绝均为静默。使用从不检查职业（所有者 2026-11-05：任何持有者都能开火，即反坦克炮筒的
 * {@link OffMatchUse} 规则）：{@link Decision#FIRE} 按事实中的模式发射，即完整的对局射击或仅表现的射击（只有投射物与声音，
 * 从不产生烟雾）。隙行者门内、骑士决斗与夏目锁定在数据包到达服务之前就按 id 丢弃它，因此不在此列为事实。
 */
public final class M32FireRules {
    private static final float MAX_PITCH = 90.0F;

    private M32FireRules() {
    }

    /** Outcome, in check order. / 判定结果，按检查顺序排列。 */
    public enum Decision {
        /** {@link OffMatchUse.Mode#REFUSED}: a dead participant of an ACTIVE match. / ACTIVE 对局中已死亡的参与者。 */
        DEAD_PARTICIPANT,
        NOT_HOLDING,
        SPECTATOR,
        STUNNED,
        SESSION_LOCKED,
        KIDNAPPED,
        WRAITH,
        WEAPON_BLOCKED,
        COOLING_DOWN,
        EMPTY,
        FIRE
    }

    /**
     * Side-effect-free facts about one fire request. / 单次开火请求的无副作用事实。
     *
     * @param mode          {@link OffMatchUse#mode(net.minecraft.entity.player.PlayerEntity)}; REFUSED or null never
     *                      fires / REFUSED 或 null 永不开火
     * @param holding       the main hand holds an M32A1 / 主手持 M32A1
     * @param spectator     spectator game mode (Rift Gate occupant, swallowed, Wathe-dead) / 旁观模式
     * @param stunned       Control Expert stun / 控场专家眩晕
     * @param sessionLocked Seeker remote session / 搜寻者遥控会话
     * @param kidnapped     under Kidnapper control / 被绑匪控制
     * @param activeWraith  an active Wraith / 激活的冤魂
     * @param weaponBlocked SparkTraits blocks a weapon action with the held launcher / SparkTraits 封锁持该发射器的武器操作
     * @param coolingDown   the launcher's item cooldown is running / 发射器物品冷却中
     * @param loaded        the cylinder holds at least one round / 转轮至少装有一发
     */
    public record Facts(OffMatchUse.Mode mode, boolean holding, boolean spectator, boolean stunned,
                        boolean sessionLocked, boolean kidnapped, boolean activeWraith, boolean weaponBlocked,
                        boolean coolingDown, boolean loaded) {
    }

    public static Decision decide(Facts facts) {
        if (facts.mode() == null || facts.mode() == OffMatchUse.Mode.REFUSED) {
            return Decision.DEAD_PARTICIPANT;
        }
        if (!facts.holding()) {
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
        if (facts.kidnapped()) {
            return Decision.KIDNAPPED;
        }
        if (facts.activeWraith()) {
            return Decision.WRAITH;
        }
        if (facts.weaponBlocked()) {
            return Decision.WEAPON_BLOCKED;
        }
        if (facts.coolingDown()) {
            return Decision.COOLING_DOWN;
        }
        if (!facts.loaded()) {
            return Decision.EMPTY;
        }
        return Decision.FIRE;
    }

    /**
     * Unit launch direction: the client's click-time aim when the payload carries a finite one, else the server
     * rotation (Death Ray rule). Yaw is wrapped and pitch clamped to [-90, 90]; the client is trusted for direction
     * only, the origin is always the server's own eye.
     * 单位发射方向：载荷带有有限朝向时采用客户端点击瞬间的朝向，否则回退到服务端朝向（与死亡射线一致）。偏航角折回，俯仰角夹在
     * [-90, 90]；客户端只被信任于方向，起点始终是服务端自身的眼部位置。
     */
    public static Vec3d aimDirection(boolean payloadHasAim, float payloadYaw, float payloadPitch, float serverYaw,
                                     float serverPitch) {
        float yaw = payloadHasAim ? payloadYaw : serverYaw;
        float pitch = payloadHasAim ? payloadPitch : serverPitch;
        if (!Float.isFinite(yaw)) {
            yaw = 0.0F;
        }
        if (!Float.isFinite(pitch)) {
            pitch = 0.0F;
        }
        return Vec3d.fromPolar(MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH), MathHelper.wrapDegrees(yaw)).normalize();
    }

    /**
     * The exact floor write of the 1 s shot cooldown ({@code UsecCooldowns.write} rule): {@code max(existing,
     * requested)}; a longer running lock (Saint Karma, a SparkFactionAPI forced lock, the Fiend aura) is never
     * shortened. Returns the ticks to write, or 0 when nothing new is written.
     * 1 秒射击冷却的精确取大写入（{@code UsecCooldowns.write} 规则）：{@code max(既有, 请求)}；绝不缩短正在运行的更长锁定（圣徒
     * 业报、SparkFactionAPI 强制锁定、魔人光环）。返回应写入的刻数；无需写入新值时为 0。
     */
    public static int floorWrite(int existingTicks, int requestedTicks) {
        int existing = Math.max(0, existingTicks);
        int merged = Math.max(existing, Math.max(0, requestedTicks));
        return merged > existing ? merged : 0;
    }
}
