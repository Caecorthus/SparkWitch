package dev.caecorthus.sparkwitch.roles.civilian.saint.flash;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTargeting;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Holy Flash ({@code sparkwitch:holy_flash}): the Saint's shop flashbang. Any participant holding one may throw it
 * (owner decision: no role check); right-click throws at once like Wathe's grenade. The server spawns a role-owned
 * {@link HolyFlashEntity}; vanilla's use-item packet is the whole request, so no forged packet can place a burst.
 * 圣光弹（{@code sparkwitch:holy_flash}）：圣徒商店里的闪光弹。任何持有它的参与者都能投掷（所有者决定：不限职业）；
 * 与 Wathe 手雷一样右键立即投出。服务端生成本职业自有的 {@link HolyFlashEntity}；原版使用物品数据包就是全部请求，
 * 因此伪造的数据包无法在任意位置制造爆闪。
 */
public final class HolyFlashItem extends Item {
    private static final int TOOLTIP_LINES = 3;
    private static final float THROW_VOLUME = 0.5F;
    private static final float THROW_SPEED = 0.5F;
    private static final float THROW_DIVERGENCE = 1.0F;
    private static final double THROW_EYE_OFFSET = 0.1D;

    public HolyFlashItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(HolyFlashRules.CARRY_LIMIT);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity thrower) || !(world instanceof ServerWorld serverWorld)
                || !HolyFlashRules.isActivePhase(serverWorld)
                || !ControlExpertTargeting.isParticipant(thrower)
                || SparkTraitsKillerBridge.blocksWeaponAction(thrower, stack)) {
            return TypedActionResult.fail(stack);
        }
        HolyFlashEntity flash = new HolyFlashEntity(serverWorld, thrower);
        flash.setItem(stack.copyWithCount(1));
        flash.setPos(thrower.getX(), thrower.getEyeY() - THROW_EYE_OFFSET, thrower.getZ());
        flash.setVelocity(thrower, thrower.getPitch(), thrower.getYaw(), 0.0F, THROW_SPEED, THROW_DIVERGENCE);
        if (!serverWorld.spawnEntity(flash)) {
            return TypedActionResult.fail(stack);
        }
        serverWorld.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), WatheSounds.ITEM_GRENADE_THROW,
                SoundCategory.NEUTRAL, THROW_VOLUME, 1.0F + (serverWorld.random.nextFloat() - 0.5F) / 10.0F);
        GameRecordManager.recordItemUse(thrower, HolyFlashRules.ITEM_ID, null, null);
        // Plain vanilla cooldown bound to the Item, so every copy (and a re-bought one) waits.
        // 普通原版冷却绑定在物品类型上，因此每一个（包括重新购买的）都要等待。
        if (!thrower.isCreative()) {
            thrower.getItemCooldownManager().set(this, HolyFlashRules.USE_COOLDOWN_TICKS);
        }
        thrower.incrementStat(Stats.USED.getOrCreateStat(this));
        stack.decrementUnlessCreative(1, thrower);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.holy_flash.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
