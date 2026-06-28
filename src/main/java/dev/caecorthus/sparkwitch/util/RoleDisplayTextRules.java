package dev.caecorthus.sparkwitch.util;

import dev.doctor4t.wathe.api.Role;

import java.util.Locale;

/**
 * Shared rules for role-name translation keys shown by Wathe HUD surfaces.
 * wathe HUD 显示职业名时共用的翻译键与兜底显示规则。
 */
public final class RoleDisplayTextRules {
    public static final String ROLE_TRANSLATION_PREFIX = "announcement.role.";

    private RoleDisplayTextRules() {
    }

    public static String roleTranslationKey(Role role) {
        return roleTranslationKey(role.identifier().getPath());
    }

    public static String roleTranslationKey(String rolePath) {
        return ROLE_TRANSLATION_PREFIX + normalizeRolePath(rolePath);
    }

    public static String normalizeRoleTranslationKey(String translationKey) {
        if (!translationKey.startsWith(ROLE_TRANSLATION_PREFIX)) {
            return translationKey;
        }
        return roleTranslationKey(translationKey.substring(ROLE_TRANSLATION_PREFIX.length()));
    }

    public static String fallbackRoleName(String translationKey) {
        String normalizedKey = normalizeRoleTranslationKey(translationKey);
        if (!normalizedKey.startsWith(ROLE_TRANSLATION_PREFIX)) {
            return normalizedKey;
        }

        String rolePath = normalizedKey.substring(ROLE_TRANSLATION_PREFIX.length());
        String readable = rolePath
                .replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (readable.isEmpty()) {
            return normalizedKey;
        }
        return toTitleCase(readable);
    }

    private static String normalizeRolePath(String rolePath) {
        return rolePath.replace('/', '.').toLowerCase(Locale.ROOT);
    }

    private static String toTitleCase(String readable) {
        StringBuilder result = new StringBuilder(readable.length());
        boolean uppercaseNext = true;
        for (int i = 0; i < readable.length(); i++) {
            char character = readable.charAt(i);
            if (Character.isWhitespace(character)) {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
                uppercaseNext = true;
            } else if (uppercaseNext) {
                result.append(Character.toUpperCase(character));
                uppercaseNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
