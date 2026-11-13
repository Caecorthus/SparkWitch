package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.payloadpolicy.GeneratedPayloadPolicy;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Side-neutral input-lock rules for the Control Expert stun.
 * 控场专家眩晕输入锁的两端通用规则。
 */
public final class ControlExpertStunRules {
    /**
     * Simple Voice Chat's key category. Push-to-talk reads raw input anyway; this keeps its toggle keys usable.
     * Simple Voice Chat 的按键分类。按键通话本就读取原始输入；此项让其开关类按键仍可使用。
     */
    public static final String EXEMPT_KEY_CATEGORY = "key.categories.voicechat";

    /**
     * Owner switch: false lets the camera rotate during the stun; true also freezes mouse look.
     * 所有者开关：false 时眩晕期间视角仍可转动；true 时同时冻结鼠标视角。
     */
    public static final boolean LOCK_CAMERA = false;

    /**
     * Stable contract: C2S payloads dropped while the sender is stunned — lethal Wathe actions, the shop, and every
     * known role-skill payload. Kept as plain ids so no optional mod class is loaded. UI-only payloads (notes, map
     * vote, walkie channel, grenade cancel, tablet chat/snapshot/channel, detective case notes, the Timekeeper's
     * watch-mode pick, whose use is an item use the stun already denies) stay allowed.
     * A new skill payload must be classified deliberately, as a complete row of the canonical table (below).
     * 稳定契约：发送者处于眩晕时丢弃的 C2S 数据包——Wathe 致命行为、商店以及所有已知职业技能包。
     * 以纯 id 保存，不加载任何可选模组的类。仅界面用途的数据包（笔记、地图投票、对讲频道、手雷取消、
     * 平板聊天/快照/频道、侦探案件笔记、计时员怀表模式切换——其使用是眩晕已拒绝的物品使用）仍然放行。
     * 新增技能包必须作为规范表中完整的一行有意识地归类（见下）。
     */
    // Generated STUN column of SparkFactionAPI config/payload-policy/actions.tsv: edit that table and
    // rerun tools/payload_policy.py, never list ids here; per-id reasons are in its note column.
    // 生成的 STUN 列（SparkFactionAPI config/payload-policy/actions.tsv）：请修改该表并重新运行
    // tools/payload_policy.py，不要在此列出 id；各 id 的理由见其 note 列。
    public static final Set<Identifier> BLOCKED_PAYLOADS = GeneratedPayloadPolicy.STUN;

    private ControlExpertStunRules() {
    }

    public static boolean isBlockedPayload(@Nullable Identifier payloadId) {
        return payloadId != null && BLOCKED_PAYLOADS.contains(payloadId);
    }

    public static boolean isExemptKeyCategory(@Nullable String category) {
        return EXEMPT_KEY_CATEGORY.equals(category);
    }
}
