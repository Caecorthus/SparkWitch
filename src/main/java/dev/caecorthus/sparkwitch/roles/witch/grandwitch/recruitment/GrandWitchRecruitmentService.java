package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.recruitment.NoellesRecruitmentCleanup;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperDragService;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoll;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoundComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting;
import dev.doctor4t.wathe.api.Role;
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

import java.util.Random;
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
                || WitchFactionRules.isAccompliceLike(game.getRole(target))) {
            return WitchSkillUseResult.fail("message.sparkwitch.recruitment.invalid_target");
        }
        GrandWitchRecruitmentRoundComponent round = GrandWitchRecruitmentRoundComponent.KEY.get(world);
        if (!round.tryBeginConversion()) {
            return WitchSkillUseResult.fail("message.sparkwitch.recruitment.no_capacity");
        }
        try {
            // Real role, never a Black Raven acting overlay. / 读取真实身份，不读黑羽鸦伪装覆盖层。
            var targetRole = game.getRole(target);
            boolean emma = dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules.isEmma(targetRole);
            var refusal = GrandWitchRecruitmentRules.refusal(InsiderParticipation.isCorruptCopRole(targetRole),
                    emma || PoliceRoles.contains(targetRole), InsiderParticipation.isInsiderRole(targetRole));
            if (refusal != GrandWitchRecruitmentRules.Refusal.NONE) {
                // Emma still records the failed recruitment, but answers with the same police line as every cop.
                // 艾玛仍记录招募未遂证据，但与其他警职显示同样的台词，拒绝提示不会暴露其身份。
                if (emma) {
                    dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaPlayerComponent.KEY.get(target).reveal(recruiter.getUuid());
                }
                var lines = GrandWitchRecruitmentRules.refusalMessages(refusal);
                return WitchSkillUseResult.fail(lines.get(world.getRandom().nextInt(lines.size())));
            }
            // A disguised Black Raven reverts first, so refund and retention value its real Raven set and wallet.
            // The revert keeps the other stashes; they are discarded only once the conversion commits below.
            // 伪装中的黑羽鸦先恢复原身份，使退款与保留按其真实黑羽鸦物品和钱包计算。
            // 恢复时保留其他存档，仅在下方转换提交后才丢弃。
            BlackRavenDisguiseService.revertForRecruitment(target);
            PlayerShopComponent shop = PlayerShopComponent.KEY.get(target);
            RecruitmentInventorySnapshot inventory;
            try {
                inventory = RecruitmentInventorySnapshot.capture(target, shop.getBalance());
            } catch (ArithmeticException exception) {
                // Refuse overflow before any destructive mutation; never silently discard money.
                // 在任何破坏性变更之前拒绝溢出，绝不静默吞掉金币。
                return WitchSkillUseResult.fail("message.sparkwitch.recruitment.balance_overflow");
            }

            // Roll once, after every refusal and before any destructive step: a refused recruitment never spends a
            // special accomplice, and a throwing roll never leaves the target stripped. "Used" is marked only at commit.
            // 在所有拒绝分支之后、任何破坏性步骤之前只抽取一次：被拒绝的招募不会消耗特殊共犯，抽取抛异常也不会让目标
            // 被清空。"已使用"仅在提交时标记。
            Role recruitRole = rollRecruitRole(world, game);
            boolean variant = AccompliceVariants.isVariant(recruitRole);
            // Read before exitOldRole clears the recruit's Shadow Jester pair. / 须在 exitOldRole 清除影子小丑配对之前读取。
            ServerPlayerEntity shadowPartner = NoellesRecruitmentCleanup.shadowPartnerLeftBehind(target);
            inventory.detachScreenInputs();
            exitOldRole(target);
            // Assignment grants must have free slots so discarded starter items cannot spill into the world.
            // 分配初始物品时保留空槽，避免应清除的职业初始物品掉落到世界中。
            target.clearActiveItem();
            target.getInventory().clear();
            // The SparkFactionAPI replay records this addRole as recruited by the Grand Witch; only replay is affected.
            // SparkFactionAPI 回放将此次 addRole 记为被大魔女招募；仅影响回放。
            dev.caecorthus.sparkfactionapi.api.replay.SparkReplayApi.withRoleChangeCause(
                    dev.caecorthus.sparkwitch.SparkWitch.id("grand_witch_recruitment"), recruiter,
                    () -> game.addRole(target, recruitRole));
            // Commit the durable quota at the role-map mutation, before downstream callbacks can reenter.
            // 在身份映射变更时提交持久化名额，早于可能重入的下游回调。
            round.recordSuccess(target.getUuid());
            if (variant) {
                AccompliceVariantRoundComponent.KEY.get(world).markUsed(recruitRole);
            }
            BlackRavenDisguiseService.discardStashesForRecruitment(target);
            for (ServerPlayerEntity player : world.getPlayers()) {
                syncRuntime(player);
            }
            try {
                // Standard assignment handles SparkStrength cleanup and SparkWitch mana/skill initialization.
                // 标准分配事件处理 SparkStrength 清理及 SparkWitch 魔力、技能初始化，不重置 Traits。
                RoleAssigned.EVENT.invoker().assignRole(target, recruitRole);
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
            if (shadowPartner != null) {
                releaseShadowPartner(shadowPartner, target);
            }
            if (variant) {
                runVariantHook(recruitRole, target, recruiter);
                // WitchSkillUseResult carries no message arguments, so the named success line is sent here.
                // WitchSkillUseResult 不支持消息参数，因此在此直接发送带职业名的成功提示。
                Text roleName = Text.translatable("announcement.role." + recruitRole.identifier().getPath());
                target.sendMessage(Text.translatable("message.sparkwitch.recruitment.converted_as",
                        roleName, inventory.finalBalance()), false);
                recruiter.sendMessage(Text.translatable("message.sparkwitch.recruitment.success_as", roleName), true);
                return WitchSkillUseResult.success(0);
            }
            target.sendMessage(Text.translatable("message.sparkwitch.recruitment.converted", inventory.finalBalance()), false);
            return WitchSkillUseResult.success(0, "message.sparkwitch.recruitment.success");
        } finally {
            round.finishConversion();
        }
    }

    public static void beginRound(ServerWorld world, int openingParticipants) {
        GrandWitchRecruitmentRoundComponent.KEY.get(world).beginRound(openingParticipants);
        // Roles are assigned before ON_FINISH_INITIALIZE, so a forced variant stays used even after its role-map
        // entry is later replaced (death into Wraith, then Curser).
        // 身份在 ON_FINISH_INITIALIZE 之前已分配；被强制指定的特殊共犯即使之后身份表条目被替换（死亡转亡灵再转诅咒者）仍视为已使用。
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        AccompliceVariantRoundComponent.KEY.get(world).beginRound(AccompliceVariants.variants().stream()
                .filter(role -> !game.getAllWithRole(role).isEmpty())
                .toList());
        for (ServerPlayerEntity player : world.getPlayers()) {
            GrandWitchRuntimeComponent runtime = GrandWitchRuntimeComponent.KEY.get(player);
            runtime.clear();
            runtime.setRoundParticipants(openingParticipants);
        }
    }

    public static void clearRound(ServerWorld world) {
        GrandWitchRecruitmentRoundComponent.KEY.get(world).clearRound();
        AccompliceVariantRoundComponent.KEY.get(world).clearRound();
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

    /**
     * Special-accomplice pool: a uniform pick among enabled variants not used this round, else the plain Accomplice.
     * "Used" is the round ledger OR a live role-map entry, so a round-start forced variant also blocks the pool.
     * 特殊共犯池：在已启用且本局未使用的特殊共犯中均匀抽取，否则为普通共犯。
     * "已使用"为本局账本或身份表中仍存在该职业，因此开局被强制指定的特殊共犯同样占用名额。
     */
    private static Role rollRecruitRole(ServerWorld world, GameWorldComponent game) {
        AccompliceVariantRoundComponent used = AccompliceVariantRoundComponent.KEY.get(world);
        return AccompliceVariantRoll.pick(AccompliceVariants.variants(), game::isRoleEnabled,
                role -> used.isUsed(role) || !game.getAllWithRole(role).isEmpty(),
                new Random(world.getRandom().nextLong()));
    }

    private static void runVariantHook(Role variant, ServerPlayerEntity recruit, ServerPlayerEntity recruiter) {
        try {
            AccompliceVariants.hooks(variant).afterRecruitCommitted(recruit, recruiter);
        } catch (RuntimeException exception) {
            // Conversion already committed: a variant hook failure never turns into a retriable failure.
            // 转换已提交：特殊共犯回调失败不会变成可重试的失败，只记录日志。
            LOGGER.error("Recruitment committed but the {} post-recruit hook failed for {}",
                    variant.identifier(), recruit.getUuid(), exception);
        }
    }

    private static void releaseShadowPartner(ServerPlayerEntity partner, ServerPlayerEntity recruit) {
        try {
            // Outside the recruitment cause scope, so the replay labels it like NoellesRoles' own shadow transform.
            // 位于招募原因作用域之外，回放因此将其标为与 NoellesRoles 自身相同的影子化身。
            NoellesRecruitmentCleanup.releaseShadowPartner(partner);
        } catch (RuntimeException exception) {
            // Conversion already committed: a partner release failure never turns into a retriable failure.
            // 转换已提交：搭档化身失败不会变成可重试的失败，只记录日志。
            LOGGER.error("Recruitment committed but releasing the Shadow Jester partner {} of {} failed",
                    partner.getUuid(), recruit.getUuid(), exception);
        }
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
