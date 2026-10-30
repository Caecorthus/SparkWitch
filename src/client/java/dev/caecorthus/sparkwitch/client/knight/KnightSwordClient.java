package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client model wiring for the Knight's cross sword: {@code sparkwitch:using_item} selects the raised-blade model
 * while the holder is right-click using the sword, so first-person can rotate only during the raise.
 * 骑士十字剑的客户端模型接线：持有者右键蓄力时，{@code sparkwitch:using_item} 选中举剑模型，
 * 使第一人称旋转只在蓄力期间生效。
 */
public final class KnightSwordClient {
    public static final Identifier USING_ITEM = SparkWitch.id("using_item");

    private KnightSwordClient() {
    }

    public static void register() {
        FabricModelPredicateProviderRegistry.register(USING_ITEM, (stack, world, entity, seed) ->
                entity != null && entity.isUsingItem() && ItemStack.areEqual(entity.getActiveItem(), stack)
                        ? 1.0F : 0.0F);
    }
}
