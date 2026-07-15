package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges Ninja loadout, parry, dark-kill bounty, and death cleanup through public Wathe hooks.
 * 通过 Wathe 公共挂钩桥接忍者初始装备、格挡、黑暗赏金与死亡清理。
 */
public final class NinjaFeatureService {
    private static boolean registered;

    private NinjaFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        NinjaShopService.register();
        KillPlayer.BEFORE.register(NinjaFeatureService::beforeKill);
        KillPlayer.AFTER.register(NinjaFeatureService::afterKill);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(player);
        if (!NinjaRules.isNinja(role)) {
            component.clearNinjaParryWindow();
            return;
        }
        player.giveItemStack(new ItemStack(WatheItems.LOCKPICK));
    }

    private static @Nullable KillPlayer.KillResult beforeKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        // Current forced/admin/scripted call sites pass no killer; only a real, distinct player killer may be parried.
        // 当前强制、管理和脚本死亡调用不传 killer；格挡只接受真实且非自身的玩家击杀者。
        if (killer == null || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return null;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(victim.getServerWorld());
        WitchPlayerComponent component = WitchPlayerComponent.KEY.get(victim);
        boolean selfKill = victim.getUuid().equals(killer.getUuid());
        if (!NinjaRules.shouldParryPlayerKill(
                NinjaRules.isNinja(gameComponent.getRole(victim)),
                component.isNinjaParryActive(),
                selfKill,
                deathReason
        )) {
            return null;
        }

        component.finishNinjaParryWindow();
        victim.playSoundToPlayer(WatheSounds.ITEM_PSYCHO_ARMOUR, SoundCategory.PLAYERS, 1.0F, 0.8F);
        victim.sendMessage(Text.translatable("message.sparkwitch.skill.ninja_parry.blocked"), true);
        return KillPlayer.KillResult.cancel();
    }

    private static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        removeNinjaWeapons(victim);
        if (killer == null || victim.getUuid().equals(killer.getUuid())) {
            return;
        }

        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(killer.getServerWorld());
        if (!NinjaRules.isNinja(gameComponent.getRole(killer))) {
            return;
        }
        int rawBrightness = killer.getServerWorld().getBaseLightLevel(killer.getBlockPos(), 0);
        boolean blackoutActive = WorldBlackoutComponent.KEY.get(killer.getServerWorld()).isBlackoutActive();
        if (!NinjaRules.isDarkKillLocation(rawBrightness, blackoutActive)) {
            return;
        }

        PlayerShopComponent.KEY.get(killer).addToBalance(NinjaRules.DARK_KILL_BOUNTY);
        killer.sendMessage(Text.translatable(
                "message.sparkwitch.ninja.bounty",
                NinjaRules.DARK_KILL_BOUNTY
        ), true);
    }

    private static void removeNinjaWeapons(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(SparkWitchItems.ninjaKnife()) || stack.isOf(SparkWitchItems.ninjaShuriken())) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        // The personal 2x2 crafting grid is player-owned; external container slots remain untouched.
        // 个人 2x2 合成格属于玩家；外部容器槽位保持不变。
        var personalCraftingInput = player.playerScreenHandler.getCraftingInput();
        for (int slot = 0; slot < personalCraftingInput.size(); slot++) {
            ItemStack stack = personalCraftingInput.getStack(slot);
            if (stack.isOf(SparkWitchItems.ninjaKnife()) || stack.isOf(SparkWitchItems.ninjaShuriken())) {
                personalCraftingInput.setStack(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
        if (cursorStack.isOf(SparkWitchItems.ninjaKnife())
                || cursorStack.isOf(SparkWitchItems.ninjaShuriken())) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            player.getInventory().markDirty();
        }
    }
}
