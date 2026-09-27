package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.doctor4t.wathe.util.AdventureUsable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Security Camera item; usable in adventure mode ({@link AdventureUsable} lets Wathe's adventure players use it on
 * blocks) and consumed on placement. The server decides every placement in {@code SeekerDeviceService.placeCamera};
 * the client only reports CONSUME.
 * 摄像头物品；冒险模式下可用（{@link AdventureUsable} 让 Wathe 的冒险模式玩家可以对方块使用），放置后消耗。
 * 每次放置都由服务端 {@code SeekerDeviceService.placeCamera} 决定；客户端只返回 CONSUME。
 */
public class SeekerCameraItem extends Item implements AdventureUsable {
    /** Tooltip lines {@code item.sparkwitch.seeker_camera.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 3;

    public SeekerCameraItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) {
            return ActionResult.CONSUME;
        }
        if (!(context.getPlayer() instanceof ServerPlayerEntity owner)) {
            return ActionResult.FAIL;
        }
        return SeekerDeviceService.placeCamera(owner, context);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.seeker_camera.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
