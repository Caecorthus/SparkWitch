package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;

/**
 * Riftwalker (隙行者) constants and pure predicates: a witch-faction special accomplice who places Rift Gates and owns
 * the Witches' Sabbath skill. Every tuning value lives here (frozen by G0, see {@code briefs/G0-contracts.md}) so work
 * packages never redefine a number. Server-authoritative; the client only mirrors values it is sent.
 * 隙行者（魔女阵营特殊共犯，放置裂隙门并拥有「魔女集会」技能）的常量与纯判定。所有数值集中于此（由 G0 冻结，见
 * {@code briefs/G0-contracts.md}），各工作包不得重复定义。服务端权威；客户端只镜像收到的值。
 */
public final class RiftwalkerRules {
    public static final Identifier ROLE_ID = SparkWitch.id("riftwalker");
    /** Role theme color (indigo, D1); distinct from every other accomplice color. / 职业主题色（靛蓝，D1）。 */
    public static final int COLOR = 0x5B6CFF;

    // --- Rift Gate item, entity and shop entry / 裂隙门物品、实体与商店条目 ---
    public static final Identifier GATE_ITEM_ID = SparkWitch.id("rift_gate");
    public static final Identifier GATE_ENTITY_ID = SparkWitch.id("rift_gate");
    /**
     * Wathe {@code ShopEntry.id()} of the gate entry; prefixed so it never collides with a Grand Witch spell id.
     * 裂隙门商品的 Wathe {@code ShopEntry.id()}；带前缀，避免与大魔女法术 id 冲突。
     */
    public static final String GATE_SHOP_ENTRY_ID = "sparkwitch_rift_gate";
    /** Mana per gate, charged inside the 0-gold entry's onBuy. / 每扇门的魔力价格，在 0 金币商品的 onBuy 中扣除。 */
    public static final int GATE_MANA_COST = 50;
    public static final int GATE_MAX_STACK = 16;

    // --- Witches' Sabbath (role skill, shared skill key) / 魔女集会（职业技能，共享技能键） ---
    public static final Identifier SABBATH_SKILL_ID = SparkWitch.id("witches_sabbath");
    /** SparkFactionAPI action id checked per pulled teammate. / 每名被召集队友校验的 SparkFactionAPI 行为 id。 */
    public static final Identifier SABBATH_ACTION_ID = SparkWitch.id("riftwalker_sabbath");
    /** The definition only displays it; the use handler spends it. / 技能定义只用于显示，由使用处理器实际扣除。 */
    public static final int SABBATH_MANA_COST = 150;
    /**
     * Owner 2026-10-05 (replaces D6's "no cooldown"): 30 s after each successful cast; none at round start, since the
     * Riftwalker starts at 0 mana.
     * 所有者 2026-10-05（取代 D6 的「无冷却」）：每次成功施放后冷却 30 秒；开局无冷却（隙行者从 0 魔力开始）。
     */
    public static final int SABBATH_INITIAL_COOLDOWN_TICKS = 0;
    public static final int SABBATH_COOLDOWN_TICKS = 30 * 20;

    // --- Gate users (D5, C1) / 门的使用者（D5、C1） ---
    /** Apprentice and Murderous Witches pay this per ENTRY; hops inside are free. / 预备魔女、杀意魔女每次进门支付。 */
    public static final int OTHER_WITCH_ENTRY_FEE = 100;
    /** Maximum stay inside (D5b), in ticks. / 门内最长停留（D5b），单位 tick。 */
    public static final int STAY_RIFTWALKER = 30 * 20;
    public static final int STAY_FACTION = 20 * 20;
    public static final int STAY_MURDEROUS_WITCH = 15 * 20;
    public static final int STAY_APPRENTICE_WITCH = 10 * 20;
    /** Re-entry cooldown after any exit except round end (D14), in ticks. / 出门后的再次进门冷却（D14）。 */
    public static final int REENTRY_COOLDOWN_RIFTWALKER = 30 * 20;
    public static final int REENTRY_COOLDOWN_OTHERS = 45 * 20;
    /** D11: 0.5 s between hops. / D11：跳门节流 0.5 秒。 */
    public static final int HOP_THROTTLE_TICKS = 10;

    // --- Gate geometry and projectiles / 门的几何与投掷物 ---
    public static final int PROJECTILE_MAX_GATE_PASSES = 3;
    /** Minimum distance between two gates (blocks). / 两扇门之间的最小距离（格）。 */
    public static final double MIN_GATE_SPACING = 3.0;
    public static final float GATE_WIDTH = 1.0F;
    public static final float GATE_HEIGHT = 2.0F;
    /** Thickness along the facing axis. / 沿朝向轴的厚度。 */
    public static final float GATE_DEPTH = 0.25F;
    /** Right-click reach for entering a gate (blocks). / 右键进门的距离（格）。 */
    public static final double ENTRY_REACH = 3.0;
    /** Entity tracking: 8 chunks, static, like the Seeker camera. / 实体追踪：8 区块、静态，与搜寻者摄像头相同。 */
    public static final int GATE_TRACKING_RANGE = 8;
    public static final int GATE_TRACKING_INTERVAL = 20;

    // --- Swapper crush (D13, C4) / 交换者夹死（D13、C4） ---
    public static final Identifier PORTAL_CRUSHED_DEATH_REASON = SparkWitch.id("portal_crushed");

    private RiftwalkerRules() {
    }

    public static boolean isRiftwalkerId(Identifier roleId) {
        return ROLE_ID.equals(roleId);
    }

    /** Exact role, by id (never the Black Raven acting role). / 精确职业，按 id 判定（从不使用黑羽鸦伪装职业）。 */
    public static boolean isRiftwalker(Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }
}
