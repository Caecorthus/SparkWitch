package dev.caecorthus.sparkwitch.client.usec;

/**
 * Fire gate of the rifle's left click (plan D2, copied from the launcher's {@code PotionFireLatch} so the two roles
 * never share mutable input state). A shot needs a fresh attack press edge, meaning one press that vanilla takes from
 * {@code KeyBinding.wasPressed()} in {@code handleInputEvents}, seen while the rifle is in the main hand. A held mouse
 * button makes no edges, so holding left-click while switching to the rifle or while a stun, Seeker or Kidnapper key
 * lock ends never fires. A held keyboard key does: auto-repeat queues a new press for every repeat event, so for a
 * keyboard-bound attack key the gate re-arms only at a tick end that sees the key physically up. Each fired edge
 * disarms the gate until the next tick end, so a double click inside one tick sends one packet.
 * 狙击步枪左键的发射闸门（计划 D2，复制自炮筒的 {@code PotionFireLatch}，两个职业从不共享可变输入状态）。一次发射需要
 * 在主手持步枪时观察到一次新的攻击按下沿，即原版在 {@code handleInputEvents} 中经 {@code KeyBinding.wasPressed()} 取出的
 * 一次按键。按住鼠标键不会产生按下沿，因此按住左键切换到步枪，或眩晕、搜寻者、绑匪按键锁结束时仍按住，都永远不会发射。
 * 按住键盘键则会：自动重复为每次重复事件排入新按键，因此攻击键绑定在键盘上时，只有在某个刻末尾观察到该键物理松开才会
 * 重新解除闸门。每次发射的按下沿都会关闭闸门直到下一个刻末尾，所以同一刻内的双击只发一个数据包。
 */
public final class UsecFireLatch {
    private boolean armed;

    /**
     * One drained attack press edge. True when it becomes a fire request: the rifle is in the main hand at the edge and
     * the gate is armed; the gate is then disarmed.
     * 一次被取出的攻击按下沿。按下沿时主手持步枪且闸门已解除时返回 true，即成为发射请求；随后关闭闸门。
     */
    public boolean onPressEdge(boolean rifleInMainHand) {
        if (!rifleInMainHand || !armed) {
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
