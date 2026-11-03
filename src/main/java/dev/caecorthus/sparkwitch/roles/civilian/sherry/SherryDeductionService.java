package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkwitch.net.OpenSherryDeductionS2CPacket;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 推演 — the detective's kill: aim at a corpse, pick a target within 60 blocks, pay 200 coins.
 * <p>Killing an innocent is a misjudgement: the coins are still spent, the target still dies, and the detective pays
 * 35% sanity plus 5 s of Slowness and Blindness. Killing a non-innocent is a clean deduction.
 * 推演——侦探的击杀：对准尸体，选择 60 格内的目标，支付 200 金币。
 * <p>杀好人属于误判：金币照扣、目标照死，侦探再付 35% 理智与 5 秒缓慢、失明。杀非好人则是一次正确的推断。
 */
public final class SherryDeductionService {

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    /** Verdicts waiting to land: victim -> (detective, tick to kill). 待执行的判罪：死者 -> (侦探, 执行时刻)。 */
    private static final Map<UUID, PendingVerdict> PENDING = new HashMap<>();

    /** The detective's delayed kill reason. 侦探延迟处决的死因。 */
    public static final net.minecraft.util.Identifier VERDICT_DEATH =
            net.minecraft.util.Identifier.of("sparkwitch", "sherry_verdict");

    /** Five seconds between the verdict and the death. 判罪到处决之间有 5 秒。 */
    public static final int VERDICT_DELAY_TICKS = 5 * 20;

    /** Whether the pending verdict was a misjudgement travels with it, so the replay can never read another kill's flag. */
    /** 是否为误判随判罪一起保存，回放不会读到别人的标记。 */
    private record PendingVerdict(UUID detective, long dueTick, int lastShownSeconds, boolean innocent) {
    }

    private SherryDeductionService() {
    }

    /** Round-start cooldown for the deduction skill. 推演的回合初始冷却。 */
    public static void applyInitialCooldown(ServerPlayerEntity player) {
        COOLDOWN_UNTIL.put(player.getUuid(),
                player.getServer().getTicks() + (long) SherryRules.INITIAL_COOLDOWN_TICKS);
        SherryComponent.KEY.get(player)
                .setDeductionCooldownSeconds(SherryRules.INITIAL_COOLDOWN_TICKS / 20);
    }

    public static boolean onCooldown(ServerPlayerEntity player) {
        long now = player.getServer().getTicks();
        long until = COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        if (now >= until) {
            COOLDOWN_UNTIL.remove(player.getUuid());
            SherryComponent.KEY.get(player).setDeductionCooldownSeconds(0);
            return false;
        }
        return true;
    }

    /** Recomputes the HUD's remaining seconds from the tick deadline. 按 tick 截止时间重算 HUD 剩余秒数。 */
    public static void refreshHud(ServerPlayerEntity player) {
        long until = COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        long left = until - player.getServer().getTicks();
        int seconds = left > 0 ? (int) Math.ceil(left / 20.0) : 0;
        SherryComponent.KEY.get(player).setDeductionCooldownSeconds(seconds);
        if (left <= 0) {
            COOLDOWN_UNTIL.remove(player.getUuid());
        }
    }

    public static void tickCooldowns() {
        // Cooldowns are re-checked lazily; the HUD second is refreshed when the component is queried.
        // 冷却按需检查；HUD 的秒数在查询组件时刷新。
    }

    /** Opens the candidate panel if the detective is aiming at a corpse. 若正对准尸体，则打开候选面板。 */
    public static void openSelection(ServerPlayerEntity player) {
        if (!SherryService.isSherry(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!isAimingAtCorpse(player)) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.no_body")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        if (onCooldown(player)) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.cooldown",
                    SherryComponent.KEY.get(player).getDeductionCooldownSeconds())
                    .formatted(Formatting.GRAY), true);
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (shop.getBalance() < SherryRules.DEDUCTION_COST) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.no_coins",
                    SherryRules.DEDUCTION_COST).formatted(Formatting.RED), true);
            return;
        }
        List<UUID> uuids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other == player || !GameFunctions.isPlayerPlayingAndAlive(other)) {
                continue;
            }
            if (player.squaredDistanceTo(other) > SherryRules.DEDUCTION_RANGE * SherryRules.DEDUCTION_RANGE) {
                continue;
            }
            uuids.add(other.getUuid());
            names.add(other.getGameProfile().getName());
        }
        if (uuids.isEmpty()) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.no_target")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        ServerPlayNetworking.send(player, new OpenSherryDeductionS2CPacket(uuids, names));
    }

    /** Resolves the kill and the misjudgement penalty. 结算击杀与误判惩罚。 */
    public static void confirm(ServerPlayerEntity player, UUID targetId) {
        if (!SherryService.isSherry(player) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (onCooldown(player)) {
            return;
        }
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            return;
        }
        if (player.squaredDistanceTo(target) > SherryRules.DEDUCTION_RANGE * SherryRules.DEDUCTION_RANGE) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.too_far")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (shop.getBalance() < SherryRules.DEDUCTION_COST) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.no_coins",
                    SherryRules.DEDUCTION_COST).formatted(Formatting.RED), true);
            return;
        }
        shop.setBalance(shop.getBalance() - SherryRules.DEDUCTION_COST);
        COOLDOWN_UNTIL.put(player.getUuid(), player.getServer().getTicks() + (long) SherryRules.DEDUCTION_COOLDOWN_TICKS);
        SherryComponent.KEY.get(player).setDeductionCooldownSeconds(SherryRules.DEDUCTION_COOLDOWN_TICKS / 20);

        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        var targetRole = game.getRole(target);
        boolean innocent = targetRole == null || targetRole.isInnocent();
        // The verdict lands five seconds later; the kill reason is the verdict itself.
        // 判罪在 5 秒后落地；死因就是“被橘雪莉判罪”。
        long dueTick = player.getServer().getTicks() + VERDICT_DELAY_TICKS;
        PENDING.put(target.getUuid(),
                new PendingVerdict(player.getUuid(), dueTick, VERDICT_DELAY_TICKS / 20, innocent));
        player.sendMessage(Text.translatable("message.sparkwitch.sherry.verdict.declared",
                target.getGameProfile().getName(), VERDICT_DELAY_TICKS / 20).formatted(Formatting.GOLD), true);
        target.sendMessage(Text.translatable("message.sparkwitch.sherry.verdict.received",
                player.getGameProfile().getName(), VERDICT_DELAY_TICKS / 20).formatted(Formatting.RED), true);

        // Recorded when the verdict resolves, in tickVerdicts. / 在判罪执行时记录（见 tickVerdicts）。
    }

    /** Runs the delayed kills, one action-bar reminder per changed second. 执行延迟处决，每秒提醒一次。 */
    public static void tickVerdicts(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getTicks();
        PENDING.entrySet().removeIf(entry -> {
            UUID victimId = entry.getKey();
            PendingVerdict verdict = entry.getValue();
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
            ServerPlayerEntity detective = server.getPlayerManager().getPlayer(verdict.detective());
            if (victim == null || detective == null || !GameFunctions.isPlayerPlayingAndAlive(victim)) {
                return true;
            }
            int remaining = (int) Math.max(0, Math.ceil((verdict.dueTick() - now) / 20.0));
            if (remaining > 0) {
                if (remaining != verdict.lastShownSeconds()) {
                    victim.sendMessage(Text.translatable("message.sparkwitch.sherry.verdict.received",
                            detective.getGameProfile().getName(), remaining).formatted(Formatting.RED), true);
                    entry.setValue(new PendingVerdict(
                            verdict.detective(), verdict.dueTick(), remaining, verdict.innocent()));
                }
                return false;
            }
            boolean innocent = verdict.innocent();
            GameFunctions.killPlayer(victim, true, detective, VERDICT_DEATH);
            if (innocent) {
                PlayerMoodComponent mood = PlayerMoodComponent.KEY.get(detective);
                mood.setMood(mood.getMood() - SherryRules.WRONG_TARGET_MOOD_LOSS);
                detective.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                        SherryRules.WRONG_TARGET_EFFECT_TICKS, 1, false, false, true));
                detective.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,
                        SherryRules.WRONG_TARGET_EFFECT_TICKS, 0, false, false, true));
                detective.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.wrong")
                        .formatted(Formatting.RED), true);
            } else {
                detective.sendMessage(Text.translatable("message.sparkwitch.sherry.deduction.right")
                        .formatted(Formatting.GREEN), true);
            }
            net.minecraft.nbt.NbtCompound replay = new net.minecraft.nbt.NbtCompound();
            replay.putString("action", "deduction");
            replay.putBoolean("success", !innocent);
            dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                    detective, SherryRules.ROLE_ID, victim, replay);
            return true;
        });
    }

    private static boolean isAimingAtCorpse(ServerPlayerEntity player) {
        var hit = player.raycast(3.0D, 0.0F, false);
        if (hit instanceof net.minecraft.util.hit.EntityHitResult entityHit
                && entityHit.getEntity() instanceof PlayerBodyEntity) {
            return true;
        }
        // Wathe's bodies sit on the ground; accept any body within three blocks in front of the player.
        // Wathe 的尸体躺在地上；只要玩家前方三格内有尸体即可。
        var box = player.getBoundingBox().expand(3.0D, 1.0D, 3.0D);
        return !player.getWorld().getOtherEntities(player, box, e -> e instanceof PlayerBodyEntity).isEmpty();
    }
}
