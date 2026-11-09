package dev.caecorthus.sparkwitch.roles.civilian.savior;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Numbers shared by the Savior's pistol and lightsaber. The pistol copies Wathe's revolver (30-block client pick,
 * 65-block server tolerance, 10 s cooldown, one lethal shot). The lightsaber copies Wathe's bat attributes.
 * 救世主手枪与光剑共用的数值。手枪复制 Wathe 左轮（客户端 30 格选靶、服务端 65 格容差、10 秒冷却、一击必杀）；
 * 光剑复制 Wathe 球棒的属性。
 */
public final class SaviorRules {
    public static final Identifier ROLE_ID = SparkWitch.id("savior");
    /** Minecraft {@code §b} aqua, the role color requested by the owner. / 所有者指定的 {@code §b} 青色。 */
    public static final int ROLE_COLOR = 0x55FFFF;
    public static final Identifier PISTOL_ID = SparkWitch.id("savior_pistol");
    public static final Identifier LIGHTSABER_ID = SparkWitch.id("savior_lightsaber");

    /** Round-start weapon cooldowns: pistol 60 s, lightsaber 45 s. / 开局武器冷却：手枪 60 秒、光剑 45 秒。 */
    public static final int PISTOL_START_COOLDOWN_TICKS = 60 * 20;
    public static final int LIGHTSABER_START_COOLDOWN_TICKS = 45 * 20;
    /** Lightsaber kill cooldown: 30 s. The pistol always returns to its 10 s cooldown, kill or miss. / 光剑击杀冷却 30 秒；手枪命中与否都只回 10 秒。 */
    public static final int LIGHTSABER_KILL_COOLDOWN_TICKS = 30 * 20;

    /** Wathe revolver cooldown: 0 min 10 s. / Wathe 左轮冷却：0 分 10 秒。 */
    public static final int PISTOL_COOLDOWN_TICKS = 200;
    /** Wathe revolver client pick distance. / Wathe 左轮客户端选靶距离。 */
    public static final double PISTOL_CLIENT_RANGE = 30.0D;
    /** Wathe revolver server tolerance (a laggy client may report farther). / Wathe 左轮服务端容差（延迟客户端可报更远）。 */
    public static final double PISTOL_SERVER_TOLERANCE = 65.0D;
    /** Wathe revolver local recoil after vanilla captured the aim. / 原版记录瞄准方向后施加的本地后坐。 */
    public static final float PISTOL_RECOIL_PITCH = 4.0F;
    /** Wathe's innocent-shooting punishment is scheduled four ticks later. / Wathe 误杀惩罚延迟四刻执行。 */
    public static final int PUNISH_DELAY_TICKS = 4;
    /** Dropped gun pickup delay, same as Wathe's punishment. / 掉落手枪的拾取延迟，与 Wathe 惩罚相同。 */
    public static final int DROPPED_GUN_PICKUP_DELAY = 10;
    /**
     * Player-inventory indices of the two main rows Wathe's LimitedInventoryScreen locks (only client-side; SFA
     * LimitedInventoryRows reopens 27–35). The hidden revolver lives at 9 ({@code SaviorLoadoutService}).
     * Wathe LimitedInventoryScreen 锁定的两行主背包索引（仅客户端锁定；SFA LimitedInventoryRows 重新开放 27–35）。
     * 隐藏左轮位于 9 号（{@code SaviorLoadoutService}）。
     */
    public static final int HIDDEN_REVOLVER_SLOT = 9;
    public static final int LOCKED_MAIN_FIRST_SLOT = 9;
    public static final int LOCKED_MAIN_LAST_SLOT = 26;
    /** Server melee reach for client-picked non-player targets: vanilla 3 + 1. / 客户端选中非玩家目标时的服务端近战距离：原版 3 + 1。 */
    public static final double LIGHTSABER_MELEE_REACH = 4.0D;

    /** Must equal the {@code sounds.json} keys: dotted paths, not underscores. / 必须与 {@code sounds.json} 的键一致：点路径，不是下划线。 */
    public static final Identifier PISTOL_SHOOT_SOUND_ID = SparkWitch.id("item.savior_pistol.shoot");
    public static final Identifier PISTOL_CLICK_SOUND_ID = SparkWitch.id("item.savior_pistol.click");
    public static final Identifier LIGHTSABER_HIT_SOUND_ID = SparkWitch.id("item.savior_lightsaber.hit");
    /** Terminal kill reasons: SparkTraits skips Last Escape / Last Stand / Depression protections for these. / 终结击杀原因。 */
    public static final Identifier PISTOL_DEATH_REASON = SparkWitch.id("savior_pistol_kill");
    public static final Identifier LIGHTSABER_DEATH_REASON = SparkWitch.id("lightsaber_kill");
    public static final Identifier LIGHTSABER_IGNITE_SOUND_ID = SparkWitch.id("item.savior_lightsaber.ignite");
    public static final Identifier LIGHTSABER_HUM_SOUND_ID = SparkWitch.id("item.savior_lightsaber.hum");
    public static final Identifier LIGHTSABER_SWING_SOUND_ID = SparkWitch.id("item.savior_lightsaber.swing");
    public static final Identifier GUNSHOT_PARTICLE_ID = SparkWitch.id("gunshot");

    /** G-skill initial and per-use cooldown: 60 s (the owner's unspecified x, kept in one place). / G 技能开局与使用后冷却 60 秒。 */
    public static final int DOMAIN_INITIAL_COOLDOWN_TICKS = 60 * 20;
    public static final int DOMAIN_COOLDOWN_TICKS = 60 * 20;
    /** The domain expands around the Savior for 10 s. / 领域以救世主为中心持续 10 秒。 */
    public static final int DOMAIN_DURATION_TICKS = 10 * 20;
    public static final double DOMAIN_RADIUS = 15.0D;
    public static final double DOMAIN_RADIUS_SQUARED = DOMAIN_RADIUS * DOMAIN_RADIUS;
    /** Effects are refreshed every tick with a short tail so leaving the domain drops them promptly. / 每刻刷新并留短尾，离开领域后效果迅速消失。 */
    public static final int DOMAIN_EFFECT_REFRESH_TICKS = 20;
    /** Domain members without the Savior's role color are outlined in pure white. / 领域中非救世主的玩家使用纯白描边。 */
    public static final int DOMAIN_WHITE_HIGHLIGHT = 0xFFFFFF;

    private SaviorRules() {
    }

    public static boolean isSavior(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * Role binding (coordinator decision): while a Wathe game is running, the pistol and the lightsaber work only for
     * a living Savior by raw role, so a looter (Ragpicker) or a disguise never gets terminal kills. Off-match anyone
     * may use them (creative testing).
     * 职业绑定（协调者决定）：Wathe 对局进行中，手枪与光剑只对按原始职业判定的存活救世主生效，捡尸者（拾荒者）或伪装者
     * 永远拿不到终结击杀。对局外任何人都可使用（创造模式测试）。
     */
    public static boolean mayUseWeapons(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return !game.isRunning()
                || (isSavior(game.getRole(player)) && GameFunctions.isPlayerPlayingAndAlive(player));
    }

    /**
     * Server, before any sound or consequence: SparkFactionAPI must allow both the weapon's action and the kill's own
     * death reason, because its {@code killPlayer} HEAD veto judges the kill by that reason; checking both keeps the
     * punishment from firing for a kill that would then be vetoed.
     * 服务端，在任何音效或后果之前：SparkFactionAPI 必须同时放行武器动作与击杀自身的死因，因为其 {@code killPlayer} HEAD
     * 否决按该死因判定；两者都查，避免惩罚已触发而击杀随后被否决。
     */
    public static boolean mayAffect(ServerPlayerEntity actor, ServerPlayerEntity target, Identifier action,
                                    Identifier deathReason, GameWorldComponent game) {
        return SparkFactionApi.canAffectPlayer(actor, target, action, game)
                && (action.equals(deathReason) || SparkFactionApi.canAffectPlayer(actor, target, deathReason, game));
    }

    /**
     * Effective good alignment, the same rule Knight/Recruit use: the Impostor trait is excluded and Conscience is
     * included. The Savior's innocent-shooting punishment must use this instead of the raw role flag.
     * 有效好人阵营，与骑士/新兵同口径：排除内鬼、包含善良杀手。救世主的误杀惩罚必须用它，而不是原始职业标记。
     */
    public static boolean isGoodPlayer(@Nullable PlayerEntity player, GameWorldComponent game) {
        return player != null && game != null
                && FactionIds.CIVILIAN.equals(SparkFactionApi.resolveEffectiveFaction(player, game));
    }

    /** True for the two terminal Savior weapon reasons. / 救世主两把武器的终结死因。 */
    public static boolean isTerminalKill(@Nullable Identifier deathReason) {
        return PISTOL_DEATH_REASON.equals(deathReason) || LIGHTSABER_DEATH_REASON.equals(deathReason);
    }

    /**
     * The delayed innocent-kill punishment still applies only to a living Savior: a shooter who died, left the round
     * or lost the role in the four ticks keeps everything. / 延迟的误杀惩罚只作用于仍存活的救世主：四刻内已死亡、离开对局或
     * 失去职业的射手不受影响。
     */
    static boolean isPunishable(ServerPlayerEntity shooter, GameWorldComponent game) {
        return GameFunctions.isPlayerPlayingAndAlive(shooter) && isSavior(game.getRole(shooter));
    }

    /** Removes one stack of {@code item} from the inventory; false when none was found. / 移除一件该物品；未找到时返回 false。 */
    static boolean takeOne(PlayerInventory inventory, Item item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(item)) {
                inventory.removeStack(slot, 1);
                return true;
            }
        }
        return false;
    }

    /**
     * Wathe keeps the gun count constant (one removed, one dropped), so the punishment drops the Savior's own hidden
     * revolver (slot {@link #HIDDEN_REVOLVER_SLOT} first, then any other) instead of a new one, with Wathe's pickup
     * delay and owner. Nothing drops when the Savior carries no revolver.
     * Wathe 保持枪支数量不变（收走一把、掉落一把），因此惩罚掉落救世主自己的隐藏左轮（先查 {@link #HIDDEN_REVOLVER_SLOT}
     * 号槽，再查其他槽），而不是新建一把；沿用 Wathe 的拾取延迟与所有者。救世主身上没有左轮时不掉落任何东西。
     */
    static void dropHiddenRevolver(ServerPlayerEntity shooter) {
        PlayerInventory inventory = shooter.getInventory();
        int slot = inventory.getStack(HIDDEN_REVOLVER_SLOT).isOf(WatheItems.REVOLVER) ? HIDDEN_REVOLVER_SLOT : -1;
        for (int index = 0; slot < 0 && index < inventory.size(); index++) {
            if (inventory.getStack(index).isOf(WatheItems.REVOLVER)) {
                slot = index;
            }
        }
        if (slot < 0) {
            return;
        }
        ItemStack revolver = inventory.removeStack(slot, 1);
        ItemEntity dropped = shooter.dropItem(revolver, false, false);
        if (dropped != null) {
            dropped.setPickupDelay(DROPPED_GUN_PICKUP_DELAY);
            dropped.setOwner(shooter.getUuid());
        }
    }

    /**
     * Settles the hit up to ten times, stopping as soon as the victim is no longer playing and alive. Each settlement
     * can spend one shield layer, so stacked protections are not missed by a single kill attempt.
     * 最多结算 10 次，目标一旦不再存活立即停止。每次结算可消耗一层护盾，避免单次尝试漏掉多层保护。
     */
    public static void settleKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        for (int attempt = 0; attempt < 10 && GameFunctions.isPlayerPlayingAndAlive(victim); attempt++) {
            GameFunctions.killPlayer(victim, true, killer, deathReason);
        }
    }
}
