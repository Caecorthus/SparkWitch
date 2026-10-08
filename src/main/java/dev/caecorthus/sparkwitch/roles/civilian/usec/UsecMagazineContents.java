package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable contract: the ordered rounds in one magazine, bottom to top. The last round loaded fires first (LIFO, Q9), and
 * types may be mixed. Immutable: {@link #push} and {@link #pop} return new instances. The NBT form is a list of
 * {@link UsecAmmoType#id()} strings in the same bottom-to-top order; decoding drops unknown ids and caps at
 * {@link UsecRules#MAGAZINE_CAPACITY}.
 * 稳定契约：一个弹匣内按自下而上排列的子弹。最后装入的子弹最先发射（后进先出，Q9），允许混装。不可变：
 * {@link #push} 与 {@link #pop} 返回新实例。NBT 形式是同样自下而上排列的 {@link UsecAmmoType#id()} 字符串列表；
 * 解码时丢弃未知 id，并截断到 {@link UsecRules#MAGAZINE_CAPACITY}。
 */
public record UsecMagazineContents(List<UsecAmmoType> rounds) {
    public static final UsecMagazineContents EMPTY = new UsecMagazineContents(List.of());

    /**
     * Rejects null rounds and more than {@link UsecRules#MAGAZINE_CAPACITY} rounds; a null list reads as empty.
     * 拒绝 null 子弹与超过 {@link UsecRules#MAGAZINE_CAPACITY} 发的列表；null 列表视为空。
     */
    public UsecMagazineContents {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
        if (rounds.size() > UsecRules.MAGAZINE_CAPACITY) {
            throw new IllegalArgumentException("A USEC magazine holds at most " + UsecRules.MAGAZINE_CAPACITY
                    + " rounds, got " + rounds.size());
        }
    }

    public int size() {
        return rounds.size();
    }

    public boolean isEmpty() {
        return rounds.isEmpty();
    }

    public boolean isFull() {
        return rounds.size() >= UsecRules.MAGAZINE_CAPACITY;
    }

    public int freeSlots() {
        return UsecRules.MAGAZINE_CAPACITY - rounds.size();
    }

    /** The round that fires next, or null when empty. / 下一发将要发射的子弹；空时为 null。 */
    public @Nullable UsecAmmoType top() {
        return rounds.isEmpty() ? null : rounds.getLast();
    }

    /** Loads one round on top; callers check {@link #isFull()} first. / 在顶部装入一发；调用方须先检查 {@link #isFull()}。 */
    public UsecMagazineContents push(UsecAmmoType type) {
        if (type == null) {
            throw new IllegalArgumentException("USEC round type must not be null");
        }
        if (isFull()) {
            throw new IllegalStateException("USEC magazine is full");
        }
        List<UsecAmmoType> next = new ArrayList<>(rounds);
        next.add(type);
        return new UsecMagazineContents(next);
    }

    /** Removes the top round; an empty magazine stays empty. / 取出顶部一发；空弹匣保持为空。 */
    public UsecMagazineContents pop() {
        if (rounds.isEmpty()) {
            return this;
        }
        return new UsecMagazineContents(rounds.subList(0, rounds.size() - 1));
    }

    /** Rounds in the order they fire (top to bottom). / 按发射顺序（自上而下）排列的子弹。 */
    public List<UsecAmmoType> firingOrder() {
        return List.copyOf(rounds.reversed());
    }

    public NbtList toNbt() {
        NbtList list = new NbtList();
        for (UsecAmmoType round : rounds) {
            list.add(NbtString.of(round.id()));
        }
        return list;
    }

    /**
     * Tolerant decode: anything but a list reads as empty, unknown ids are dropped, and extra rounds beyond capacity
     * are ignored (the bottom ones are kept).
     * 宽容解码：非列表一律视为空，未知 id 被丢弃，超出容量的部分被忽略（保留底部的子弹）。
     */
    public static UsecMagazineContents fromNbt(@Nullable NbtElement element) {
        if (!(element instanceof NbtList list) || list.isEmpty()) {
            return EMPTY;
        }
        List<UsecAmmoType> rounds = new ArrayList<>();
        for (NbtElement entry : list) {
            if (rounds.size() >= UsecRules.MAGAZINE_CAPACITY) {
                break;
            }
            UsecAmmoType type = entry instanceof NbtString string ? UsecAmmoType.fromId(string.asString()) : null;
            if (type != null) {
                rounds.add(type);
            }
        }
        return rounds.isEmpty() ? EMPTY : new UsecMagazineContents(rounds);
    }
}
