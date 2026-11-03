package dev.caecorthus.sparkwitch.roles.civilian.recruit;

import dev.caecorthus.sparkwitch.compat.SparkTraitsRecruitBridge;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority for the Recruit: the first completed task grants Wathe's knife, knife kills cost 35 sanity, and
 * killing a good player with the knife or pushing one off the train kills the Recruit with Wathe's own SHOT_INNOCENT
 * death (shown as "小脑" in zh_cn). A SparkTraits Impostor trait removes both the knife sanity cost and that penalty.
 * Revolver kills cost no sanity and keep the 10 s base cooldown through {@code mixin/recruit/RecruitGunParityMixin};
 * Wathe's own innocent-gun punishment still applies. Ground guns follow Wathe's own pickup rule, and
 * {@code mixin/recruit/ItemEntityRecruitGunMixin} allows only one ground gun pickup per round.
 * 新兵的服务端权威：完成第一个任务获得 Wathe 的刀；用刀击杀损失 35 理智；用刀杀死好人或把好人推下列车时，以 Wathe 自身的
 * SHOT_INNOCENT 死亡（中文显示为“小脑”）处死新兵。拥有 SparkTraits 内鬼词条时，免除用刀理智消耗与该惩罚。左轮击杀
 * 不扣理智，并通过 {@code mixin/recruit/RecruitGunParityMixin} 保持 10 秒基础冷却；左轮误杀仍由 Wathe 自身惩罚处理。
 * 地面枪械沿用 Wathe 自身拾取规则，{@code mixin/recruit/ItemEntityRecruitGunMixin} 只允许每局从地面捡一把。
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
    }

    /**
     * Called by {@code ItemEntityRecruitGunMixin} right after vanilla inserted a ground stack of {@code item} (so the
     * pickup really happened): a Recruit's first {@code wathe:guns} pickup uses up the round's one pickup.
     * 由 {@code ItemEntityRecruitGunMixin} 在原版已把地面物品放入背包后调用（拾取确已发生）：新兵第一次捡起
     * {@code wathe:guns} 即用掉本局唯一一次捡枪。
     */
    public static void onGroundPickup(PlayerEntity player, Item item) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || player.isCreative()
                || !item.getDefaultStack().isIn(WatheItemTags.GUNS)
                || !RecruitRules.isRecruit(GameWorldComponent.KEY.get(serverPlayer.getServerWorld()).getRole(player))) {
            return;
        }
        RecruitPlayerComponent.KEY.get(player).markGunPickedUp();
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
            giveKnife(player);
            component.grantKnife();
            RecruitReplay.recordKnifeGranted(player);
        }
    }

    /**
     * The knife always lands in the hotbar, never in a row SparkFactionAPI hides: a free hotbar slot, else the selected
     * slot, whose displaced item moves to a free inventory slot, then an empty off hand, or is dropped at the
     * Recruit's feet, never deleted (the Knight duel knife and Vendetta precedent).
     * 刀一定发到快捷栏，绝不进入 SparkFactionAPI 隐藏的物品栏行：优先空的快捷栏格，否则放进当前选中格；被替换的物品移到
     * 背包空位，其次是空着的副手，否则掉落在新兵脚下，绝不删除（沿用骑士决斗小刀与复仇者的先例）。
     */
    private static void giveKnife(ServerPlayerEntity player) {
        ItemStack knife = new ItemStack(WatheItems.KNIFE);
        PlayerInventory inventory = player.getInventory();
        int empty = inventory.getEmptySlot();
        if (PlayerInventory.isValidHotbarIndex(empty)) {
            inventory.setStack(empty, knife);
        } else {
            ItemStack displaced = inventory.getStack(inventory.selectedSlot);
            inventory.setStack(inventory.selectedSlot, knife);
            if (!displaced.isEmpty() && !player.giveItemStack(displaced)) {
                if (player.getOffHandStack().isEmpty()) {
                    player.setStackInHand(Hand.OFF_HAND, displaced);
                } else {
                    player.dropItem(displaced, false, true);
                }
            }
        }
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
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
        if (SparkTraitsRecruitBridge.isImpostor(killer)) {
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
