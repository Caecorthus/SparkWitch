package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.compat.cooldown.SparkWitchItemCooldownReleases;
import dev.caecorthus.sparkwitch.mixin.accessor.ServerItemCooldownManagerAccessor;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerItemCooldownManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Marks the admin path only: SparkFactionAPI {@code /sparkfactionapi:clearCooldown} (the same method body from 0.1.5.12
 * through 0.1.5.15). When its {@code remove} really cleared the held item, the clear also sticks against Saint Karma.
 * Role mechanics that remove a cooldown (NoellesRoles Catalyst, the Bomber pass) never come through here, so Karma
 * still re-covers those items.
 * 只标记管理员路径：SparkFactionAPI {@code /sparkfactionapi:clearCooldown}（0.1.5.12 至 0.1.5.15 方法体相同）。其
 * {@code remove} 真正清除了手持物品的冷却时，该清除对圣徒业障也保持有效。移除冷却的职业机制（NoellesRoles 催化剂、
 * 投弹手传递）不经过这里，因此业障仍会重新覆盖那些物品。
 */
@Mixin(targets = "dev.caecorthus.sparkfactionapi.command.admin.CooldownCommand")
public abstract class SparkFactionClearCooldownMixin {
    @WrapOperation(method = "clearCooldown", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;remove(Lnet/minecraft/item/Item;)V"), require = 1)
    private static void sparkwitch$markAdminClear(ItemCooldownManager manager, Item item, Operation<Void> original) {
        boolean wasCooling = manager.isCoolingDown(item);
        original.call(manager, item);
        if (wasCooling && manager instanceof ServerItemCooldownManager server && !manager.isCoolingDown(item)) {
            SparkWitchItemCooldownReleases.onAdminCleared(
                    ((ServerItemCooldownManagerAccessor) server).sparkwitch$getPlayer(), item);
        }
    }
}
