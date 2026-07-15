package dev.caecorthus.sparkwitch.roles.killer.hunter;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Pure Hunter tuning and role-id based compatibility rules.
 * 猎人的纯数值与基于身份 ID 的兼容规则集中在这里。
 */
public final class HunterRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "hunter");
    public static final int COLOR = 0x5C4C34;

    public static final int SHOTGUN_PRICE = 100;
    public static final int SHELL_PRICE = 125;
    public static final int TRAP_PRICE = 75;
    public static final int SHOTGUN_STOCK = 1;

    public static final int MAX_SHELLS = 2;
    public static final double SHOTGUN_RANGE = 8.0D;
    public static final int FOLLOW_UP_COOLDOWN_TICKS = 4;
    public static final int EMPTY_COOLDOWN_TICKS = 20 * 30;
    public static final int SECOND_SHELL_WINDOW_TICKS = 20 * 10;

    public static final int MAX_OWNED_TRAPS = 2;
    public static final int TRAP_ARM_TICKS = 10;
    public static final int TRAP_LIFESPAN_TICKS = 20 * 60 * 10;
    public static final int TRAP_ROOT_TICKS = 20 * 3;
    public static final int FRACTURE_LAYER_TICKS = 20 * 60;
    public static final int MAX_FRACTURE_LAYERS = 5;
    public static final double SLOW_PER_FRACTURE_LAYER = 0.20D;
    public static final int TRAP_POISON_TICKS = 20 * 40;
    public static final int POISONER_REWARD = 75;
    public static final int PLACER_REWARD = 50;

    public static final Identifier VIGILANTE_ROLE_ID = Identifier.of("wathe", "vigilante");
    public static final Identifier VETERAN_ROLE_ID = Identifier.of("wathe", "veteran");
    public static final Identifier CORRUPT_COP_ROLE_ID = Identifier.of("noellesroles", "corrupt_cop");
    public static final Identifier ENGINEER_ROLE_ID = Identifier.of("noellesroles", "engineer");
    public static final Identifier GRAND_WITCH_ROLE_ID = Identifier.of("sparkwitch", "grand_witch");
    public static final Identifier ACCOMPLICE_ROLE_ID = Identifier.of("sparkwitch", "accomplice");
    public static final Identifier MURDEROUS_WITCH_ROLE_ID = Identifier.of("sparkwitch", "murderous_witch");

    private static final Set<Identifier> DIRECT_VIEWERS = Set.of(
            VIGILANTE_ROLE_ID,
            VETERAN_ROLE_ID,
            CORRUPT_COP_ROLE_ID,
            ENGINEER_ROLE_ID
    );
    private static final Set<Identifier> INSTINCT_VIEWERS = Set.of(
            GRAND_WITCH_ROLE_ID,
            ACCOMPLICE_ROLE_ID,
            MURDEROUS_WITCH_ROLE_ID
    );
    private static final Set<Identifier> DISMANTLERS = Set.of(
            VETERAN_ROLE_ID,
            CORRUPT_COP_ROLE_ID,
            ENGINEER_ROLE_ID
    );
    private static final Set<Identifier> EXTRA_DISMANTLE_COOLDOWN_ITEMS = Set.of(
            Identifier.of("noellesroles", "antidote"),
            Identifier.of("noellesroles", "iron_man_vial"),
            Identifier.of("noellesroles", "poison_needle"),
            Identifier.of("noellesroles", "double_barrel_shotgun"),
            Identifier.of("noellesroles", "repair_tool"),
            Identifier.of("noellesroles", "riot_shield"),
            Identifier.of("noellesroles", "riot_fork"),
            Identifier.of("noellesroles", "timed_bomb"),
            Identifier.of("noellesroles", "neutral_master_key")
    );

    private HunterRules() {
    }

    public static boolean canReload(int loadedShells, boolean coolingDown, long currentTick, long reloadWindowUntil) {
        if (coolingDown || loadedShells < 0 || loadedShells >= MAX_SHELLS) {
            return false;
        }
        return loadedShells == 0 || currentTick <= reloadWindowUntil;
    }

    public static long reloadWindowAfterLoading(
            int previouslyLoadedShells,
            long currentTick,
            long existingWindowUntil
    ) {
        return previouslyLoadedShells == 0 ? currentTick + SECOND_SHELL_WINDOW_TICKS : existingWindowUntil;
    }

    public static int cooldownAfterShot(int remainingShells) {
        return remainingShells <= 0 ? EMPTY_COOLDOWN_TICKS : FOLLOW_UP_COOLDOWN_TICKS;
    }

    public static TrapVisibility trapVisibility(
            @Nullable Identifier roleId,
            boolean nativeKiller,
            boolean deadSpectator,
            boolean hasLineOfSight,
            boolean instinctActive
    ) {
        if (nativeKiller || deadSpectator) {
            return TrapVisibility.THROUGH_WALL;
        }
        if (roleId != null && INSTINCT_VIEWERS.contains(roleId)) {
            if (instinctActive) {
                return TrapVisibility.THROUGH_WALL;
            }
            return hasLineOfSight ? TrapVisibility.DIRECT_ONLY : TrapVisibility.HIDDEN;
        }
        if (roleId != null && DIRECT_VIEWERS.contains(roleId) && hasLineOfSight) {
            return TrapVisibility.DIRECT_ONLY;
        }
        return TrapVisibility.HIDDEN;
    }

    public static boolean isInstinctTrapViewer(@Nullable Identifier roleId) {
        return roleId != null && INSTINCT_VIEWERS.contains(roleId);
    }

    public static boolean canDismantle(@Nullable Identifier roleId, boolean hasLineOfSight) {
        return hasLineOfSight && roleId != null && DISMANTLERS.contains(roleId);
    }

    static boolean isExtraDismantleCooldownItem(Identifier itemId) {
        return EXTRA_DISMANTLE_COOLDOWN_ITEMS.contains(itemId);
    }

    public static TrapPoisonRewards trapPoisonRewards() {
        return new TrapPoisonRewards(POISONER_REWARD, PLACER_REWARD);
    }

    public enum TrapVisibility {
        HIDDEN,
        DIRECT_ONLY,
        THROUGH_WALL
    }

    public record TrapPoisonRewards(int poisoner, int placer) {
        public int samePlayerTotal() {
            return poisoner + placer;
        }
    }
}
