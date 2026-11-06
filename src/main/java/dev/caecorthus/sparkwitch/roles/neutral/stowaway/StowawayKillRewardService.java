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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Owner request 2026-11-02: when a Stowaway is killed by an effective good player, the nearest eligible good player
 * (alive, effective civilian, no Impostor flip, not banned from guns by a Wathe misfire, no revolver anywhere in the
 * inventory) receives one revolver at the death spot, and the responsible good killer receives 75 extra coins. The
 * revolver goes to the nearest candidate whose inventory accepts it: a role that refuses guns (USEC, a stage-2
 * Undercover, a psycho Depression civilian) or a full inventory passes it on to the next nearest, never destroys it.
 * The scan runs only on death; there is no per-tick position tracking.
 * 所有者 2026-11-02 需求：偷渡客被有效好人击杀时，死亡位置附近最近的合格好人（存活、有效平民、没有内鬼翻转、未因 Wathe
 * 误杀被禁止拾取枪械、物品栏任何位置都没有左轮）获得一把左轮，责任好人凶手额外获得 75 金钱。左轮交给最近且物品栏接受它
 * 的候选者：拒绝枪械的职业（USEC、第二阶段卧底、疯魔中的抑郁平民）或物品栏已满时顺延给下一位，绝不销毁。只在死亡时扫描
 * 一次，不做每 tick 的位置记录。
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
     * gives exactly one revolver to the nearest eligible good player whose inventory accepts it. {@code giveItemStack}
     * leaves a refused stack intact (unlike {@code offerOrDrop}, whose split stack is lost when a role guard refuses
     * the insert), so the next candidate is tried. No accepting candidate means no item.
     * 在死亡位置只重新扫描一次（受害者已是旁观者，但位置仍是死亡位置），把恰好一把左轮发给物品栏接受它的最近合格好人。
     * {@code giveItemStack} 被拒绝时保留原物品（{@code offerOrDrop} 拆分出的物品在职业守卫拒绝插入时会丢失），因此可以
     * 尝试下一位候选者；没有候选者接受就不发。
     */
    private static void grantRevolverToNearestGood(ServerPlayerEntity victim, GameWorldComponent game) {
        ServerWorld world = victim.getServerWorld();
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate == victim
                    || candidate.isSpectator()
                    || candidate.isCreative()
                    || !GameFunctions.isPlayerPlayingAndAlive(candidate)
                    || game.isPreventedFromGunPickup(candidate)
                    || !isGood(candidate, game)
                    || hasRevolver(candidate)) {
                continue;
            }
            candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingDouble(victim::squaredDistanceTo));
        for (ServerPlayerEntity candidate : candidates) {
            if (candidate.giveItemStack(new ItemStack(WatheItems.REVOLVER))) {
                candidate.currentScreenHandler.sendContentUpdates();
                return;
            }
        }
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
