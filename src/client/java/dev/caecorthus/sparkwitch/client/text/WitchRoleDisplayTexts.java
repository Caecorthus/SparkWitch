package dev.caecorthus.sparkwitch.client.text;

import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

/**
 * Resolves role names for Wathe HUD hooks without leaking raw translation keys.
 * 为 wathe HUD 钩子解析职业名，避免把未翻译 key 直接显示给玩家。
 */
public final class WitchRoleDisplayTexts {
    private WitchRoleDisplayTexts() {
    }

    public static MutableText roleName(String translationKey) {
        String normalizedKey = RoleDisplayTextRules.normalizeRoleTranslationKey(translationKey);
        if (Language.getInstance().hasTranslation(normalizedKey)) {
            return Text.translatable(normalizedKey);
        }
        return Text.literal(RoleDisplayTextRules.fallbackRoleName(normalizedKey));
    }
}
