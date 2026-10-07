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
 * blocks), stackable (the owner may place any number) and one is consumed per placement. The server decides every
 * placement in {@code SeekerDeviceService.placeCamera}; the client only reports CONSUME.
 * 摄像头物品；冒险模式下可用（{@link AdventureUsable} 让 Wathe 的冒险模式玩家可以对方块使用），可堆叠（拥有者可放置任意数量），
 * 每次放置消耗一个。每次放置都由服务端 {@code SeekerDeviceService.placeCamera} 决定；客户端只返回 CONSUME。
 */
public class SeekerCameraItem extends Item implements AdventureUsable {
    /** Tooltip lines {@code item.sparkwitch.seeker_camera.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 3;
    /** Cameras per stack; each shop purchase still arrives as its own stack. / 每组数量；每次商店购买仍单独成组。 */
    public static final int MAX_STACK = 16;

    public SeekerCameraItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(MAX_STACK);
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
