package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import org.jetbrains.annotations.Nullable;

/**
 * Pure decision for a click on the launcher in the inventory. Both sides compute the same answer from synced facts so
 * the client predicts whether the click is consumed; only the server mutates the launcher and the cursor.
 * 背包中点击炮筒的纯函数判定。两端由同步的事实算出相同结果，使客户端能预测点击是否被消耗；只有服务端修改炮筒与光标。
 */
public final class PotionLauncherLoadRules {
    private PotionLauncherLoadRules() {
    }

    public enum Action {
        /** Not a launcher click: vanilla pickup or swap runs. / 非炮筒点击：执行原版拿起或交换。 */
        PASS,
        /** Move one shell from the cursor into the empty launcher. / 把光标上的一发炮弹装入空炮筒。 */
        LOAD,
        /** Move the loaded shell onto the empty cursor. / 把已装填的炮弹退到空光标上。 */
        UNLOAD,
        /** A shell offered to a loaded launcher; refused with a message. / 向已装填的炮筒再塞炮弹；拒绝并提示。 */
        REFUSE_LOADED,
        /** Loading or unloading while stunned or in a Seeker session; refused silently. / 眩晕或遥控中装填/退弹；静默拒绝。 */
        REFUSE_LOCKED;

        /**
         * Every non-PASS action consumes the click, so vanilla never swaps the launcher onto the cursor.
         * 除 PASS 外的动作都会消耗点击，因此原版永远不会把炮筒换到光标上。
         */
        public boolean consumesClick() {
            return this != PASS;
        }
    }

    /**
     * @param rightClick  the click is a right click (left clicks keep vanilla pickup)
     * @param cursorShell the shell type on the cursor, or null when the cursor holds anything else or nothing
     * @param cursorEmpty the cursor is empty
     * @param loaded      the launcher's loaded shell, or null when empty
     * @param eligible    the clicker is a living, playing, exact Potion Gunner, or in creative mode
     * @param locked      the clicker is stunned by a Control Expert or locked in a Seeker remote session
     */
    public static Action decide(boolean rightClick, @Nullable PotionShellType cursorShell, boolean cursorEmpty,
                                @Nullable PotionShellType loaded, boolean eligible, boolean locked) {
        if (!rightClick || !eligible) {
            return Action.PASS;
        }
        Action wanted;
        if (cursorShell != null) {
            wanted = loaded == null ? Action.LOAD : Action.REFUSE_LOADED;
        } else if (cursorEmpty && loaded != null) {
            wanted = Action.UNLOAD;
        } else {
            return Action.PASS;
        }
        return locked ? Action.REFUSE_LOCKED : wanted;
    }
}
