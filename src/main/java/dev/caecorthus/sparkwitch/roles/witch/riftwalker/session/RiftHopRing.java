package dev.caecorthus.sparkwitch.roles.witch.riftwalker.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure hop-ring math (plan §6.5, D11). The ring is the registry's live gate numbers in number order (the per-round
 * placement sequence, C9), so it is stable and readable; previous/next wrap around and never return the current
 * gate. The client only sends a direction; the server resolves it here against the live ring at receive time, so a
 * gate closed between sync and key press can never be targeted.
 * 纯跳门环计算（plan §6.5、D11）。环为登记表中按编号排序的存活门编号（本局放置序号，C9），稳定且易读；上一扇/下一扇
 * 循环取值且从不返回当前门。客户端只发送方向，服务端在收到时按实时环在此解析，因此同步与按键之间被关闭的门永远不会成为目标。
 */
public final class RiftHopRing {
    public static final int PREVIOUS = -1;
    public static final int NEXT = 1;

    private RiftHopRing() {
    }

    /** Only -1 and +1 are directions; anything else is ignored by the hop handler. / 只有 -1 与 +1 是方向。 */
    public static boolean isDirection(int direction) {
        return direction == PREVIOUS || direction == NEXT;
    }

    /**
     * 1-based position of {@code current} in the number-ordered ring (the "2" of "2/4"), 0 when absent.
     * {@code current} 在按编号排序的环中的位置（从 1 开始，「2/4」中的 2），不在环中时为 0。
     */
    public static int ringIndex(List<Integer> ring, int current) {
        List<Integer> sorted = new ArrayList<>(ring);
        sorted.sort(Integer::compare);
        int index = sorted.indexOf(current);
        return index < 0 ? 0 : index + 1;
    }

    /**
     * Every other gate in hop order for {@code direction}: the neighbour first, then onward with wrap-around, ending
     * just before the current gate (each other gate exactly once). When {@code current} is not in the ring (closed
     * concurrently) the walk starts from where it would sit by number. Empty for a non-direction or no other gate.
     * 按 {@code direction} 的跳转顺序列出其他所有门：先是相邻门，再循环向后，直到当前门之前（每扇其他门恰好一次）。
     * 当 {@code current} 不在环中（同时被关闭）时，从它按编号应处的位置开始。方向无效或没有其他门时为空。
     */
    public static List<Integer> hopOrder(List<Integer> ring, int current, int direction) {
        List<Integer> sorted = new ArrayList<>(ring);
        sorted.sort(Integer::compare);
        sorted.removeIf(number -> number == current);
        int size = sorted.size();
        if (!isDirection(direction) || size == 0) {
            return List.of();
        }
        // First index whose number is above current: the "next" neighbour. / 第一个编号大于当前门的下标：即「下一扇」。
        int above = 0;
        while (above < size && sorted.get(above) < current) {
            above++;
        }
        List<Integer> order = new ArrayList<>(size);
        for (int step = 0; step < size; step++) {
            int index = direction == NEXT
                    ? Math.floorMod(above + step, size)
                    : Math.floorMod(above - 1 - step, size);
            order.add(sorted.get(index));
        }
        return List.copyOf(order);
    }
}
