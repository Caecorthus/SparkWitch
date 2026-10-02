package dev.caecorthus.sparkwitch.roles.civilian.blind.item;

import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * ComTac VIII ({@code sparkwitch:comtac_viii}): a bound head-slot {@link Equipment}, not an {@code ArmorItem} (no armor
 * material, no protection). It triples the perception range only while worn (D5). Equipping is silent: vanilla plays
 * the equip sound publicly at the wearer from every equip path (right-click, armor slot, {@code equipStack}), which
 * would expose the hidden headset, so the equip sound is {@code minecraft:intentionally_empty}. Right-click keeps
 * vanilla's equip swap but returns CONSUME on the client, so no arm swing is broadcast.
 * ComTac VIII（{@code sparkwitch:comtac_viii}）：绑定的头部槽 {@link Equipment}，不是 {@code ArmorItem}（无护甲材质、
 * 无防护）。仅在戴着时使感知距离 ×3（D5）。穿戴是静音的：原版在每条穿戴途径（右键、护甲槽、{@code equipStack}）都会在
 * 穿戴者处公开播放穿戴音效，会暴露隐藏的耳机，因此穿戴音效为 {@code minecraft:intentionally_empty}。右键沿用原版穿戴
 * 交换，但客户端返回 CONSUME，因此不会广播挥手动作。
 */
public final class ComTacItem extends Item implements Equipment {
    private static final int TOOLTIP_LINES = 2;

    public ComTacItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public RegistryEntry<SoundEvent> getEquipSound() {
        return Registries.SOUND_EVENT.getEntry(SoundEvents.INTENTIONALLY_EMPTY);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        TypedActionResult<ItemStack> result = equipAndSwap(this, world, user, hand);
        return result.getResult() == ActionResult.SUCCESS ? TypedActionResult.consume(result.getValue()) : result;
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
