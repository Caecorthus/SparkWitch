package dev.caecorthus.sparkwitch.roles.killer.hunter;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Shared double-barrel ammo (2026-10-29): used with the shotgun in the other hand (or right-clicked onto it in the
 * inventory) it loads one shell through {@link DoubleBarrelShotgunItem#tryReload}, which pushes {@link #shellType()}
 * onto the gun's shell stack. Buckshot and slug differ only in that type and their tooltip key
 * ({@code <item key>.tooltip}).
 * 双管猎枪的共用弹药（2026-10-29）：另一只手持枪时使用（或在背包中右键猎枪）经 {@link DoubleBarrelShotgunItem#tryReload}
 * 装填一发，并把 {@link #shellType()} 压入枪的弹药栈。霰弹与独头弹只在该类型与提示键（{@code <物品键>.tooltip}）上不同。
 */
public abstract class DoubleBarrelAmmoItem extends Item {
    protected DoubleBarrelAmmoItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(16);
    }

    /** {@link HunterRules#SHELL_BUCKSHOT} or {@link HunterRules#SHELL_SLUG}. / 霰弹或独头弹。 */
    public abstract int shellType();

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack shells = user.getStackInHand(hand);
        ItemStack shotgun = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        if (!(shotgun.getItem() instanceof DoubleBarrelShotgunItem)) {
            return TypedActionResult.pass(shells);
        }
        if (!DoubleBarrelShotgunItem.tryReload(user, shotgun, shells)) {
            return TypedActionResult.fail(shells);
        }
        return TypedActionResult.success(shells, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(getTranslationKey() + ".tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
