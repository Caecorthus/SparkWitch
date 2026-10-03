package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Rift Gate item ({@code sparkwitch:rift_gate}): stackable, bought for mana, hidden in hand (NoellesHiddenEquipment).
 * Using it places a gate at the user's feet; the server decides every placement in
 * {@link RiftGatePlacementService#tryPlace} (which also consumes one item on success). The client only reports CONSUME,
 * so nothing swings before the server agrees.
 * 裂隙门物品（{@code sparkwitch:rift_gate}）：可堆叠、以魔力购买、手持时对他人隐藏（NoellesHiddenEquipment）。
 * 使用时在脚下放置一扇门；每次放置都由服务端 {@link RiftGatePlacementService#tryPlace} 决定（成功时同时消耗一个物品）。
 * 客户端只返回 CONSUME，服务端认可前不会挥手。
 */
public class RiftGateItem extends Item {
    /** Tooltip lines {@code item.sparkwitch.rift_gate.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 3;

    public RiftGateItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(RiftwalkerRules.GATE_MAX_STACK);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.consume(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.fail(stack);
        }
        ActionResult result = RiftGatePlacementService.tryPlace(player, hand);
        return new TypedActionResult<>(result, user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.rift_gate.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
