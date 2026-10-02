package dev.caecorthus.sparkwitch.roles.civilian.blind.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * ComTac VIII ({@code sparkwitch:comtac_viii}): a head-slot {@link Equipment}, not an {@code ArmorItem} (no armor
 * material, no protection). Implementing {@code Equipment} lets the vanilla armor slot accept it, and {@link #use}
 * is vanilla's right-click equip swap. It triples the perception range only while worn (D5); the shop, binding and
 * in-round hidden model belong to later work packages.
 * ComTac VIII（{@code sparkwitch:comtac_viii}）：头部槽 {@link Equipment}，不是 {@code ArmorItem}（无护甲材质、无防护）。
 * 实现 {@code Equipment} 使原版护甲槽接受它，{@link #use} 即原版右键穿戴交换。仅在戴着时使感知距离 ×3（D5）；
 * 商店、绑定与局内隐藏模型由后续工作包实现。
 */
public final class ComTacItem extends Item implements Equipment {
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
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }
}
