package dev.caecorthus.sparkwitch.roles.civilian.bear.launcher;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract (WP0): the M32A1's six-chamber cylinder, stored on the launcher stack as the synced, persistent data
 * component {@code sparkwitch:m32_cylinder}. Bit {@code i} of {@code loadedMask} is chamber {@code i} (0..5) holding a
 * smoke round; {@code next} is the chamber that fires next. The compact constructor normalises any input (mask to 6
 * bits, {@code next} modulo 6), so decoded or hand-written components are always valid. Pure and side-neutral: the
 * server writes it, the client only reads it (HUD).
 * <p>
 * Unload rule (D8 "unloading takes the most recently loaded round") needs no extra state: {@link #load()} fills the
 * first empty chamber after the loaded run that starts at {@code next}, and {@link #fire()} consumes that run from its
 * front and advances {@code next} past it, so every load, fire and unload keeps the loaded chambers one contiguous run
 * beginning at {@code next}, ordered oldest load first. {@link #unload()} therefore removes the LAST loaded chamber in
 * rotation order from {@code next} (the one that would fire last), which is exactly the most recently loaded round. A
 * non-contiguous mask (only from a hand-edited component) keeps the same well-defined rule and fires with empties
 * skipped.
 * 冻结契约（WP0）：M32A1 的六弹膛转轮，以同步且持久化的数据组件 {@code sparkwitch:m32_cylinder} 存放在发射器物品上。
 * {@code loadedMask} 的第 {@code i} 位表示第 {@code i} 号弹膛（0..5）装有烟雾榴弹；{@code next} 为下一发击发的弹膛。紧凑构造器
 * 会规整任何输入（掩码取低 6 位，{@code next} 对 6 取模），因此解码或手写的组件始终有效。纯数据、两端通用：服务端写入，
 * 客户端只读（HUD）。
 * 卸弹规则（D8“卸下最近装入的一发”）无需额外状态：{@link #load()} 填入从 {@code next} 开始的已装填连续段之后的第一个空膛，
 * {@link #fire()} 从该段最前端击发并把 {@code next} 移到其后，因此每次装填、击发与卸弹后，已装填弹膛始终是从 {@code next}
 * 开始、按装填先后排列的一段连续弹膛。于是 {@link #unload()} 移除按转动顺序从 {@code next} 起最后一个已装填弹膛（最后才会击发的
 * 那一发），它恰好就是最近装入的一发。不连续的掩码（只可能来自手改组件）沿用同一明确规则，击发时跳过空膛。
 */
public record M32Cylinder(int loadedMask, int next) {
    public static final int CHAMBERS = BearRules.M32_CHAMBERS;
    private static final int FULL_MASK = (1 << CHAMBERS) - 1;

    public static final M32Cylinder EMPTY = new M32Cylinder(0, 0);

    /** Persistent codec: {@code {loaded: int, next: int}}, both optional. / 持久化编解码：两个字段都可省略。 */
    public static final Codec<M32Cylinder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("loaded", 0).forGetter(M32Cylinder::loadedMask),
            Codec.INT.optionalFieldOf("next", 0).forGetter(M32Cylinder::next)
    ).apply(instance, M32Cylinder::new));

    /** Network codec: two var-ints. / 网络编解码：两个 var-int。 */
    public static final PacketCodec<ByteBuf, M32Cylinder> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, M32Cylinder::loadedMask,
            PacketCodecs.VAR_INT, M32Cylinder::next,
            M32Cylinder::new
    );

    public M32Cylinder {
        loadedMask &= FULL_MASK;
        next = Math.floorMod(next, CHAMBERS);
    }

    public boolean isLoaded(int chamber) {
        return chamber >= 0 && chamber < CHAMBERS && (loadedMask & (1 << chamber)) != 0;
    }

    public int loadedCount() {
        return Integer.bitCount(loadedMask);
    }

    public boolean isEmpty() {
        return loadedMask == 0;
    }

    public boolean isFull() {
        return loadedMask == FULL_MASK;
    }

    /**
     * One round into the next empty chamber in rotation order from {@code next}: the first empty chamber after the
     * loaded run (for the always-contiguous run this is the first empty chamber from {@code next}; scanning after the
     * run keeps {@link #unload()} exact even for a hand-edited mask). {@code this} when full (callers check
     * {@link #isFull()} first).
     * 把一发装入从 {@code next} 起按转动顺序的下一个空膛：已装填段之后的第一个空膛（对始终连续的已装填段而言，就是从
     * {@code next} 起的第一个空膛；从该段之后查找可使手改掩码下的 {@link #unload()} 依然准确）。已满时返回 {@code this}
     * （调用方应先检查 {@link #isFull()}）。
     */
    public M32Cylinder load() {
        if (isFull()) {
            return this;
        }
        int lastLoaded = lastLoadedChamber();
        int start = lastLoaded < 0 ? next : lastLoaded + 1;
        for (int step = 0; step < CHAMBERS; step++) {
            int chamber = (start + step) % CHAMBERS;
            if (!isLoaded(chamber)) {
                return new M32Cylinder(loadedMask | (1 << chamber), next);
            }
        }
        return this;
    }

    /**
     * Removes the most recently loaded round (see the class comment); {@code this} when empty (callers check
     * {@link #isEmpty()} first). {@code next} is unchanged.
     * 移除最近装入的一发（见类注释）；为空时返回 {@code this}（调用方应先检查 {@link #isEmpty()}）。{@code next} 不变。
     */
    public M32Cylinder unload() {
        int chamber = lastLoadedChamber();
        return chamber < 0 ? this : new M32Cylinder(loadedMask & ~(1 << chamber), next);
    }

    /**
     * Fires the first loaded chamber in rotation order from {@code next} (empties skipped) and advances {@code next} to
     * the chamber after it; null when empty.
     * 击发从 {@code next} 起按转动顺序的第一个已装填弹膛（跳过空膛），并把 {@code next} 移到其后一个弹膛；为空时返回 null。
     */
    public @Nullable Fired fire() {
        int chamber = firstLoadedChamber();
        if (chamber < 0) {
            return null;
        }
        return new Fired(new M32Cylinder(loadedMask & ~(1 << chamber), chamber + 1), chamber);
    }

    /** The chamber {@link #fire()} would fire, or -1 when empty. / {@link #fire()} 将击发的弹膛；为空时为 -1。 */
    public int firstLoadedChamber() {
        for (int step = 0; step < CHAMBERS; step++) {
            int chamber = (next + step) % CHAMBERS;
            if (isLoaded(chamber)) {
                return chamber;
            }
        }
        return -1;
    }

    /** The chamber {@link #unload()} would empty, or -1 when empty. / {@link #unload()} 将清空的弹膛；为空时为 -1。 */
    public int lastLoadedChamber() {
        for (int step = CHAMBERS - 1; step >= 0; step--) {
            int chamber = (next + step) % CHAMBERS;
            if (isLoaded(chamber)) {
                return chamber;
            }
        }
        return -1;
    }

    /**
     * The result of {@link #fire()}: the cylinder afterwards and the chamber index (0..5) that fired.
     * {@link #fire()} 的结果：击发后的转轮与击发的弹膛下标（0..5）。
     */
    public record Fired(M32Cylinder after, int chamber) {
    }
}
