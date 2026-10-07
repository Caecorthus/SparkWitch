package dev.caecorthus.sparkwitch.item.ninja;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
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
 * Ninja Grappling Hook (owner 2026-10-07): right-click throws the hook, right-click again while it is latched pulls the
 * holder to it. Not a weapon. The server owns every decision through {@link NinjaGrappleService}; the client only
 * swings the hand. The item carries no cooldown while a hook is out, so vanilla's cooldown gate never swallows the
 * second right-click.
 * 忍者钩爪（所有者 2026-10-07）：右键投出钩爪，钩住后再次右键把持有者拉到钩点。不是武器。所有判断都由服务端通过
 * {@link NinjaGrappleService} 做出，客户端只挥手。钩爪在外时物品没有冷却，因此原版冷却检查不会吞掉第二次右键。
 */
public final class NinjaGrapplingHookItem extends Item {
    public NinjaGrapplingHookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // SparkTraits Last Escape forbids attacking and interacting (like the Rift Gate placement and corpse throw);
        // not the weapon gate, since the hook is no weapon. / SparkTraits 脱险期间禁止攻击与交互（同裂隙门放置与
        // 尸体投掷）；钩爪不是武器，因此不用武器门槛。
        if (SparkTraitsKillerBridge.isKillerInteractionBlocked(user)) {
            return TypedActionResult.fail(stack);
        }
        if (world.isClient()) {
            return user.isSpectator() ? TypedActionResult.pass(stack) : TypedActionResult.success(stack, true);
        }
        return user instanceof ServerPlayerEntity player && NinjaGrappleService.use(player)
                ? TypedActionResult.consume(stack)
                : TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.ninja_grappling_hook.desc").formatted(Formatting.GRAY));
    }
}
