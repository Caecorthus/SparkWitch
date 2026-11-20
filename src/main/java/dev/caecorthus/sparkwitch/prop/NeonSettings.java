package dev.caecorthus.sparkwitch.prop;

import dev.caecorthus.sparkwitch.net.props.NeonSettingsS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blink settings for the shining neon columns. A column is a vertical chain of blocks, so the settings are keyed by
 * the column's lowest block: every block in the chain reads the same entry, which keeps a chain blinking together.
 * <p>
 * 闪烁霓虹灯柱的设置。一根灯柱是竖直的一串方块，因此设置以该列最低的方块为键：整串读同一条记录，保证一根柱子整体闪烁。
 */
public final class NeonSettings {
    /** Bounds for the two durations the builder types in, in ticks. / 建造者输入的两种时长上下限，tick。 */
    public static final int MIN_TICKS = 2;
    public static final int MAX_TICKS = 20 * 600;
    /** Random mode picks a fresh wait in this range after every flip. / 随机模式每次翻转后在此时长区间里重新取值。 */
    public static final int RANDOM_MIN_TICKS = 3 * 20;
    public static final int RANDOM_MAX_TICKS = 8 * 20;

    /** Column base position -> settings. / 灯柱底部坐标 -> 设置。 */
    private static final Map<BlockPos, Config> COLUMNS = new ConcurrentHashMap<>();
    /** Column base -> world time of the last state flip. / 灯柱底部 -> 上一次翻转状态的世界时间。 */
    private static final Map<BlockPos, Long> LAST_FLIP = new ConcurrentHashMap<>();
    /** Column base -> how long the current lit/unlit phase should last, so random mode stays stable between ticks. */
    private static final Map<BlockPos, Long> WAIT = new ConcurrentHashMap<>();

    /** One column's blink configuration. / 一根灯柱的闪烁配置。 */
    public record Config(int onTicks, int offTicks, boolean random) {
        public Config clamp() {
            return new Config(Math.max(MIN_TICKS, Math.min(MAX_TICKS, onTicks)),
                    Math.max(MIN_TICKS, Math.min(MAX_TICKS, offTicks)), random);
        }
    }

    private NeonSettings() {
    }

    /** Sends the current settings of the column containing {@code pos} to the player's settings screen. / 把 {@code pos} 所在灯柱的当前设置发到玩家的设置界面。 */
    public static void openScreen(ServerPlayerEntity player, World world, BlockPos pos) {
        BlockPos base = columnBase(world, pos);
        Config config = COLUMNS.getOrDefault(base, new Config(0, 0, false));
        ServerPlayNetworking.send(player, new NeonSettingsS2CPayload(
                base, config.onTicks(), config.offTicks(), COLUMNS.containsKey(base), config.random()
        ));
    }

    /** Applies settings handed back by the screen. / 应用界面回传的设置。 */
    public static void apply(ServerPlayerEntity player, BlockPos pos, int onTicks, int offTicks, boolean enabled, boolean random) {
        World world = player.getWorld();
        if (!world.isChunkLoaded(pos)) {
            return;
        }
        BlockPos base = columnBase(world, pos);
        if (enabled) {
            COLUMNS.put(base, new Config(onTicks, offTicks, random).clamp());
            LAST_FLIP.put(base, world.getTime());
            WAIT.put(base, phaseLength(world, base, new Config(onTicks, offTicks, random).clamp(), true));
        } else {
            COLUMNS.remove(base);
            LAST_FLIP.remove(base);
            WAIT.remove(base);
        }
    }

    /** Walks down the pillar chain to find the column base. / 沿灯柱向下找这一列的底。 */
    public static BlockPos columnBase(World world, BlockPos pos) {
        BlockPos.Mutable cursor = pos.mutableCopy();
        int guard = 0;
        while (guard++ < 64) {
            BlockPos below = cursor.down();
            BlockState state = world.getBlockState(below);
            if (!state.isOf(world.getBlockState(pos).getBlock())) {
                break;
            }
            cursor.set(below);
        }
        return cursor.toImmutable();
    }

    /** True when this block belongs to a blinking column. / 该方块属于一根正在闪烁的灯柱时为真。 */
    public static boolean isBlinking(World world, BlockPos pos) {
        return COLUMNS.containsKey(columnBase(world, pos));
    }

    /**
     * Server tick: flips the {@code lit} state of every blinking column on its own schedule. Lighting is purely a
     * block state, so clients only need the normal block update — no extra packet.
     * 服务端 tick：按各自节奏翻转每根闪烁灯柱的 {@code lit} 状态。点亮只是方块状态，客户端只需要普通方块更新，不另发包。
     */
    public static void tick(World world) {
        if (COLUMNS.isEmpty()) {
            return;
        }
        long now = world.getTime();
        COLUMNS.forEach((base, config) -> {
            if (!world.isChunkLoaded(base)) {
                return;
            }
            BlockState head = world.getBlockState(base);
            if (!(head.getBlock() instanceof dev.caecorthus.sparkwitch.block.props.ShiningNeonBlock)) {
                return;
            }
            boolean lit = head.get(Properties.LIT);
            long wait = WAIT.computeIfAbsent(base, key -> phaseLength(world, key, config, true));
            long last = LAST_FLIP.getOrDefault(base, now);
            if (now - last < wait) {
                return;
            }
            LAST_FLIP.put(base, now);
            WAIT.put(base, phaseLength(world, base, config, lit));
            for (int i = 0; i < 32; i++) {
                BlockPos cursor = base.up(i);
                BlockState state = world.getBlockState(cursor);
                if (!(state.getBlock() instanceof dev.caecorthus.sparkwitch.block.props.ShiningNeonBlock)) {
                    break;
                }
                world.setBlockState(cursor, state.with(Properties.LIT, !lit));
            }
            world.playSound(null, base, net.minecraft.sound.SoundEvents.BLOCK_LEVER_CLICK,
                    net.minecraft.sound.SoundCategory.BLOCKS, 0.25F, lit ? 0.7F : 1.4F);
        });
    }

    /**
     * How long the next phase should last. In random mode every flip draws a fresh value, so a column never settles
     * into a rhythm; otherwise the builder's own "on" / "off" numbers are used.
     * 下一段相位该持续多久。随机模式下每次翻转都重新取值，灯柱不会稳定成固定节奏；否则用建造者自己填的「亮」「灭」时长。
     */
    private static long phaseLength(World world, BlockPos base, Config config, boolean turningOff) {
        if (config.random()) {
            return RANDOM_MIN_TICKS + world.getRandom().nextInt(RANDOM_MAX_TICKS - RANDOM_MIN_TICKS + 1);
        }
        return turningOff ? config.offTicks() : config.onTicks();
    }

    public static void clear() {
        COLUMNS.clear();
        LAST_FLIP.clear();
        WAIT.clear();
    }
}
