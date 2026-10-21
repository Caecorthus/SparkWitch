package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * The Homing Feather (归羽) item {@code sparkwitch:homing_feather}: display only. The crow hotbar (crow-client) and the
 * marker renderer draw its icon; it is never obtainable, never in a creative tab or shop, and a stack that reaches a
 * player inventory anyway (a command) is deleted on the server's next inventory tick.
 * 归羽物品 {@code sparkwitch:homing_feather}：仅用于显示。乌鸦快捷栏（crow-client）与标记渲染器绘制其图标；它不可获得，不进入创造物品栏
 * 或商店，若仍有物品堆进入玩家物品栏（例如通过命令），服务端会在下一次物品栏刻将其删除。
 */
public final class BlackRavenHomingFeatherItem extends Item {
    public BlackRavenHomingFeatherItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        // Server authority: the client copy disappears with the next slot sync. / 服务端权威：客户端副本随下一次栏位同步消失。
        if (!world.isClient()) {
            stack.setCount(0);
        }
    }
}
