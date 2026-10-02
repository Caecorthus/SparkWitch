package dev.caecorthus.sparkwitch.roles.civilian.blind.item;

import net.minecraft.item.Item;

/**
 * The Blind's White Cane ({@code sparkwitch:white_cane}). Registration placeholder: the server-validated use, binding
 * and cooldown belong to the kit work package. Hidden from other living players through NoellesRoles' equipment filter.
 * 盲人的盲杖（{@code sparkwitch:white_cane}）。仅为注册占位：服务端校验的使用、绑定与冷却由道具工作包实现。
 * 经由 NoellesRoles 装备过滤对其他存活玩家隐藏。
 */
public final class WhiteCaneItem extends Item {
    public WhiteCaneItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }
}
