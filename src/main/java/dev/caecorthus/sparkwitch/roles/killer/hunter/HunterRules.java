package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Pure Hunter tuning and role-id based compatibility rules.
 * 猎人的纯数值与基于身份 ID 的兼容规则集中在这里。
 */
public final class HunterRules {
    public static final Identifier ROLE_ID = Identifier.of("sparkwitch", "hunter");
    public static final int COLOR = 0x5C4C34;

    public static final int SHOTGUN_PRICE = 75;
    public static final int SHELL_PRICE = 150;
    public static final int SLUG_PRICE = 50;
    public static final int TRAP_PRICE = 25;
    public static final int SHOTGUN_STOCK = 1;

    public static final int MAX_SHELLS = 2;
    public static final double SHOTGUN_RANGE = 8.0D;
    // The slug's single-ray range, longer than the buckshot spread (2026-10-29).
    // 独头弹单射线射程，长于霰弹散射（2026-10-29）。
    public static final double SLUG_RANGE = 15.0D;
    // Angular offset of each spread ray from the centre: up/down, left/right and the four diagonals (2026-10-29).
    // 散射射线相对中心射线的偏角：上下、左右与四角（2026-10-29）。
    public static final double SHOTGUN_SPREAD_DEGREES = 1.0D;

    // Bundled shotgun audio (2026-10-29): the shot carries 16 blocks, the reload 8, by attenuation_distance in
    // sounds.json. / 猎枪自带音频（2026-10-29）：枪声 16 格、装填 8 格，由 sounds.json 的 attenuation_distance 决定。
    public static final Identifier SHOT_SOUND_ID = SparkWitch.id("item.double_barrel.shot");
    public static final Identifier LOAD_SOUND_ID = SparkWitch.id("item.double_barrel.load");
    public static final int FOLLOW_UP_COOLDOWN_TICKS = 4;
    public static final int EMPTY_COOLDOWN_TICKS = 20 * 30;
    // Round-start shotgun lock: 40 s (owner decision 2026-10-07), shorter than Wathe's 60 s revolver lock.
    // 猎枪开局锁定 40 秒（所有者 2026-10-07 决定），短于 Wathe 左轮的 60 秒开局锁定。
    public static final int SHOTGUN_INITIAL_COOLDOWN_TICKS = 40 * 20;
    public static final int SECOND_SHELL_WINDOW_TICKS = 20 * 10;
    // A shot into a SparkStrength junction box takes 70% of its durability (owner decision 2026-10-21), in
    // SparkStrength's unit where 10_000 = 100%, so two shots open a full box.
    // 一枪打中 SparkStrength 接线箱扣除其 70% 耐久（所有者 2026-10-21 决定），单位为 SparkStrength 的 10_000 = 100%，
    // 因此满耐久的箱子两枪即开。
    public static final int SHOTGUN_JUNCTION_BOX_DAMAGE = 7_000;

    public static final int MAX_OWNED_TRAPS = 4;
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
    public static final Identifier BEWITCHED_ROLE_ID = Identifier.of("sparkwitch", "bewitched");
    public static final Identifier MURDEROUS_WITCH_ROLE_ID = Identifier.of("sparkwitch", "murderous_witch");

    private static final Set<Identifier> DIRECT_VIEWERS = Set.of(
            VIGILANTE_ROLE_ID,
            VETERAN_ROLE_ID,
            CORRUPT_COP_ROLE_ID,
            ENGINEER_ROLE_ID
    );
    // Fixed instinct viewers (the Bewitched is accomplice-like, C2); special accomplices join through
    // isInstinctTrapViewer, which reads the live registry.
    // 固定的本能观察者（魔化使属于共犯类，C2）；特殊共犯经 isInstinctTrapViewer 读取实时注册表加入。
    private static final Set<Identifier> INSTINCT_VIEWERS = Set.of(
            GRAND_WITCH_ROLE_ID,
            ACCOMPLICE_ROLE_ID,
            BEWITCHED_ROLE_ID,
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
        return trapVisibility(roleId, nativeKiller, deadSpectator, () -> hasLineOfSight, () -> instinctActive);
    }

    /**
     * Same matrix with lazy inputs: line of sight (a raycast) and instinct are read only for the viewers whose answer
     * depends on them, at most once each. Line of sight only ever separates {@code DIRECT_ONLY} from {@code HIDDEN}.
     * 输入惰性求值的同一矩阵：视线（射线检测）与本能仅在答案依赖它们的观察者上读取，且各至多一次。视线只区分
     * {@code DIRECT_ONLY} 与 {@code HIDDEN}。
     */
    public static TrapVisibility trapVisibility(
            @Nullable Identifier roleId,
            boolean nativeKiller,
            boolean deadSpectator,
            BooleanSupplier hasLineOfSight,
            BooleanSupplier instinctActive
    ) {
        if (nativeKiller || deadSpectator) {
            return TrapVisibility.THROUGH_WALL;
        }
        if (isInstinctTrapViewer(roleId)) {
            if (instinctActive.getAsBoolean()) {
                return TrapVisibility.THROUGH_WALL;
            }
            return hasLineOfSight.getAsBoolean() ? TrapVisibility.DIRECT_ONLY : TrapVisibility.HIDDEN;
        }
        if (roleId != null && DIRECT_VIEWERS.contains(roleId) && hasLineOfSight.getAsBoolean()) {
            return TrapVisibility.DIRECT_ONLY;
        }
        return TrapVisibility.HIDDEN;
    }

    /**
     * Grand Witch, every accomplice (plain, special or Bewitched) and the Murderous Witch see traps through walls with
     * instinct.
     * 大魔女、所有共犯（普通、特殊或魔化使）和杀意魔女在开启本能时可隔墙看到捕兽夹。
     */
    public static boolean isInstinctTrapViewer(@Nullable Identifier roleId) {
        return roleId != null
                && (INSTINCT_VIEWERS.contains(roleId) || WitchFactionRules.isAccompliceVariantId(roleId));
    }

    public static boolean canDismantle(@Nullable Identifier roleId, boolean hasLineOfSight) {
        return hasLineOfSight && roleId != null && DISMANTLERS.contains(roleId);
    }

    /**
     * Active Wraiths stay outside living players' traps; only the bound killer's own trap may catch its Vendetta.
     * 激活冤魂不会触发生者的捕兽夹；仅绑定凶手自己放置的捕兽夹可以夹住其仇杀客。
     */
    public static boolean canTrapCatch(boolean aliveAndSurvival, boolean activeWraith, boolean ownerIsBoundKiller) {
        return aliveAndSurvival && (!activeWraith || ownerIsBoundKiller);
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
