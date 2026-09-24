package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.recruitment.NoellesRecruitmentCleanup;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperDragService;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopUtils;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Server-authoritative, zero-cost and zero-cooldown recruitment transaction.
 * 服务端权威、免费、无冷却的招募事务；只在成功转换后消耗持久化名额。 */
public final class GrandWitchRecruitmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrandWitchRecruitmentService.class);

    private GrandWitchRecruitmentService() { }

    public static void register() {
        NoellesRecruitmentCleanup.register();
    }

    public static WitchSkillUseResult use(ServerPlayerEntity recruiter, @Nullable UUID targetId) {
        ServerWorld world = recruiter.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(recruiter)
                || !game.isRole(recruiter, SparkWitchRoles.grandWitch())) {
            return WitchSkillUseResult.fail("message.sparkwitch.recruitment.invalid_recruiter");
        }
        if (WitchPlayerComponent.KEY.get(recruiter).getGrandWitchCeremonialSwordTasks() < 2) {
            return WitchSkillUseResult.fail("message.sparkwitch.recruitment.locked");
        }
        ServerPlayerEntity target = GrandWitchTargeting.findTarget(recruiter, targetId);
        if (target == null || target == recruiter || target.getServerWorld() != world
                || !GameFunctions.isPlayerPlayingAndAlive(target)
                || game.isRole(target, SparkWitchRoles.accomplice())) {
            return WitchSkillUseResult.fail("message.sparkwitch.recruitment.invalid_target");
        }
        GrandWitchRecruitmentRoundComponent round = GrandWitchRecruitmentRoundComponent.KEY.get(world);
        if (!round.tryBeginConversion()) {
            return WitchSkillUseResult.fail("message.sparkwitch.recruitment.no_capacity");
        }
        try {
            PlayerShopComponent shop = PlayerShopComponent.KEY.get(target);
            RecruitmentInventorySnapshot inventory;
            try {
                inventory = RecruitmentInventorySnapshot.capture(target, shop.getBalance());
            } catch (ArithmeticException exception) {
                // Refuse overflow before any destructive mutation; never silently discard money.
                // 在任何破坏性变更之前拒绝溢出，绝不静默吞掉金币。
                return WitchSkillUseResult.fail("message.sparkwitch.recruitment.balance_overflow");
            }

            inventory.detachScreenInputs();
            exitOldRole(target);
            // Assignment grants must have free slots so discarded starter items cannot spill into the world.
            // 分配初始物品时保留空槽，避免应清除的职业初始物品掉落到世界中。
            target.clearActiveItem();
            target.getInventory().clear();
            game.addRole(target, SparkWitchRoles.accomplice());
            // Commit the durable quota at the role-map mutation, before downstream callbacks can reenter.
            // 在身份映射变更时提交持久化名额，早于可能重入的下游回调。
            round.recordSuccess(target.getUuid());
            for (ServerPlayerEntity player : world.getPlayers()) {
                syncRuntime(player);
            }
            try {
                // Standard assignment handles SparkStrength cleanup and SparkWitch mana/skill initialization.
                // 标准分配事件处理 SparkStrength 清理及 SparkWitch 魔力、技能初始化，不重置 Traits。
                RoleAssigned.EVENT.invoker().assignRole(target, SparkWitchRoles.accomplice());
            } catch (RuntimeException exception) {
                // Conversion already committed: do not report a retriable failure or lose factor recovery.
                // 转换已提交：不能返回可重试失败或跳过调用方的因子回收；记录扩展回调故障。
                LOGGER.error("Recruitment committed but a role-assignment listener failed for {}", target.getUuid(), exception);
            } finally {
                inventory.applyRetainedInventory(target);
                shop.setBalance(inventory.finalBalance());
                shop.initializeShop(ShopUtils.getShopEntriesForPlayer(target));
                game.sync();
                shop.sync();
            }
            target.sendMessage(Text.translatable("message.sparkwitch.recruitment.converted", inventory.finalBalance()), false);
            return WitchSkillUseResult.success(0, "message.sparkwitch.recruitment.success");
        } finally {
            round.finishConversion();
        }
    }

    public static void beginRound(ServerWorld world, int openingParticipants) {
        GrandWitchRecruitmentRoundComponent.KEY.get(world).beginRound(openingParticipants);
        for (ServerPlayerEntity player : world.getPlayers()) {
            GrandWitchRuntimeComponent runtime = GrandWitchRuntimeComponent.KEY.get(player);
            runtime.clear();
            runtime.setRoundParticipants(openingParticipants);
        }
    }

    public static void clearRound(ServerWorld world) {
        GrandWitchRecruitmentRoundComponent.KEY.get(world).clearRound();
    }

    public static void syncRuntime(ServerPlayerEntity player) {
        GrandWitchRecruitmentRoundComponent round = GrandWitchRecruitmentRoundComponent.KEY.get(player.getServerWorld());
        GrandWitchRuntimeComponent runtime = GrandWitchRuntimeComponent.KEY.get(player);
        runtime.setRoundParticipants(round.getParticipants());
        runtime.setRecruitmentCount(round.getLimit() - round.getRemaining());
    }

    public static int getRemaining(ServerPlayerEntity recruiter) {
        return GrandWitchRecruitmentRoundComponent.KEY.get(recruiter.getServerWorld()).getRemaining();
    }

    public static int getLimit(ServerPlayerEntity recruiter) {
        return GrandWitchRecruitmentRoundComponent.KEY.get(recruiter.getServerWorld()).getLimit();
    }

    private static void exitOldRole(ServerPlayerEntity target) {
        NoellesRecruitmentCleanup.exitRole(target);
        KidnapperDragService.release(target);
        BlackRavenPerceptionService.clearForRoleLossOrDeath(target);
        WitchFactionFeatureService.clearPlayerRuntime(target);
        WraithLifecycle.clearPlayer(target);
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(target);
        witch.cancelMightyForceWindow();
        witch.clear();
        GrandWitchRuntimeComponent.KEY.get(target).clear();
    }
}
