package dev.caecorthus.sparkwitch.roles.witch.bewitched;

import dev.caecorthus.sparkfactionapi.api.replay.SparkReplayApi;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.special.wraith.runtime.WraithRoleAnnouncementService;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariantRoundComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopUtils;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server-authoritative Bewitched promotion (C3, C4). {@link #onTaskComplete} counts tasks and queues the promotion;
 * {@link #promote} is the transaction the end-of-tick queue runs: pick the role once (forced lock, else the
 * special-accomplice roll), write it under the {@code sparkwitch:bewitched_promotion} replay cause, mark a special
 * accomplice used, fire {@code RoleAssigned} for skills, mana and loadout, restore the balance it reset, rebuild the
 * shop, then run the special accomplice's hook and announce the new role. The inventory is kept.
 * 服务端权威的魔化使晋升（C3、C4）。{@link #onTaskComplete} 计数并让晋升入队；{@link #promote} 是刻末队列执行的事务：只选择
 * 一次身份（强制锁定，否则从特殊共犯池抽取），在 {@code sparkwitch:bewitched_promotion} 回放原因下写入身份，标记特殊共犯
 * 已使用，触发 {@code RoleAssigned} 以初始化技能、魔力与装备，恢复被其重置的余额，重建商店，然后执行特殊共犯回调并公告新身份。
 * 背包保持不变。
 */
public final class BewitchedPromotionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BewitchedPromotionService.class);
    static final String PROMOTED_KEY = "message.sparkwitch.bewitched.promoted";
    static final String PROMOTED_GRAND_WITCH_KEY = "message.sparkwitch.bewitched.promoted_grand_witch";

    private BewitchedPromotionService() {
    }

    /**
     * Wathe {@code TaskComplete} listener. Wathe is iterating the task map here, so this only counts and enqueues; it
     * never changes the role.
     * Wathe {@code TaskComplete} 监听器。此时 Wathe 正在遍历任务表，因此这里只计数与入队，从不变更身份。
     */
    public static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        // ACTIVE only (never once the win is decided); real role, never a Black Raven acting overlay.
        // 仅在 ACTIVE 阶段（胜负已定后不计）；读取真实身份，不读黑羽鸦伪装覆盖层。
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !BewitchedRules.isBewitched(game.getRole(player))) {
            return;
        }
        BewitchedPlayerComponent progress = BewitchedPlayerComponent.KEY.get(player);
        int previous = progress.getPromotionTasks();
        if (BewitchedRules.reachesPromotion(previous, progress.recordTask())) {
            BewitchedPromotionQueue.enqueue(player.getUuid());
        }
    }

    /** Called by the queue after validation, on the server thread. / 由队列在校验后于服务端线程调用。 */
    static void promote(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        // Locks live on the overworld component, like forced Wraith promotions. / 锁定与强制冤魂晋升一样保存在主世界组件上。
        WitchWorldComponent locks = WitchWorldComponent.KEY.get(world.getServer().getOverworld());
        AccompliceVariantRoundComponent ledger = AccompliceVariantRoundComponent.KEY.get(world);
        UUID uuid = player.getUuid();

        Role role = choosePromotionRole(world, game, locks, ledger, uuid);
        boolean variant = AccompliceVariants.isVariant(role);
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        int balance = shop.getBalance();
        SparkReplayApi.withRoleChangeCause(BewitchedRules.PROMOTION_CAUSE, (UUID) null,
                () -> game.addRole(player, role));
        if (variant) {
            ledger.markUsed(role);
        }
        // The lock is consumed (or was dropped as already used). / 锁定已被使用（或因已被使用而作废）。
        locks.clearForcedAccompliceRole(uuid);
        BewitchedPlayerComponent.KEY.get(player).clear();
        try {
            // Skills, mana, starting money and role kits (SparkWitch and other mods). / 技能、魔力、开局金币与职业装备。
            RoleAssigned.EVENT.invoker().assignRole(player, role);
        } catch (RuntimeException exception) {
            // The role is committed: log the listener failure, never undo. / 身份已提交：记录监听器故障，绝不撤销。
            LOGGER.error("Bewitched promotion committed but a role-assignment listener failed for {}", uuid, exception);
        } finally {
            // RoleAssigned reset the balance to starting money; the Bewitched keeps what it earned (C4).
            // RoleAssigned 已把余额重置为开局金币；魔化使保留已赚到的金币（C4）。
            shop.setBalance(balance);
            shop.initializeShop(ShopUtils.getShopEntriesForPlayer(player));
            game.sync();
            shop.sync();
        }
        if (variant) {
            runVariantHook(role, player);
        }
        WraithRoleAnnouncementService.announceCurrentRole(player);
        sendPromotionLines(world, game, player, role);
    }

    /**
     * One role per promotion, chosen before any mutation. "Used" is the round ledger OR a live role-map entry; a
     * special accomplice is reserved while another living Bewitched holds its lock.
     * 每次晋升只选一次身份，且在任何变更之前。"已使用"为本局账本或身份表中仍存在该职业；另一名存活魔化使持有其锁定时，
     * 该特殊共犯被预留。
     */
    private static Role choosePromotionRole(ServerWorld world, GameWorldComponent game, WitchWorldComponent locks,
                                            AccompliceVariantRoundComponent ledger, UUID self) {
        Role plainAccomplice = SparkWitchRoles.accomplice();
        Map<UUID, Identifier> lockMap = locks.getForcedAccompliceRoles();
        Predicate<UUID> livingBewitched = holder -> isLivingBewitched(game, holder);
        Role locked = BewitchedPromotionRules.resolveLock(lockMap.get(self), plainAccomplice,
                AccompliceVariants.variants());
        return BewitchedPromotionRules.choose(locked, plainAccomplice, AccompliceVariants.variants(),
                game::isRoleEnabled,
                role -> ledger.isUsed(role) || !game.getAllWithRole(role).isEmpty(),
                role -> BewitchedPromotionRules.reservedByAnother(role, self, lockMap, livingBewitched),
                new Random(world.getRandom().nextLong()));
    }

    /**
     * A living player in this round (in the role map, not dead, online or not) whose real role is the Bewitched: the
     * one test for a lock that reserves a seat (D4, C5), shared by the promotion roll and the lock command.
     * 本局存活（在身份表中且未死亡，是否在线均可）且真实身份为魔化使的玩家：判断锁定是否预留席位的唯一标准（D4、C5），
     * 由晋升抽取与锁定命令共用。
     */
    public static boolean isLivingBewitched(GameWorldComponent game, UUID player) {
        return game.hasAnyRole(player) && !game.isPlayerDead(player)
                && BewitchedRules.isBewitched(game.getRole(player));
    }

    private static void runVariantHook(Role variant, ServerPlayerEntity player) {
        try {
            AccompliceVariants.hooks(variant).afterPromotionCommitted(player);
        } catch (RuntimeException exception) {
            // The promotion is committed: a hook failure is logged, never undone. / 晋升已提交：回调失败只记录日志，不撤销。
            LOGGER.error("Bewitched promotion committed but the {} post-promotion hook failed for {}",
                    variant.identifier(), player.getUuid(), exception);
        }
    }

    /**
     * The promoted player learns the role; every living Grand Witch learns who and what (C4).
     * 晋升者得知自己的身份；每名存活的大魔女得知是谁晋升为何种身份（C4）。
     */
    private static void sendPromotionLines(ServerWorld world, GameWorldComponent game, ServerPlayerEntity player,
                                           Role role) {
        Text roleName = Text.translatable(RoleDisplayTextRules.roleTranslationKey(role)).withColor(role.color());
        player.sendMessage(Text.translatable(PROMOTED_KEY, roleName), false);
        Text line = Text.translatable(PROMOTED_GRAND_WITCH_KEY, player.getName(), roleName);
        for (UUID grandWitchId : game.getAllWithRole(SparkWitchRoles.grandWitch())) {
            ServerPlayerEntity grandWitch = world.getServer().getPlayerManager().getPlayer(grandWitchId);
            if (grandWitch != null && grandWitch != player && GameFunctions.isPlayerPlayingAndAlive(grandWitch)) {
                grandWitch.sendMessage(line, false);
            }
        }
    }
}
