package dev.caecorthus.sparkwitch.client.potiongunner;

/**
 * Fire gate of the launcher's left click (D-R3). A shot needs a fresh attack press edge, meaning one press that
 * vanilla takes from {@code KeyBinding.wasPressed()} in {@code handleInputEvents}, seen while the launcher is in the
 * main hand. A held mouse button makes no edges, so holding left-click while switching to the launcher or while a
 * stun, Seeker or Kidnapper key lock ends never fires. A held keyboard key does: auto-repeat queues a new press for
 * every repeat event. For a keyboard-bound attack key the gate therefore re-arms only at a tick end that sees the key
 * physically up, which no key lock, screen or {@code setPressed(false)} can fake. Each fired edge disarms the gate
 * until the next tick end, so a double click inside one tick sends one packet.
 * 炮筒左键的发射闸门（D-R3）。一次发射需要在主手持炮筒时观察到一次新的攻击按下沿，即原版在
 * {@code handleInputEvents} 中经 {@code KeyBinding.wasPressed()} 取出的一次按键。按住鼠标键不会产生按下沿，因此按住
 * 左键切换到炮筒，或眩晕、搜寻者、绑匪按键锁结束时仍按住，都永远不会发射。按住键盘键则会：自动重复为每次重复事件排入
 * 新按键。因此攻击键绑定在键盘上时，只有在某个刻末尾观察到该键物理松开才会重新解除闸门，任何按键锁、界面或
 * {@code setPressed(false)} 都无法伪造这一点。每次发射的按下沿都会关闭闸门直到下一个刻末尾，所以同一刻内的双击只发
 * 一个数据包。
 */
public final class PotionFireLatch {
    private boolean armed;

    /**
     * One drained attack press edge. True when it becomes a fire request: the launcher is in the main hand at the edge
     * and the gate is armed; the gate is then disarmed.
     * 一次被取出的攻击按下沿。按下沿时主手持炮筒且闸门已解除时返回 true，即成为发射请求；随后关闭闸门。
     */
    public boolean onPressEdge(boolean launcherInMainHand) {
        if (!launcherInMainHand || !armed) {
            return false;
        }
        armed = false;
        return true;
    }

    /**
     * End of every client tick. {@code repeatingKeyHeld}: the attack key is a keyboard key and is physically down
     * (mouse buttons never repeat and always pass false). Arms the next edge unless such a key is held.
     * 每个客户端刻末尾。{@code repeatingKeyHeld}：攻击键是键盘键且物理按下（鼠标键从不重复，始终传 false）。
     * 除非这样的键仍被按住，否则为下一次按下沿解除闸门。
     */
    public void endTick(boolean repeatingKeyHeld) {
        armed = !repeatingKeyHeld;
    }

    /** Fails closed until the next tick end. / 失败即关闭，直到下一个刻末尾。 */
    public void reset() {
        armed = false;
    }
}
