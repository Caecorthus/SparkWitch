package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkfactionapi.api.FactionEconomyPolicy;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

import java.util.OptionalInt;

/**
 * Pure Witch faction rule constants and predicates.
 * 魔女阵营的纯规则集中在这里，事件和 UI 只读取这些判断。
 */
public final class GrandWitchRules {
    public static final int STARTING_MONEY = 0;
    public static final int DIRECT_KILL_MONEY_REWARD = 100;
    public static final int WITCH_TEAM_KILL_MONEY_REWARD = 25;

    public static final int CEREMONIAL_SWORD_MANA_COST = 100;
    public static final int CEREMONIAL_SWORD_DURATION_TICKS = GameConstants.getInTicks(0, 10);
    public static final int CEREMONIAL_SWORD_COOLDOWN_TICKS = GameConstants.getInTicks(1, 30);

    public static final int OTHER_WITCH_INSTINCT_COLOR = 0x7AB8FF;
    public static final int NON_WITCH_INSTINCT_COLOR = 0x36E51B;

    private GrandWitchRules() {
    }

    public static boolean isGrandWitch(Role role) {
        return role != null && role == SparkWitchRoles.grandWitch();
    }

    public static boolean isAccomplice(Role role) {
        return role != null && role == SparkWitchRoles.accomplice();
    }

    public static boolean isWitchFactionMember(Role role) {
        return role != null && (role == SparkWitchRoles.grandWitch() || role == SparkWitchRoles.accomplice());
    }

    public static boolean usesKillerStyleInstinctLight(Role role) {
        return isWitchFactionMember(role);
    }

    /**
     * Grand Witch faction highlights are living-player instincts; dead spectators use Wathe defaults.
     * 大魔女阵营高亮只属于存活玩家本能；死亡旁观者使用 wathe 默认逻辑。
     */
    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive) {
        return viewerAlive;
    }

    public static boolean isOtherWitchRole(Role role) {
        return role != null && (role == SparkWitchRoles.murderousWitch() || role == SparkWitchRoles.apprenticeWitch());
    }

    public static boolean isAffectedByWitchAreaSpell(Role role) {
        return !isWitchFactionMember(role);
    }

    public static boolean isAffectedByFear(Role role) {
        return isAffectedByWitchAreaSpell(role) && role != null && role.getMoodType() == Role.MoodType.REAL;
    }

    public static OptionalInt instinctColor(Role viewerRole, Role targetRole) {
        if (isGrandWitch(viewerRole)) {
            if (targetRole == SparkWitchRoles.accomplice()) {
                return OptionalInt.of(SparkWitchRoles.accomplice().color());
            }
            if (isOtherWitchRole(targetRole)) {
                return OptionalInt.of(OTHER_WITCH_INSTINCT_COLOR);
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        if (isAccomplice(viewerRole)) {
            if (targetRole == SparkWitchRoles.grandWitch()) {
                return OptionalInt.of(SparkWitchRoles.grandWitch().color());
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        return OptionalInt.empty();
    }

    public static Boolean economyDecision(Role role, FactionEconomyPolicy.RewardKind rewardKind) {
        if (isGrandWitch(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL) {
                return true;
            }
            if (rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return false;
            }
            return null;
        }
        if (isAccomplice(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL
                    || rewardKind == FactionEconomyPolicy.RewardKind.PASSIVE) {
                return true;
            }
        }
        return null;
    }

    public static boolean isSparkWitchShopSpellId(String entryId) {
        return GrandWitchSpell.fromEntryId(entryId) != null;
    }

    public enum GrandWitchSpell {
        OBSCURE("sparkwitch_obscure", 80, GameConstants.getInTicks(0, 30), GameConstants.getInTicks(2, 0)),
        BLINDNESS("sparkwitch_blindness", 80, GameConstants.getInTicks(0, 20), GameConstants.getInTicks(3, 0)),
        FEAR("sparkwitch_fear", 50, GameConstants.getInTicks(0, 10), GameConstants.getInTicks(5, 0)),
        HEAVINESS("sparkwitch_heaviness", 60, GameConstants.getInTicks(0, 10), GameConstants.getInTicks(3, 0));

        private final String entryId;
        private final int manaCost;
        private final int durationTicks;
        private final int cooldownTicks;

        GrandWitchSpell(String entryId, int manaCost, int durationTicks, int cooldownTicks) {
            this.entryId = entryId;
            this.manaCost = manaCost;
            this.durationTicks = durationTicks;
            this.cooldownTicks = cooldownTicks;
        }

        public String entryId() {
            return entryId;
        }

        public int manaCost() {
            return manaCost;
        }

        public int durationTicks() {
            return durationTicks;
        }

        public int cooldownTicks() {
            return cooldownTicks;
        }

        public Identifier id() {
            return SparkWitch.id(path());
        }

        public String path() {
            return entryId.substring("sparkwitch_".length());
        }

        public String translationKey() {
            return "shop.sparkwitch." + path();
        }

        /**
         * Player-facing description key for the Grand Witch mana shop.
         * 大魔女魔力商店中展示给玩家看的功能描述翻译键。
         */
        public String descriptionTranslationKey() {
            return translationKey() + ".description";
        }

        public static GrandWitchSpell fromEntryId(String entryId) {
            for (GrandWitchSpell spell : values()) {
                if (spell.entryId.equals(entryId)) {
                    return spell;
                }
            }
            return null;
        }
    }
}
