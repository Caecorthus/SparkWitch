package dev.caecorthus.sparkwitch.roles.civilian.saint;

import dev.caecorthus.sparkwitch.roles.civilian.junker.JunkerRules;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorRules;
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
     * padding, one font row): the Ragpicker and the Dominator (always on, PR #192); the Specter (PR #207) and the
     * Charon (PR #202) share the padding but reserve two rows above ({@link #ownsTwoCornerRows}). Reserved by role so
     * the Karma line any player can carry sits above it.
     * 没有魔女技能、却在共享右下角位置绘制自有技能行（5 像素边距、一行字高）的 SparkWitch 职业：拾荒者与支配者（常驻显示，
     * PR #192）；幽魂（PR #207）与卡戎（PR #202）共用该边距，但向上预留两行（{@link #ownsTwoCornerRows}）。按职业预留，使
     * 任何玩家都可能带有的业障行位于其上方。
     */
    private static boolean ownsCornerSkillLine(Identifier roleId) {
        return JunkerRules.ROLE_ID.equals(roleId)
                || DominatorRules.ROLE_ID.equals(roleId)
                || ownsTwoCornerRows(roleId);
    }

    /**
     * Roles that draw two corner rows: the Specter (PR #207: skill one below, the Otherworld state above) and the
     * Charon (PR #202, {@code client/ferryman/FerrymanHud}: the pardon above, the ferry below; both rows lift together
     * above the hotbar column at narrow GUI widths, which still leaves the reserved corner free).
     * 绘制两行右下角提示的职业：幽魂（PR #207：下行技能一，上行里世界状态）与卡戎（PR #202，
     * {@code client/ferryman/FerrymanHud}：上行宽恕，下行摆渡；GUI 较窄时两行一同抬升到快捷栏列之上，预留的角落依然空着）。
     */
    private static boolean ownsTwoCornerRows(Identifier roleId) {
        return SpecterRules.ROLE_ID.equals(roleId) || FerrymanRules.ROLE_ID.equals(roleId);
    }
}
