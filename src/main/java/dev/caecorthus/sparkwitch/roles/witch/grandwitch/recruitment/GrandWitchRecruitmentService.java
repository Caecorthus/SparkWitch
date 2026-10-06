package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import dev.caecorthus.sparkfactionapi.api.PoliceRoles;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.compat.SparkTraitsLastStandBridge;
import dev.caecorthus.sparkwitch.compat.recruitment.NoellesRecruitmentCleanup;
import dev.caecorthus.sparkwitch.compat.recruitment.RecruitmentTraitChange;
import dev.caecorthus.sparkwitch.compat.recruitment.RecruitmentTraits;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperDragService;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderParticipation;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithLifecycle;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionFeatureService;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoll;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoundComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopUtils;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative, zero-cost and zero-cooldown recruitment transaction.
 * 服务端权威、免费、无冷却的招募事务；只在成功转换后消耗持久化名额。 */
public final class GrandWitchRecruitmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrandWitchRecruitmentService.class);

    private GrandWitchRecruitmentService() { }

    public static void register() {
        NoellesRecruitmentCleanup.register();
    }

    /**
     * The result plus the converted player, so the caller can settle the Witch Factor on the right recruit.
     * 结果与被转换的玩家，供调用方对正确的被招募者结算魔女因子。
     */
    public record Outcome(WitchSkillUseResult result, @Nullable ServerPlayerEntity recruit) {
        static Outcome fail(String messageKey) {
            return new Outcome(WitchSkillUseResult.fail(messageKey), null);
        }
    }

    /**
     * Owner request 2026-10-06: no aim. Each use converts a random pickable living player, or the player forced for
     * this recruitment number ({@code /sparkwitch:forceAccompliceRole}). The recruit leaves a fake corpse where they
     * stood, is teleported to the Grand Witch and is held for 5 seconds ({@link RecruitmentHold}).
     * 所有者 2026-10-06 要求：无需瞄准。每次使用转换一名随机的可选存活玩家，或本次招募序号被强制指定的玩家
     * （{@code /sparkwitch:forceAccompliceRole}）。被招募者在原地留下假尸体，传送到大魔女身边并被定身 5 秒（{@link RecruitmentHold}）。
     */
    public static Outcome use(ServerPlayerEntity recruiter) {
        ServerWorld world = recruiter.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning() || !GameFunctions.isPlayerPlayingAndAlive(recruiter)
                || !game.isRole(recruiter, SparkWitchRoles.grandWitch())) {
            return Outcome.fail("message.sparkwitch.recruitment.invalid_recruiter");
        }
        if (WitchPlayerComponent.KEY.get(recruiter).getGrandWitchCeremonialSwordTasks() < 2) {
            return Outcome.fail("message.sparkwitch.recruitment.locked");
        }
        GrandWitchRecruitmentRoundComponent round = GrandWitchRecruitmentRoundComponent.KEY.get(world);
        if (!round.tryBeginConversion()) {
            return Outcome.fail("message.sparkwitch.recruitment.no_capacity");
        }
        try {
            int order = RecruitmentTargetRules.nextOrder(round.getRecruitedCount());
            WitchWorldComponent forcedStore = WitchWorldComponent.KEY.get(world.getServer().getOverworld());
            ForcedRecruit forced = forcedStore.getForcedRecruit(order);
            RecruitmentTargetRules.Choice<ServerPlayerEntity> choice = chooseTarget(recruiter, world, game, order,
                    forced, forcedStore.getForcedRecruits());
            if (choice.dropForcedEntry()) {
                // The forced player left the round (dead, offline, already a witch): this number goes random.
                // 强制玩家已离开对局（死亡、离线、已是魔女阵营）：本序号改为随机。
                forcedStore.removeForcedRecruit(order);
            }
            ServerPlayerEntity target = choice.target();
            switch (choice.kind()) {
                case NONE -> {
                    return Outcome.fail("message.sparkwitch.recruitment.no_target");
                }
                case FORCED_BUSY -> {
                    return Outcome.fail("message.sparkwitch.recruitment.target_busy");
                }
                case FORCED, RANDOM -> {
                }
            }
            boolean forcedPick = choice.kind() == RecruitmentTargetRules.Kind.FORCED;
            // Real role, never a Black Raven acting overlay; the fake corpse shows it. / 读取真实身份（不读黑羽鸦伪装），假尸体显示该身份。
            Role formerRole = game.getRole(target);
            RecruitmentDecoyBody.Origin origin = RecruitmentDecoyBody.Origin.capture(target);
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
                return Outcome.fail("message.sparkwitch.recruitment.balance_overflow");
            }

            // Pick once, after every refusal and before any destructive step: a refused recruitment never spends a
            // special accomplice, and a throwing roll never leaves the target stripped. "Used" is marked only at commit.
            // 在所有拒绝分支之后、任何破坏性步骤之前只确定一次：被拒绝的招募不会消耗特殊共犯，抽取抛异常也不会让目标
            // 被清空。"已使用"仅在提交时标记。
            Role recruitRole = recruitRole(world, game, forcedPick ? forced : null);
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
            if (forcedPick) {
                forcedStore.removeForcedRecruit(order);
            }
            BlackRavenDisguiseService.discardStashesForRecruitment(target);
            for (ServerPlayerEntity player : world.getPlayers()) {
                syncRuntime(player);
            }
            RecruitmentTraitChange traitChange = RecruitmentTraitChange.NONE;
            try {
                // Standard assignment handles SparkStrength cleanup and SparkWitch mana/skill initialization; Traits
                // the recruit role cannot roll are swapped for redraws in the finally block below, the rest survive.
                // 标准分配事件处理 SparkStrength 清理及 SparkWitch 魔力、技能初始化；新身份无法抽到的 Traits 词条在下方
                // finally 中替换为补抽词条，其余保留。
                RoleAssigned.EVENT.invoker().assignRole(target, recruitRole);
            } catch (RuntimeException exception) {
                // Conversion already committed: do not report a retriable failure or lose factor recovery.
                // 转换已提交：不能返回可重试失败或跳过调用方的因子回收；记录扩展回调故障。
                LOGGER.error("Recruitment committed but a role-assignment listener failed for {}", target.getUuid(), exception);
            } finally {
                // The converted balance goes first: Well Supplied, kept or redrawn, multiplies it like starting money.
                // 先写入折算余额：持有物资充沛（保留或补抽到）时会像起始金币一样再加成一次。
                shop.setBalance(inventory.finalBalance());
                // Server-side swap (owner decisions 2026-10-04/05): after RoleAssigned so SparkTraits filters and
                // redraws against the committed role; before the restore so cleanup grants are wiped and the shop
                // sees final traits.
                // 服务端替换词条（所有者 2026-10-04/05 决定）：位于 RoleAssigned 之后，使 SparkTraits 按已提交的新身份
                // 筛选与补抽；位于背包恢复之前，使清理发放的物品被抹除、商店按最终词条初始化。
                traitChange = RecruitmentTraits.replaceIneligibleTraits(target);
                inventory.applyRetainedInventory(target);
                shop.initializeShop(ShopUtils.getShopEntriesForPlayer(target));
                game.sync();
                shop.sync();
            }
            int balance = shop.getBalance();
            if (shadowPartner != null) {
                releaseShadowPartner(shadowPartner, target);
            }
            if (variant) {
                runVariantHook(recruitRole, target, recruiter);
            }
            relocate(world, target, recruiter, origin, formerRole);
            Text roleName = Text.translatable("announcement.role." + recruitRole.identifier().getPath());
            if (variant) {
                target.sendMessage(Text.translatable("message.sparkwitch.recruitment.converted_as",
                        roleName, balance), false);
            } else {
                target.sendMessage(Text.translatable("message.sparkwitch.recruitment.converted", balance), false);
            }
            sendTraitChange(target, traitChange);
            // The Grand Witch did not choose the recruit, so she is told who it is (and the role).
            // WitchSkillUseResult carries no message arguments, so the line is sent here.
            // 招募对象由系统选出，因此告知大魔女是谁（及其身份）；WitchSkillUseResult 不支持消息参数，故在此直接发送。
            recruiter.sendMessage(Text.translatable("message.sparkwitch.recruitment.success_named",
                    target.getName(), roleName), true);
            return new Outcome(WitchSkillUseResult.success(0), target);
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
     * The forced role when this recruitment number has one, unless it is a special accomplice already used this round
     * (then the pool decides; a disabled forced role is still honoured, as the admin asked for it). Otherwise the
     * special-accomplice pool: a uniform pick among enabled variants not used this round, else the plain Accomplice.
     * "Used" is the round ledger OR a live role-map entry, so a round-start forced variant also blocks the pool.
     * 本次招募序号有强制身份时使用该身份，除非它是本局已使用的特殊共犯（此时交给抽取池；被禁用的强制身份仍照常给予，因为
     * 这是管理员的指定）。否则为特殊共犯池：在已启用且本局未使用的特殊共犯中均匀抽取，否则为普通共犯。
     * "已使用"为本局账本或身份表中仍存在该职业，因此开局被强制指定的特殊共犯同样占用名额。
     */
    private static Role recruitRole(ServerWorld world, GameWorldComponent game, @Nullable ForcedRecruit forced) {
        AccompliceVariantRoundComponent used = AccompliceVariantRoundComponent.KEY.get(world);
        java.util.function.Predicate<Role> isUsed = role -> used.isUsed(role) || !game.getAllWithRole(role).isEmpty();
        Role forcedRole = forced == null ? null : forcedRole(forced.role());
        if (forcedRole != null && !(AccompliceVariants.isVariant(forcedRole) && isUsed.test(forcedRole))) {
            return forcedRole;
        }
        return AccompliceVariantRoll.pick(AccompliceVariants.variants(), game::isRoleEnabled, isUsed,
                new Random(world.getRandom().nextLong()));
    }

    private static @Nullable Role forcedRole(Identifier roleId) {
        if (SparkWitchRoles.ACCOMPLICE_ID.equals(roleId)) {
            return SparkWitchRoles.accomplice();
        }
        for (Role variant : AccompliceVariants.variants()) {
            if (variant.identifier().equals(roleId)) {
                return variant;
            }
        }
        return null;
    }

    /**
     * Forced player of this number first, then a uniform pick among pickable players not reserved by a forced entry.
     * Candidates are this world's players; iteration order is the world's player list.
     * 先看本序号的强制玩家，再在未被强制条目预留的可选玩家中均匀抽取。候选为本世界玩家，顺序为世界玩家列表顺序。
     */
    private static RecruitmentTargetRules.Choice<ServerPlayerEntity> chooseTarget(
            ServerPlayerEntity recruiter,
            ServerWorld world,
            GameWorldComponent game,
            int order,
            @Nullable ForcedRecruit forced,
            Map<Integer, ForcedRecruit> forcedEntries
    ) {
        Set<UUID> reserved = new HashSet<>();
        forcedEntries.forEach((entryOrder, entry) -> {
            if (entryOrder >= order) {
                reserved.add(entry.player());
            }
        });
        List<ServerPlayerEntity> pool = new ArrayList<>();
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (!reserved.contains(candidate.getUuid())
                    && standing(recruiter, world, game, candidate) == RecruitmentTargetRules.Standing.PICKABLE) {
                pool.add(candidate);
            }
        }
        ServerPlayerEntity forcedPlayer = forced == null ? null
                : world.getServer().getPlayerManager().getPlayer(forced.player());
        RecruitmentTargetRules.Standing forcedStanding = forced == null ? null
                : forcedPlayer == null ? RecruitmentTargetRules.Standing.GONE
                : standing(recruiter, world, game, forcedPlayer);
        return RecruitmentTargetRules.choose(forcedPlayer, forcedStanding, pool,
                new Random(world.getRandom().nextLong()));
    }

    /**
     * Where one player stands for this recruitment. The refused roles are the old aimed-recruitment refusals (owner
     * rules 2026-10-02 and 2026-10-05): with a random pick they are simply never drawn. Real roles only.
     * 单名玩家在本次招募中的状态。拒绝的身份即旧版瞄准招募的拒绝规则（所有者 2026-10-02、2026-10-05 规则）：随机抽取时
     * 他们永远不会被抽中。只读真实身份。
     */
    private static RecruitmentTargetRules.Standing standing(
            ServerPlayerEntity recruiter,
            ServerWorld world,
            GameWorldComponent game,
            ServerPlayerEntity player
    ) {
        Role role = game.getRole(player);
        if (player == recruiter || player.getServerWorld() != world
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || WitchFactionRules.isWitchFactionMember(role)) {
            return RecruitmentTargetRules.Standing.GONE;
        }
        // A Rift Gate occupant is an ALIVE spectator (Riftwalker D3) and comes back; so do the other busy states.
        // Owner decision 2026-10-04: SparkTraits stashes a Depression psycho player's real inventory, which the
        // conversion would lose or leak.
        // 裂隙门内的玩家是存活旁观者（隙行者 D3），之后会回来；其余暂不可选状态同理。所有者 2026-10-04 决定：SparkTraits
        // 暂存了抑郁狂暴玩家的真实背包，转换会使其丢失或泄漏。
        if (player.isSpectator()
                || RecruitmentTraits.isDepressionPsychoActive(player)
                || NoellesRecruitmentCleanup.isSwallowed(player)
                || KidnapperControlComponent.KEY.maybeGet(player).map(KidnapperControlComponent::isControlled).orElse(false)) {
            return RecruitmentTargetRules.Standing.BUSY;
        }
        boolean emma = dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules.isEmma(role);
        var refusal = GrandWitchRecruitmentRules.refusal(InsiderParticipation.isCorruptCopRole(role),
                emma || PoliceRoles.contains(role), InsiderParticipation.isInsiderRole(role),
                SparkTraitsLastStandBridge.isLastStandLooseEnd(world, player.getUuid(), role));
        return refusal == GrandWitchRecruitmentRules.Refusal.NONE
                ? RecruitmentTargetRules.Standing.PICKABLE
                : RecruitmentTargetRules.Standing.REFUSED;
    }

    /**
     * Committed conversion, last step: the fake corpse where the recruit stood, the teleport to the Grand Witch, then
     * the 5-second hold. Failures are logged and never undo the recruitment.
     * 已提交的转换的最后一步：在被招募者原地生成假尸体、传送到大魔女身边，然后定身 5 秒。失败只记录日志，绝不撤销招募。
     */
    private static void relocate(
            ServerWorld world,
            ServerPlayerEntity recruit,
            ServerPlayerEntity recruiter,
            RecruitmentDecoyBody.Origin origin,
            @Nullable Role formerRole
    ) {
        try {
            if (formerRole != null) {
                RecruitmentDecoyBody.spawn(world, recruit, origin, formerRole.identifier(),
                        new Random(world.getRandom().nextLong()));
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Recruitment committed but the fake corpse of {} failed", recruit.getUuid(), exception);
        }
        try {
            recruit.stopRiding();
            if (recruit.isSleeping()) {
                recruit.wakeUp();
            }
            recruit.teleport(recruiter.getServerWorld(), recruiter.getX(), recruiter.getY(), recruiter.getZ(),
                    recruiter.getYaw(), 0.0F);
            recruit.fallDistance = 0.0F;
            RecruitmentHold.apply(recruit);
        } catch (RuntimeException exception) {
            LOGGER.error("Recruitment committed but moving {} to the Grand Witch failed", recruit.getUuid(), exception);
        }
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

    /** One recruit-only line naming the visible traits the swap removed and drew; hidden ones were already left out,
     * so a recruit who lost only hidden traits hears just the redraws.
     * 仅向被招募者发送一行，列出替换中失去与补抽的可见词条；隐藏词条已由提供方剔除，因此只失去隐藏词条者只看到补抽结果。 */
    private static void sendTraitChange(ServerPlayerEntity recruit, RecruitmentTraitChange change) {
        if (change.lost().isEmpty() && change.gained().isEmpty()) return;
        Text separator = Text.translatable("message.sparkwitch.recruitment.traits_lost.separator");
        Text line;
        if (change.gained().isEmpty()) {
            line = Text.translatable("message.sparkwitch.recruitment.traits_lost", Texts.join(change.lost(), separator));
        } else if (change.lost().isEmpty()) {
            line = Text.translatable("message.sparkwitch.recruitment.traits_rerolled", Texts.join(change.gained(), separator));
        } else {
            line = Text.translatable("message.sparkwitch.recruitment.traits_replaced",
                    Texts.join(change.lost(), separator), Texts.join(change.gained(), separator));
        }
        recruit.sendMessage(line, false);
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
