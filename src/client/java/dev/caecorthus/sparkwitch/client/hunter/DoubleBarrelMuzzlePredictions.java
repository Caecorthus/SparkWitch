package dev.caecorthus.sparkwitch.client.hunter;

import java.util.ArrayDeque;

/**
 * Pairs the shooter's predicted first-person flashes with the server's muzzle packets, oldest first. A packet that
 * finds a prediction was already shown; a packet with none is a shot the client did not predict (vanilla sends the
 * held-trigger repeat without calling {@code use} while the client still predicts the 4-tick follow-up cooldown), so
 * the flash is shown late. A prediction the server never confirms (a server-only refusal) expires after
 * {@link #CONFIRM_WINDOW_TICKS}. Client thread only.
 * 按先后顺序把射手预测的第一人称火焰与服务端枪口数据包配对。找到预测的数据包表示火焰已显示；没有预测的数据包是客户端未预测的
 * 一枪（客户端仍预测 4 刻追击冷却时，原版发送按住扳机的重复使用包而不调用 {@code use}），于是补显火焰。服务端始终未确认的
 * 预测（仅服务端的拒绝）在 {@link #CONFIRM_WINDOW_TICKS} 后过期。仅限客户端线程。
 */
public final class DoubleBarrelMuzzlePredictions {
    /** Two seconds: longer than any playable round trip. / 两秒：长于任何可玩的往返延迟。 */
    static final long CONFIRM_WINDOW_TICKS = 40L;

    private final ArrayDeque<Long> predictedAt = new ArrayDeque<>();

    /** Records a flash the client already showed. / 记录客户端已显示的火焰。 */
    public void predicted(long tick) {
        predictedAt.addLast(tick);
    }

    /**
     * One server muzzle packet for the local shooter.
     * 本地射手收到的一个服务端枪口数据包。
     *
     * @return whether the flash still has to be shown / 是否仍需显示火焰
     */
    public boolean confirm(long tick) {
        expire(tick);
        return predictedAt.pollFirst() == null;
    }

    /** Drops predictions older than the window. / 丢弃超出窗口的预测。 */
    public void expire(long tick) {
        while (!predictedAt.isEmpty() && tick - predictedAt.peekFirst() > CONFIRM_WINDOW_TICKS) {
            predictedAt.pollFirst();
        }
    }

    public void clear() {
        predictedAt.clear();
    }
}
