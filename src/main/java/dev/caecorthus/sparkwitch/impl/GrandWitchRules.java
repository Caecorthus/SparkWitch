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
    public static final int DIRECT_KILL_MONEY_REWARD = 0;
    public static final int WITCH_TEAM_KILL_MONEY_REWARD = 25;

    public static final int CEREMONIAL_SWORD_MANA_COST = 150;
    public static final int CEREMONIAL_SWORD_DURATION_TICKS = GameConstants.getInTicks(0, 15);
    public static final int CEREMONIAL_SWORD_COOLDOWN_TICKS = GameConstants.getInTicks(1, 30);
    public static final int CEREMONIAL_SWORD_INITIAL_COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    public static final int CEREMONIAL_SWORD_SPEED_AMPLIFIER = 0;

    public static final int OTHER_WITCH_INSTINCT_COLOR = 0x7AB8FF;
    public static final int NON_WITCH_INSTINCT_COLOR = 0x36E51B;
    public static final int DROPPED_ITEM_INSTINCT_COLOR = 0xDB9D00;
    // Ordinary Witch instinct must stay below Wathe hard skips such as Last Stand and hidden Survival Master.
    // 普通魔女本能必须低于 wathe 硬跳过规则，例如背水一战和被遮挡的生存大师。
    public static final int INSTINCT_PRIORITY = 90;

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

    public static OptionalInt droppedItemInstinctColor(Role viewerRole) {
        return isWitchFactionMember(viewerRole)
                ? OptionalInt.of(DROPPED_ITEM_INSTINCT_COLOR)
                : OptionalInt.empty();
    }

    /**
     * Grand Witch faction highlights are living-player instincts; dead spectators use Wathe defaults.
     * 大魔女阵营高亮只属于存活玩家本能；死亡旁观者使用 wathe 默认逻辑。
     */
    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive, boolean viewerSpectatingOrCreative) {
        return viewerAlive && !viewerSpectatingOrCreative;
    }

    public static boolean shouldUseCustomInstinctHighlight(boolean viewerAlive) {
        return shouldUseCustomInstinctHighlight(viewerAlive, false);
    }

    /**
     * Obscure blocks only active non-Witch instinct users; spectators keep Wathe information vision.
     * 障眼只遮蔽正在游玩的非魔女本能使用者；旁观者保留 wathe 信息透视。
     */
    public static boolean shouldObscureInstinct(
            boolean instinctObscured,
            Role viewerRole,
            boolean viewerAlive,
            boolean viewerSpectatingOrCreative
    ) {
        return shouldUseCustomInstinctHighlight(viewerAlive, viewerSpectatingOrCreative)
                && instinctObscured
                && isAffectedByWitchAreaSpell(viewerRole);
    }

    public static boolean isOtherWitchRole(Role role) {
        return role != null && (role == SparkWitchRoles.murderousWitch() || role == SparkWitchRoles.apprenticeWitch());
    }

    public static boolean isAffectedByWitchAreaSpell(Role role) {
        return !isWitchFactionMember(role);
    }

    public static boolean isAffectedByFear(Role role) {
        return role != null && isAffectedByWitchAreaSpell(role);
    }

    /**
     * Blocks only NoellesRoles' Voodoo chain-death reason for Grand Witch.
     * 只阻止 NoellesRoles 巫毒链式死亡对大魔女生效。
     */
    public static boolean shouldBlockVoodooCurse(Role role, Identifier deathReason) {
        return isGrandWitch(role) && NoellesRoleIds.VOODOO_CURSE_DEATH_REASON.equals(deathReason);
    }

    public static OptionalInt instinctColor(Role viewerRole, Role targetRole) {
        if (isGrandWitch(viewerRole)) {
            if (targetRole == SparkWitchRoles.grandWitch()) {
                return OptionalInt.of(SparkWitchRoles.grandWitch().color());
            }
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
            if (targetRole == SparkWitchRoles.accomplice()) {
                return OptionalInt.of(SparkWitchRoles.accomplice().color());
            }
            return OptionalInt.of(NON_WITCH_INSTINCT_COLOR);
        }
        return OptionalInt.empty();
    }

    public static Boolean economyDecision(Role role, FactionEconomyPolicy.RewardKind rewardKind) {
        if (isGrandWitch(role)) {
            if (rewardKind == FactionEconomyPolicy.RewardKind.DIRECT_KILL) {
                return false;
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

    /**
     * Grand Witch kills now pay only the living Accomplice teammate.
     * 大魔女击杀现在只给存活的共犯队友发钱。
     */
    public static boolean shouldAwardWitchTeamKillMoney(
            Role killerRole,
            Role teammateRole,
            boolean samePlayer,
            boolean teammateAlive
    ) {
        return isGrandWitch(killerRole)
                && isAccomplice(teammateRole)
                && !samePlayer
                && teammateAlive;
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
