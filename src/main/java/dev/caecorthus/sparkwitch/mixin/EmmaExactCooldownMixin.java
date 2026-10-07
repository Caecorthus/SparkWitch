package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaCooldowns;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Only the explicit fallback write scope changes entries; ordinary cooldowns are untouched.
 * 仅显式回退写入作用域可修改条目，普通冷却保持原样。 */
@Mixin(value = ItemCooldownManager.class, priority = 800)
public abstract class EmmaExactCooldownMixin {
    @ModifyArgs(method = "set", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager$Entry;<init>(II)V"))
    private void sparkwitch$emmaExactEntry(Args args, Item item, int duration) {
        Integer exact = EmmaCooldowns.exactDuration((ItemCooldownManager) (Object) this, item);
        if (exact != null) args.set(1, (int) args.get(0) + exact);
    }

    @ModifyArg(method = "set", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;onCooldownUpdate(Lnet/minecraft/item/Item;I)V"), index = 1)
    private int sparkwitch$emmaExactPacket(Item item, int duration) {
        Integer exact = EmmaCooldowns.exactDuration((ItemCooldownManager) (Object) this, item);
        return exact == null ? duration : exact;
    }
}
