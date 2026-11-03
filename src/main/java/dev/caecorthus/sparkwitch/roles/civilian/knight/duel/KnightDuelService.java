package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.api.SparkWitchApi;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.KnightDuelStopSoundS2CPayload;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server owner of the Knight duel: validation, the good-pick self-execution, duel state, teams/effects, the weapon
 * seal ({@link KnightDuelSeal}), the marked knife ({@link KnightDuelKnives}), titles, audio and cleanup.
 * 骑士决斗的服务端归属：校验、选到好人时的自裁、决斗状态、队伍/效果、武器封印（{@link KnightDuelSeal}）、
 * 标记小刀（{@link KnightDuelKnives}）、标题、音频与收尾。
 */
public final class KnightDuelService {
    private static boolean registered;

    private KnightDuelService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        KnightDuelSeal.registerGuards();
        // Owner-approved forced kills (2026-10-30): SparkTraits reads terminal reasons from a live set at kill time, so
        // registering on server start is early enough and idempotent; an absent or older SparkTraits registers nothing.
        // 所有者批准的强制击杀（2026-10-30）：SparkTraits 在击杀时实时读取终结原因集合，服务端启动时注册即足够早且幂等；
        // 缺失或旧版 SparkTraits 不注册任何内容。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.DUEL_SUICIDE);
            SparkTraitsKillerBridge.registerTerminalDeathReason(SparkWitchDeathReasons.DUEL_FAILED);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(KnightDuelService::resetDuelTeams);
        KillPlayer.AFTER.register(KnightDuelService::afterKill);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // A marked knife saved on someone who left mid-duel never comes back (a resumed duel keeps the target's).
            // 中途离线者存档里的标记小刀不会随其回来（恢复中的决斗保留对手的小刀）。
            ServerPlayerEntity player = handler.player;
            if (!KnightDuelWorldComponent.KEY.get(player.getServerWorld()).isTarget(player.getUuid())) {
                KnightDuelKnives.confiscate(player);
            }
        });
        // Owner keeps it: a disconnect only ends the duel, nobody dies. / 所有者保留：断线只结束决斗，无人死亡。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player.getWorld() instanceof ServerWorld world) {
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
                if (duel.isDuelist(player.getUuid())) {
                    endDuel(world, duel, player);
                }
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                resetDuelTeams(serverWorld.getServer());
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(serverWorld);
                if (duel.active()) {
                    // The new round already reset cooldowns: never write a stale duel's seal back onto it.
                    // 新回合已重置冷却：绝不把过期决斗的封印恢复写入新回合。
                    duel.sealedItems().clear();
                    endDuel(serverWorld, duel, null);
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(serverWorld);
                if (duel.active()) {
                    endDuel(serverWorld, duel, null);
                }
            }
        });
    }

    /**
     * C2S entry, on the server thread: the client only proposes; every rule is re-checked here. The cost is charged
     * only when a duel actually starts.
     * C2S 入口（服务端线程）：客户端只提议，所有规则在此重查。仅在决斗真正开始时扣费。
     */
    public static void requestDuel(ServerPlayerEntity knight, UUID targetId) {
        if (knight == null || targetId == null) {
            return;
        }
        ServerWorld world = knight.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        if (duel.active() || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !KnightRules.isKnight(game.getRole(knight)) || !canDuel(knight)) {
            return;
        }
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null || target == knight || target.getServerWorld() != world || !canDuel(target)) {
            return;
        }
        // Stun and Last Escape/silence refuse silently (their own HUD explains); Fear names the block, as the Judge.
        // 眩晕与脱险/沉默静默拒绝（其自身 HUD 会说明）；恐惧会提示被阻止，与法官一致。
        if (ControlExpertStun.isStunned(knight) || SparkTraitsKillerBridge.isRoleSkillBlocked(knight)
                || GrandWitchFearService.denyRoleSkillIfFeared(knight)) {
            return;
        }
        if (PlayerShopComponent.KEY.get(knight).getBalance() < KnightDuelRules.COST) {
            return;
        }
        // Structural vetoes (Rift occupants, Wraiths, add-on protections) refuse before alignment is read, so a refusal
        // never tells the Knight whether the target is good.
        // 结构性否决（门内玩家、冤魂、附属模组保护）在读取阵营之前拒绝，因此拒绝不会向骑士透露目标是否为好人。
        if (!SparkFactionApi.canAffectPlayer(knight, target, KnightDuelRules.DUEL_ACTION_ID, game)) {
            return;
        }
        if (KnightRules.isGoodForKnight(target, game)) {
            selfExecute(world, game, knight, target);
            return;
        }
        startDuel(world, duel, knight, target);
    }

    /** Playing, alive, not a spectator or creative, and not an active Wraith. / 在局存活、非旁观或创造、且非活跃冤魂。 */
    private static boolean canDuel(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player)
                && !SparkWitchApi.isWraithRestricted(player);
    }

    /** The Knight challenged a good player: he dies at once, uncharged. / 骑士挑战了好人：当场死亡，不扣费。 */
    private static void selfExecute(ServerWorld world, GameWorldComponent game,
                                    ServerPlayerEntity knight, ServerPlayerEntity target) {
        GameFunctions.killPlayer(knight, true, null, SparkWitchDeathReasons.DUEL_SUICIDE, true);
        if (GameFunctions.isPlayerPlayingAndAlive(knight)) {
            // Should not happen (forced + SparkTraits-terminal): no sting without a death. / 不应发生：未死亡则不播放。
            return;
        }
        playSoundToAll(world.getServer(), SparkWitchSounds.DUEL_MANBA);
        if (isSurvivalMaster(game, target)) {
            showTitleToAll(world.getServer(),
                    Text.translatable("title.sparkwitch.duel.survival").formatted(Formatting.WHITE));
        }
    }

    private static void startDuel(ServerWorld world, KnightDuelWorldComponent duel,
                                  ServerPlayerEntity knight, ServerPlayerEntity target) {
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(knight);
        shop.setBalance(shop.getBalance() - KnightDuelRules.COST);
        duel.begin(knight.getUuid(), target.getUuid(), world.getTime() + KnightDuelRules.DUEL_TICKS);
        applyTeamsAndEffects(world, duel, knight, target);
        // Wathe cooldowns are per item, so the issued knife shares the target's knife cooldown: clear it so the issued
        // or own knife can be raised at once (owner bug report). The knife is whitelisted, so the seal never touches it.
        // Wathe 冷却按物品计，发出的小刀与对手原有小刀共用冷却：清除它，使发出的或自己的小刀能立即举起（所有者反馈）。
        // 小刀在白名单内，封印不会碰它。
        target.getItemCooldownManager().remove(WatheItems.KNIFE);
        KnightDuelKnives.issue(target);
        sealBoth(world, duel);
        // No stop before the play: endDuel already stopped the last track, and a stop sent in the same tick as the
        // play can reach the client after it and cut the new track.
        // 播放前不再发送停止：endDuel 已停止上一首；与播放同一刻发出的停止可能晚于播放到达客户端并掐断新曲。
        playSoundToAll(world.getServer(), SparkWitchSounds.DUEL_SF);
        showTitleToAll(world.getServer(), Text.translatable("title.sparkwitch.duel.start", target.getNameForScoreboard())
                .formatted(Formatting.WHITE));
    }

    static void tick(ServerWorld world, KnightDuelWorldComponent duel) {
        if (!duel.active()) {
            return;
        }
        if (duel.remainingTicks() <= 0) {
            timeout(world, duel);
            return;
        }
        if (world.getTime() % KnightDuelRules.REFRESH_INTERVAL_TICKS != 0) {
            return;
        }
        // Force-end if a duelist died without a kill event, then re-raise the seal (also covers pickups), re-apply the
        // duel's effects and resync.
        // 若有一方死亡且未走击杀事件则强制结束；随后重新抬高封印（也覆盖新捡到的物品）、补回决斗效果并重新同步。
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        UUID knightId = duel.knight();
        UUID targetId = duel.target();
        if ((knightId != null && game.isPlayerDead(knightId))
                || (targetId != null && game.isPlayerDead(targetId))) {
            endDuel(world, duel, null);
            return;
        }
        sealBoth(world, duel);
        refreshEffects(world, duel);
        duel.sync();
    }

    /** 120 s without a duel kill: both duelists fail (spec). / 120 秒未分胜负：双方均决斗失败（规格）。 */
    private static void timeout(ServerWorld world, KnightDuelWorldComponent duel) {
        List<UUID> duelists = duelists(duel);
        endDuel(world, duel, null);
        for (UUID id : duelists) {
            ServerPlayerEntity player = player(world.getServer(), id);
            if (player != null && GameFunctions.isPlayerPlayingAndAlive(player)) {
                GameFunctions.killPlayer(player, true, null, SparkWitchDeathReasons.DUEL_FAILED, true);
            }
        }
    }

    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier reason) {
        if (!(victim.getWorld() instanceof ServerWorld world)) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        if (!duel.active() || !duel.isDuelist(victim.getUuid())) {
            return;
        }
        UUID otherId = duel.isKnight(victim.getUuid()) ? duel.target() : duel.knight();
        // Being pushed off the train is a normal duel end, never a third-party violation (owner, 2026-10-30).
        // 被推下列车属于正常结束，不算第三方违反规则（所有者，2026-10-30）。
        boolean violation = killer != null && !duel.isDuelist(killer.getUuid())
                && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(reason);
        endDuel(world, duel, null);
        if (violation && otherId != null) {
            ServerPlayerEntity other = player(world.getServer(), otherId);
            if (other != null && GameFunctions.isPlayerPlayingAndAlive(other)) {
                GameFunctions.killPlayer(other, true, null, SparkWitchDeathReasons.DUEL_FAILED, true);
            }
        }
    }

    /**
     * Ends the duel and undoes everything it applied. {@code leaving} is a disconnecting duelist, who may already be
     * missing from the player list but must still be cleaned up before the save.
     * 结束决斗并撤销其施加的一切。{@code leaving} 为正在断线的决斗者：其可能已不在玩家列表中，但仍须在存档前完成清理。
     */
    static void endDuel(ServerWorld world, KnightDuelWorldComponent duel, @Nullable ServerPlayerEntity leaving) {
        if (!duel.active()) {
            return;
        }
        UUID knightId = duel.knight();
        UUID targetId = duel.target();
        // Every re-raise targets the duel's end, so the time still left is the seal's own floor on each sealed item.
        // 每次重新抬高都以决斗结束为目标，因此尚余的决斗时间就是封印施加在每件被封印物品上的下限。
        int sealFloor = duel.remainingTicks();
        int elapsed = KnightDuelRules.DUEL_TICKS - sealFloor;
        long now = world.getTime();
        Map<UUID, StatusEffectInstance> previousGlowing = Map.copyOf(duel.previousGlowing());
        Map<UUID, StatusEffectInstance> previousSpeed = Map.copyOf(duel.previousSpeed());
        Map<UUID, String> previousTeams = Map.copyOf(duel.previousTeams());
        Map<UUID, Map<Item, Long>> sealed = Map.copyOf(duel.sealedItems());
        duel.clearState();
        duel.sync();
        stopSf(world);
        ServerPlayerEntity knight = resolve(world.getServer(), knightId, leaving);
        if (knight != null) {
            restoreEffect(knight, StatusEffects.SPEED, previousSpeed.get(knightId), elapsed);
        }
        cleanupPlayer(world, knight, elapsed, sealFloor, now, previousGlowing, previousTeams, sealed);
        cleanupPlayer(world, resolve(world.getServer(), targetId, leaving), elapsed, sealFloor, now,
                previousGlowing, previousTeams, sealed);
        KnightDuelKnives.confiscateEverywhere(world.getServer());
    }

    private static void cleanupPlayer(ServerWorld world, @Nullable ServerPlayerEntity player, int elapsed,
                                      int sealFloor, long now,
                                      Map<UUID, StatusEffectInstance> previousGlowing,
                                      Map<UUID, String> previousTeams,
                                      Map<UUID, Map<Item, Long>> sealed) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        restoreEffect(player, StatusEffects.GLOWING, previousGlowing.get(uuid), elapsed);
        restoreTeam(world.getScoreboard(), player, previousTeams.get(uuid));
        KnightDuelKnives.confiscate(player);
        KnightDuelSeal.restore(player, sealed.getOrDefault(uuid, Map.of()), now, sealFloor);
    }

    private static void restoreEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect,
                                      @Nullable StatusEffectInstance previous, int elapsed) {
        player.removeStatusEffect(effect);
        if (previous == null) {
            return;
        }
        // An infinite pre-duel effect comes back infinite (INFINITE is -1, never 0). / 决斗前的无限效果恢复为无限。
        int remaining = KnightDuelRules.restoredEffectTicks(previous.getDuration(), previous.isInfinite(), elapsed);
        if (remaining != 0) {
            player.addStatusEffect(new StatusEffectInstance(effect, remaining, previous.getAmplifier(),
                    previous.isAmbient(), previous.shouldShowParticles(), previous.shouldShowIcon()));
        }
    }

    private static void sealBoth(ServerWorld world, KnightDuelWorldComponent duel) {
        int ticks = duel.remainingTicks();
        long now = world.getTime();
        for (UUID id : duelists(duel)) {
            ServerPlayerEntity player = player(world.getServer(), id);
            if (player != null) {
                KnightDuelSeal.seal(player, duel.sealedItems().computeIfAbsent(id, key -> new HashMap<>()), ticks, now);
            }
        }
    }

    private static void applyTeamsAndEffects(ServerWorld world, KnightDuelWorldComponent duel,
                                             ServerPlayerEntity knight, ServerPlayerEntity target) {
        Scoreboard scoreboard = world.getScoreboard();
        Team knightTeam = team(scoreboard, KnightDuelRules.KNIGHT_TEAM, KnightDuelRules.KNIGHT_COLOR);
        Team targetTeam = team(scoreboard, KnightDuelRules.TARGET_TEAM, KnightDuelRules.TARGET_COLOR);
        joinTeam(scoreboard, duel, knight, knightTeam);
        joinTeam(scoreboard, duel, target, targetTeam);
        applyEffect(knight, StatusEffects.GLOWING, 0, duel.previousGlowing());
        applyEffect(knight, StatusEffects.SPEED, KnightDuelRules.KNIGHT_SPEED_AMPLIFIER, duel.previousSpeed());
        applyEffect(target, StatusEffects.GLOWING, 0, duel.previousGlowing());
    }

    /**
     * Puts back the duel's Glowing (both) and Speed II (Knight) when something removed them or left a weaker level;
     * a stronger or longer effect is left alone, and a weaker one waits underneath through vanilla's hidden-effect
     * upgrade. The pre-duel snapshot taken at the start is never overwritten.
     * 当决斗的发光（双方）与速度 II（骑士）被移除或被降为较低等级时补回；更强或更久的效果保持不动，较弱的效果经原版隐藏效果
     * 升级机制保留在下层。开局时记录的决斗前快照不会被覆盖。
     */
    private static void refreshEffects(ServerWorld world, KnightDuelWorldComponent duel) {
        int ticks = duel.remainingTicks();
        if (ticks <= 0) {
            return;
        }
        ServerPlayerEntity knight = player(world.getServer(), duel.knight());
        if (knight != null) {
            ensureEffect(knight, StatusEffects.GLOWING, 0, ticks);
            ensureEffect(knight, StatusEffects.SPEED, KnightDuelRules.KNIGHT_SPEED_AMPLIFIER, ticks);
        }
        ServerPlayerEntity target = player(world.getServer(), duel.target());
        if (target != null) {
            ensureEffect(target, StatusEffects.GLOWING, 0, ticks);
        }
    }

    private static void ensureEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier,
                                     int ticks) {
        StatusEffectInstance existing = player.getStatusEffect(effect);
        if (existing == null || existing.getAmplifier() < amplifier) {
            player.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier));
        }
    }

    /**
     * The teams only colour the glow. Collision NEVER turns off vanilla soft pushing for duelists; passing through
     * players is the separate {@code Entity.collidesWith} seam.
     * 队伍只用于发光颜色。碰撞规则 NEVER 关闭决斗者的原版软推挤；穿过玩家由独立的 {@code Entity.collidesWith} 接缝负责。
     */
    private static Team team(Scoreboard scoreboard, String name, Formatting color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.addTeam(name);
        }
        team.setColor(color);
        team.setFriendlyFireAllowed(true);
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        return team;
    }

    private static void joinTeam(Scoreboard scoreboard, KnightDuelWorldComponent duel,
                                 ServerPlayerEntity player, Team duelTeam) {
        String name = player.getNameForScoreboard();
        Team previous = scoreboard.getScoreHolderTeam(name);
        if (previous != null) {
            duel.previousTeams().put(player.getUuid(), previous.getName());
            scoreboard.removeScoreHolderFromTeam(name, previous);
        }
        scoreboard.addScoreHolderToTeam(name, duelTeam);
    }

    private static void restoreTeam(Scoreboard scoreboard, ServerPlayerEntity player, @Nullable String previousName) {
        String name = player.getNameForScoreboard();
        Team current = scoreboard.getScoreHolderTeam(name);
        if (current != null && (current.getName().equals(KnightDuelRules.KNIGHT_TEAM)
                || current.getName().equals(KnightDuelRules.TARGET_TEAM))) {
            scoreboard.removeScoreHolderFromTeam(name, current);
        }
        if (previousName != null) {
            Team previous = scoreboard.getTeam(previousName);
            if (previous != null) {
                scoreboard.addScoreHolderToTeam(name, previous);
            }
        }
    }

    private static void applyEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier,
                                    Map<UUID, StatusEffectInstance> previous) {
        StatusEffectInstance existing = player.getStatusEffect(effect);
        if (existing != null) {
            previous.put(player.getUuid(), new StatusEffectInstance(existing));
        }
        player.removeStatusEffect(effect);
        player.addStatusEffect(new StatusEffectInstance(effect, KnightDuelRules.DUEL_TICKS, amplifier));
    }

    private static boolean isSurvivalMaster(GameWorldComponent game, ServerPlayerEntity player) {
        Role role = game.getRole(player);
        return role != null && KnightDuelRules.SURVIVAL_MASTER_ID.equals(role.identifier());
    }

    /**
     * Drops memberships a crash or an older build left in the saved scoreboard and keeps the rule at NEVER.
     * 清除崩溃或旧版本残留在存档计分板中的成员，并保持碰撞规则为 NEVER。
     */
    private static void resetDuelTeams(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            Scoreboard scoreboard = world.getScoreboard();
            for (String name : List.of(KnightDuelRules.KNIGHT_TEAM, KnightDuelRules.TARGET_TEAM)) {
                Team team = scoreboard.getTeam(name);
                if (team == null) {
                    continue;
                }
                team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
                for (String member : List.copyOf(team.getPlayerList())) {
                    scoreboard.removeScoreHolderFromTeam(member, team);
                }
            }
        }
    }

    private static List<UUID> duelists(KnightDuelWorldComponent duel) {
        List<UUID> ids = new ArrayList<>(2);
        if (duel.knight() != null) {
            ids.add(duel.knight());
        }
        if (duel.target() != null) {
            ids.add(duel.target());
        }
        return ids;
    }

    private static @Nullable ServerPlayerEntity player(MinecraftServer server, @Nullable UUID uuid) {
        return uuid == null ? null : server.getPlayerManager().getPlayer(uuid);
    }

    private static @Nullable ServerPlayerEntity resolve(MinecraftServer server, @Nullable UUID uuid,
                                                        @Nullable ServerPlayerEntity leaving) {
        if (uuid != null && leaving != null && uuid.equals(leaving.getUuid())) {
            return leaving;
        }
        return player(server, uuid);
    }

    private static void playSoundToAll(MinecraftServer server, SoundEvent sound) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(sound, SoundCategory.MASTER, 1.0F, 1.0F);
        }
    }

    private static void stopSf(ServerWorld world) {
        KnightDuelStopSoundS2CPayload payload = new KnightDuelStopSoundS2CPayload();
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void showTitleToAll(MinecraftServer server, Text text) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(
                    KnightDuelRules.TITLE_FADE_IN, KnightDuelRules.TITLE_STAY, KnightDuelRules.TITLE_FADE_OUT));
            player.networkHandler.sendPacket(new TitleS2CPacket(text));
        }
    }
}
