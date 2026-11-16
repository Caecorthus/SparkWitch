package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.api.WitchSkillUseContext;
import dev.caecorthus.sparkwitch.api.WitchSkillUseResult;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchTargeting;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Shared factor authority, invoked before role-clearing death callbacks.
 * 共通因子的服务端权威；死亡结算早于身份清理。 */
public final class WitchFactorService {
    public static final Identifier SKILL_ID = SparkWitch.id("witch_factor");
    public static final Identifier EMMA_ROLE_ID = SparkWitch.id("emma");
    public static final Identifier VOODOO_ROLE_ID = Identifier.of("noellesroles", "voodoo");
    public static final int COOLDOWN_TICKS = 400;
    public static final int MANA_COST = 50;

    private WitchFactorService() { }

    public static void beginRound(ServerWorld world, int participants) {
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        component.state().begin(participants, component.settings().limit(participants));
        component.sync();
    }

    public static void clearRound(ServerWorld world) {
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        component.state().clear();
        component.sync();
    }

    public static boolean isEmma(Role role) {
        return role != null && EMMA_ROLE_ID.equals(role.identifier());
    }

    /** Every accomplice (plain or special) is never a carrier. / 任一共犯（普通或特殊）都不能携带因子。 */
    public static boolean isEligibleCarrier(PlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && role != SparkWitchRoles.grandWitch() && !WitchFactionRules.isAccompliceLike(role)
                && role != SparkWitchRoles.witchMaiden() && !isEmma(role)
                && !VOODOO_ROLE_ID.equals(role.identifier());
    }

    /**
     * A removed factor syncs at once; a role-only view change (viewer rights, pink) reaches clients through the world
     * component's next per-recipient tick diff, at most one tick later.
     * 被移除的因子立即同步；仅由身份引起的视图变化（观看权限、粉色）由世界组件下一刻的逐接收者差异同步，最多晚一刻。
     */
    public static void onRoleChanged(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            if (!isEligibleCarrier(player) && component.state().recover(player.getUuid())) component.sync();
        }
    }

    /**
     * Apprentice Purify (owner 2026-10-06 D3): removes the holder's factor in every world, dormant or mature. Like any
     * removal it never refunds the shared quota. Returns whether a factor was removed.
     * 预备魔女的净化（所有者 2026-10-06 D3）：清除持有者在所有世界的因子（潜伏或成熟）。与其他回收一样不返还共用额度。
     * 返回是否清除了因子。
     */
    public static boolean purify(ServerPlayerEntity holder) {
        boolean removed = false;
        for (ServerWorld world : holder.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            if (component.state().recover(holder.getUuid())) {
                removed = true;
                component.sync();
            }
        }
        return removed;
    }

    public static WitchSkillUseResult use(WitchSkillUseContext context) {
        ServerPlayerEntity source = context.player();
        if (context.role() != SparkWitchRoles.grandWitch()
                || WitchPlayerComponent.KEY.get(source).getMana() < MANA_COST) {
            return WitchSkillUseResult.fail(null);
        }
        ServerPlayerEntity target = GrandWitchTargeting.findTarget(source,
                context.target() == null ? null : context.target().getUuid());
        // No target and an open refusal stay free; a hidden refusal costs and reads exactly like a spread (owner 2026-11).
        // 没有目标与公开的拒绝仍然免费；隐藏的拒绝与一次传播的消耗和提示完全相同（所有者 2026-11）。
        if (target == null || attemptSpread(source, target) == SpreadOutcome.REFUSED) return WitchSkillUseResult.fail(null);
        WitchPlayerComponent mana = WitchPlayerComponent.KEY.get(source);
        mana.setMana(mana.getMana() - MANA_COST);
        return WitchSkillUseResult.success(COOLDOWN_TICKS, "message.sparkwitch.skill.witch_factor.spread");
    }

    /**
     * How a spread on an aimed target ended, as far as the caster may be allowed to tell (owner 2026-11). A refusal
     * the caster cannot see the reason for must not be free, or a ready caster could probe every player in reach at
     * no cost: {@link #HIDDEN_REFUSAL} inserts nothing and spends no quota, but the caller charges mana and cooldown
     * and answers exactly as for {@link #SPREAD}.
     * 对已瞄准目标的一次传播的结局，以施放者被允许得知的程度为准（所有者 2026-11）。施放者看不到原因的拒绝不得免费，否则
     * 就绪的施放者可以零成本试探射程内的每名玩家：{@link #HIDDEN_REFUSAL} 不插入因子、不消耗额度，但调用方照常扣除魔力与
     * 冷却，并给出与 {@link #SPREAD} 完全相同的回应。
     */
    public enum SpreadOutcome {
        /** A factor was inserted and the quota spent. / 已插入因子并消耗额度。 */
        SPREAD,
        /** Nothing changed, for a reason only hidden state explains. / 未发生变化，其原因只有隐藏状态才能解释。 */
        HIDDEN_REFUSAL,
        /** Nothing changed, for a reason the caster can see or that hits every target alike. / 未发生变化，其原因施放者可见，或对所有目标一视同仁。 */
        REFUSED
    }

    /**
     * Pure classification. Open refusals come first, so a hidden one is only ever reported where an ordinary target
     * would have succeeded; otherwise the two would differ again (free against charged) and tell the caster the same
     * thing. Open: the cast itself is not possible (round, caster, self, world, life), SparkFactionAPI vetoes the
     * pair, the shared quota is closed, or the target visibly holds a factor (mature and in the caster's own network
     * view). Hidden: the target's role makes it no carrier, or it holds a factor the caster cannot see (dormant).
     * 纯分类。公开的拒绝优先判定，因此只有在普通目标本会成功的情况下才会报告隐藏的拒绝；否则两者又会出现差别（免费与扣费），
     * 等于把同样的信息告诉施放者。公开：施放本身不成立（对局、施放者、自身、世界、存活）、SparkFactionAPI 否决这一对、
     * 共用额度已关闭，或目标可见地持有因子（已成熟且在施放者自己的网络视图中）。隐藏：目标的职业使其不能携带因子，或它持有
     * 施放者看不到的因子（潜伏）。
     */
    static SpreadOutcome classifySpread(boolean castPossible, boolean vetoed, boolean quotaOpen, boolean visibleHolder,
                                        boolean eligibleCarrier, boolean holder) {
        if (!castPossible || vetoed || !quotaOpen || visibleHolder) {
            return SpreadOutcome.REFUSED;
        }
        return !eligibleCarrier || holder ? SpreadOutcome.HIDDEN_REFUSAL : SpreadOutcome.SPREAD;
    }

    /**
     * Tries the spread and says how it ended. The caller owns costs and targeting; successful insertion alone spends
     * the common quota.
     * 尝试传播并说明结局。调用方负责消耗与选人；只有成功插入因子才扣除共用额度。
     */
    public static SpreadOutcome attemptSpread(ServerPlayerEntity source, ServerPlayerEntity target) {
        ServerWorld world = source.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Role role = game.getRole(source);
        boolean castPossible = game.isRunning() && (role == SparkWitchRoles.grandWitch() || isEmma(role))
                && target != source && target.getServerWorld() == world && alive(source) && alive(target);
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        WitchFactorState state = component.state();
        SpreadOutcome outcome = classifySpread(
                castPossible,
                castPossible && !dev.caecorthus.sparkfactionapi.api.SparkFactionApi.canAffectPlayer(source, target, SKILL_ID, game),
                state.active() && state.remaining() != 0,
                castPossible && isVisibleTo(source, target),
                isEligibleCarrier(target),
                isFactorHolder(target));
        if (outcome != SpreadOutcome.SPREAD) {
            return outcome;
        }
        if (!state.spread(source.getUuid(), target.getUuid())) {
            return SpreadOutcome.REFUSED;
        }
        // Dormant identities never leave the server, even to their own recipient.
        // 潜伏因子的身份不离开服务端，包括其持有者本人。
        component.sync();
        return SpreadOutcome.SPREAD;
    }

    /**
     * Quota-free entry for a factor the holder makes herself (二阶堂希罗's 魔女化): no Grand Witch or Emma, no quota (a
     * round without any witch, or with the quota used up, still grants it) and no 20 s admission. It needs a running
     * round and a living eligible carrier without a factor; a holder who already has one keeps it untouched and this
     * returns false. The ledger is opened at every {@code ON_FINISH_INITIALIZE} whatever the roles are; if a running
     * round is nevertheless found without one, it is opened here exactly as the round start would have. The new factor
     * is an ordinary one (see {@code WitchFactorState.grantSelfMade} for its owner) in everything but Emma's Speed
     * threshold, toward which it never counts, on her or on an heir (owner 2026-11-11).
     * 持有者自成因子（二阶堂希罗的魔女化）的免额度入口：不需要大魔女或艾玛、不占额度（没有任何魔女或额度已用尽的对局同样授予）、
     * 也没有 20 秒潜伏期。要求对局进行中、目标存活且可携带、且尚无因子；已持有因子者保持原样并返回 false。账本在每次
     * {@code ON_FINISH_INITIALIZE} 时开启，与职业无关；若进行中的对局仍没有账本，则在此按开局时的方式开启。新因子是普通因子
     * （来源见 {@code WitchFactorState.grantSelfMade}），唯一的例外是它无论在她还是继承者身上都永不计入艾玛速度的门槛
     * （所有者 2026-11-11）。
     */
    public static boolean grantSelfMade(ServerPlayerEntity holder) {
        ServerWorld world = holder.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning() || !alive(holder) || !isEligibleCarrier(holder) || isFactorHolder(holder)) return false;
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
        if (!component.state().active()) beginRound(world, game.getAllPlayers().size());
        if (!component.state().grantSelfMade(holder.getUuid())) return false;
        component.sync();
        return true;
    }

    /**
     * Shows {@code player} in their own role colour, instead of the factor violet, to everyone who sees the network
     * view, while the player lives; it does not depend on holding a factor (a purified self-made witch stays shown).
     * Idempotent and cheap, so its owner may re-assert it every tick; only authorized viewers ever receive the id (the
     * world component's per-recipient view), within one tick.
     * 让所有拥有因子网络视图的人在 {@code player} 存活期间以其职业色（而非因子紫色）看到该玩家；与是否持有因子无关（被净化的
     * 自成魔女仍然显示）。幂等且开销很小，写入方可以每刻重复调用；该 id 只会发给获准的观看者（世界组件的逐接收者视图），最多晚一刻。
     */
    public static void exposeRoleColor(ServerPlayerEntity player) {
        WitchFactorWorldComponent.KEY.get(player.getServerWorld()).state().exposeRoleColor(player.getUuid());
    }

    /** Ends {@link #exposeRoleColor} in every world. / 在所有世界结束 {@link #exposeRoleColor}。 */
    public static void concealRoleColor(ServerPlayerEntity player) {
        for (ServerWorld world : player.getServer().getWorlds()) {
            WitchFactorWorldComponent.KEY.get(world).state().concealRoleColor(player.getUuid());
        }
    }

    public static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier reason) {
        if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) return;
        boolean killerMarked = killer != null && isFactorHolder(killer);
        for (ServerWorld world : victim.getServer().getWorlds()) {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(world);
            boolean valid = killer != null && !killerMarked && killer.getServerWorld() == world
                    && alive(killer) && isEligibleCarrier(killer) && GameWorldComponent.KEY.get(world).isRunning();
            if (component.state().afterDeath(victim.getUuid(), killer == null ? null : killer.getUuid(), valid)) {
                component.sync();
            }
        }
    }

    /** Client queries read only this recipient's mature, sanitized view. / 客户端只读取当前接收者获准的成熟因子。 */
    public static boolean isFactorHolder(PlayerEntity player) {
        if (player.getWorld().isClient) {
            return WitchFactorWorldComponent.KEY.get(player.getWorld()).isFactorHolder(player.getUuid());
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            for (ServerWorld world : serverPlayer.getServer().getWorlds()) {
                if (WitchFactorWorldComponent.KEY.get(world).isFactorHolder(player.getUuid())) return true;
            }
        }
        return false;
    }

    /** The server decides the network view (every accomplice sees it); the client reads the synced flag.
     * 因子网络视图由服务端判定（所有共犯都可见），客户端只读取同步结果。 */
    public static boolean isNetworkViewer(PlayerEntity viewer) {
        if (!alive(viewer) || !GameWorldComponent.KEY.get(viewer.getWorld()).isRunning()) return false;
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(viewer.getWorld());
        if (viewer.getWorld().isClient) return component.hasNetworkView();
        Role role = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        return component.state().active() && (role == SparkWitchRoles.grandWitch()
                || WitchFactionRules.isAccompliceLike(role) || isEmma(role) || component.state().mature(viewer.getUuid()));
    }

    public static boolean isVisibleTo(PlayerEntity viewer, PlayerEntity target) {
        if (viewer.getWorld() != target.getWorld() || !alive(target) || !isNetworkViewer(viewer)) return false;
        WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(viewer.getWorld());
        return viewer.getWorld().isClient ? component.isFactorHolder(target.getUuid())
                : component.state().mature(target.getUuid());
    }

    public static int getRemaining(ServerWorld world) { return WitchFactorWorldComponent.KEY.get(world).state().remaining(); }
    public static boolean hasReachedSpeedThreshold(ServerWorld world) {
        return WitchFactorWorldComponent.KEY.get(world).state().speedUnlocked();
    }

    static void tick(ServerWorld world, WitchFactorWorldComponent component) {
        WitchFactorState state = component.state();
        if (!state.active()) return;
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        if (!game.isRunning()) { clearRound(world); return; }
        Iterator<Map.Entry<UUID, WitchFactorState.Factor>> iterator = state.factors().entrySet().iterator();
        int matureCount = 0;
        while (iterator.hasNext()) {
            Map.Entry<UUID, WitchFactorState.Factor> entry = iterator.next();
            ServerPlayerEntity holder = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (holder != null && WitchFactorTraitsBridge.isDeathIntercepted(holder)) continue;
            if (game.isPlayerDead(entry.getKey()) || !game.hasAnyRole(entry.getKey())
                    || (holder != null && !isEligibleCarrier(holder))) {
                iterator.remove();
                continue;
            }
            // Offline/off-world holders pause; source presence is irrelevant.
            // 持有者离线或离开原世界时暂停，与来源是否存活无关。
            if (holder == null || holder.getServerWorld() != world || !alive(holder)) continue;
            WitchFactorState.Factor previous = entry.getValue();
            WitchFactorState.Factor factor = previous.advance();
            entry.setValue(factor);
            if (!previous.mature() && factor.mature()) {
                holder.sendMessage(Text.translatable("message.sparkwitch.factor.awakened"), false);
            }
            // Emma's Speed threshold counts spread factors only; mana, mood and the view below treat every factor alike.
            // 艾玛速度的门槛只统计传播所得的因子；下面的魔力、情绪与视图对所有因子一视同仁。
            if (factor.countsTowardSpeed()) matureCount++;
            Role role = game.getRole(holder);
            boolean apprentice = role == SparkWitchRoles.apprenticeWitch();
            if (factor.manaTicks() == 0) {
                int reward = WitchFactorState.manaReward(apprentice, role == SparkWitchRoles.murderousWitch());
                for (ServerPlayerEntity recipient : world.getPlayers()) {
                    if (alive(recipient) && game.getRole(recipient) == SparkWitchRoles.grandWitch()) {
                        WitchPlayerComponent.KEY.get(recipient).addMana(reward);
                    }
                }
            }
            if (factor.moodTicks() == 0 && role != null && role.getMoodType() == Role.MoodType.REAL) {
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(holder);
                mood.setMood(mood.getMood() - WitchFactorState.moodLoss(apprentice));
            }
        }
        state.observeMatureCarriers(matureCount);
        // Role-colour entries end with their player's life or role, like factors (a Last Stand death is not one).
        // 职业色条目随玩家死亡或失去身份而结束，与因子相同（背水一战拦截的死亡不算）。
        if (!state.roleColored().isEmpty()) {
            state.roleColored().removeIf(id -> {
                ServerPlayerEntity shown = world.getServer().getPlayerManager().getPlayer(id);
                if (shown != null && WitchFactorTraitsBridge.isDeathIntercepted(shown)) return false;
                return game.isPlayerDead(id) || !game.hasAnyRole(id);
            });
        }
    }

    static boolean alive(PlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && !GameFunctions.isPlayerSpectatingOrCreative(player);
    }
}
