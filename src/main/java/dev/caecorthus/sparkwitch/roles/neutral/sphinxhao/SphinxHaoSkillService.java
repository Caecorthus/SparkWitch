package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority of Sphinx Hao's two skills (CORE).
 * <ul>
 *   <li>嘉豪病毒 ({@code sparkwitch:use_sphinx_hao_infect}, ability key): {@link #mayCast} (silent), the cooldown, the
 *   crosshair target ({@link SphinxHaoTargeting}), not already infected, SparkFactionAPI {@code canAffectPlayer}, then
 *   the Grand Witch Fear re-check; it infects with a private notice 15–25 s later and starts the 10 s cooldown. The
 *   caster reads why on its actionbar.</li>
 *   <li>豪气冲天 ({@code sparkwitch:use_sphinx_hao_roar}, secondary key): {@link #mayCast}, the cooldown, one window per
 *   world, Fear; the crawlers are the caster plus every living infected player, the caster earns one hao per infected
 *   crawler (owner Q4), and the cooldown is set to 30 s after the scheduled end, then re-set from the real end.</li>
 *   <li>Each server world tick ({@code END_WORLD_TICK}): releases crawlers that died, left, were swallowed or entered a
 *   Rift Gate; ends the window when its 10 s run out or early (caster dead, offline or reset; round no longer ACTIVE),
 *   which clears every infection in the world; then shows due private notices.</li>
 *   <li>A real death removes the victim's infection and releases it; round start and stop clear the world state.</li>
 * </ul>
 * The forced crawl pose, the instinct veto and 《Fade》 are PRESENTATION's and read only the synced world component.
 * 狮身豪两个技能的服务端权威（CORE）。
 * <ul>
 *   <li>嘉豪病毒（{@code sparkwitch:use_sphinx_hao_infect}，技能键）：{@link #mayCast}（静默）、冷却、准星目标
 *   （{@link SphinxHaoTargeting}）、未被感染、SparkFactionAPI {@code canAffectPlayer}，最后再检查大魔女恐惧；感染后 15–25 秒
 *   发出私人提示，并开始 10 秒冷却。施法者在动作栏看到原因。</li>
 *   <li>豪气冲天（{@code sparkwitch:use_sphinx_hao_roar}，第二技能键）：{@link #mayCast}、冷却、每个世界同时只有一个窗口、恐惧；
 *   陆泳者为施法者与所有存活的感染者，每名被感染的陆泳者给施法者 1 点豪度值（所有者 Q4），冷却先设为计划结束后 30 秒，
 *   实际结束时再按实际结束重设。</li>
 *   <li>每个服务端世界刻（{@code END_WORLD_TICK}）：释放死亡、离线、被吞下或进入裂隙门的陆泳者；10 秒用尽或提前（施法者死亡、
 *   离线或被重置；回合不再 ACTIVE）时结束窗口，并清除本世界全部感染；随后显示到期的私人提示。</li>
 *   <li>真实死亡会移除被害者的感染并释放其陆泳；回合开始与结束清空世界状态。</li>
 * </ul>
 * 强制陆泳姿势、本能屏蔽与《Fade》属于 PRESENTATION，只读取已同步的世界组件。
 */
public final class SphinxHaoSkillService {
    private static final String COUNT = "count";
    private static final String HAO = "hao";
    private static boolean registered;

    private SphinxHaoSkillService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(SphinxHaoRules.INFECT_SKILL_ID, SphinxHaoSkillService::formatInfect);
        ReplayRegistry.registerSkillFormatter(SphinxHaoRules.ROAR_SKILL_ID, SphinxHaoSkillService::formatRoar);
        ServerTickEvents.END_WORLD_TICK.register(SphinxHaoSkillService::tick);
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                SphinxHaoWorldComponent.KEY.get(serverWorld).clear();
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                SphinxHaoWorldComponent.KEY.get(serverWorld).clear();
            }
        });
    }

    /**
     * Server caster gate of both skills (Specter / Dominator {@code mayCast} pattern): an ACTIVE round, the raw Sphinx
     * Hao role, alive and playing, not spectating/creative, not swallowed, not held (Ninja hook, Kidnapper, stun, Last
     * Escape/Stand, Hunter root, Seeker session), not silenced, and not role-skill-blocked by SparkTraits.
     * 两个技能共用的服务端施放门槛（幽魂 / 支配者 {@code mayCast} 模式）：回合处于 ACTIVE、真实身份为狮身豪、存活且参与对局、
     * 非旁观/创造、未被吞下、未被控制（忍者钩爪、绑匪、眩晕、脱险/背水、猎人定身、搜寻者会话）、未被沉默，且未被 SparkTraits
     * 封锁职业技能。
     */
    public static boolean mayCast(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        return game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && SphinxHaoRules.isSphinxHao(game.getRole(caster))
                && GameFunctions.isPlayerPlayingAndAlive(caster)
                && !caster.isSpectator()
                && !GameFunctions.isPlayerSpectatingOrCreative(caster)
                && !NoellesTaotieSeekerBridge.isSwallowed(caster)
                && !NinjaGrappleService.isHeld(caster)
                && !NoellesSilenceBridge.isSilenced(caster)
                && !SparkTraitsKillerBridge.isRoleSkillBlocked(caster);
    }

    // ---- 嘉豪病毒 / Hao Virus -----------------------------------------------------------------------------------

    /** Server entry of the ability key. A refused press changes nothing. / 技能键的服务端入口；被拒绝的按键不改变任何东西。 */
    public static void infect(@Nullable ServerPlayerEntity caster) {
        if (caster == null || !mayCast(caster)) {
            return;
        }
        ServerWorld world = caster.getServerWorld();
        long now = world.getTime();
        SphinxHaoPlayerComponent state = SphinxHaoPlayerComponent.KEY.get(caster);
        SphinxHaoWorldComponent infections = SphinxHaoWorldComponent.KEY.get(world);
        int cooling = state.infectCooldownRemaining(now);
        ServerPlayerEntity target = cooling > 0 ? null
                : SphinxHaoTargeting.findInfectTarget(caster) instanceof ServerPlayerEntity found ? found : null;
        boolean already = target != null && infections.isInfected(target.getUuid());
        boolean allows = target != null && !already && SparkFactionApi.canAffectPlayer(caster, target,
                SphinxHaoRules.INFECT_SKILL_ID, GameWorldComponent.KEY.get(world));
        switch (SphinxHaoSkillRules.infectOutcome(cooling, target != null, already, allows)) {
            case COOLING -> feedback(caster, Text.translatable(SphinxHaoSkillRules.INFECT_COOLDOWN_KEY,
                    SphinxHaoSkillRules.secondsCeil(cooling)), Formatting.GRAY);
            case NO_TARGET -> feedback(caster, Text.translatable(SphinxHaoSkillRules.INFECT_NO_TARGET_KEY),
                    Formatting.GRAY);
            case ALREADY_INFECTED -> feedback(caster, Text.translatable(SphinxHaoSkillRules.INFECT_ALREADY_KEY),
                    Formatting.GRAY);
            // SparkFactionAPI's structural veto (Wraith, Vendetta, Rift occupant, Knight duel): silent, no cooldown.
            // SparkFactionAPI 结构性否决（冤魂、复仇者、裂隙门内、骑士决斗）：静默，不进冷却。
            case VETOED -> {
            }
            case INFECT -> {
                if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
                    return;
                }
                if (!infections.infect(target.getUuid(), caster.getUuid(),
                        now + SphinxHaoRules.noticeDelayTicks(world.getRandom()))) {
                    return;
                }
                state.setInfectReadyTick(now + SphinxHaoRules.INFECT_COOLDOWN_TICKS);
                caster.sendMessage(Text.translatable(SphinxHaoSkillRules.INFECT_SUCCESS_KEY)
                        .withColor(SphinxHaoRules.COLOR), true);
                GameRecordManager.recordSkillUse(caster, SphinxHaoRules.INFECT_SKILL_ID, target, null);
            }
        }
    }

    // ---- 豪气冲天 / Hao to the Sky ---------------------------------------------------------------------------------

    /** Server entry of the secondary key. A refused press changes nothing. / 第二技能键的服务端入口；被拒绝的按键不改变任何东西。 */
    public static void roar(@Nullable ServerPlayerEntity caster) {
        if (caster == null || !mayCast(caster)) {
            return;
        }
        ServerWorld world = caster.getServerWorld();
        long now = world.getTime();
        SphinxHaoPlayerComponent state = SphinxHaoPlayerComponent.KEY.get(caster);
        SphinxHaoWorldComponent window = SphinxHaoWorldComponent.KEY.get(world);
        int cooling = state.roarCooldownRemaining(now);
        switch (SphinxHaoSkillRules.roarOutcome(cooling, window.roarCaster() != null)) {
            case COOLING -> feedback(caster, Text.translatable(SphinxHaoSkillRules.ROAR_COOLDOWN_KEY,
                    SphinxHaoSkillRules.secondsCeil(cooling)), Formatting.GRAY);
            case BUSY -> feedback(caster, Text.translatable(SphinxHaoSkillRules.ROAR_BUSY_KEY), Formatting.GRAY);
            case CAST -> {
                if (GrandWitchFearService.denyRoleSkillIfFeared(caster)) {
                    return;
                }
                List<ServerPlayerEntity> infected = livingInfected(world, window, caster);
                List<UUID> crawlerIds = new ArrayList<>(infected.size());
                for (ServerPlayerEntity victim : infected) {
                    crawlerIds.add(victim.getUuid());
                }
                long endTick = now + SphinxHaoRules.ROAR_TICKS;
                window.startRoar(caster.getUuid(), crawlerIds, endTick);
                state.setRoarReadyTick(SphinxHaoSkillRules.roarReadyTickAfter(endTick));
                int hao = SphinxHaoRules.roarHao(infected.size());
                state.addHao(hao);
                // A victim whose notice is still pending reads it now (owner spec). / 提示尚未出现的被感染者此刻看到提示。
                for (ServerPlayerEntity victim : infected) {
                    SphinxHaoWorldComponent.Infection infection = window.infections().get(victim.getUuid());
                    if (infection != null && !infection.noticed()) {
                        showNotice(victim);
                        window.markNoticed(victim.getUuid());
                    }
                }
                caster.sendMessage(Text.translatable(SphinxHaoSkillRules.ROAR_START_KEY, infected.size(), hao)
                        .withColor(SphinxHaoRules.COLOR), true);
                NbtCompound extra = new NbtCompound();
                extra.putInt(COUNT, infected.size());
                extra.putInt(HAO, hao);
                GameRecordManager.recordSkillUse(caster, SphinxHaoRules.ROAR_SKILL_ID, null, extra);
            }
        }
    }

    /**
     * Every infected player of this world that is a living, visible participant: online here, playing and alive, not
     * spectating/creative (so not in a Rift Gate), not swallowed; never the caster, which the payout never counts.
     * 本世界中所有存活且可见的被感染参与者：在线于本世界、参与且存活、非旁观/创造（因此不在裂隙门内）、未被吞下；从不包括施法者
     * （豪度值结算从不计入施法者）。
     */
    private static List<ServerPlayerEntity> livingInfected(ServerWorld world, SphinxHaoWorldComponent state,
                                                           ServerPlayerEntity caster) {
        List<ServerPlayerEntity> living = new ArrayList<>();
        for (UUID id : state.infectedIds()) {
            if (id.equals(caster.getUuid())) {
                continue;
            }
            PlayerEntity player = world.getPlayerByUuid(id);
            if (player instanceof ServerPlayerEntity victim && !releases(victim)) {
                living.add(victim);
            }
        }
        return living;
    }

    private static boolean releases(@Nullable PlayerEntity player) {
        return SphinxHaoSkillRules.releasesCrawler(
                player != null,
                GameFunctions.isPlayerPlayingAndAlive(player),
                player != null && GameFunctions.isPlayerSpectatingOrCreative(player),
                player != null && NoellesTaotieSeekerBridge.isSwallowed(player));
    }

    // ---- world tick, deaths, resets / 世界刻、死亡、重置 ------------------------------------------------------------

    private static void tick(ServerWorld world) {
        SphinxHaoWorldComponent state = SphinxHaoWorldComponent.KEY.get(world);
        if (state.roarCaster() == null && state.infections().isEmpty()) {
            return;
        }
        long now = world.getTime();
        boolean active = GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        UUID casterId = state.roarCaster();
        if (casterId != null) {
            ServerPlayerEntity caster = world.getPlayerByUuid(casterId) instanceof ServerPlayerEntity player
                    ? player : null;
            SphinxHaoSkillRules.RoarEnd end = SphinxHaoSkillRules.roarEnd(now, state.roarEndTick(), active,
                    caster != null, GameFunctions.isPlayerPlayingAndAlive(caster),
                    caster != null && SphinxHaoParticipation.isSphinxHao(caster));
            if (end != null) {
                finishRoar(state, caster, end == SphinxHaoSkillRules.RoarEnd.ELAPSED ? state.roarEndTick() : now);
            } else {
                for (UUID crawler : List.copyOf(state.crawlers())) {
                    if (releases(world.getPlayerByUuid(crawler))) {
                        state.releaseCrawler(crawler);
                    }
                }
            }
        }
        if (active) {
            showDueNotices(world, state, now);
        }
    }

    /**
     * Closes the window: releases every pose, clears every infection in the world and starts 豪气冲天's 30 s from
     * {@code actualEnd} for a caster that is still here and still a Sphinx Hao (an offline caster keeps the longer
     * ready tick set at the cast).
     * 关闭窗口：释放所有姿势、清除本世界全部感染，并为仍在场且仍是狮身豪的施法者从 {@code actualEnd} 起开始豪气冲天的 30 秒
     * 冷却（离线的施法者保留施放时设定的、更晚的就绪刻）。
     */
    private static void finishRoar(SphinxHaoWorldComponent state, @Nullable ServerPlayerEntity caster,
                                   long actualEnd) {
        state.endRoar();
        state.clearInfections();
        if (caster != null && SphinxHaoParticipation.isSphinxHao(caster)) {
            SphinxHaoPlayerComponent.KEY.get(caster).setRoarReadyTick(SphinxHaoSkillRules.roarReadyTickAfter(actualEnd));
        }
    }

    /**
     * Shows each due notice once to a living victim and marks it shown; an online victim that is no longer alive is
     * marked without a line, an offline one stays pending until it is back (still infected).
     * 对存活的被感染者各显示一次到期提示并标记为已显示；在线但已不存活者只标记、不显示；离线者保持待显示，直到其返回（且仍被感染）。
     */
    private static void showDueNotices(ServerWorld world, SphinxHaoWorldComponent state, long now) {
        for (Map.Entry<UUID, SphinxHaoWorldComponent.Infection> entry : List.copyOf(state.infections().entrySet())) {
            SphinxHaoWorldComponent.Infection infection = entry.getValue();
            if (!SphinxHaoSkillRules.noticeDue(now, infection.noticeTick(), infection.noticed())) {
                continue;
            }
            if (!(world.getPlayerByUuid(entry.getKey()) instanceof ServerPlayerEntity victim)) {
                continue;
            }
            if (GameFunctions.isPlayerPlayingAndAlive(victim)) {
                showNotice(victim);
            }
            state.markNoticed(entry.getKey());
        }
    }

    /** The victim's private actionbar line 「你感觉自己有点豪」. / 被感染者的私人动作栏文字「你感觉自己有点豪」。 */
    private static void showNotice(ServerPlayerEntity victim) {
        victim.sendMessage(Text.translatable(SphinxHaoSkillRules.NOTICE_KEY).withColor(SphinxHaoRules.COLOR), true);
    }

    /**
     * {@code KillPlayer.AFTER}: a real death (the victim is on Wathe's dead list) removes its infection and releases its
     * crawl; the caster's death ends the window early.
     * {@code KillPlayer.AFTER}：真实死亡（被害者在 Wathe 死亡名单中）移除其感染并释放其陆泳；施法者死亡则提前结束窗口。
     */
    static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        ServerWorld world = victim.getServerWorld();
        if (!GameWorldComponent.KEY.get(world).isPlayerDead(victim.getUuid())) {
            return;
        }
        forget(world, victim);
        // The dead now spectate and may read the infected set (SphinxHaoWorldComponent.mayKnowInfections).
        // 死者转为旁观，可以读取感染集合（SphinxHaoWorldComponent.mayKnowInfections）。
        SphinxHaoWorldComponent state = SphinxHaoWorldComponent.KEY.get(world);
        if (!state.infections().isEmpty()) {
            state.sync();
        }
    }

    /**
     * Wathe's {@code ResetPlayer} (round end, a reset mid-round): the player leaves the infection map and any window;
     * a reset caster ends its window early. The player component itself is cleared by the hub.
     * Wathe 的 {@code ResetPlayer}（回合结束、局中重置）：该玩家离开感染表与窗口；被重置的施法者提前结束其窗口。玩家组件本身由
     * 注册中心清空。
     */
    static void onReset(ServerPlayerEntity player) {
        forget(player.getServerWorld(), player);
    }

    private static void forget(ServerWorld world, ServerPlayerEntity player) {
        SphinxHaoWorldComponent state = SphinxHaoWorldComponent.KEY.get(world);
        if (player.getUuid().equals(state.roarCaster())) {
            finishRoar(state, player, world.getTime());
        } else {
            state.releaseCrawler(player.getUuid());
        }
        state.removeInfection(player.getUuid());
    }

    private static void feedback(ServerPlayerEntity caster, Text message, Formatting color) {
        caster.sendMessage(message.copy().formatted(color), true);
    }

    // ---- replay / 回放 ---------------------------------------------------------------------------------------------

    private static @Nullable Text formatInfect(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                               ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable("replay.skill.sparkwitch.sphinx_hao.infect",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), players),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), players));
    }

    private static @Nullable Text formatRoar(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                             ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        // Wathe copies the extra compound into the event data itself. / Wathe 把附加数据直接复制进事件数据。
        return Text.translatable("replay.skill.sparkwitch.sphinx_hao.roar",
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), ReplayGenerator.getPlayerInfoCache(match)),
                data.getInt(COUNT));
    }
}
