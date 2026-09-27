package dev.caecorthus.sparkwitch.roles.civilian.seeker.device;

import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerCarState;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerStatusComponent;
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
 * The Search Car item: the "garage". It stays in its slot while deployed and carries every car cooldown (vanilla
 * {@code interactItem} already refuses use while it cools down). Server authority: READY → {@code deployCar};
 * DEPLOYED → the tablet hint (or, without SparkStrength, the client console opener has already intercepted the use).
 * The client only reports CONSUME so the use packet goes out without a swing or an off-hand fallthrough.
 * 搜寻小车物品即“车库”：部署后仍留在槽位中，并承载所有小车冷却（原版 {@code interactItem} 在冷却中已拒绝使用）。
 * 服务端权威：READY → {@code deployCar}；DEPLOYED → 提示改用平板（未装 SparkStrength 时客户端控制台打开器已先行拦截）。
 * 客户端只返回 CONSUME，使用数据包照常发出，但不挥手、也不落到副手。
 */
public class SeekerCarItem extends Item {
    /** Tooltip lines {@code item.sparkwitch.seeker_car.tooltip.line1..N}. / 物品提示行数。 */
    public static final int TOOLTIP_LINES = 4;

    public SeekerCarItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.consume(stack);
        }
        if (!(user instanceof ServerPlayerEntity owner)) {
            return TypedActionResult.fail(stack);
        }
        SeekerStatusComponent status = SeekerStatusComponent.KEY.getNullable(owner);
        SeekerCarState state = status == null ? SeekerCarState.NONE : status.carState();
        return switch (state) {
            case READY -> SeekerDeviceService.deployCar(owner, hand);
            case DEPLOYED -> SeekerDeviceService.useDeployedCarItem(owner, hand);
            case NONE, SWALLOWED -> TypedActionResult.fail(stack);
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.seeker_car.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
