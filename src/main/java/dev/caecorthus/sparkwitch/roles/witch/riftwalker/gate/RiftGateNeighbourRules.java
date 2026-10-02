package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.doctor4t.wathe.block.CrosshairEnabling;
import dev.doctor4t.wathe.block.DoorPartBlock;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block.MountableBlock;
import dev.doctor4t.wathe.block.VentHatchBlock;
import dev.doctor4t.wathe.index.tag.WatheBlockTags;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.registry.tag.BlockTags;
import org.jetbrains.annotations.Nullable;

/**
 * Riftwalker-owned copy of the Seeker camera's forbidden-neighbour table (plan §5.1; the Seeker module stays
 * untouched). A gate may not stand within one block of a door (Wathe door parts, vanilla doors, trapdoors, gates), a
 * vent hatch, a bed, a seat (every Wathe mountable block) or another interactive block (buttons, levers, Wathe
 * crosshair blocks such as cabinets and cargo boxes, food platters and drink trays): a closing cabin door would trap a
 * traveller, and a gate must never cover something people need to click. Server only (reads block states).
 * 本职业自有的搜寻者摄像头禁放邻居表副本（plan §5.1；不改动搜寻者模块）。门不得立在以下方块 1 格之内：门（Wathe 门、
 * 原版门、活板门、栅栏门）、通风口舱盖、床、座位（所有 Wathe 可乘坐方块）及其他可交互方块（按钮、拉杆、柜子与货箱等
 * Wathe 准星方块、餐盘与饮料托盘）：关上的包厢门会困住传送者，门也绝不能挡住别人需要点击的东西。仅服务端（读取方块状态）。
 */
public final class RiftGateNeighbourRules {
    private RiftGateNeighbourRules() {
    }

    public static boolean isForbiddenNeighbour(@Nullable BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof DoorPartBlock
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock
                || block instanceof VentHatchBlock
                || block instanceof BedBlock
                || block instanceof MountableBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof CrosshairEnabling
                || block instanceof FoodPlatterBlock
                || state.isIn(BlockTags.DOORS)
                || state.isIn(BlockTags.TRAPDOORS)
                || state.isIn(BlockTags.FENCE_GATES)
                || state.isIn(BlockTags.BEDS)
                || state.isIn(BlockTags.BUTTONS)
                || state.isIn(WatheBlockTags.VENT_HATCHES);
    }
}
