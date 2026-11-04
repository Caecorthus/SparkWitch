package dev.caecorthus.sparkwitch.roles.civilian.junker;

import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Stable contract: the 拾荒者 loot marker and the two exemption rules (owner-confirmed 2026-11-04).
 * <p>A looted stack carries {@link #MARKER} in its custom data. Room keys, walkie-talkies and the NoellesRoles neutral
 * master key never block the next search; walkie-talkies and the neutral master key are also never spent, while a
 * looted room key is spent when it opens a door.
 * 稳定契约：拾荒者战利品标记与两条豁免规则（所有者 2026-11-04 确认）。
 * <p>搜刮所得的物品在自定义数据里带有 {@link #MARKER}。房间钥匙、对讲机与 NoellesRoles 中立万能钥匙不挡下一次搜刮；
 * 对讲机与中立万能钥匙也永不消耗，而搜刮来的房间钥匙开门时会被消耗。
 */
public final class JunkerLoot {
    /** Custom-data key; namespaced like the other SparkWitch item markers. / 自定义数据键，与其它 SparkWitch 物品标记一样带命名空间。 */
    public static final String MARKER = "sparkwitch:ragpicker_loot";

    private static final Identifier NEUTRAL_MASTER_KEY_ID = Identifier.of("noellesroles", "neutral_master_key");

    /** The item kinds the exemption rules tell apart. / 豁免规则区分的物品类别。 */
    enum Kind {
        ROOM_KEY,
        WALKIE_TALKIE,
        NEUTRAL_MASTER_KEY,
        OTHER
    }

    private JunkerLoot() {
    }

    public static void mark(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.putBoolean(MARKER, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static boolean isLoot(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Key probe before the copy: the spend ledger checks every hotbar stack once per tick.
        // 先探测键再复制：消耗账本每 tick 都会检查每个快捷栏物品。
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(MARKER) && data.copyNbt().getBoolean(MARKER);
    }

    /** Loot that blocks the next search. / 会挡住下一次搜刮的战利品。 */
    public static boolean blocksSearch(ItemStack stack) {
        return isLoot(stack) && blocksSearch(kindOf(stack));
    }

    /** Loot that is spent once it has really been used. / 真正用过一次后会被消耗的战利品。 */
    public static boolean isDestroyable(ItemStack stack) {
        return isLoot(stack) && isDestroyable(kindOf(stack));
    }

    /** First blocking loot anywhere in the inventory (main, armour, off hand), or null. / 整个背包中第一件挡搜刮的战利品。 */
    public static ItemStack findBlockingLoot(PlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (blocksSearch(stack)) {
                return stack;
            }
        }
        return null;
    }

    public static int findEmptyHotbarSlot(PlayerEntity player) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    static boolean blocksSearch(Kind kind) {
        return kind == Kind.OTHER;
    }

    static boolean isDestroyable(Kind kind) {
        return kind == Kind.OTHER || kind == Kind.ROOM_KEY;
    }

    static Kind kindOf(ItemStack stack) {
        if (stack.isOf(WatheItems.KEY)) {
            return Kind.ROOM_KEY;
        }
        if (stack.isOf(WatheItems.WALKIE_TALKIE)) {
            return Kind.WALKIE_TALKIE;
        }
        return isNeutralMasterKey(stack) ? Kind.NEUTRAL_MASTER_KEY : Kind.OTHER;
    }

    private static boolean isNeutralMasterKey(ItemStack stack) {
        Item masterKey = Registries.ITEM.get(NEUTRAL_MASTER_KEY_ID);
        return masterKey != Items.AIR && stack.isOf(masterKey);
    }
}
