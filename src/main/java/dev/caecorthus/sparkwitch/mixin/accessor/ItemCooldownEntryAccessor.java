package dev.caecorthus.sparkwitch.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes only the vanilla cooldown end tick needed by Saint Karma.
 * 只暴露圣徒业障所需的原版物品冷却结束 tick。
 */
@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface ItemCooldownEntryAccessor {
    @Accessor("endTick")
    int sparkwitch$getEndTick();
}
