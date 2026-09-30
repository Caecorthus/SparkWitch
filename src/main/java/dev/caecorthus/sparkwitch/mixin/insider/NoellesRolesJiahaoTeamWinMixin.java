package dev.caecorthus.sparkwitch.mixin.insider;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.insider.JiahaoTeamWinSeam;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CheckWinCondition;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.UUID;

/**
 * External seam (pinned NoellesRoles {@code 1.7.6-h1.5.6-spark}, commit b58fa5f), owner decisions D2/D7, approval A2:
 * Team Jiahao takes over NoellesRoles' Corrupt Cop win branch, the last win/block branch before the Shadow Jester in
 * the win listener {@code lambda$registerEvents$14}, so NoellesRoles' win order (Vulture > Survival Master > Pathogen >
 * Jester > Taotie > this team > Shadow Jester) stays intact. Three wraps, all inside that branch:
 * {@code getAllWithRole} ordinal 6 (the living-cop lookup list becomes the whole team, so a living Insider also blocks
 * killers and passengers), {@code isPlayerPlayingAndAlive} ordinal 9 (the {@code aliveCount} loop counts the alive team
 * once, so {@code aliveCount == 1} means only members are alive), and single-argument {@code neutralWin} ordinal 5
 * (the cop's win becomes the team's co-win). The Corrupt Cop Moment counter is untouched (C10). The ordinals are
 * pinned by {@code JiahaoTeamWinContractTest}; {@code NoellesRolesFiendAliveCountMixin} (Fiend branch) wraps ordinal 9
 * too, and the two AND-filters chain. Additive {@code @WrapOperation}s, never {@code @Redirect}. Server only.
 * 外部接缝（固定 NoellesRoles {@code 1.7.6-h1.5.6-spark}，提交 b58fa5f），所有者决定 D2/D7，批准项 A2：嘉豪阵营接管
 * NoellesRoles 胜利监听器 {@code lambda$registerEvents$14} 中的黑警分支（影子小丑之前最后一个胜利/阻止分支），因此
 * NoellesRoles 的胜利优先级（秃鹫 > 生存大师 > 病原体 > 小丑 > 饕餮 > 本阵营 > 影子小丑）保持不变。三个包装都在该
 * 分支内：{@code getAllWithRole} 序号 6（“存活黑警”查找列表换成整个阵营，存活的内应也会阻止杀手与乘客获胜），
 * {@code isPlayerPlayingAndAlive} 序号 9（{@code aliveCount} 循环只把存活阵营计一次，{@code aliveCount == 1} 即只剩成员
 * 存活），以及单参数 {@code neutralWin} 序号 5（黑警胜利改为阵营共同胜利）。黑警时刻的人数统计保持不变（C10）。序号由
 * {@code JiahaoTeamWinContractTest} 固定；魔人分支的 {@code NoellesRolesFiendAliveCountMixin} 也包装序号 9，两个
 * “与”过滤可以串联。均为叠加式 {@code @WrapOperation}，绝不使用 {@code @Redirect}。仅服务端。
 */
@Mixin(targets = "org.agmas.noellesroles.Noellesroles", remap = false)
public abstract class NoellesRolesJiahaoTeamWinMixin {
    @WrapOperation(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getAllWithRole(Ldev/doctor4t/wathe/api/Role;)Ljava/util/List;",
                    ordinal = 6
            ),
            require = 1,
            allow = 1
    )
    private static List<UUID> sparkwitch$lookUpTeamJiahao(
            GameWorldComponent game,
            Role role,
            Operation<List<UUID>> original,
            @Local(argsOnly = true) ServerWorld world
    ) {
        List<UUID> corruptCops = original.call(game, role);
        return InsiderParticipation.isCorruptCopRole(role) ? JiahaoTeamWinSeam.memberLookup(world, game) : corruptCops;
    }

    @WrapOperation(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;isPlayerPlayingAndAlive(Lnet/minecraft/entity/player/PlayerEntity;)Z",
                    ordinal = 9
            ),
            require = 1,
            allow = 1
    )
    private static boolean sparkwitch$countTeamJiahaoOnce(PlayerEntity player, Operation<Boolean> original) {
        return JiahaoTeamWinSeam.countsTowardAliveCount(original.call(player), player);
    }

    @WrapOperation(
            method = "lambda$registerEvents$14",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;neutralWin(Lnet/minecraft/server/network/ServerPlayerEntity;)Ldev/doctor4t/wathe/api/event/CheckWinCondition$WinResult;",
                    ordinal = 5
            ),
            require = 1,
            allow = 1
    )
    private static CheckWinCondition.WinResult sparkwitch$teamJiahaoWinsTogether(
            ServerPlayerEntity livingMember,
            Operation<CheckWinCondition.WinResult> original
    ) {
        return JiahaoTeamWinSeam.teamWin(livingMember, original.call(livingMember));
    }
}
