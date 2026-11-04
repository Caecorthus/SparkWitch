package dev.caecorthus.sparkwitch.roles.killer.natsume;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.compat.NoellesSilenceBridge;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.OpenNatsumeMenuS2CPacket;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerExitReason;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEndReason;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowSessions;
import dev.caecorthus.sparkwitch.roles.killer.ninja.NinjaGrappleService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 夏目安安 server authority: the target menu ({@code sparkwitch:request_natsume_menu} →
 * {@code sparkwitch:open_natsume_menu}), the cast ({@code sparkwitch:confirm_natsume_target}), the per-tick hypnosis
 * (blindness, real-sanity drain with a breakdown credited to her, a roar only the victim hears) and her shop. All
 * per-player state lives in {@link NatsumeComponent}; every gate is a pure rule in {@link NatsumeRules}. Both C2S
 * payloads are on the Control Expert stun, Fear, Seeker session, Rift occupant and Knight duel deny lists; this
 * re-checks every gate against server state anyway.
 * 夏目安安的服务端权威：目标菜单（{@code sparkwitch:request_natsume_menu} → {@code sparkwitch:open_natsume_menu}）、施放
 * （{@code sparkwitch:confirm_natsume_target}）、逐刻催眠（失明、扣除真实理智且崩溃死亡记在她名下、只有受害者听得到的咆哮）以及
 * 她的商店。所有每名玩家的状态都在 {@link NatsumeComponent} 中；每个门槛都是 {@link NatsumeRules} 中的纯规则。两个 C2S 数据包
 * 都在控场专家眩晕、恐惧、搜寻者会话、裂隙门内与骑士决斗的拦截名单上；这里仍按服务端状态重新检查每个门槛。
 */
public final class NatsumeService {
    private static boolean registered;

    private NatsumeService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register(NatsumeService::onRoleAssigned);
        // Every round end and every player reset: no hypnosis or cooldown carries over.
        // 每次回合结束与玩家重置：催眠与冷却都不会延续。
        ResetPlayer.EVENT.register(NatsumeService::clearPlayer);
        // A disconnect ends the victim's hypnosis before Wathe saves the player (NBT never stores it anyway).
        // 断线在 Wathe 保存玩家之前结束受害者的催眠（NBT 本来也不保存它）。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> endHypnosis(handler.getPlayer(), false));
        ServerTickEvents.END_SERVER_TICK.register(NatsumeService::tick);
        // Her shop, on both sides (Wathe builds it on both and resolves a purchase by index).
        // 她的商店，两端执行（Wathe 在两端构建商店并按下标解析购买）。
        BuildShopEntries.EVENT.register((player, context) -> {
            if (isNatsume(player)) {
                applyShop(context.getEntries());
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            for (PlayerEntity player : world.getPlayers()) {
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    clearPlayer(serverPlayer);
                }
            }
        });
    }

    public static boolean isNatsume(PlayerEntity player) {
        return NatsumeRules.isNatsume(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /**
     * Role assignment ends any hypnosis on this player and clears the cooldown; a drawn 夏目安安 starts her 60 s
     * opening cooldown. / 身份分配结束该玩家身上的催眠并清空冷却；抽到夏目安安时开始 60 秒开局冷却。
     */
    private static void onRoleAssigned(PlayerEntity player, @Nullable Role role) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        clearPlayer(serverPlayer);
        if (NatsumeRules.isNatsume(role)) {
            NatsumeComponent.KEY.get(serverPlayer).setCooldownReadyTick(
                    serverPlayer.getWorld().getTime() + NatsumeRules.INITIAL_COOLDOWN_TICKS);
        }
    }

    private static void clearPlayer(ServerPlayerEntity player) {
        endHypnosis(player, false);
        NatsumeComponent.KEY.get(player).clear();
    }

    /**
     * Sends the target menu: herself first, then every player in her world who passes {@link #isTargetable}.
     * 发送目标菜单：自己在最前，随后是她所在世界中所有通过 {@link #isTargetable} 的玩家。
     */
    public static void requestMenu(ServerPlayerEntity caster) {
        if (caster == null || !mayUse(caster)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        ids.add(caster.getUuid());
        names.add(caster.getGameProfile().getName());
        for (ServerPlayerEntity candidate : caster.getServerWorld().getPlayers()) {
            if (candidate == caster || !isTargetable(caster, candidate, game)) {
                continue;
            }
            ids.add(candidate.getUuid());
            names.add(candidate.getGameProfile().getName());
        }
        ServerPlayNetworking.send(caster, new OpenNatsumeMenuS2CPacket(ids, names));
    }

    /** Casts hypnosis on the confirmed pick. / 对确认的目标施放催眠。 */
    public static void cast(ServerPlayerEntity caster, UUID targetId) {
        if (caster == null || targetId == null || !mayUse(caster)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        ServerPlayerEntity target = caster.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null || !isTargetable(caster, target, game)) {
            return;
        }
        boolean self = caster.getUuid().equals(target.getUuid());
        if (!self) {
            if (caster.squaredDistanceTo(target) > NatsumeRules.RANGE * NatsumeRules.RANGE) {
                caster.sendMessage(Text.translatable("message.sparkwitch.natsume.too_far",
                        target.getGameProfile().getName()).formatted(Formatting.RED), true);
                return;
            }
            // Block line of sight from eye to eye: no hypnosis through walls. 眼到眼的方块视线：不能隔墙催眠。
            if (!caster.canSee(target)) {
                caster.sendMessage(Text.translatable("message.sparkwitch.natsume.no_line_of_sight")
                        .formatted(Formatting.RED), true);
                return;
            }
        }
        long now = caster.getWorld().getTime();
        NatsumeComponent.KEY.get(caster).setCooldownReadyTick(now + NatsumeRules.COOLDOWN_TICKS);
        int ticks = NatsumeRules.durationTicksFor(target, game);
        long targetNow = target.getWorld().getTime();
        NatsumeComponent.KEY.get(target).startHypnosis(caster.getUuid(), target.getWorld().getRegistryKey(),
                targetNow + ticks, targetNow);
        refreshBlindness(target, ticks);
        interruptOngoingActions(target);

        target.getWorld().playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 1.0F, 0.7F);
        caster.sendMessage(Text.translatable("message.sparkwitch.natsume.cast",
                target.getGameProfile().getName()).formatted(Formatting.DARK_PURPLE), true);
        target.sendMessage(Text.translatable("message.sparkwitch.natsume.hypnotised")
                .formatted(Formatting.DARK_PURPLE), true);

        NbtCompound replay = new NbtCompound();
        replay.putString("action", "hypnosis");
        replay.putUuid("target", target.getUuid());
        GameRecordManager.recordSkillUse(caster, NatsumeRules.ROLE_ID, target, replay);
    }

    /**
     * The lock refuses new actions, so what is already running stops at the cast, as for a Control Expert stun: an item
     * in use (drink, food, knife, bow or grenade charge) is cancelled without a release, and a Seeker remote session or
     * a Black Raven crow session ends through its own single end path (both idempotent no-ops without a session; the
     * crow ends like a stun, cooldown included). Sessions owned by other mods are not reachable here.
     * 封锁只拒绝新的行为，因此已在进行的行为在施放时停止，与控场专家眩晕相同：正在使用的物品（饮品、食物、刀、弓或手雷蓄力）
     * 被取消且不触发松手；搜寻者遥控会话与黑羽鸦化鸦会话经各自唯一的结束路径结束（无会话时均为幂等空操作；化鸦按眩晕结束，
     * 含冷却）。其他模组拥有的会话在此无法触及。
     */
    private static void interruptOngoingActions(ServerPlayerEntity target) {
        target.clearActiveItem();
        SeekerRemoteSessionService.end(target, SeekerExitReason.BLOCKED);
        BlackRavenCrowSessions.end(target, BlackRavenCrowEndReason.STUNNED);
    }

    /**
     * Caster gate ({@link NatsumeRules#casterVerdict}); a Fear refusal tells her why, any other refusal is silent.
     * 施放者门槛（{@link NatsumeRules#casterVerdict}）；恐惧拒绝会提示原因，其他拒绝均静默。
     */
    private static boolean mayUse(ServerPlayerEntity caster) {
        GameWorldComponent game = GameWorldComponent.KEY.get(caster.getWorld());
        NatsumeComponent state = NatsumeComponent.KEY.get(caster);
        NatsumeRules.CastVerdict verdict = NatsumeRules.casterVerdict(
                NatsumeRules.isNatsume(game.getRole(caster)),
                game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE,
                GameFunctions.isPlayerPlayingAndAlive(caster) && !caster.isSpectator()
                        && !NoellesTaotieSeekerBridge.isSwallowed(caster),
                NinjaGrappleService.isHeld(caster),
                NoellesSilenceBridge.isSilenced(caster),
                SparkTraitsKillerBridge.isRoleSkillBlocked(caster),
                state.isHypnotised(),
                state.cooldownRemainingTicks(caster.getWorld().getTime()) <= 0,
                GrandWitchFearService.isPlayerFeared(caster));
        if (verdict == NatsumeRules.CastVerdict.FEARED) {
            GrandWitchFearService.sendSkillBlocked(caster);
        }
        return verdict == NatsumeRules.CastVerdict.USE;
    }

    /** Live {@link NatsumeRules#isTargetable}. / 实时的 {@link NatsumeRules#isTargetable}。 */
    private static boolean isTargetable(ServerPlayerEntity caster, ServerPlayerEntity target, GameWorldComponent game) {
        return NatsumeRules.isTargetable(
                caster.getUuid().equals(target.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(target),
                target.isSpectator(),
                target.getWorld() == caster.getWorld(),
                NoellesTaotieSeekerBridge.isSwallowed(target),
                RiftSessionService.isInside(target),
                () -> SparkFactionApi.canAffectPlayer(caster, target, NatsumeRules.HYPNOSIS_SKILL_ID, game));
    }

    private static void tick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            long now = world.getTime();
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            boolean active = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
            List<ServerPlayerEntity> breakdowns = null;
            for (ServerPlayerEntity player : world.getPlayers()) {
                NatsumeComponent state = NatsumeComponent.KEY.get(player);
                state.refreshCooldownSeconds(now);
                if (state.hasHypnosis() && tickHypnosis(world, game, active, player, state, now)) {
                    if (breakdowns == null) {
                        breakdowns = new ArrayList<>(1);
                    }
                    breakdowns.add(player);
                }
            }
            // Killed after the loop: a death changes game mode and fires listeners while the world list is iterated.
            // 循环结束后再处死：死亡会切换游戏模式并触发监听器，不在遍历世界玩家列表时进行。
            if (breakdowns != null) {
                for (ServerPlayerEntity victim : breakdowns) {
                    killByBreakdown(server, victim);
                }
            }
        }
    }

    /**
     * One hypnosis tick; true when the victim breaks down and must be killed in her name. Ends the window early, with
     * no message, once the round is no longer ACTIVE or the victim died, spectates or left the cast's world.
     * 一刻催眠；受害者崩溃、需要以她的名义处死时返回 true。对局不再是 ACTIVE、受害者死亡、旁观或离开施放时所在世界时，静默
     * 提前结束窗口。
     */
    private static boolean tickHypnosis(ServerWorld world, GameWorldComponent game, boolean active,
                                        ServerPlayerEntity victim, NatsumeComponent state, long now) {
        if (!active
                || !GameFunctions.isPlayerPlayingAndAlive(victim)
                || victim.isSpectator()
                || !world.getRegistryKey().equals(state.hypnosisWorld())) {
            endHypnosis(victim, false);
            return false;
        }
        long left = state.hypnosisEndTick() - now;
        if (left > 0L) {
            refreshBlindness(victim, left);
            if (NatsumeRules.roarsAt(left)) {
                // Owner: only the victim hears it. 所有者：只有受害者能听到。
                victim.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 0.7F, 1.0F);
            }
        }
        Role role = game.getRole(victim);
        boolean realMood = role != null && role.getMoodType() == Role.MoodType.REAL;
        PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(victim);
        if (realMood && NatsumeRules.drainsOn(state.getHypnosisSeconds(),
                NatsumeRules.secondsLeft(state.hypnosisEndTick(), now))) {
            mood.setMood(NatsumeRules.drained(mood.getMood()));
        }
        // Owner: a breakdown inside the window is hers. Wathe would kill at -1 on its next mood tick with no killer.
        // 所有者：窗口内的崩溃死亡记在她名下。否则 Wathe 会在下一次理智刻于 -1 处死且没有击杀者。
        if (realMood && NatsumeRules.breaksDown(mood.getMood())) {
            return true;
        }
        if (left <= 0L) {
            endHypnosis(victim, true);
        } else if (state.refreshHypnosisSeconds(now)) {
            victim.sendMessage(Text.translatable("message.sparkwitch.natsume.remaining", state.getHypnosisSeconds())
                    .formatted(Formatting.DARK_PURPLE), true);
        }
        return false;
    }

    /**
     * Wathe's own breakdown kill ({@code MENTAL_BREAKDOWN}, body, forced), credited to the caster when she is online.
     * A self-hypnosis breakdown has no killer, exactly like Wathe's own.
     * Wathe 自身的崩溃处死（{@code MENTAL_BREAKDOWN}、留尸体、强制），施放者在线时记为击杀者。对自己催眠导致的崩溃没有击杀者，
     * 与 Wathe 自身完全相同。
     */
    private static void killByBreakdown(MinecraftServer server, ServerPlayerEntity victim) {
        NatsumeComponent state = NatsumeComponent.KEY.get(victim);
        UUID casterId = state.hypnosisCaster();
        endHypnosis(victim, false);
        if (!GameFunctions.isPlayerPlayingAndAlive(victim)) {
            return;
        }
        ServerPlayerEntity caster = casterId == null || casterId.equals(victim.getUuid())
                ? null
                : server.getPlayerManager().getPlayer(casterId);
        GameFunctions.killPlayer(victim, true, caster, GameConstants.DeathReasons.MENTAL_BREAKDOWN, true);
    }

    /**
     * Ends the victim's hypnosis. On expiry the blindness has already run out and the victim is told; an early end
     * also removes a blindness no longer than what was left of the window (so another source's longer blindness stays).
     * 结束受害者的催眠。自然到期时失明已同时结束，并提示受害者；提前结束时还会移除不长于窗口剩余时间的失明（因此其他来源更长的
     * 失明保持不变）。
     */
    private static void endHypnosis(@Nullable ServerPlayerEntity victim, boolean expired) {
        if (victim == null) {
            return;
        }
        NatsumeComponent state = NatsumeComponent.KEY.get(victim);
        if (!state.hasHypnosis()) {
            return;
        }
        long left = Math.max(0L, state.hypnosisEndTick() - victim.getWorld().getTime());
        state.endHypnosis();
        if (expired) {
            victim.sendMessage(Text.translatable("message.sparkwitch.natsume.ended")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        StatusEffectInstance blindness = victim.getStatusEffect(StatusEffects.BLINDNESS);
        if (blindness != null && !blindness.isInfinite() && blindness.getDuration() <= left + 1L) {
            victim.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }

    /**
     * Blindness for exactly the rest of the window (vanilla keeps a longer one), so it ends with the hypnosis.
     * 失明恰好持续到窗口结束（原版会保留更长的失明），因此与催眠同时结束。
     */
    private static void refreshBlindness(ServerPlayerEntity victim, long leftTicks) {
        int duration = (int) Math.clamp(leftTicks, 1L, Integer.MAX_VALUE);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, duration, 0, false, false, false));
    }

    /**
     * Applies {@link NatsumeShopRules}: removes the listed rows and reprices the poison vial, rebuilding it with every
     * field the Builder supports (display and actual stack, stock, cooldowns, and the original {@code onBuy}).
     * 应用 {@link NatsumeShopRules}：移除所列条目并为毒药瓶改价，用 Builder 支持的全部字段重建它（展示与实际物品、库存、冷却，
     * 以及原有的 {@code onBuy}）。
     */
    private static void applyShop(List<ShopEntry> entries) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            ShopEntry entry = entries.get(index);
            if (NatsumeShopRules.removes(entry.id())) {
                entries.remove(index);
                continue;
            }
            int price = NatsumeShopRules.priceFor(entry.id(), entry.price());
            if (price != entry.price()) {
                entries.set(index, new ShopEntry.Builder(entry.id(), entry.displayStack(), price, entry.type())
                        .actualStack(entry.getActualStack())
                        .cooldown(entry.cooldownTicks())
                        .initialCooldown(entry.initialCooldownTicks())
                        .stock(entry.maxStock())
                        .onBuy(entry::onBuy)
                        .build());
            }
        }
    }
}
