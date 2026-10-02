package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import net.minecraft.util.math.BlockPos;

/**
 * Pure Deep Dark Zone block rules: which blocks may wear a fake sculk/deepslate look and which look each gets.
 * Only an opaque full cube with a full collision cube and vanilla movement multipliers may convert, and it becomes
 * another opaque full cube, so collision, lighting and client movement prediction stay identical while the server
 * world never changes. Never filter by the {@code wathe:} namespace: the Harpy Express floors are Wathe blocks.
 * 深暗领域的纯方块规则：哪些方块可以显示假的幽匿/深板岩外观，以及各自显示成什么。只有碰撞箱为完整立方体、移动倍率为原版默认值的
 * 不透明完整方块才能转换，并且只会变成另一个不透明完整方块，因此碰撞、光照与客户端移动预测保持一致，而服务端世界从不改变。
 * 不得按 {@code wathe:} 命名空间排除：Harpy 列车的地板本身就是 Wathe 方块。
 */
public final class DeepDarkZoneEligibility {
    /** Vanilla defaults; any other value would desync client movement over a fake block. / 原版默认值。 */
    static final float DEFAULT_SLIPPERINESS = 0.6F;
    static final float DEFAULT_VELOCITY_MULTIPLIER = 1.0F;
    static final float DEFAULT_JUMP_VELOCITY_MULTIPLIER = 1.0F;
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String WATHE_NAMESPACE = "wathe";
    private static final String SCULK_PATH = "sculk";
    private static final String DEEPSLATE_PATH_PART = "deepslate";
    /** 9 of 15 buckets (60 %) are sculk; the other three looks get 2 each. / 15 个桶中 9 个（60%）为幽匿块。 */
    private static final int LOOK_BUCKETS = 15;
    private static final int SCULK_BUCKETS = 9;
    private static final int OTHER_LOOK_BUCKETS = 2;

    private DeepDarkZoneEligibility() {
    }

    /**
     * Server-read facts about one block, gathered once at the landing snapshot (research/04 §4.2).
     * 落地快照时读取一次的方块事实（research/04 §4.2）。
     *
     * @param propertyCount   number of block-state properties / 方块状态属性数量
     * @param hasAxisProperty the state has {@code Properties.AXIS} (pillars) / 是否带朝向轴属性（柱子）
     * @param immune          in the {@code sparkwitch:sculk_conversion_immune} block tag / 在免疫方块标签内
     * @param deepDarkLook    already deep-dark palette, see {@link #isDeepDarkPalette} / 本身已是深暗调色板方块
     */
    public record Facts(
            boolean insidePlayArea,
            boolean insideResetTemplate,
            boolean air,
            boolean fluid,
            boolean blockEntity,
            boolean modelRender,
            boolean opaqueFullCube,
            boolean fullCollisionCube,
            int luminance,
            boolean emitsRedstonePower,
            int propertyCount,
            boolean hasAxisProperty,
            boolean fallingBlock,
            float slipperiness,
            float velocityMultiplier,
            float jumpVelocityMultiplier,
            String namespace,
            boolean immune,
            boolean deepDarkLook
    ) {
    }

    /** The four fake looks; floors are always sculk. / 四种假外观；地板总是幽匿块。 */
    public enum Look {
        SCULK,
        DEEPSLATE_TILES,
        DEEPSLATE_BRICKS,
        COBBLED_DEEPSLATE
    }

    /**
     * The research/04 §4.2 predicate. Every rule fails closed; it keeps plain blocks and axis-only pillars and drops
     * lamps, cabinets, cargo boxes, privacy glass, vent shafts, culling hulls, glass, slabs, carpets and doors.
     * research/04 §4.2 的判定。每条规则都安全失败；只保留普通方块与仅带朝向轴的柱子，排除灯、柜子、货箱、隐私玻璃、
     * 通风管、剔除外壳、玻璃、台阶、地毯与门。
     */
    public static boolean isConvertible(Facts facts) {
        if (!facts.insidePlayArea() || facts.insideResetTemplate()) {
            return false;
        }
        if (facts.air() || facts.fluid() || facts.blockEntity() || !facts.modelRender()) {
            return false;
        }
        if (!facts.opaqueFullCube() || !facts.fullCollisionCube()) {
            return false;
        }
        if (facts.luminance() > 0 || facts.emitsRedstonePower()) {
            return false;
        }
        if (facts.propertyCount() != 0 && !(facts.propertyCount() == 1 && facts.hasAxisProperty())) {
            return false;
        }
        if (facts.fallingBlock()
                || facts.slipperiness() != DEFAULT_SLIPPERINESS
                || facts.velocityMultiplier() != DEFAULT_VELOCITY_MULTIPLIER
                || facts.jumpVelocityMultiplier() != DEFAULT_JUMP_VELOCITY_MULTIPLIER) {
            return false;
        }
        if (!MINECRAFT_NAMESPACE.equals(facts.namespace()) && !WATHE_NAMESPACE.equals(facts.namespace())) {
            return false;
        }
        return !facts.immune() && !facts.deepDarkLook();
    }

    /**
     * Already deep-dark palette, by block id path: sculk, or any deepslate-family block (every id containing
     * {@code deepslate}: the stone, its cobbled/polished/brick/tile/chiseled/cracked forms, every
     * {@code deepslate_*_ore}, {@code infested_deepslate}, {@code reinforced_deepslate}). Such a block is never converted
     * and never counted as a zone cell. The id match covers every variant without a hand-kept list.
     * 按方块 id 路径判断是否已属深暗调色板：幽匿块，或任何深板岩家族方块（所有 id 含 {@code deepslate} 的方块：深板岩本身、
     * 其圆石/磨制/砖/瓦/雕纹/裂纹形式、所有 {@code deepslate_*_ore}、{@code infested_deepslate}、{@code reinforced_deepslate}）。
     * 这类方块从不转换，也从不计为领域格子。按 id 匹配可覆盖所有变种，无需手工维护列表。
     */
    public static boolean isDeepDarkPalette(String path) {
        return SCULK_PATH.equals(path) || path.contains(DEEPSLATE_PATH_PART);
    }

    /**
     * Deterministic look of a converted cell, so overlapping zones always agree: an upward-exposed floor is sculk,
     * every other face hashes its position (60 % sculk, the rest split between the three deepslate looks).
     * 转换格的确定性外观，使重叠领域始终一致：朝上暴露的地板为幽匿块，其余按位置哈希（60% 幽匿块，其余平分给三种深板岩）。
     */
    public static Look look(BlockPos pos, boolean floor) {
        if (floor) {
            return Look.SCULK;
        }
        int bucket = (int) Long.remainderUnsigned(mix(pos.asLong()), LOOK_BUCKETS);
        if (bucket < SCULK_BUCKETS) {
            return Look.SCULK;
        }
        if (bucket < SCULK_BUCKETS + OTHER_LOOK_BUCKETS) {
            return Look.DEEPSLATE_TILES;
        }
        if (bucket < SCULK_BUCKETS + 2 * OTHER_LOOK_BUCKETS) {
            return Look.DEEPSLATE_BRICKS;
        }
        return Look.COBBLED_DEEPSLATE;
    }

    /** SplitMix64 finalizer: neighbouring cells get unrelated looks. / SplitMix64 混合：相邻格外观互不相关。 */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
