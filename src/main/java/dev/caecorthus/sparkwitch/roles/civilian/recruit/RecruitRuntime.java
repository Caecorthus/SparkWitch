package dev.caecorthus.sparkwitch.roles.civilian.recruit;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.mixin.recruit.RecruitItemEntityAccessor;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority for the Recruit: the first completed task grants Wathe's knife, knife kills cost 35 sanity, and
 * killing or pushing off a good player kills the Recruit with Wathe's own SHOT_INNOCENT death (shown as "小脑" in
 * zh_cn). A SparkTraits Impostor trait removes both the knife sanity cost and that penalty. Revolver kills cost no
 * sanity and keep the 10 s base cooldown through the police-gun parity mixin; Wathe's own innocent-gun punishment
 * still applies.
 * 新兵的服务端权威：完成第一个任务获得 Wathe 的刀；用刀击杀损失 35 理智；击杀或推下好人时，以 Wathe 自身的
 * SHOT_INNOCENT 死亡（中文显示为“小脑”）处死新兵。拥有 SparkTraits 内鬼词条时，免除用刀理智消耗与该惩罚。左轮击杀
 * 不扣理智，并通过警察枪械对等 Mixin 保持 10 秒基础冷却；左轮误杀仍由 Wathe 自身惩罚处理。
 */
public final class RecruitRuntime {
    private static boolean registered;

    private RecruitRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RecruitReplay.register();
        TaskComplete.EVENT.register(RecruitRuntime::onTaskComplete);
        KillPlayer.AFTER.register(RecruitRuntime::afterKill);
        RoleAssigned.EVENT.register((player, role) -> RecruitPlayerComponent.KEY.get(player).clear());
        ResetPlayer.EVENT.register(player -> RecruitPlayerComponent.KEY.get(player).clear());
        ServerTickEvents.END_SERVER_TICK.register(RecruitRuntime::tickGunPickup);
    }

    private static void onTaskComplete(ServerPlayerEntity player, PlayerMoodComponent.Task taskType) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (!RecruitRules.isRecruit(game.getRole(player))
                || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        RecruitPlayerComponent component = RecruitPlayerComponent.KEY.get(player);
        if (component.isKnifeGranted()) {
            return;
        }
        component.recordTask();
        if (component.getTasksCompleted() >= RecruitRules.REQUIRED_TASKS) {
            player.giveItemStack(new ItemStack(WatheItems.KNIFE));
            component.grantKnife();
            RecruitReplay.recordKnifeGranted(player);
        }
    }

    /**
     * The Recruit's separate ground gun-pickup whitelist. Wathe refuses an innocent player without a gun or Veteran
     * stabs, and SparkTraits refuses ground guns to an Impostor; neither rule is changed. For a Recruit carrying
     * Wathe's knife this performs the ordinary inventory insertion inside the vanilla pickup box, so both refusals are
     * bypassed only for this role.
     * 新兵独有的地面枪械拾取白名单。Wathe 会拒绝没有枪、也没有老兵刺击次数的好人；SparkTraits 也会拒绝内鬼从地面捡枪。
     * 这里不改动任何一条规则，只对带着 Wathe 的刀的新兵，在原版拾取范围内执行普通物品栏插入，使两种拒绝都仅对本职业失效。
     */
    private static void tickGunPickup(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld world) || !RecruitRules.mayPickUpGuns(player)) {
                continue;
            }
            RecruitPlayerComponent component = RecruitPlayerComponent.KEY.get(player);
            // A gun acquired by any source (this whitelist, the shop, a command) locks ground pickup for the round.
            // 任何来源（本白名单、商店、命令）拿到枪后，本局余下时间都锁定地面拾取。
            if (RecruitRules.hasGun(player)) {
                component.lockGunPickup();
                continue;
            }
            for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class,
                    player.getBoundingBox().expand(1.0, 0.5, 1.0),
                    entity -> ((RecruitItemEntityAccessor) entity).sparkwitch$getPickupDelay() <= 0
                            && !entity.getStack().isEmpty()
                            && entity.getStack().isIn(WatheItemTags.GUNS))) {
                if (pickUpGun(player, item)) {
                    component.lockGunPickup();
                    break;
                }
            }
        }
    }

    private static boolean pickUpGun(ServerPlayerEntity player, ItemEntity item) {
        ItemStack stack = item.getStack();
        int before = stack.getCount();
        player.getInventory().insertStack(stack);
        int moved = before - stack.getCount();
        if (moved <= 0) {
            return false;
        }
        player.sendPickup(item, moved);
        if (stack.isEmpty()) {
            item.discard();
        }
        return true;
    }

    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                  Identifier deathReason) {
        if (killer == null || killer == victim) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getServerWorld());
        if (!RecruitRules.isRecruit(game.getRole(killer))) {
            return;
        }
        // A SparkTraits Impostor is a killer by trait: the Recruit's knife sanity cost and innocent-kill death do not
        // apply. Absent/older/failing Traits reads as not Impostor, so the ordinary rule stays.
        // 拥有 SparkTraits 内鬼词条时按杀手处理：免除新兵的用刀理智消耗与误杀处死。Traits 缺失、过旧或出错时视为非内鬼，
        // 保留普通规则。
        if (SparkTraitsKillerBridge.isImpostorActive(killer)) {
            return;
        }
        boolean knifeKill = GameConstants.DeathReasons.KNIFE.equals(deathReason);
        if (knifeKill) {
            PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(killer);
            // Wathe clamps mood to -1..1 itself; 0.35 is 35 sanity points. / Wathe 自行限制 -1..1；0.35 即 35 理智点。
            mood.setMood(mood.getMood() - RecruitRules.KNIFE_KILL_MOOD_COST);
        }
        if (!RecruitRules.isGood(victim, game)
                || (!knifeKill && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(deathReason))) {
            return;
        }
        if (GameFunctions.isPlayerPlayingAndAlive(killer)) {
            // Non-forced, exactly Wathe's own SHOT_INNOCENT kill ("小脑" in zh_cn). / 非强制，即 Wathe 自身的
            // SHOT_INNOCENT 击杀（中文 lang 中显示为“小脑”）。
            GameFunctions.killPlayer(killer, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }
}
