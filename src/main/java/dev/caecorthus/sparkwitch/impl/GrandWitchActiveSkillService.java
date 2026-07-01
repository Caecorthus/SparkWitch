package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Grand Witch active skill item swap orchestration.
 * 大魔女主动技能的物品替换流程；仪礼剑自身战斗逻辑仍由物品服务独立处理。
 */
public final class GrandWitchActiveSkillService {
    public static final Identifier CEREMONIAL_SWORD_SKILL_ID = SparkWitch.id("ceremonial_sword");

    private GrandWitchActiveSkillService() {
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        if (!GrandWitchRules.isGrandWitch(context.role())) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.unavailable");
        }

        ServerPlayerEntity player = context.player();
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (component.hasActiveCeremonialSword()) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.ceremonial_sword.active");
        }

        int knifeSlot = findFirstKnifeSlot(player.getInventory());
        if (knifeSlot < 0) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.ceremonial_sword.no_knife");
        }
        if (!component.spendMana(GrandWitchRules.CEREMONIAL_SWORD_MANA_COST)) {
            return WitchSkillUseResult.fail("message.sparkwitch.skill.not_enough_mana");
        }

        player.getInventory().setStack(knifeSlot, new ItemStack(SparkWitchItems.ceremonialSword()));
        player.getInventory().markDirty();
        component.beginCeremonialSwordWindow(knifeSlot, GrandWitchRules.CEREMONIAL_SWORD_DURATION_TICKS);
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                GrandWitchRules.CEREMONIAL_SWORD_DURATION_TICKS,
                GrandWitchRules.CEREMONIAL_SWORD_SPEED_AMPLIFIER,
                false,
                false,
                true
        ));
        if (shouldAutoSelectCeremonialSwordSlot(knifeSlot)) {
            // Keep both server held-item logic and the client hotbar UI on the new ceremonial sword.
            // 同步服务端手持物品逻辑和客户端快捷栏 UI，让它们都切到新的仪礼剑。
            player.getInventory().selectedSlot = knifeSlot;
            player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(knifeSlot));
        }
        return WitchSkillUseResult.success(0, "message.sparkwitch.skill.ceremonial_sword.activated");
    }

    public static void finishCeremonialSwordWindow(ServerPlayerEntity player, WitchPlayerComponent component) {
        restoreKnife(player, component.getCeremonialSwordSlot());
        component.completeCeremonialSwordWindow(GrandWitchRules.CEREMONIAL_SWORD_COOLDOWN_TICKS);
    }

    public static void clearCeremonialSword(ServerPlayerEntity player, boolean restoreKnife) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (restoreKnife && component.hasActiveCeremonialSword()) {
            restoreKnife(player, component.getCeremonialSwordSlot());
        } else {
            removeCeremonialSwords(player);
        }
        component.clearCeremonialSwordWindow();
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

    private static void restoreKnife(ServerPlayerEntity player, int preferredSlot) {
        PlayerInventory inventory = player.getInventory();
        int slot = findCeremonialSwordSlot(inventory, preferredSlot);
        if (slot >= 0) {
            inventory.setStack(slot, new ItemStack(WatheItems.KNIFE));
            inventory.markDirty();
        }
    }

    private static int findCeremonialSwordSlot(PlayerInventory inventory, int preferredSlot) {
        if (preferredSlot >= 0
                && preferredSlot < inventory.size()
                && inventory.getStack(preferredSlot).isOf(SparkWitchItems.ceremonialSword())) {
            return preferredSlot;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isOf(SparkWitchItems.ceremonialSword())) {
                return slot;
            }
        }
        return -1;
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
