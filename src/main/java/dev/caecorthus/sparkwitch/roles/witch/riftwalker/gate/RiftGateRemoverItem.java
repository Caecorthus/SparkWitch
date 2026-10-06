package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Rift Gate Remover ({@code sparkwitch:rift_gate_remover}, 传送门清除工具): an operator tool, listed only in vanilla's
 * Operator Utilities tab. Right-clicking deletes the Rift Gate the user aims at; {@link RiftGateRemoverService} decides
 * everything on the server (permission, target, close). The client only reports CONSUME, so nothing swings before the
 * server agrees.
 * 传送门清除工具（{@code sparkwitch:rift_gate_remover}）：管理员工具，只出现在原版「管理员用品」物品栏。右键删除准星所指的
 * 裂隙门；权限、目标与关闭都由服务端 {@link RiftGateRemoverService} 决定。客户端只返回 CONSUME，服务端认可前不会挥手。
 */
public class RiftGateRemoverItem extends Item {
    /** Tooltip lines {@code item.sparkwitch.rift_gate_remover.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 2;

    public RiftGateRemoverItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(1).rarity(Rarity.EPIC);
    }

    /**
     * The hand a right-click reaches first holds the remover: the main hand, or the off-hand while the main hand is
     * empty (vanilla skips an empty main hand). A remover behind another main-hand item never changes targeting.
     * 右键最先用到的那只手持有清除工具：主手，或主手为空时的副手（原版会跳过空主手）。主手拿着别的物品时，副手的清除工具
     * 不改变准星选取。
     */
    public static boolean isReadyIn(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        ItemStack main = player.getMainHandStack();
        return main.getItem() instanceof RiftGateRemoverItem
                || main.isEmpty() && player.getOffHandStack().getItem() instanceof RiftGateRemoverItem;
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
        ActionResult result = RiftGateRemoverService.tryRemove(player);
        return new TypedActionResult<>(result, stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.rift_gate_remover.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
