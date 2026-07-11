package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.util.Identifier;

/**
 * Pure rules owned only by the Grand Witch role.
 * 仅由大魔女角色拥有的纯规则，包括仪礼剑、法术和巫毒保护。
 */
public final class GrandWitchRules {
    public static final int CEREMONIAL_SWORD_MANA_COST = 150;
    public static final int CEREMONIAL_SWORD_DURATION_TICKS = GameConstants.getInTicks(0, 15);
    public static final int CEREMONIAL_SWORD_COOLDOWN_TICKS = GameConstants.getInTicks(1, 30);
    public static final int CEREMONIAL_SWORD_INITIAL_COOLDOWN_TICKS = 0;
    public static final int CEREMONIAL_SWORD_UNLOCK_TASKS = 2;
    public static final int CEREMONIAL_SWORD_SPEED_AMPLIFIER = 1;

    private GrandWitchRules() {
    }

    public static int clampCeremonialSwordTaskProgress(int completedTasks) {
        return Math.max(0, Math.min(CEREMONIAL_SWORD_UNLOCK_TASKS, completedTasks));
    }

    public static boolean isCeremonialSwordUnlocked(int completedTasks) {
        return completedTasks >= CEREMONIAL_SWORD_UNLOCK_TASKS;
    }

    /**
     * Blocks only NoellesRoles' Voodoo chain-death reason for Grand Witch.
     * 只阻止 NoellesRoles 巫毒链式死亡对大魔女生效。
     */
    public static boolean shouldBlockVoodooCurse(Role role, Identifier deathReason) {
        return WitchFactionRules.isGrandWitch(role)
                && NoellesRoleIds.VOODOO_CURSE_DEATH_REASON.equals(deathReason);
    }

    public static boolean isShopSpellId(String entryId) {
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
