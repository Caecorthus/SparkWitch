package dev.caecorthus.sparkwitch.client.magician;

/**
 * One Magician stage step per physical key press. The shared ability key counts OS key-repeat events as presses, so
 * holding it would walk record → stop → play → stop; a press is accepted only after the key was seen released.
 * 每次物理按键只推进一个魔术师阶段。共享技能键会把系统按键重复事件计为按下，按住不放会依次走完录制 → 停止 → 播放 → 停止；
 * 只有在观察到按键松开之后才接受下一次按下。
 */
public final class MagicianKeyRepeatGuard {
    private static boolean released = true;

    private MagicianKeyRepeatGuard() {
    }

    /**
     * Call every client tick before reading presses, with whether the key is held now.
     * 每个客户端刻在读取按下之前调用，传入当前是否按住。
     */
    public static void observe(boolean heldNow) {
        if (!heldNow) {
            released = true;
        }
    }

    /**
     * Whether a press seen this tick may advance the stage. / 本刻看到的按下能否推进阶段。
     */
    public static boolean accept() {
        if (!released) {
            return false;
        }
        released = false;
        return true;
    }

    public static void reset() {
        released = true;
    }
}
