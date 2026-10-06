package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

/**
 * Why a disguise identity stops being live; adapters clean up transient role state on every reason.
 * 伪装身份不再生效的原因；适配器在任何原因下都清理临时职业状态。
 */
public enum DisguiseExitReason {
    /** Disguise to another disguise. / 伪装切换到另一伪装。 */
    SWITCH(false),
    /** Disguise back to Black Raven. / 伪装恢复为黑羽鸦。 */
    REVERT(false),
    /** Final death; every stash vanishes. / 最终死亡；所有存档消失。 */
    DEATH(true),
    /** Raw role changed away from Black Raven. / 真实职业不再是黑羽鸦。 */
    ROLE_LOSS(true),
    /** Transient cleanup only; component NBT and live inventory persist. / 仅临时清理；组件 NBT 与当前背包保留。 */
    DISCONNECT(false),
    /** ResetPlayer or round finalize. / 玩家重置或对局结算。 */
    RESET(true),
    /** Component-tick fallback: stale match, lost role, or dead. / 组件刻回退：对局过期、失去职业或死亡。 */
    STALE(true);

    private final boolean discardsStashes;

    DisguiseExitReason(boolean discardsStashes) {
        this.discardsStashes = discardsStashes;
    }

    public boolean discardsStashes() {
        return discardsStashes;
    }
}
