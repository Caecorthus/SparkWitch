package dev.caecorthus.sparkwitch.roles.civilian.fisher.item;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherFishUse;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * An instantly-used fish. It carries no FoodComponent and never calls {@code PlayerEntity#eatFood}, so Wathe's EAT
 * task (a HEAD inject on {@code eatFood}) and DRINK task ({@code CocktailItem#finishUsing}) can never complete from it.
 * 即时使用的鱼。没有 FoodComponent，也从不调用 {@code PlayerEntity#eatFood}，因此 Wathe 的进食任务（{@code eatFood}
 * 头部注入）和饮酒任务（{@code CocktailItem#finishUsing}）永远不会因它完成。
 */
public final class FisherFishItem extends Item {
    private final FisherFishKind kind;

    public FisherFishItem(Settings settings, FisherFishKind kind) {
        super(settings);
        this.kind = kind;
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(FisherRules.FISH_MAX_COUNT);
    }

    public FisherFishKind kind() {
        return kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // No client prediction and no swing: the server decides and answers the user alone.
        // 客户端不预测、不挥手：由服务端决定并只回应使用者本人。
        if (world.isClient()) {
            return TypedActionResult.success(stack, false);
        }
        if (!(user instanceof ServerPlayerEntity player) || !FisherFishUse.use(player, stack, kind)) {
            return TypedActionResult.fail(stack);
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        FisherItemTooltips.append(tooltip, kind.itemId(), kind.tooltipLines());
        super.appendTooltip(stack, context, tooltip, type);
    }
}
