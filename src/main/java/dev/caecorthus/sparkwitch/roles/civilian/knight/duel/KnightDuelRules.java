package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** All Knight duel numbers and ids. / 骑士决斗的全部数值与 id。 */
public final class KnightDuelRules {
    public static final int COST = 150;
    public static final int DUEL_TICKS = 20 * 120;
    public static final int ITEM_COOLDOWN_TICKS = DUEL_TICKS;
    public static final int REVOLVER_COOLDOWN_TICKS = DUEL_TICKS;
    public static final int TITLE_FADE_IN = 10;
    public static final int TITLE_STAY = 40;
    public static final int TITLE_FADE_OUT = 40;
    public static final Formatting KNIGHT_COLOR = Formatting.AQUA;
    public static final Formatting TARGET_COLOR = Formatting.RED;
    public static final String KNIGHT_TEAM = "sparkwitch_duel_knight";
    public static final String TARGET_TEAM = "sparkwitch_duel_target";
    public static final Identifier SURVIVAL_MASTER_ID = Identifier.of("noellesroles", "survival_master");
    public static final Identifier MASTER_KEY_ID = Identifier.of("noellesroles", "master_key");
    public static final Identifier NEUTRAL_MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");
    public static final Identifier MANBA_SOUND_ID = SparkWitch.id("duel.manba");
    public static final Identifier SF_SOUND_ID = SparkWitch.id("duel.sf");

    private KnightDuelRules() {
    }

    /** Third, vanilla-synced duel marker: membership in either duel team. / 第三个原版同步标记：身处任一决斗队伍。 */
    public static boolean isDuelTeam(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        var team = player.getScoreboardTeam();
        if (team == null) {
            return false;
        }
        String name = team.getName();
        return name.equals(KNIGHT_TEAM) || name.equals(TARGET_TEAM);
    }
}
