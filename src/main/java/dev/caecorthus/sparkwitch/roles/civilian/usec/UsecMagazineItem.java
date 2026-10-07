package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A loose USEC magazine. Its rounds live in vanilla {@code CUSTOM_DATA} under {@link #NBT_KEY} as
 * {@link UsecMagazineContents} NBT (bottom to top). The name lists the rounds in firing order, each tinted by its type
 * (Q9, after the NoellesRoles Bartender base-spirit naming), e.g. "Magazine [AP·FMJ·FMJ]". Loading rounds is owned by
 * the attachment work package, not this class.
 * 散装的 USEC 弹匣。子弹以 {@link UsecMagazineContents} NBT（自下而上）存放在原版 {@code CUSTOM_DATA} 的
 * {@link #NBT_KEY} 下。名称按发射顺序列出子弹，并按弹种着色（Q9，参照 NoellesRoles 酒保基酒的命名），例如
 * 「弹匣 [AP·FMJ·FMJ]」。装弹由配件工作包负责，不在本类实现。
 */
public class UsecMagazineItem extends Item {
    /** Stable NBT key; do not rename. / 稳定的 NBT 键名，不得改名。 */
    public static final String NBT_KEY = "UsecMagazine";
    static final String LOADED_NAME_KEY = "item.sparkwitch.usec_magazine.loaded_name";
    static final String SEPARATOR_KEY = "item.sparkwitch.usec_magazine.separator";
    static final String LOADED_TOOLTIP_KEY = "item.sparkwitch.usec_magazine.tooltip.loaded";
    static final String FREE_TOOLTIP_KEY = "item.sparkwitch.usec_magazine.tooltip.free";
    static final String ORDER_TOOLTIP_KEY = "item.sparkwitch.usec_magazine.tooltip.order";

    public UsecMagazineItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /** Tolerant read; a missing or malformed key is an empty magazine. / 宽容读取；缺失或残缺的键视为空弹匣。 */
    public static UsecMagazineContents contents(ItemStack magazine) {
        NbtComponent customData = magazine.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return UsecMagazineContents.EMPTY;
        }
        return UsecMagazineContents.fromNbt(customData.copyNbt().get(NBT_KEY));
    }

    /**
     * Writes the contents; an empty magazine drops the key (and the component when nothing else is stored), so a new
     * and an emptied magazine are identical stacks.
     * 写入内容；空弹匣会移除该键（没有其他数据时移除整个组件），使新弹匣与清空的弹匣完全一致。
     */
    public static void setContents(ItemStack magazine, @Nullable UsecMagazineContents contents) {
        NbtComponent customData = magazine.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = customData == null ? new NbtCompound() : customData.copyNbt();
        if (contents == null || contents.isEmpty()) {
            nbt.remove(NBT_KEY);
        } else {
            nbt.put(NBT_KEY, contents.toNbt());
        }
        if (nbt.isEmpty()) {
            magazine.remove(DataComponentTypes.CUSTOM_DATA);
        } else {
            magazine.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        }
    }

    /**
     * Round labels joined by the separator, each tinted by its type; pass {@link UsecMagazineContents#firingOrder()}.
     * 以分隔符连接、按弹种着色的子弹标签；传入 {@link UsecMagazineContents#firingOrder()}。
     */
    public static MutableText roundsText(List<UsecAmmoType> rounds) {
        MutableText text = Text.empty();
        for (int i = 0; i < rounds.size(); i++) {
            if (i > 0) {
                text.append(Text.translatable(SEPARATOR_KEY));
            }
            text.append(rounds.get(i).label());
        }
        return text;
    }

    @Override
    public Text getName(ItemStack stack) {
        UsecMagazineContents contents = contents(stack);
        Text base = super.getName(stack);
        if (contents.isEmpty()) {
            return base;
        }
        return Text.translatable(LOADED_NAME_KEY, base, roundsText(contents.firingOrder()));
    }

    /** Loading only rewrites CUSTOM_DATA; never replay the equip animation. / 装弹只改写 CUSTOM_DATA；不重播换手动画。 */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        UsecMagazineContents contents = contents(stack);
        tooltip.add(Text.translatable(LOADED_TOOLTIP_KEY, contents.size(), UsecRules.MAGAZINE_CAPACITY)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(FREE_TOOLTIP_KEY, contents.freeSlots()).formatted(Formatting.GRAY));
        if (!contents.isEmpty()) {
            tooltip.add(Text.translatable(ORDER_TOOLTIP_KEY, roundsText(contents.firingOrder()))
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
