package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Holy Flash lifecycle: a confirmed death removes the victim's flashes (the drop mixin already kept them out of
 * Wathe's death drops) and ends their flash; reset and the round-end finalize clear every flash and discard flashes
 * still in flight. Not role-gated: any holder may carry one.
 * 圣光弹生命周期：确认死亡时移除死者身上的圣光弹（掉落 mixin 已将其排除在 Wathe 死亡掉落之外）并结束其闪光；
 * 重置与回合结束收尾时清除所有闪光并移除仍在飞行的圣光弹。不按职业判断：任何人都可能持有。
 */
public final class HolyFlashFeatureService {
    private static boolean registered;

    private HolyFlashFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            // SparkTraits Last Stand keeps the victim in play, holding their flashes.
            // SparkTraits 背水一战会让受害者继续留在对局中，并保留其圣光弹。
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                return;
            }
            removeHolyFlashes(victim);
            HolyFlashComponent.KEY.get(victim).clear();
        });
        ResetPlayer.EVENT.register(player -> HolyFlashComponent.KEY.get(player).clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    HolyFlashComponent.KEY.get(player).clear();
                }
                HolyFlashEntity.discardAll(serverWorld);
            }
        });
    }

    private static void removeHolyFlashes(ServerPlayerEntity victim) {
        boolean changed = false;
        for (int slot = 0; slot < victim.getInventory().size(); slot++) {
            if (HolyFlashInventoryRules.isHolyFlash(victim.getInventory().getStack(slot))) {
                victim.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            victim.currentScreenHandler.sendContentUpdates();
        }
    }
}
