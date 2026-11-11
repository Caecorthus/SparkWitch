package dev.caecorthus.sparkwitch.roles.killer.hiro.witchification;

import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 二阶堂希罗's passive 魔女化 (Witchification). SCAFFOLD: only the state lifecycle is wired (progress cleared at role
 * assignment and on player reset); the passive itself is in progress.
 * <p>Owner spec: after {@code HiroRules.requiredKills(roundPlayers)} kills she gains a witch factor if she has none, is
 * shown in her role colour in the witch-factor network view for the rest of the round, and gains permanent Speed II
 * and a loaded {@code wathe:derringer}.
 * 二阶堂希罗的被动「魔女化」。骨架阶段：只接好了状态生命周期（分配职业与玩家重置时清除进度），被动本身仍在实现中。
 * <p>所有者规格：击杀数达到 {@code HiroRules.requiredKills(本局玩家数)} 后，若她没有魔女因子则获得一个，此后在魔女因子网络视图中
 * 始终以职业色显示，并获得永久速度 II 与一把已装填的 {@code wathe:derringer}。
 */
public final class HiroWitchificationService {
    private static boolean registered;

    private HiroWitchificationService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroWitchificationComponent.KEY.get(serverPlayer).clear();
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroWitchificationComponent.KEY.get(serverPlayer).clear();
            }
        });
        // TODO(魔女化): set the round threshold (HiroRules.requiredKills), count Hiro's kills, grant the factor, the
        // role-colour entry in the witch-factor network view, Speed II and the loaded derringer.
        // TODO（魔女化）：写入本局所需击杀数（HiroRules.requiredKills）、统计希罗的击杀、授予因子、魔女因子网络视图中的职业色显示、
        // 速度 II 与已装填的德林加手枪。
    }
}
