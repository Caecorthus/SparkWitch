package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * 信息纸条 — what the detective received when a badge holder died: the killer's name and role.
 * 信息纸条——徽章持有者死亡时侦探收到的报告：凶手的名字与职业。
 */
public final class SherryNoteItem extends Item {

    public SherryNoteItem(Settings settings) {
        super(settings);
    }

    private static NbtCompound data(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            NbtCompound data = data(stack);
            String killer = data.getString("killer_name");
            String role = data.getString("killer_role");
            user.sendMessage(Text.translatable("message.sparkwitch.sherry.note.read", killer, role)
                    .formatted(Formatting.GOLD), true);
        }
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound data = data(stack);
        if (data.contains("killer_name")) {
            tooltip.add(Text.translatable("message.sparkwitch.sherry.note.read",
                    data.getString("killer_name"), data.getString("killer_role"))
                    .formatted(Formatting.GOLD));
        } else {
            tooltip.add(Text.translatable("item.sparkwitch.sherry_note.tooltip").formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable("item.sparkwitch.sherry_note.tooltip.hand").formatted(Formatting.DARK_GRAY));
    }
}
