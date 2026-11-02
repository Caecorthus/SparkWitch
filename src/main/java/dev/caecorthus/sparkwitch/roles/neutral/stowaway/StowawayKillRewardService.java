package dev.caecorthus.sparkwitch.roles.neutral.stowaway;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Owner request 2026-11-02: when a Stowaway is killed by an effective good player, the nearest eligible good player
 * (alive, effective civilian, no Impostor flip, no revolver anywhere in the inventory) receives one revolver at the
 * death spot, and the responsible good killer receives 75 extra coins. The scan runs only on death — there is no
 * continuous per-tick position tracking, by owner request to keep the server load down.
 * 所有者 2026-11-02 需求：偷渡客被有效好人击杀时，死亡位置附近最近的合格好人（存活、有效平民、没有内鬼翻转、物品栏
 * 任何位置都没有左轮）获得一把左轮，责任好人凶手额外获得 75 金钱。只在死亡时扫描一次，不做每 tick 的持续位置记录
 * （所有者要求降低服务器压力）。
 */
public final class StowawayKillRewardService {
    static final int EXTRA_KILL_MONEY = 75;
    private static boolean registered;

    private StowawayKillRewardService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register(StowawayKillRewardService::afterKill);
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getWorld());
        if (!StowawayRules.isStowaway(game.getRole(victim))) {
            return;
        }
        UUID responsibleId = JudgeKillAttribution.committedResponsible(victim);
        if (responsibleId == null && killer != null) {
            responsibleId = killer.getUuid();
        }
        if (responsibleId == null) {
            return;
        }
        ServerPlayerEntity responsible = victim.getServer().getPlayerManager().getPlayer(responsibleId);
        if (responsible == null || !isGood(responsible, game)) {
            return;
        }

        grantRevolverToNearestGood(victim, game);
        PlayerShopComponent.KEY.get(responsible).addToBalance(EXTRA_KILL_MONEY);
    }

    /**
     * Rescans once at the death spot (the victim is already a spectator, but its position is the death position) and
     * gives exactly one revolver to the nearest eligible good player. No candidate means no item.
     * 在死亡位置只重新扫描一次（受害者已是旁观者，但位置仍是死亡位置），把恰好一把左轮发给最近的合格好人；没有候选者
     * 就不发。
     */
    private static void grantRevolverToNearestGood(ServerPlayerEntity victim, GameWorldComponent game) {
        ServerWorld world = victim.getServerWorld();
        ServerPlayerEntity nearest = null;
        double nearestSquared = Double.MAX_VALUE;
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate == victim
                    || candidate.isSpectator()
                    || candidate.isCreative()
                    || !GameFunctions.isPlayerPlayingAndAlive(candidate)
                    || !isGood(candidate, game)
                    || hasRevolver(candidate)) {
                continue;
            }
            double squared = victim.squaredDistanceTo(candidate);
            if (squared < nearestSquared) {
                nearestSquared = squared;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            return;
        }
        nearest.getInventory().offerOrDrop(new ItemStack(WatheItems.REVOLVER));
        nearest.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Effective faction, not the raw role: a Conscience killer counts as good, an Impostor civilian does not.
     * 使用有效阵营而不是原始职业：善良词条杀手算好人，内鬼词条平民不算。
     */
    static boolean isGood(ServerPlayerEntity player, GameWorldComponent game) {
        return FactionIds.CIVILIAN.equals(SparkFactionApi.resolveEffectiveFaction(player, game));
    }

    /** Checks hotbar, main inventory, armor, offhand and the cursor. / 检查快捷栏、主背包、盔甲、副手与光标。 */
    static boolean hasRevolver(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(WatheItems.REVOLVER)) {
                return true;
            }
        }
        return player.currentScreenHandler.getCursorStack().isOf(WatheItems.REVOLVER);
    }
}
