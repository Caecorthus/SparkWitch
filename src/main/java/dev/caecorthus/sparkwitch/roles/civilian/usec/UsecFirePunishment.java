package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge;
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
 * The lethal half of a match hit: the kill and the hit punishment (Q8 and owner O1 2026-10-07, revolver parity with
 * Wathe's revolver receiver). Factions are pre-shot SparkFactionAPI snapshots. Wathe's
 * {@code ShouldPunishGunShooter} event is asked for every player hit, before the kill, as Wathe's own gun receiver does
 * (listeners read the victim's live role and traits, which a death may clear). The punishment is decided on the hit,
 * whether or not the victim dies, and runs four ticks later, Wathe's own delay: a listener's custom punishment as is;
 * otherwise, only while the shooter still carries a USEC rifle (Wathe: still carries a gun), the rifle is confiscated,
 * sanity drops to 0, gun pickup is prevented, and {@code KILL_SHOOTER} also kills the shooter. The kill itself is
 * Wathe's ordinary {@code killPlayer(victim, true, shooter, wathe:gun_shot)} in {@link #kill}, the single AXMC kill
 * site, run once inside the round's shield-pierce budget (O3), so only shield layers can be pierced and every other
 * protection hooked there (Saint, Fiend, Ninja parry, Last Stand, Depression, Judge denial, SparkFactionAPI vetoes)
 * applies unchanged.
 * 对局命中的致命部分：击杀与命中惩罚（Q8 与所有者 O1 2026-10-07，与 Wathe 左轮接收器一致）。
 * 阵营取射击前的 SparkFactionAPI 快照。与 Wathe 自身的开枪接收器一样，每次命中玩家都在击杀之前询问 Wathe
 * {@code ShouldPunishGunShooter} 事件（监听器读取受害者的实时职业与词条，死亡可能会清除它们）。惩罚在命中时即作判定，
 * 与受害者是否死亡无关，并在四刻后执行（Wathe 自身的延迟）：监听器的自定义惩罚原样执行；否则仅当射手仍携带 USEC 步枪时
 * （Wathe：仍携带枪械），没收步枪、理智清零、禁止拾枪，{@code KILL_SHOOTER} 还会处死射手。击杀本身是 {@link #kill} 中
 * Wathe 普通的 {@code killPlayer(victim, true, shooter, wathe:gun_shot)}，即 AXMC 唯一的击杀点，在本发子弹的穿盾预算内
 * 执行一次（O3），因此只有护盾层可被击穿，挂在其上的其他所有保护（圣徒、魔人、忍者格挡、背水一战、抑郁、法官禁杀、
 * SparkFactionAPI 否决）照常生效。
 */
final class UsecFirePunishment {
    /** Wathe's own gun receiver waits this long before punishing. / Wathe 自身开枪接收器执行惩罚前的延迟。 */
    static final int PUNISHMENT_DELAY_TICKS = 4;
    static final String CONFISCATED_MESSAGE_KEY = "message.sparkwitch.usec.rifle_confiscated";

    private UsecFirePunishment() {
    }

    /**
     * Decides the hit punishment, schedules it, then kills {@code victim} with the rifle.
     * 判定命中惩罚并安排执行，随后用步枪击杀 {@code victim}。
     */
    static void killAndPunish(ServerPlayerEntity shooter, ServerPlayerEntity victim, UsecAmmoType ammo) {
        GameWorldComponent game = GameWorldComponent.KEY.get(shooter.getWorld());
        boolean innocentShot = UsecFireRules.isInnocentShot(SparkFactionApi.resolveEffectiveFaction(shooter, game),
                SparkFactionApi.resolveEffectiveFaction(victim, game));
        // Wathe asks for every player hit, before the kill; the Judge's revolver-only wrapper never sees this call.
        // Wathe 对每次命中玩家都在击杀前询问；法官只包装左轮接收器内的调用，不会经过这里。
        ShouldPunishGunShooter.PunishResult eventResult =
                ShouldPunishGunShooter.EVENT.invoker().shouldPunish(shooter, victim);
        UsecFireRules.Punishment punishment = UsecFireRules.punishment(innocentShot, shooter.isCreative(),
                eventResult, game.getShootInnocentPunishment());
        schedule(shooter, victim, game, punishment, eventResult);
        kill(shooter, victim, ammo);
    }

    /**
     * The single place an AXMC round kills a player (O3): ONE ordinary {@code killPlayer} with
     * {@code wathe:gun_shot}, run inside the round's shield-pierce budget ({@link UsecShieldPierce}: FMJ 2, AP 5, +1
     * on a SparkTraits Heavy Artillery shot, asked here before the kill because a death clears traits). The kill is
     * never repeated, so every non-shield protection runs exactly once.
     * AXMC 子弹击杀玩家的唯一位置（O3）：在本发子弹的穿盾预算内（{@link UsecShieldPierce}：FMJ 2、AP 5，SparkTraits 重炮手
     * 射击再 +1，在此于击杀之前询问，因为死亡会清除词条）执行一次普通的 {@code wathe:gun_shot} {@code killPlayer}。击杀从不
     * 重复，因此每个非护盾保护都只执行一次。
     */
    static void kill(ServerPlayerEntity shooter, ServerPlayerEntity victim, UsecAmmoType ammo) {
        int budget = UsecShieldPierce.budget(ammo, SparkTraitsUsecBridge.isHeavyArtilleryGunShot(shooter, victim));
        UsecShieldPierce.run(shooter.getUuid(), victim.getUuid(), ammo, budget,
                () -> GameFunctions.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GUN));
    }

    private static void schedule(ServerPlayerEntity shooter, ServerPlayerEntity victim, GameWorldComponent game,
                                 UsecFireRules.Punishment punishment,
                                 @Nullable ShouldPunishGunShooter.PunishResult eventResult) {
        switch (punishment) {
            case CUSTOM -> {
                if (eventResult != null) {
                    Scheduler.schedule(() -> {
                        GameRecordManager.recordItemUse(shooter, UsecReplay.RECORD_ID, victim,
                                UsecReplay.punishData(punishment));
                        eventResult.executeCustomPunishment();
                    }, PUNISHMENT_DELAY_TICKS);
                }
            }
            case KILL_SHOOTER, CONFISCATE -> Scheduler.schedule(() -> punish(shooter, victim, game, punishment),
                    PUNISHMENT_DELAY_TICKS);
            default -> {
            }
        }
    }

    /**
     * Wathe's delayed punishment: nothing once the shooter carries no USEC rifle any more (Wathe: no gun left);
     * otherwise the replay line (before the shooter's own death line), the confiscation, sanity 0, the pickup ban and,
     * for {@code KILL_SHOOTER}, the shooter's death.
     * Wathe 的延迟惩罚：射手已不再携带 USEC 步枪时什么都不做（Wathe：已无枪）；否则依次为回放行（排在射手自身死亡行之前）、
     * 没收、理智清零、禁止拾枪，{@code KILL_SHOOTER} 时再处死射手。
     */
    static void punish(ServerPlayerEntity shooter, ServerPlayerEntity victim, GameWorldComponent game,
                       UsecFireRules.Punishment punishment) {
        if (!carriesRifle(shooter)) {
            return;
        }
        GameRecordManager.recordItemUse(shooter, UsecReplay.RECORD_ID, victim, UsecReplay.punishData(punishment));
        if (confiscate(shooter) > 0) {
            shooter.sendMessage(Text.translatable(CONFISCATED_MESSAGE_KEY).withColor(UsecRules.COLOR), true);
        }
        PlayerMoodComponent.KEY.get(shooter).setMood(0);
        game.addToPreventGunPickup(shooter);
        if (punishment == UsecFireRules.Punishment.KILL_SHOOTER && GameFunctions.isPlayerPlayingAndAlive(shooter)
                && GameFunctions.isPlayerAliveAndSurvival(shooter)) {
            GameFunctions.killPlayer(shooter, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    /**
     * Wathe's "still carries a gun" guard for the rifle: inventory, offhand, or the cursor (closing Wathe's
     * cursor gap: a rifle parked on the cursor still counts).
     * Wathe“仍携带枪械”检查的步枪版本：背包、副手或光标（补上 Wathe 的光标缺口：放在光标上的步枪同样算携带）。
     */
    static boolean carriesRifle(ServerPlayerEntity shooter) {
        Item rifle = SparkWitchItems.usecRifle();
        return shooter.getInventory().contains(stack -> stack.isOf(rifle))
                || (shooter.currentScreenHandler != null && shooter.currentScreenHandler.getCursorStack().isOf(rifle));
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
