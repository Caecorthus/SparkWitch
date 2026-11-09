package dev.caecorthus.sparkwitch.item.sparekey;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * The NoellesRoles Attendant's round-start Spare Key (owner decision 2026-11-03). Granted once per round from an own
 * {@code ON_FINISH_INITIALIZE} phase after Wathe's default phase (Fisher rod / Blind kit precedent), where SparkTraits'
 * Conscience compensation has settled final roles. {@code RoleAssigned} may fire again mid-round, but nothing here
 * listens to it, so a used-up key is never re-granted and a mid-round Attendant gets none. Only the real Wathe role
 * counts: a SparkStrength Coroner disguised as an Attendant lends its gear through its own list and gets no key.
 * NoellesRoles 乘务员的开局备用钥匙（所有者 2026-11-03 决定）。在 Wathe 默认阶段之后的自有 {@code ON_FINISH_INITIALIZE}
 * 阶段每局发放一次（钓鱼佬鱼竿/盲人道具先例），此时 SparkTraits 良知补偿已确定最终身份。{@code RoleAssigned} 可能在局中再次
 * 触发，但这里不监听它，所以用完的钥匙不会被补发，局中成为乘务员也不会获得。只认 Wathe 的真实身份：伪装成乘务员的
 * SparkStrength 验尸官按其自有清单借出装备，不会获得钥匙。
 */
public final class SpareKeyAttendantKit {
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("spare_key_finish_initialize");
    private static boolean registered;

    private SpareKeyAttendantKit() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                grantRoundStartKeys(serverWorld, game);
            }
        });
    }

    static void grantRoundStartKeys(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Still STARTING here, so no running-state gate. / 此时仍是 STARTING，因此不判断运行态。
            PlayerInventory inventory = player.getInventory();
            if (!SpareKeyRules.receivesRoundStartKey(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()),
                    game.getRole(player), inventory.contains(stack -> stack.isOf(SparkWitchItems.spareKey())))) {
                continue;
            }
            // A full inventory drops it at the player's feet, so the key is never lost.
            // 背包已满时掉落在玩家脚下，钥匙不会丢失。
            inventory.offerOrDrop(new ItemStack(SparkWitchItems.spareKey()));
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
