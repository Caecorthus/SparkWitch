package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Control Expert Shock Device: a consumable, non-lethal throwable. The server throws a role-owned
 * {@link ShockDeviceEntity}, which resolves the landing stun. The client only swings the hand; vanilla's use-item
 * packet (which carries the aim) is the whole request, so no forged packet can place a stun anywhere.
 * 控场专家的电击装置：非致命的消耗型投掷物。服务端投出本职业自有的 {@link ShockDeviceEntity}，由其结算落点电击。
 * 客户端只做挥手反馈；原版使用物品数据包（已携带瞄准方向）就是全部请求，因此伪造的数据包无法在任意位置施加电击。
 */
public final class ShockDeviceItem extends Item {
    private static final int TOOLTIP_LINES = 3;
    private static final SoundEvent THROW_SOUND = SoundEvents.ENTITY_SNOWBALL_THROW;
    private static final float THROW_VOLUME = 0.5F;
    private static final float THROW_PITCH = 0.5F;

    public ShockDeviceItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity ce) || !(world instanceof ServerWorld serverWorld)
                || !ControlExpertTargeting.canUse(ce)
                || SparkTraitsKillerBridge.blocksWeaponAction(ce, stack)) {
            return TypedActionResult.fail(stack);
        }
        ShockDeviceEntity device = new ShockDeviceEntity(serverWorld, ce);
        device.setItem(stack.copyWithCount(1));
        device.setVelocity(ce, ce.getPitch(), ce.getYaw(), 0.0F, ControlExpertRules.SHOCK_THROW_SPEED,
                ControlExpertRules.SHOCK_THROW_DIVERGENCE);
        if (!serverWorld.spawnEntity(device)) {
            return TypedActionResult.fail(stack);
        }
        // Plain vanilla cooldown so SparkTraits Fast Hands may shorten it; it binds the Item, so a re-bought device
        // waits as well.
        // 普通原版冷却，SparkTraits 快手可缩短；冷却绑定在物品类型上，因此重新购买的装置同样需要等待。
        ce.getItemCooldownManager().set(this, ControlExpertRules.SHOCK_DEVICE_COOLDOWN);
        GameRecordManager.recordItemUse(ce, ControlExpertRules.SHOCK_DEVICE_ID, null, null);
        stack.decrementUnlessCreative(1, ce);
        serverWorld.playSound(null, ce.getX(), ce.getY(), ce.getZ(), THROW_SOUND, SoundCategory.PLAYERS,
                THROW_VOLUME, THROW_PITCH);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.shock_device.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
