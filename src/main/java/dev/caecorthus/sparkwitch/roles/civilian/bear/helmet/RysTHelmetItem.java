package dev.caecorthus.sparkwitch.roles.civilian.bear.helmet;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The black Rys-T helmet with face shield ({@code sparkwitch:rys_t}), worn in the head slot. Its ricochet (H1-H6)
 * applies to whoever wears it (D1); its visor state and glass cracks live in
 * {@link SparkWitchDataComponents#RYS_T_VISOR} (default {@link RysTVisor#DEFAULT}: visor down, no cracks). WP2: it is a
 * head-slot {@link Equipment} (not an {@code ArmorItem}: no armor material, no protection, no armor points), so vanilla
 * {@code ArmorSlot.canInsert} takes it in the head slot only and a right-click in hand equips it; vanilla
 * {@code HeadFeatureRenderer} draws its item model ({@code display.head}) on the wearer for everyone. The visor is
 * toggled only by {@link RysTVisorToggle} (right-click in the head slot, H9). The ricochet is WP3.
 * 黑色 Rys-T 头盔与面罩（{@code sparkwitch:rys_t}），戴在头部槽。其跳弹（H1-H6）作用于佩戴者（D1）；面罩状态与玻璃裂纹存于
 * {@link SparkWitchDataComponents#RYS_T_VISOR}（默认 {@link RysTVisor#DEFAULT}：面罩放下、无裂纹）。WP2：它是头部槽
 * {@link Equipment}（不是 {@code ArmorItem}：无护甲材质、无防护、无护甲值），因此原版 {@code ArmorSlot.canInsert} 只允许它放入
 * 头部槽，手持右键即可戴上；原版 {@code HeadFeatureRenderer} 为所有人在佩戴者头上绘制其物品模型（{@code display.head}）。面罩
 * 只能经由 {@link RysTVisorToggle} 开合（在头部槽右键，H9）。跳弹归 WP3。
 */
public class RysTHelmetItem extends Item implements Equipment {
    static final String TOOLTIP_VISOR_DOWN_KEY = "item.sparkwitch.rys_t.tooltip.visor_down";
    static final String TOOLTIP_VISOR_UP_KEY = "item.sparkwitch.rys_t.tooltip.visor_up";
    static final String TOOLTIP_CRACKS_KEY = "item.sparkwitch.rys_t.tooltip.cracks";
    static final String TOOLTIP_HINT_KEY = "item.sparkwitch.rys_t.tooltip.hint";

    public RysTHelmetItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1).component(SparkWitchDataComponents.RYS_T_VISOR, RysTVisor.DEFAULT);
    }

    /** The stack's visor, {@link RysTVisor#DEFAULT} when absent. / 物品堆的面罩状态；缺失时为 {@link RysTVisor#DEFAULT}。 */
    public static RysTVisor visor(ItemStack stack) {
        return stack.getOrDefault(SparkWitchDataComponents.RYS_T_VISOR, RysTVisor.DEFAULT);
    }

    /**
     * The Rys-T worn in {@code entity}'s head slot, or {@link ItemStack#EMPTY}. Side-safe.
     * {@code entity} 头部槽中佩戴的 Rys-T；没有时为 {@link ItemStack#EMPTY}。两端通用。
     */
    public static ItemStack wornHelmet(@Nullable LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        ItemStack head = entity.getEquippedStack(EquipmentSlot.HEAD);
        return head.getItem() instanceof RysTHelmetItem ? head : ItemStack.EMPTY;
    }

    /** The worn Rys-T's visor, or null when none is worn. / 所戴 Rys-T 的面罩状态；未佩戴时为 null。 */
    public static @Nullable RysTVisor wornVisor(@Nullable LivingEntity entity) {
        ItemStack worn = wornHelmet(entity);
        return worn.isEmpty() ? null : visor(worn);
    }

    /**
     * Stable contract (WP2, for the WP3 ricochet; server only): appends {@code crack} to the glass of the Rys-T
     * {@code player} wears ({@link RysTVisor#withCrack}: at most {@code MAX_VISOR_CRACKS}, the oldest dropped). The
     * stack is changed in place, so the wearer's screen-handler sync and vanilla's equipment sync carry it to the wearer
     * and to every tracking client; no packet of its own. Returns false (nothing written) when no Rys-T is worn.
     * 稳定契约（WP2，供 WP3 跳弹使用；仅服务端）：把 {@code crack} 追加到 {@code player} 所戴 Rys-T 的玻璃上
     * （{@link RysTVisor#withCrack}：至多 {@code MAX_VISOR_CRACKS} 个，丢弃最早的）。原地修改物品堆，因此佩戴者的界面处理器同步
     * 与原版装备同步会把它带给佩戴者与所有追踪该实体的客户端；无独立数据包。未佩戴 Rys-T 时返回 false（不写入）。
     */
    public static boolean addCrack(ServerPlayerEntity player, @Nullable VisorCrack crack) {
        ItemStack worn = wornHelmet(player);
        if (crack == null || worn.isEmpty()) {
            return false;
        }
        worn.set(SparkWitchDataComponents.RYS_T_VISOR, visor(worn).withCrack(crack));
        return true;
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public RegistryEntry<SoundEvent> getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_IRON;
    }

    /** Right-click in hand: vanilla's equip swap onto the head. / 手持右键：原版穿戴交换到头部。 */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }

    /** The visor toggle (H9); every other click stays vanilla. / 面罩开合（H9）；其他点击保持原版。 */
    @Override
    public boolean onClicked(ItemStack stack, ItemStack otherStack, Slot slot, ClickType clickType,
                             PlayerEntity player, StackReference cursorStackReference) {
        return RysTVisorToggle.onHelmetClicked(stack, otherStack, slot, clickType, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        RysTVisor visor = visor(stack);
        tooltip.add(Text.translatable(visor.down() ? TOOLTIP_VISOR_DOWN_KEY : TOOLTIP_VISOR_UP_KEY)
                .formatted(Formatting.GRAY));
        if (!visor.cracks().isEmpty()) {
            tooltip.add(Text.translatable(TOOLTIP_CRACKS_KEY, visor.cracks().size()).formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable(TOOLTIP_HINT_KEY).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
