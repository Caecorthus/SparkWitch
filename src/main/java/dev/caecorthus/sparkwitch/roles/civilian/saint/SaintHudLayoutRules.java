package dev.caecorthus.sparkwitch.roles.civilian.saint;

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
        if ("sparkwitch".equals(roleId.getNamespace()) && hasSparkWitchSkill) {
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
        if ("sparkwitch".equals(roleId.getNamespace()) && hasSparkWitchSkill) {
            return SPARKWITCH_BOTTOM_PADDING;
        }
        return "noellesroles".equals(roleId.getNamespace()) && "assassin".equals(roleId.getPath()) ? 5 : 0;
    }
}
