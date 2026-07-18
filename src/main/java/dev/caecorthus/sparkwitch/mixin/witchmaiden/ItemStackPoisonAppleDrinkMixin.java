package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonAppleDrinkMarker;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonAppleDrinkRules;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.item.CocktailItem;
import dev.doctor4t.wathe.util.PoisonUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Wathe food poison once for marked cocktails whose item subclasses bypass CocktailItem.finishUsing. */
@Mixin(ItemStack.class)
public abstract class ItemStackPoisonAppleDrinkMixin {
    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void sparkwitch$applyMarkedCocktailPoison(
            World world,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!(user instanceof PlayerEntity player)
                || !PoisonAppleDrinkRules.shouldApply(
                        stack.getItem() instanceof CocktailItem,
                        PoisonAppleDrinkMarker.isMarked(stack)
                )) {
            return;
        }
        PoisonUtils.applyFoodPoison(player, stack);
        PoisonAppleDrinkMarker.clear(stack);
        stack.remove(WatheDataComponentTypes.POISONER);
    }
}
