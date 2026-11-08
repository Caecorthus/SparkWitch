package dev.caecorthus.sparkwitch.roles.civilian.bear.smoke;

import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The passability seam of the smoke flood fill: the only world facts the pure shape code reads, one block at a time.
 * The server adapter is {@link WorldSmokeBlockView}; tests use fake grids.
 * 烟雾泛洪的可通行接缝：纯形状代码唯一读取的世界事实，按方块逐个读取。服务端适配为 {@link WorldSmokeBlockView}；
 * 测试使用假网格。
 */
public interface SmokeBlockView {
    /**
     * The block's collision boxes in block-local coordinates (0..1 inside the block), as
     * {@code getCollisionShape(..., ShapeContext.absent())} reports them; an empty list means no collision. Null means
     * smoke can never enter the block (fluid, unloaded chunk, outside the build limit).
     * 该方块在方块局部坐标（方块内为 0..1）下的碰撞箱，与 {@code getCollisionShape(..., ShapeContext.absent())} 一致；
     * 空列表表示没有碰撞。返回 null 表示烟永远进不去（流体、未加载区块、超出建筑高度）。
     */
    @Nullable
    List<Box> collisionBoxes(int x, int y, int z);

    /** True when the block sees the open sky (its smoke lives half as long). / 该方块露天时为 true（其中的烟寿命减半）。 */
    boolean seesSky(int x, int y, int z);
}
