package dev.caecorthus.sparkwitch.mixin.recruit;

import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the private vanilla pickup delay so the Recruit's whitelist path honours the same 40-tick wait as ordinary
 * item pickup.
 * 读取原版私有拾取延迟，使新兵白名单路径与普通物品拾取一样遵守相同的 40 tick 等待。
 */
@Mixin(ItemEntity.class)
public interface RecruitItemEntityAccessor {
    @Accessor("pickupDelay")
    int sparkwitch$getPickupDelay();
}
