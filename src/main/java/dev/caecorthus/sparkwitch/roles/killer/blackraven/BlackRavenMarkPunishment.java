package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenStashedDeathDrops;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Feather Blade friendly fire (plan v2 §2.1, owner 2026-10-21): a settled mark that really killed a civilian punishes
 * its Black Raven like an innocent revolver kill. Modelled on the Swordfish stab (pre-kill faction and
 * {@code deadBefore} snapshots, the same intercepted/non-final gates) and on Wathe's revolver punishment for the kind:
 * {@code KILL_SHOOTER} kills the marker with {@code wathe:shot_innocent}; {@code PREVENT_GUN_PICKUP} drops its revolvers
 * the way Wathe's gun receiver does, sets sanity to 0 and bans gun pickup, without a death. Server only.
 * 羽刃误杀（计划 v2 §2.1，所有者 2026-10-21）：结算时确实杀死好人的标记，会像左轮误杀一样惩罚其黑羽鸦。门槛照抄剑鱼刺击
 * （击杀前的阵营与 {@code deadBefore} 快照、相同的拦截/非最终死亡判断），惩罚种类照抄 Wathe 左轮：{@code KILL_SHOOTER}
 * 以 {@code wathe:shot_innocent} 处死标记者；{@code PREVENT_GUN_PICKUP} 按 Wathe 枪械接收器的方式掉落其左轮、理智清零并禁止
 * 捡枪，不处死。仅服务端。
 */
final class BlackRavenMarkPunishment {
    /** Wathe's gun receiver gives a dropped punishment revolver this pickup delay. / Wathe 惩罚掉落左轮的拾取延迟。 */
    static final int DROPPED_REVOLVER_PICKUP_DELAY = 10;

    private BlackRavenMarkPunishment() {
    }

    /** Pre-kill snapshot of both factions and the victim's death flag. / 击杀前双方阵营与受害者死亡标记的快照。 */
    record Snapshot(@Nullable Identifier markerFaction, @Nullable Identifier victimFaction, boolean deadBefore) {
    }

    /**
     * Applies the punishment after the mark's {@code killPlayer}. {@code marker} is the online marker entity, or null
     * when the marker is offline (then nothing happens; the kill itself already ran).
     * 在标记的 {@code killPlayer} 之后施加惩罚。{@code marker} 为在线的标记者实体；标记者离线时为 null（此时什么都不做，
     * 击杀本身已经执行）。
     */
    static void afterSettlement(@Nullable ServerPlayerEntity marker, ServerPlayerEntity victim, GameWorldComponent game,
                                Snapshot snapshot) {
        if (marker == null) {
            return;
        }
        boolean qualifying = BlackRavenPoliceRules.shouldPunishMarkKill(snapshot.markerFaction(),
                snapshot.victimFaction(), snapshot.deadBefore(), game.isPlayerDead(victim.getUuid()),
                WitchFactorTraitsBridge.isDeathIntercepted(victim),
                SparkTraitsKillerBridge.isNonFinalKillPending(victim, marker));
        switch (BlackRavenPoliceRules.punishment(qualifying, true, GameFunctions.isPlayerPlayingAndAlive(marker),
                GameFunctions.isPlayerAliveAndSurvival(marker), game.getShootInnocentPunishment())) {
            case KILL_MARKER -> GameFunctions.killPlayer(marker, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
            case PREVENT_GUN_PICKUP -> preventGunPickup(marker, game);
            case NONE -> {
            }
        }
    }

    /**
     * Wathe's non-lethal revolver punishment: every revolver the marker carries (inventory, offhand, cursor, and every
     * disguise stash, so a Raven that disguised after marking never gets one back on revert) is dropped at its feet with
     * Wathe's pickup delay and thrower, sanity drops to 0, and gun pickup is banned.
     * Wathe 的非致命左轮惩罚：标记者携带的所有左轮（背包、副手、光标，以及每个伪装暂存，因此标记后才伪装的黑羽鸦恢复时不会拿回左轮）
     * 按 Wathe 的拾取延迟与投掷者掉落在其脚下，理智清零，并禁止捡枪。
     */
    static void preventGunPickup(ServerPlayerEntity marker, GameWorldComponent game) {
        int removed = 0;
        PlayerInventory inventory = marker.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(WatheItems.REVOLVER)) {
                inventory.setStack(slot, ItemStack.EMPTY);
                removed++;
            }
        }
        if (marker.currentScreenHandler != null && marker.currentScreenHandler.getCursorStack().isOf(WatheItems.REVOLVER)) {
            marker.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            removed++;
        }
        int stashed = 0;
        for (ItemStack stack : BlackRavenStashedDeathDrops.takeFromStashes(marker,
                stack -> stack.isOf(WatheItems.REVOLVER))) {
            stashed += stack.getCount();
        }
        for (int dropped = 0; dropped < removed + stashed; dropped++) {
            ItemEntity item = marker.dropItem(WatheItems.REVOLVER.getDefaultStack(), false, false);
            if (item != null) {
                item.setPickupDelay(DROPPED_REVOLVER_PICKUP_DELAY);
                item.setThrower(marker);
            }
        }
        if (removed > 0) {
            inventory.markDirty();
            marker.currentScreenHandler.sendContentUpdates();
        }
        PlayerMoodComponent.KEY.get(marker).setMood(0);
        game.addToPreventGunPickup(marker);
    }
}
