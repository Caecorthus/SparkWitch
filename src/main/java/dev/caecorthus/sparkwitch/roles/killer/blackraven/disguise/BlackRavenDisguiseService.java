package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMatch;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapter;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapters;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.DisguiseExitReason;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-authoritative owner of every disguise transition: the switch transaction, per-identity wallets,
 * lifecycle ends, and the only writer of the server acting index. Runs on the server thread; a failed
 * transaction reverts to the Black Raven identity best-effort and never drops an item. It never fires
 * RoleAssigned, calls addRole or initializeShop, records Tarot history, or tags stacks. The per-identity
 * wallet swap inside the transaction is the only setBalance caller (amendment W).
 * 所有伪装切换的服务端权威拥有者：切换事务、各身份钱包、生命周期结束，以及服务端扮演索引的唯一写入方。
 * 在服务端线程运行；事务失败时尽力恢复为黑羽鸦身份，且从不丢弃物品。从不触发 RoleAssigned、调用
 * addRole/initializeShop、记录塔罗历史或给物品打标记。事务内的身份钱包交换是唯一的 setBalance 调用方（修订 W）。
 */
public final class BlackRavenDisguiseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlackRavenDisguiseService.class);
    private static final Identifier RAVEN = BlackRavenDisguiseRules.BLACK_RAVEN_ID;
    static final String REPLAY_FROM_KEY = "from";
    static final String REPLAY_TO_KEY = "to";
    private static final String REPLAY_ACTOR_KEY = "actor";
    private static final String REPLAY_BECAME_KEY = "replay.sparkwitch.black_raven_disguise.became";
    private static final String REPLAY_REVERTED_KEY = "replay.sparkwitch.black_raven_disguise.reverted";

    /**
     * Stash that receives any offerOrDrop remainder while a transaction for that player is running (server thread).
     * 某玩家事务进行期间，接收其 offerOrDrop 剩余物品的存档（服务端线程）。
     */
    private static final Map<UUID, BlackRavenIdentityStash> TRANSACTION_CAPTURE = new ConcurrentHashMap<>();
    private static boolean registered;

    private BlackRavenDisguiseService() {
    }

    /** Registers JOIN, ON_GAME_START, SERVER_STOPPED and the replay formatter. / 注册新增的生命周期事件与回放格式化器。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        GameEvents.ON_GAME_START.register(gameMode -> clearAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
        ReplayRegistry.registerSkillFormatter(BlackRavenDisguiseRules.DISGUISE_ID, BlackRavenDisguiseService::formatReplay);
    }

    /** C2S select handler (server thread). / C2S 选择处理（服务端线程）。 */
    public static void requestSwitch(ServerPlayerEntity player, int session, Identifier target) {
        if (player == null || target == null) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();
        boolean sessionValid = BlackRavenDisguiseSessions.consume(player.getUuid(), session, now);
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean eligible = sessionValid && isEligible(player, game, state);
        BlackRavenDisguiseRules.SwitchCheck check = eligible
                ? new BlackRavenDisguiseRules.SwitchCheck(
                        true,
                        true,
                        now,
                        state.unlockAt(),
                        state.nextSwitchAt(),
                        state.acting(),
                        target,
                        isTargetSelectable(state, target),
                        isWindowActive(player, state.acting()),
                        NoellesTaotieSeekerBridge.isSwallowed(player),
                        GrandWitchFearService.isPlayerFeared(player),
                        SparkTraitsKillerBridge.isRoleSkillBlocked(player))
                : new BlackRavenDisguiseRules.SwitchCheck(sessionValid, false, now, state.unlockAt(),
                        state.nextSwitchAt(), state.acting(), target, false, false, false, false, false);
        BlackRavenDisguiseRules.SwitchVerdict verdict = BlackRavenDisguiseRules.canSwitch(check);
        if (verdict != BlackRavenDisguiseRules.SwitchVerdict.ALLOWED) {
            sendRefusal(player, verdict, state, now);
            return;
        }

        Identifier from = identityId(state.acting());
        boolean reverting = RAVEN.equals(target);
        SwapResult result = swap(player, component, target,
                reverting ? DisguiseExitReason.REVERT : DisguiseExitReason.SWITCH, now);
        if (result == null) {
            return;
        }
        // No mask item cooldown: vanilla refuses use of a cooling item, which would block opening while locked
        // (plan §3.5); the tooltip/ledger show the countdown from the owner sync instead.
        // 不给面具设置物品冷却：原版会拒绝冷却中物品的使用，从而阻止未解锁时打开（计划 §3.5）；倒计时由拥有者同步在提示/账本中显示。
        player.sendMessage(reverting
                ? Text.translatable(BlackRavenDisguiseRules.messageKey("reverted"))
                : Text.translatable(BlackRavenDisguiseRules.messageKey("became"), roleName(target)), true);
        if (result.itemsKept()) {
            player.sendMessage(Text.translatable(BlackRavenDisguiseRules.messageKey("items_kept")), false);
        }
        NbtCompound extra = new NbtCompound();
        extra.putString(REPLAY_FROM_KEY, from.toString());
        extra.putString(REPLAY_TO_KEY, target.toString());
        GameRecordManager.recordSkillUse(player, BlackRavenDisguiseRules.DISGUISE_ID, null, extra);
    }

    /**
     * Component server tick: every 20 ticks, ends a stale state (other match, lost role, dead), drops an expired
     * session, and re-seeds a missing index entry for a live disguise.
     * 组件服务端刻：每 20 刻结束过期状态（其他对局、失去职业、死亡）、丢弃过期会话，并为仍有效的伪装补回索引。
     */
    public static void tick(ServerPlayerEntity player, BlackRavenDisguiseComponent component) {
        BlackRavenDisguiseState state = component.state();
        if (!state.hasRoundState() || player.age % BlackRavenDisguiseRules.FALLBACK_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        BlackRavenDisguiseSessions.isOpen(player.getUuid(), player.getServerWorld().getTime());
        if (!isLiveRaven(player, state)) {
            endSilently(player);
            return;
        }
        Identifier acting = state.acting();
        if (acting != null && BlackRavenActingRole.serverActing(player.getUuid()) == null) {
            Role role = BlackRavenActingRole.resolveRole(acting);
            if (role == null) {
                endSilently(player);
            } else {
                BlackRavenActingRole.putServer(player.getUuid(), role);
            }
        }
    }

    public static boolean isBoundToCurrentMatch(ServerPlayerEntity player) {
        return player != null && BlackRavenDisguiseComponent.KEY.get(player).state().isBoundTo(BlackRavenMatch.currentId());
    }

    // Lifecycle, called by BlackRavenFeatureService (stage 2). / 生命周期，由 BlackRavenFeatureService 调用（第二阶段）。

    /** ON_FINISH_INITIALIZE: binds and snapshots a Raven, clears everyone else. / 开局：绑定黑羽鸦并快照名单，清除其他人。 */
    public static void beginRound(ServerPlayerEntity player, ServerWorld world, GameWorldComponent game) {
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        exit(state.acting(), player, DisguiseExitReason.RESET);
        state.clear();
        forget(player);
        UUID matchId = BlackRavenMatch.currentId();
        if (matchId != null && BlackRavenRules.isBlackRaven(game.getRole(player))) {
            long now = world.getTime();
            state.bind(matchId, now);
            try {
                state.setPool(BlackRavenDisguisePool.snapshot(world, game));
            } catch (RuntimeException exception) {
                // An empty pool fails closed: nothing is selectable this round. / 空名单失败关闭：本局无可选项。
                LOGGER.error("Black Raven disguise pool snapshot failed for {}", player.getUuid(), exception);
            }
        }
        component.sync();
    }

    /** KillPlayer.AFTER, before the existing Perception cleanup. / 死亡后，在现有感知清理之前。 */
    public static void endForDeath(ServerPlayerEntity player) {
        end(player, DisguiseExitReason.DEATH, true);
    }

    /** Raw role changed away: stashes vanish; the live inventory is left to the new role. / 失去职业：存档消失，当前背包交给新职业。 */
    public static void endForRoleLoss(ServerPlayerEntity player) {
        end(player, DisguiseExitReason.ROLE_LOSS, false);
    }

    /**
     * Grand Witch recruitment, non-destructive half: swap back to the Raven set and wallet before the inventory
     * snapshot. Every other identity's stash and the visited set are kept, so a recruitment refused after this
     * (e.g. balance overflow) leaves an un-disguised Raven that can still re-enter its disguises intact.
     * 大魔女招募的非破坏部分：在背包快照前换回黑羽鸦物品与钱包。其他身份的存档与已访问集合均保留，
     * 因此之后被拒绝的招募（如余额溢出）只会留下未伪装的黑羽鸦，其伪装仍可完整重新进入。
     */
    public static void revertForRecruitment(ServerPlayerEntity player) {
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        if (!state.hasRoundState()) {
            forget(player);
            return;
        }
        if (state.acting() != null) {
            swap(player, component, RAVEN, DisguiseExitReason.RECRUITMENT, player.getServerWorld().getTime());
        }
        state.setActing(null);
        forget(player);
        component.sync();
    }

    /**
     * Grand Witch recruitment, destructive half: called only after the conversion commits (the role map already
     * changed), it discards every other identity's stash and the visited set.
     * 大魔女招募的破坏部分：仅在转换已提交（身份映射已变更）后调用，丢弃所有其他身份的存档与已访问集合。
     */
    public static void discardStashesForRecruitment(ServerPlayerEntity player) {
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        boolean hadState = state.hasRoundState();
        state.clearStashes();
        state.clearVisited();
        state.setActing(null);
        forget(player);
        if (hadState) {
            component.sync();
        }
    }

    /**
     * RoleAssigned(black_raven) for a player who may already be a disguised Raven (e.g. a forced role mid-round):
     * swaps back to the Raven set and wallet before the loadout re-grants its kit, so the stashed blade and ledger
     * return to the live inventory, the kit's cleanup removes them, and exactly one blade, ledger and mask remain.
     * Other identities' stashes and the visited set are kept, as for an un-disguised Raven. An unbound state ends
     * silently instead.
     * 对可能已处于伪装中的玩家触发 RoleAssigned(black_raven)（如回合中强制指定职业）：在装备服务重新发放套装前
     * 换回黑羽鸦物品与钱包，使存档中的羽刃与账本回到当前背包并被套装清理移除，最终恰好保留一把羽刃、一本账本、
     * 一个假面。其他身份的存档与已访问集合保持不变，与未伪装的黑羽鸦一致；未绑定的状态则静默结束。
     */
    public static void revertForRoleReassign(ServerPlayerEntity player) {
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        if (state.acting() == null) {
            return;
        }
        if (!state.isBoundTo(BlackRavenMatch.currentId())) {
            endSilently(player);
            return;
        }
        swap(player, component, RAVEN, DisguiseExitReason.REVERT, player.getServerWorld().getTime());
        state.setActing(null);
        forget(player);
        component.sync();
    }

    /** Transient cleanup only; component NBT and the live inventory persist. / 仅临时清理；组件 NBT 与当前背包保留。 */
    public static void onDisconnect(ServerPlayerEntity player) {
        exit(BlackRavenDisguiseComponent.KEY.get(player).state().acting(), player, DisguiseExitReason.DISCONNECT);
        forget(player);
    }

    /** Re-seeds the index for a bound, alive raw Raven; otherwise ends silently. / 为绑定且存活的黑羽鸦补回索引，否则静默结束。 */
    public static void onJoin(ServerPlayerEntity player) {
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        if (!state.hasRoundState()) {
            return;
        }
        if (!isLiveRaven(player, state)) {
            endSilently(player);
            return;
        }
        Identifier acting = state.acting();
        if (acting != null) {
            Role role = BlackRavenActingRole.resolveRole(acting);
            if (role == null) {
                endSilently(player);
                return;
            }
            BlackRavenActingRole.putServer(player.getUuid(), role);
        }
        component.sync();
    }

    /** ResetPlayer and ON_FINISH_FINALIZE. / 玩家重置与对局结算。 */
    public static void clearForReset(ServerPlayerEntity player) {
        end(player, DisguiseExitReason.RESET, false);
    }

    public static void endSilently(ServerPlayerEntity player) {
        end(player, DisguiseExitReason.STALE, false);
    }

    /** ON_GAME_START and SERVER_STOPPED: whole index and all sessions. / 清空整个索引与所有会话。 */
    public static void clearAll() {
        BlackRavenActingRole.clearServer();
        BlackRavenDisguiseSessions.clearAll();
        TRANSACTION_CAPTURE.clear();
    }

    // Guards (amendment fix 2, M4). / 保护窗口（修订 2 与 M4）。

    /** True for a bound Raven within 20 ticks of LastChangeAt. / 绑定的黑羽鸦在最近变化后 20 刻内为 true。 */
    public static boolean isWithinPostSwitchGuard(PlayerEntity player) {
        if (player == null || player.getWorld().isClient) {
            return false;
        }
        BlackRavenDisguiseState state = BlackRavenDisguiseComponent.KEY.get(player).state();
        return BlackRavenDisguiseRules.isWithinPostSwitchGuard(state.lastChangeAt(), player.getWorld().getTime())
                && state.isBoundTo(BlackRavenMatch.currentId());
    }

    /**
     * Routes an offerOrDrop remainder into the stash overflow instead of the floor: into the transaction's stash
     * while a swap runs, else into the live identity's stash for a bound, alive raw Raven inside the 20-tick window.
     * Stores a copy; true when captured.
     * 将 offerOrDrop 剩余物品转入存档溢出区而非掉落：交换进行时进入事务存档；否则仅对绑定、存活的真实黑羽鸦，
     * 在 20 刻窗口内进入当前身份存档。保存副本；被收取时返回 true。
     */
    public static boolean captureSwapRemainder(PlayerEntity player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.getWorld().isClient
                || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        BlackRavenIdentityStash scoped = TRANSACTION_CAPTURE.get(serverPlayer.getUuid());
        if (scoped != null) {
            scoped.overflow().add(stack.copy());
            return true;
        }
        if (!isWithinPostSwitchGuard(serverPlayer)) {
            return false;
        }
        BlackRavenDisguiseState state = BlackRavenDisguiseComponent.KEY.get(serverPlayer).state();
        if (!isLiveRaven(serverPlayer, state)) {
            return false;
        }
        state.stashOrCreate(identityId(state.acting())).overflow().add(stack.copy());
        serverPlayer.sendMessage(Text.translatable(BlackRavenDisguiseRules.messageKey("items_kept")), false);
        return true;
    }

    // Separate wallets (amendment W). / 独立钱包（修订 W）。

    /** True when killer income must go to the stashed Black Raven wallet. / 杀手收入需进入黑羽鸦存档钱包时为 true。 */
    public static boolean routesKillerIncome(PlayerEntity player) {
        if (player == null || player.getWorld().isClient || BlackRavenActingRole.serverActing(player.getUuid()) == null) {
            return false;
        }
        BlackRavenDisguiseState state = BlackRavenDisguiseComponent.KEY.get(player).state();
        return state.acting() != null
                && state.stash(RAVEN) != null
                && state.isBoundTo(BlackRavenMatch.currentId());
    }

    public static int ravenWalletBalance(PlayerEntity player) {
        if (player == null) {
            return 0;
        }
        BlackRavenIdentityStash raven = BlackRavenDisguiseComponent.KEY.get(player).state().stash(RAVEN);
        return raven == null ? 0 : Math.max(0, raven.balance());
    }

    /** Adds to the stashed Raven wallet (never below 0) and syncs the owner. / 向黑羽鸦存档钱包入账（不低于 0）并同步拥有者。 */
    public static void creditRavenWallet(PlayerEntity player, int amount) {
        if (player == null || player.getWorld().isClient) {
            return;
        }
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenIdentityStash raven = component.state().stash(RAVEN);
        if (raven == null) {
            return;
        }
        int next = (int) Math.clamp((long) raven.balance() + amount, 0L, Integer.MAX_VALUE);
        if (next != raven.balance()) {
            raven.setBalance(next);
            component.sync();
        }
    }

    // Transaction. / 事务。

    /** Outcome of a committed swap. / 已提交交换的结果。 */
    private record SwapResult(boolean firstEntry, boolean itemsKept) {
    }

    /** Where the live items and wallet belong while a swap runs, for the best-effort recovery. / 交换期间物品与钱包的归属，用于尽力恢复。 */
    private static final class Progress {
        private final Identifier from;
        private final Identifier to;
        private @Nullable Identifier liveItems;
        private boolean walletLoaded;
        private boolean committed;

        private Progress(Identifier from, Identifier to) {
            this.from = from;
            this.to = to;
            this.liveItems = from;
        }
    }

    /**
     * One identity change, in the contract order: onExit(old), the no-drop swap, first-entry kit and aligned
     * cooldowns, restored scalars and wallet, Acting plus index, then onEnter(new). Returns null on failure after
     * a best-effort revert to the Black Raven identity.
     * 一次身份变化，按契约顺序：onExit(旧)、无丢弃交换、首次进入的物品与对齐冷却、恢复数值与钱包、写入
     * Acting 与索引、最后 onEnter(新)。失败时尽力恢复为黑羽鸦并返回 null。
     */
    private static @Nullable SwapResult swap(ServerPlayerEntity player, BlackRavenDisguiseComponent component,
                                             Identifier to, DisguiseExitReason reason, long now) {
        BlackRavenDisguiseState state = component.state();
        Identifier fromActing = state.acting();
        Progress progress = new Progress(identityId(fromActing), to);
        boolean toRaven = RAVEN.equals(to);
        BlackRavenDisguiseAdapter newAdapter = toRaven ? null : BlackRavenDisguiseAdapters.get(to);
        Role newRole = toRaven ? null : BlackRavenActingRole.resolveRole(to);
        if (!toRaven && (newAdapter == null || newRole == null)) {
            return null;
        }
        UUID uuid = player.getUuid();
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(player);
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        try {
            exit(fromActing, player, reason);
            player.clearActiveItem();

            BlackRavenIdentityStash outgoing = state.stashOrCreate(progress.from);
            TRANSACTION_CAPTURE.put(uuid, outgoing);
            List<ItemStack> pinned = BlackRavenInventorySwap.detachScreenInputs(player, outgoing);
            BlackRavenInventorySwap.stashLive(player, outgoing);
            progress.liveItems = null;
            BlackRavenInventorySwap.returnPinned(player, pinned, outgoing);
            outgoing.setAbilityCooldownUntil(now + Math.max(0, ability.getCooldown()));
            outgoing.setSkillId(witch.getActiveSkillId());
            outgoing.setSkillCooldownUntil(now + Math.max(0, witch.getCooldownTicks()));
            outgoing.setBalance(Math.max(0, shop.getBalance()));

            boolean first = newAdapter != null && !state.isVisited(to);
            BlackRavenIdentityStash saved = state.stash(to);
            BlackRavenIdentityStash incoming = state.stashOrCreate(to);
            TRANSACTION_CAPTURE.put(uuid, incoming);
            progress.liveItems = to;
            boolean itemsKept = BlackRavenInventorySwap.loadStash(player, incoming);
            if (first) {
                state.markVisited(to);
                List<ItemStack> kit = newAdapter.firstKit(player);
                itemsKept |= BlackRavenInventorySwap.grantKit(player, kit == null ? List.of() : kit, incoming);
                BlackRavenDisguiseAdapter.FirstEntry entry =
                        new BlackRavenDisguiseAdapter.FirstEntry(state.roundStartAt(), now);
                ability.setCooldown(entry.aligned(newAdapter.initialAbilityCooldownTicks()));
                witch.setActiveSkill(newAdapter.witchSkillId());
                witch.setCooldownTicks(entry.aligned(newAdapter.initialWitchSkillCooldownTicks()));
                newAdapter.onFirstEntry(player, entry);
                setBalance(shop, 0);
            } else {
                ability.setCooldown(saved == null ? 0
                        : BlackRavenDisguiseRules.remainingTicks(saved.abilityCooldownUntil(), now));
                witch.setActiveSkill(saved == null ? defaultSkill(to, newAdapter) : saved.skillId());
                witch.setCooldownTicks(saved == null ? 0
                        : BlackRavenDisguiseRules.remainingTicks(saved.skillCooldownUntil(), now));
                if (saved != null || !toRaven) {
                    setBalance(shop, saved == null ? 0 : saved.balance());
                }
            }
            progress.walletLoaded = true;

            state.setActing(toRaven ? null : to);
            if (newRole != null) {
                BlackRavenActingRole.putServer(uuid, newRole);
            } else {
                BlackRavenActingRole.removeServer(uuid);
            }
            state.recordChange(now);
            progress.committed = true;
            BlackRavenDisguiseSessions.clear(uuid);
            component.sync();
            witch.sync();
            if (newAdapter != null) {
                newAdapter.onEnter(player, first);
            }
            BlackRavenInventorySwap.resync(player);
            // The live identity keeps a stash only for items that are still waiting in its overflow.
            // 当前身份仅在其溢出区仍有物品时保留存档。
            if (!incoming.hasItems()) {
                state.removeStash(to);
            }
            return new SwapResult(first, itemsKept || incoming.hasItems());
        } catch (RuntimeException exception) {
            LOGGER.error("Black Raven disguise switch {} -> {} failed for {}; reverting to Black Raven",
                    progress.from, to, uuid, exception);
            recover(player, component, progress, shop, ability, witch, now);
            return null;
        } finally {
            TRANSACTION_CAPTURE.remove(uuid);
        }
    }

    /**
     * Best-effort revert after a failed transaction: live items go back to the identity that owns them, the Raven
     * set and wallet are restored, and the index entry is removed. Never drops an item.
     * 事务失败后的尽力恢复：当前物品归还其所属身份，恢复黑羽鸦物品与钱包，并移除索引条目。从不丢弃物品。
     */
    private static void recover(ServerPlayerEntity player, BlackRavenDisguiseComponent component, Progress progress,
                                PlayerShopComponent shop, AbilityPlayerComponent ability, WitchPlayerComponent witch,
                                long now) {
        UUID uuid = player.getUuid();
        BlackRavenDisguiseState state = component.state();
        try {
            if (progress.committed && !RAVEN.equals(progress.to)) {
                exit(progress.to, player, DisguiseExitReason.REVERT);
            }
            Identifier live = progress.liveItems;
            if (live != null && !RAVEN.equals(live)) {
                BlackRavenIdentityStash holder = state.stashOrCreate(live);
                TRANSACTION_CAPTURE.put(uuid, holder);
                List<ItemStack> pinned = BlackRavenInventorySwap.detachScreenInputs(player, holder);
                BlackRavenInventorySwap.stashLive(player, holder);
                BlackRavenInventorySwap.returnPinned(player, pinned, holder);
            }
            BlackRavenIdentityStash raven = state.stash(RAVEN);
            if (!RAVEN.equals(live) && raven != null) {
                TRANSACTION_CAPTURE.put(uuid, raven);
                BlackRavenInventorySwap.loadStash(player, raven);
                ability.setCooldown(BlackRavenDisguiseRules.remainingTicks(raven.abilityCooldownUntil(), now));
                witch.setActiveSkill(raven.skillId());
                witch.setCooldownTicks(BlackRavenDisguiseRules.remainingTicks(raven.skillCooldownUntil(), now));
            }
            Identifier walletOwner = progress.walletLoaded ? progress.to : progress.from;
            if (!RAVEN.equals(walletOwner)) {
                state.stashOrCreate(walletOwner).setBalance(Math.max(0, shop.getBalance()));
                if (raven != null) {
                    setBalance(shop, raven.balance());
                }
            }
            if (raven != null && !raven.hasItems()) {
                state.removeStash(RAVEN);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Black Raven disguise recovery failed for {}", uuid, exception);
        } finally {
            state.setActing(null);
            BlackRavenActingRole.removeServer(uuid);
            state.recordChange(now);
            BlackRavenDisguiseSessions.clear(uuid);
            component.sync();
            witch.sync();
            BlackRavenInventorySwap.resync(player);
        }
    }

    // Helpers. / 辅助方法。

    private static void end(ServerPlayerEntity player, DisguiseExitReason reason, boolean keepBinding) {
        BlackRavenDisguiseComponent component = BlackRavenDisguiseComponent.KEY.get(player);
        BlackRavenDisguiseState state = component.state();
        boolean hadState = state.hasRoundState();
        exit(state.acting(), player, reason);
        if (keepBinding) {
            state.clearStashes();
            state.clearVisited();
            state.setActing(null);
        } else {
            state.clear();
        }
        forget(player);
        if (hadState) {
            component.sync();
        }
    }

    /** Index entry, session and any transaction capture of this player. / 该玩家的索引条目、会话与事务收取。 */
    private static void forget(ServerPlayerEntity player) {
        BlackRavenActingRole.removeServer(player.getUuid());
        BlackRavenDisguiseSessions.clear(player.getUuid());
    }

    private static void exit(@Nullable Identifier acting, ServerPlayerEntity player, DisguiseExitReason reason) {
        BlackRavenDisguiseAdapter adapter = BlackRavenDisguiseAdapters.get(acting);
        if (adapter == null) {
            return;
        }
        try {
            adapter.onExit(player, reason);
        } catch (RuntimeException exception) {
            LOGGER.error("Black Raven disguise adapter {} failed on exit ({}) for {}",
                    acting, reason, player.getUuid(), exception);
        }
    }

    /** The only setBalance caller: the per-identity wallet swap (amendment W). / 唯一的 setBalance 调用：身份钱包交换。 */
    private static void setBalance(PlayerShopComponent shop, int balance) {
        shop.setBalance(Math.max(0, balance));
    }

    private static @Nullable Identifier defaultSkill(Identifier identity, @Nullable BlackRavenDisguiseAdapter adapter) {
        if (RAVEN.equals(identity)) {
            return BlackRavenRules.PERCEPTION_SKILL_ID;
        }
        return adapter == null ? null : adapter.witchSkillId();
    }

    private static Identifier identityId(@Nullable Identifier acting) {
        return acting == null ? RAVEN : acting;
    }

    private static boolean isEligible(ServerPlayerEntity player, GameWorldComponent game, BlackRavenDisguiseState state) {
        return game.isRunning()
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !GameFunctions.isPlayerSpectatingOrCreative(player)
                && BlackRavenRules.isBlackRaven(game.getRole(player))
                && state.isBoundTo(BlackRavenMatch.currentId());
    }

    /** Bound to the live match, raw role still Black Raven, playing and alive. / 绑定当前对局、真实职业仍为黑羽鸦且存活。 */
    private static boolean isLiveRaven(ServerPlayerEntity player, BlackRavenDisguiseState state) {
        return state.isBoundTo(BlackRavenMatch.currentId())
                && BlackRavenRules.isBlackRaven(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player);
    }

    private static boolean isTargetSelectable(BlackRavenDisguiseState state, Identifier target) {
        return state.poolFlag(target) == BlackRavenDisguiseRules.PoolFlag.SELECTABLE
                && BlackRavenDisguiseAdapters.isShipped(target)
                && BlackRavenActingRole.resolveRole(target) != null;
    }

    private static boolean isWindowActive(ServerPlayerEntity player, @Nullable Identifier acting) {
        WitchPlayerComponent witch = WitchPlayerComponent.KEY.get(player);
        if (BlackRavenPerceptionPlayerComponent.KEY.get(player).isActive()
                || witch.getActiveSkillWindowTicks() > 0
                || witch.hasDeferredCooldown()) {
            return true;
        }
        BlackRavenDisguiseAdapter adapter = BlackRavenDisguiseAdapters.get(acting);
        return adapter != null && adapter.blocksSwitchReason(player) != null;
    }

    private static void sendRefusal(ServerPlayerEntity player, BlackRavenDisguiseRules.SwitchVerdict verdict,
                                    BlackRavenDisguiseState state, long now) {
        String key = verdict.messageKey();
        if (key == null) {
            return;
        }
        Text message = switch (verdict) {
            case LOCKED -> Text.translatable(key, BlackRavenDisguiseRules.ceilSeconds(
                    BlackRavenDisguiseRules.remainingTicks(state.unlockAt(), now)));
            case COOLDOWN -> Text.translatable(key, BlackRavenDisguiseRules.ceilSeconds(
                    BlackRavenDisguiseRules.remainingTicks(state.nextSwitchAt(), now)));
            default -> Text.translatable(key);
        };
        player.sendMessage(message, true);
    }

    /** Server-built role name with a literal fallback and the role color. / 服务端构建的职业名（带字面兜底与职业颜色）。 */
    static MutableText roleName(Identifier roleId) {
        String key = RoleDisplayTextRules.roleTranslationKey(roleId.getPath());
        MutableText name = Text.translatableWithFallback(key, RoleDisplayTextRules.fallbackRoleName(key));
        Role role = BlackRavenActingRole.resolveRole(roleId);
        return role == null ? name : name.withColor(role.color());
    }

    static @Nullable Text formatReplay(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                       @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(REPLAY_ACTOR_KEY)) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid(REPLAY_ACTOR_KEY),
                ReplayGenerator.getPlayerInfoCache(match));
        Identifier to = BlackRavenDisguiseState.parseRoleId(data.getString(REPLAY_TO_KEY));
        if (to == null) {
            return null;
        }
        return RAVEN.equals(to)
                ? Text.translatable(REPLAY_REVERTED_KEY, actor)
                : Text.translatable(REPLAY_BECAME_KEY, actor, roleName(to));
    }
}
