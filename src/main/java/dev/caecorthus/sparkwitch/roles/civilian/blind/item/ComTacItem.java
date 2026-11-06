package dev.caecorthus.sparkwitch.roles.civilian.blind.item;

import dev.caecorthus.sparkwitch.headset.HeadsetSlots;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * ComTac VIII ({@code sparkwitch:comtac_viii}): a bound headset worn in the headset slot
 * ({@code dev.caecorthus.sparkwitch.headset.HeadsetSlots}, E1 2026-11-05), never on the head: it is neither an
 * {@code ArmorItem} nor an {@code Equipment}, so vanilla never equips it to an armor slot. It triples the perception
 * range only while worn there (D5). Wearing it is silent and invisible to others: the headset slot is not an equipment
 * slot, so no equip sound plays and no model is drawn on anyone. Right-click moves it from the hand into an EMPTY
 * headset slot (the server writes; no sound) and returns CONSUME on the client, so no arm swing is broadcast.
 * ComTac VIII（{@code sparkwitch:comtac_viii}）：戴在耳机槽（{@code dev.caecorthus.sparkwitch.headset.HeadsetSlots}，E1
 * 2026-11-05）中的绑定耳机，从不戴在头上：它既不是 {@code ArmorItem} 也不是 {@code Equipment}，因此原版从不把它穿到护甲槽。
 * 仅在戴于耳机槽时使感知距离 ×3（D5）。佩戴无声且他人不可见：耳机槽不是装备槽，因此不播放穿戴音效，也不会在任何人身上绘制模型。
 * 右键把它从手中放入空的耳机槽（由服务端写入，无音效），客户端返回 CONSUME，因此不会广播挥手动作。
 */
public final class ComTacItem extends Item {
    private static final int TOOLTIP_LINES = 2;

    public ComTacItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        return HeadsetSlots.insertFromHand(user, hand) ? TypedActionResult.consume(held) : TypedActionResult.pass(held);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.comtac_viii.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
