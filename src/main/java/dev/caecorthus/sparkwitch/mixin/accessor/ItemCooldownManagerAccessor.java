package dev.caecorthus.sparkwitch.mixin.accessor;

import java.util.Map;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads vanilla cooldown entries so Karma can extend, but never shorten, an existing item cooldown.
 * 读取原版物品冷却条目，让业障只能延长而不会缩短已有冷却。
 */
@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessor {
    @Accessor("entries")
    Map<Item, ?> sparkwitch$getEntries();

    @Accessor("tick")
    int sparkwitch$getTick();
}
