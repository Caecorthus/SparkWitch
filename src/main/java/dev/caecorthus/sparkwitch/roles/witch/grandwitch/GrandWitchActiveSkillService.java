package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Grants the permanent task reward; legacy spell/window entry points remain link-compatible.
 * 发放永久任务奖励；保留旧法术和窗口入口的链接兼容性。
 */
public final class GrandWitchActiveSkillService {
    public static final Identifier CEREMONIAL_SWORD_SKILL_ID = SparkWitch.id("ceremonial_sword");

    private GrandWitchActiveSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
    }

    public static void onTaskComplete(ServerPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        if (!WitchFactionRules.isGrandWitch(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            return;
        }
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        int previousTasks = component.getGrandWitchCeremonialSwordTasks();
        component.recordGrandWitchCeremonialSwordTask();
        if (!GrandWitchRules.shouldGrantCeremonialSword(previousTasks, component.getGrandWitchCeremonialSwordTasks())) {
            return;
        }
        grantCeremonialSword(player);
    }

    private static void grantCeremonialSword(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.ceremonialSword())) {
                return;
            }
        }
        int knifeSlot = findFirstKnifeSlot(inventory);
        ItemStack sword = new ItemStack(SparkWitchItems.ceremonialSword());
        if (knifeSlot >= 0) {
            inventory.setStack(knifeSlot, sword);
            if (shouldAutoSelectCeremonialSwordSlot(knifeSlot)) {
                inventory.selectedSlot = knifeSlot;
                player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(knifeSlot));
            }
        } else if (!inventory.insertStack(sword)) {
            // Never overwrite unrelated inventory or silently lose a full-inventory reward.
            // 背包已满时掉落奖励，不能覆盖无关物品或静默丢失奖励。
            player.dropItem(sword, false);
        }
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Retire persisted legacy windows without replacing the permanent sword or starting a cooldown.
     * 清理持久化的旧窗口，但不换回匕首、不启动技能冷却。
     */
    public static void finishCeremonialSwordWindow(ServerPlayerEntity player, WitchPlayerComponent component) {
        stopCeremonialSwordBgm(player);
        component.clearCeremonialSwordWindow();
    }

    public static void tickCeremonialSwordWindow(ServerPlayerEntity player, WitchPlayerComponent component) {
        if (component.getCeremonialSwordTicks() > 0 || component.getCeremonialSwordSlot() >= 0) {
            finishCeremonialSwordWindow(player, component);
        }
    }

    public static void stopCeremonialSwordBgm(ServerPlayerEntity player) {
        WitchWorldComponent.KEY.get(player.getServerWorld())
                .stopGrandWitchCeremonialSwordBgm(player.getUuid());
    }

    /**
     * Lifecycle cleanup only; the legacy restoreKnife argument no longer restores temporary weapons.
     * 仅用于生命周期清理；旧 restoreKnife 参数不再触发临时武器还原。
     */
    public static void clearCeremonialSword(ServerPlayerEntity player, boolean restoreKnife) {
        removeCeremonialSwords(player);
        stopCeremonialSwordBgm(player);
        WitchPlayerComponent.KEY.get(player).clearCeremonialSwordWindow();
    }

    private static int findFirstKnifeSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(WatheItems.KNIFE)) {
                return slot;
            }
        }
        return -1;
    }

    static boolean shouldAutoSelectCeremonialSwordSlot(int slot) {
        return PlayerInventory.isValidHotbarIndex(slot);
    }

    private static void removeCeremonialSwords(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.ceremonialSword())) {
                inventory.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.markDirty();
        }
    }
}
