package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.caecorthus.sparkwitch.roles.killer.specter.SpecterRules;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the Saint line above the bottom-right HUDs shipped by the bundled NoellesRoles version.
 * 让圣徒提示避开当前内置 NoellesRoles 版本的右下角职业 HUD。
 */
public final class SaintHudLayoutRules {
    private static final int LINE_GAP = 2;
    private static final int SPARKWITCH_BOTTOM_PADDING = 5;

    private SaintHudLayoutRules() {
    }

    public static int reservedBottomHeight(
            @Nullable Identifier roleId,
            int fontHeight,
            boolean hasSparkWitchSkill
    ) {
        if (roleId == null) {
            return 0;
        }
        if (ownsTwoCornerRows(roleId)) {
            return fontHeight * 2 + LINE_GAP + SPARKWITCH_BOTTOM_PADDING;
        }
        // The Medium draws two corner rows too: 通灵 above, 幽灵的加护 below (招魂 lives in the inventory drawer and
        // has no row); both lift above the hotbar column at narrow widths like the Charon's.
        // 通灵师同样在右下角绘制两行：上行为通灵，下行为幽灵的加护（招魂位于背包抽屉中，没有角落行）；GUI 较窄时两行与卡戎
        // 一样抬升到快捷栏列之上。
        if (MediumRules.ROLE_ID.equals(roleId)) {
            return fontHeight * 2 + LINE_GAP + SPARKWITCH_BOTTOM_PADDING;
        }
        if ("sparkwitch".equals(roleId.getNamespace()) && (hasSparkWitchSkill || ownsCornerSkillLine(roleId))) {
            return fontHeight + SPARKWITCH_BOTTOM_PADDING;
        }
        if (!"noellesroles".equals(roleId.getNamespace())) {
            return 0;
        }

        return switch (roleId.getPath()) {
            case "assassin" -> fontHeight * 3 + LINE_GAP * 2 + 5;
            case "taotie" -> fontHeight * 3 + LINE_GAP * 2;
            case "morphling", "reporter" -> fontHeight * 2;
            case "corrupt_cop", "detective", "noisemaker", "party_animal", "pathogen",
                    "phantom", "recaller", "shadow_jester", "silencer", "spiritualist", "vulture" -> fontHeight;
            default -> 0;
        };
    }

    public static int drawY(int screenHeight, int lineHeight, int reservedBottomHeight) {
        int gap = reservedBottomHeight > 0 ? LINE_GAP : 0;
        return screenHeight - reservedBottomHeight - lineHeight - gap;
    }

    public static int rightPadding(@Nullable Identifier roleId, boolean hasSparkWitchSkill) {
        if (roleId == null) {
            return 0;
        }
        if ("sparkwitch".equals(roleId.getNamespace()) && (hasSparkWitchSkill || ownsCornerSkillLine(roleId))) {
            return SPARKWITCH_BOTTOM_PADDING;
        }
        return "noellesroles".equals(roleId.getNamespace()) && "assassin".equals(roleId.getPath()) ? 5 : 0;
    }

    /**
     * SparkWitch roles without a witch skill that still draw their own skill line in the shared corner slot (5 px
     * padding, one font row): the Ragpicker, the Dominator (always on, PR #192) and the Savior (G-skill line, PR #200);
     * the Specter (PR #207), the Charon (PR #202), 橘雪莉 (PR #206) and 二阶堂希罗 share the padding but reserve two rows
     * above ({@link #ownsTwoCornerRows}). Reserved by role so the Karma line any player can carry sits above it.
     * 没有魔女技能、却在共享右下角位置绘制自有技能行（5 像素边距、一行字高）的 SparkWitch 职业：拾荒者、支配者（常驻显示，
     * PR #192）与救世主（G 技能行，PR #200）；幽魂（PR #207）、卡戎（PR #202）、橘雪莉（PR #206）与二阶堂希罗共用该边距，但向上
     * 预留两行（{@link #ownsTwoCornerRows}）。按职业预留，使任何玩家都可能带有的业障行位于其上方。
     */
    private static boolean ownsCornerSkillLine(Identifier roleId) {
        return JunkerRules.ROLE_ID.equals(roleId)
                || DominatorRules.ROLE_ID.equals(roleId)
                || SaviorRules.ROLE_ID.equals(roleId)
                || MediumRules.ROLE_ID.equals(roleId)
                || ownsTwoCornerRows(roleId);
    }

    /**
     * Roles that draw two corner rows: the Specter (PR #207: skill one below, the Otherworld state above), the
     * Charon (PR #202, {@code client/ferryman/FerrymanHud}: the pardon above, the ferry below; both rows lift together
     * above the hotbar column at narrow GUI widths, which still leaves the reserved corner free), 橘雪莉 (PR #206,
     * {@code client/sherry/SherryHud}: 推演 above, her own 巨力魔法 below, with the Charon's layout and lift) and
     * 二阶堂希罗 ({@code client/hud/HiroHudRenderer}: 魔女化 above, 死亡回归 below).
     * 绘制两行右下角提示的职业：幽魂（PR #207：下行技能一，上行里世界状态）、卡戎（PR #202，
     * {@code client/ferryman/FerrymanHud}：上行宽恕，下行摆渡；GUI 较窄时两行一同抬升到快捷栏列之上，预留的角落依然空着）、
     * 橘雪莉（PR #206，{@code client/sherry/SherryHud}：上行推演，下行她自己的巨力魔法，布局与抬升同卡戎）
     * 与二阶堂希罗（{@code client/hud/HiroHudRenderer}：上行魔女化，下行死亡回归）。
     */
    private static boolean ownsTwoCornerRows(Identifier roleId) {
        return SpecterRules.ROLE_ID.equals(roleId) || FerrymanRules.ROLE_ID.equals(roleId)
                || SherryRules.ROLE_ID.equals(roleId) || HiroRules.ROLE_ID.equals(roleId);
    }
}
