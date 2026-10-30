package dev.caecorthus.sparkwitch.client.knight;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.knight.CrossSwordItem;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client wiring for the Knight's cross sword: {@code sparkwitch:using_item} selects the raised-blade model while the
 * holder is right-click using the sword, so first-person can rotate only during the raise; the release hook installs
 * the client stab sender ({@link CrossSwordClientStab}).
 * 骑士十字剑的客户端接线：持有者右键蓄力时，{@code sparkwitch:using_item} 选中举剑模型，使第一人称旋转只在蓄力期间
 * 生效；松手钩子安装客户端刺击发送器（{@link CrossSwordClientStab}）。
 */
public final class KnightSwordClient {
    public static final Identifier USING_ITEM = SparkWitch.id("using_item");

    private KnightSwordClient() {
    }

    public static void register() {
        FabricModelPredicateProviderRegistry.register(USING_ITEM, (stack, world, entity, seed) ->
                entity != null && entity.isUsingItem() && ItemStack.areEqual(entity.getActiveItem(), stack)
                        ? 1.0F : 0.0F);
        CrossSwordItem.installClientRelease(CrossSwordClientStab::onRelease);
    }
}
