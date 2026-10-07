package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.Scheduler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The lethal half of a match hit: the kill and the innocent-shot punishment (Q8, revolver parity). The kill is Wathe's
 * ordinary {@code killPlayer(victim, true, shooter, wathe:gun_shot)}, so every protection hooked there (Saint, Fiend,
 * Ninja parry, Last Stand, Depression, Judge denial, SparkFactionAPI vetoes) applies unchanged. Factions are pre-shot
 * SparkFactionAPI snapshots, and Wathe's {@code ShouldPunishGunShooter} event is asked before the kill, as Wathe's own
 * gun receiver does (listeners read the victim's live role and traits, which a death may clear). The punishment only
 * applies when the victim really died ({@link UsecFireRules#victimDied}); a listener's custom punishment runs four ticks
 * later, Wathe's own delay.
 * 对局命中的致命部分：击杀与误杀惩罚（Q8，与左轮一致）。击杀即 Wathe 普通的
 * {@code killPlayer(victim, true, shooter, wathe:gun_shot)}，因此挂在其上的所有保护（圣徒、魔人、忍者格挡、背水一战、抑郁、
 * 法官禁杀、SparkFactionAPI 否决）照常生效。阵营取射击前的 SparkFactionAPI 快照；与 Wathe 自身的开枪接收器一样，在击杀之前询问
 * Wathe {@code ShouldPunishGunShooter} 事件（监听器读取受害者的实时职业与词条，死亡可能会清除它们）。只有受害者确实死亡时才施加
 * 惩罚（{@link UsecFireRules#victimDied}）；监听器的自定义惩罚在四刻后执行，与 Wathe 自身的延迟相同。
 */
final class UsecFirePunishment {
    /** Wathe's own gun receiver waits this long before punishing. / Wathe 自身开枪接收器执行惩罚前的延迟。 */
    static final int CUSTOM_PUNISHMENT_DELAY_TICKS = 4;
    static final String CONFISCATED_MESSAGE_KEY = "message.sparkwitch.usec.rifle_confiscated";

    private UsecFirePunishment() {
    }

    /** Kills {@code victim} with the rifle, then punishes an innocent shot that really killed. / 击杀并处理误杀惩罚。 */
    static void killAndPunish(ServerPlayerEntity shooter, ServerPlayerEntity victim) {
        GameWorldComponent game = GameWorldComponent.KEY.get(shooter.getWorld());
        boolean innocentShot = UsecFireRules.isInnocentShot(SparkFactionApi.resolveEffectiveFaction(shooter, game),
                SparkFactionApi.resolveEffectiveFaction(victim, game));
        boolean deadBefore = game.isPlayerDead(victim.getUuid());
        ShouldPunishGunShooter.PunishResult eventResult = innocentShot && !deadBefore
                ? ShouldPunishGunShooter.EVENT.invoker().shouldPunish(shooter, victim)
                : null;
        GameFunctions.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GUN);
        boolean died = UsecFireRules.victimDied(deadBefore, game.isPlayerDead(victim.getUuid()),
                WitchFactorTraitsBridge.isDeathIntercepted(victim),
                SparkTraitsKillerBridge.isNonFinalKillPending(victim, shooter));
        UsecFireRules.Punishment punishment = UsecFireRules.punishment(innocentShot, died, shooter.isCreative(),
                eventResult, game.getShootInnocentPunishment());
        apply(shooter, victim, game, punishment, eventResult);
    }

    private static void apply(ServerPlayerEntity shooter, ServerPlayerEntity victim, GameWorldComponent game,
                              UsecFireRules.Punishment punishment,
                              @Nullable ShouldPunishGunShooter.PunishResult eventResult) {
        if (punishment == UsecFireRules.Punishment.NONE) {
            return;
        }
        // The replay line comes first, so it reads before the shooter's own death line.
        // 回放行先写入，使其排在射手自身的死亡行之前。
        GameRecordManager.recordItemUse(shooter, UsecReplay.RECORD_ID, victim, UsecReplay.punishData(punishment));
        switch (punishment) {
            case CUSTOM -> {
                if (eventResult != null) {
                    Scheduler.schedule(eventResult::executeCustomPunishment, CUSTOM_PUNISHMENT_DELAY_TICKS);
                }
            }
            case KILL_SHOOTER -> {
                PlayerMoodComponent.KEY.get(shooter).setMood(0);
                game.addToPreventGunPickup(shooter);
                if (GameFunctions.isPlayerPlayingAndAlive(shooter) && GameFunctions.isPlayerAliveAndSurvival(shooter)) {
                    GameFunctions.killPlayer(shooter, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
                }
            }
            case CONFISCATE -> {
                PlayerMoodComponent.KEY.get(shooter).setMood(0);
                game.addToPreventGunPickup(shooter);
                if (confiscate(shooter) > 0) {
                    shooter.sendMessage(Text.translatable(CONFISCATED_MESSAGE_KEY).withColor(UsecRules.COLOR), true);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Removes every USEC rifle the shooter carries (inventory, offhand, cursor); loose magazines, rounds and the
     * suppressor stay. Nothing re-grants it this round: the rifle is granted only at role assignment. Returns how many
     * rifles were removed.
     * 移除射手携带的所有 USEC 步枪（背包、副手、光标）；散装弹匣、子弹与消音器保留。本局不会再补发：步枪只在分配职业时发放。
     * 返回移除的步枪数量。
     */
    static int confiscate(ServerPlayerEntity shooter) {
        Item rifle = SparkWitchItems.usecRifle();
        int removed = 0;
        PlayerInventory inventory = shooter.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(rifle)) {
                inventory.setStack(slot, ItemStack.EMPTY);
                removed++;
            }
        }
        if (shooter.currentScreenHandler != null && shooter.currentScreenHandler.getCursorStack().isOf(rifle)) {
            shooter.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            removed++;
        }
        if (removed > 0) {
            inventory.markDirty();
            shooter.currentScreenHandler.sendContentUpdates();
        }
        return removed;
    }
}
