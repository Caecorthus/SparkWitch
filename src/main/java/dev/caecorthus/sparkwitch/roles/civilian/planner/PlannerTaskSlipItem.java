package dev.caecorthus.sparkwitch.roles.civilian.planner;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 任务单 Task slip ({@code sparkwitch:planner_task_slip}), sold only in the planner's shop
 * ({@link PlannerRules#SLIP_PRICE}, re-buyable) and usable by whoever holds it: right-click with a player under the
 * crosshair rerolls that player's tasks, right-click at nothing rerolls one's own ({@link PlannerTaskSlipService}).
 * Used up on success; a refusal keeps it. {@code maxCount(1)}.
 * <p>Client/server authority: both sides refuse a player outside an ACTIVE round from synced state, so the client never
 * swings for a use the server cannot accept; the target, the reroll, the consumption and the replay are server-only.
 * 任务单（{@code sparkwitch:planner_task_slip}），只在规划大师的商店出售（{@link PlannerRules#SLIP_PRICE}，可重复购买），谁拿到
 * 都能用：准星下有玩家时右键重排该玩家的任务，没有目标时重排自己的（{@link PlannerTaskSlipService}）。成功时用掉；被拒绝时
 * 保留。{@code maxCount(1)}。
 * <p>客户端/服务端权威：两端都依据已同步状态拒绝不在 ACTIVE 回合中的玩家，因此客户端不会为服务端无法接受的使用挥手；目标、
 * 重排、消耗与回放都只在服务端执行。
 */
public final class PlannerTaskSlipItem extends Item {
    public PlannerTaskSlipItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!PlannerTaskSlipService.inActiveRound(user)) {
            return TypedActionResult.fail(stack);
        }
        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }
        if (user instanceof ServerPlayerEntity player && PlannerTaskSlipService.use(player)) {
            stack.decrementUnlessCreative(1, player);
            return TypedActionResult.success(stack, false);
        }
        return TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        // Stack data only: the creative search index builds tooltips off-thread with no player.
        // 只读物品堆数据：创造模式搜索索引会在后台线程、无玩家时构建提示文本。
        tooltip.add(Text.translatable("item.sparkwitch.planner_task_slip.tooltip.usage").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.sparkwitch.planner_task_slip.tooltip.self")
                .formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
