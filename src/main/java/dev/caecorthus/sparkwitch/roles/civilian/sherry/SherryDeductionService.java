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
import net.minecraft.nbt.NbtCompound;
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
    /** Sherry's reveal windows: the killer every 20 s, other non-innocents every 40 s, 5 s each. */
    /** 雪莉的揭示窗口：凶手每 20 秒、其它非好人每 40 秒，各亮 5 秒。 */
    private static final Map<UUID, UUID> VISION_TARGET = new HashMap<>();
    private static final Map<UUID, Long> VISION_NEXT = new HashMap<>();

    public static void confirm(ServerPlayerEntity player, UUID targetId) {
        if (!dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryService.isSherry(player)) {
            return;
        }
        long now = player.getServer().getTicks();
        Long until = COOLDOWN_UNTIL.get(player.getUuid());
        if (until != null && now < until) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.cooldown",
                    (until - now) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null) {
            return;
        }
        var shop = dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(player);
        if (shop.getBalance() < SherryRules.DEDUCTION_COST) {
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.no_coins")
                    .formatted(Formatting.RED), true);
            return;
        }
        var world = player.getServerWorld();
        var role = GameWorldComponent.KEY.get(world).getRole(target);
        boolean innocent = role != null && role.isInnocent();
        boolean killer = role != null && GameWorldComponent.KEY.get(world).canUseKillerFeatures(target);

        shop.setBalance(shop.getBalance() - SherryRules.DEDUCTION_COST);
        COOLDOWN_UNTIL.put(player.getUuid(), now + (long) SherryRules.DEDUCTION_COOLDOWN_TICKS);

        if (innocent) {
            // Misjudgement: -20% sanity, Blindness + Slowness II for 3 s.
            // 误判：-20% 理智，失明与缓慢 II 各 3 秒。
            var mood = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(player);
            mood.setMood(Math.max(-1.0F, mood.getMood() - 0.20F));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,
                    (3 * 20), 0, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    (3 * 20), 1, false, false, false));
            player.sendMessage(Text.translatable("message.sparkwitch.sherry.wrong")
                    .formatted(Formatting.RED), true);
            VISION_TARGET.remove(player.getUuid());
            VISION_NEXT.remove(player.getUuid());
            record(player, "fail", target, null);
            return;
        }

        // Correct: open a repeating reveal window. 正确：开启循环揭示窗口。
        VISION_TARGET.put(player.getUuid(), target.getUuid());
        VISION_NEXT.put(player.getUuid(), now + (long) SherryRules.DEDUCTION_COOLDOWN_TICKS);
        dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent.KEY.get(player)
                .setVision(target.getUuid(), 0);
        player.sendMessage(Text.translatable(killer
                        ? "message.sparkwitch.sherry.right_killer"
                        : "message.sparkwitch.sherry.right_non_innocent")
                .formatted(Formatting.AQUA), true);
        record(player, killer ? "success_killer" : "success_non_innocent", target, null);
    }

    private static void record(ServerPlayerEntity player, String outcome, ServerPlayerEntity target, String detail) {
        NbtCompound replay = new NbtCompound();
        replay.putString("action", "deduction");
        replay.putString("outcome", outcome);
        replay.putUuid("target", target == null ? player.getUuid() : target.getUuid());
        dev.doctor4t.wathe.record.GameRecordManager.recordSkillUse(
                player, SherryRules.ROLE_ID, target, replay);
    }

    /** Repeating reveal: 5 s of Glowing on the deduced target. 循环揭示：目标发光 5 秒。 */
    public static void tickVision(net.minecraft.server.MinecraftServer server) {
        if (VISION_TARGET.isEmpty()) {
            return;
        }
        long now = server.getTicks();
        var it = VISION_TARGET.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID sherryId = entry.getKey();
            Long next = VISION_NEXT.get(sherryId);
            if (next == null || now < next) {
                continue;
            }
            ServerPlayerEntity sherry = server.getPlayerManager().getPlayer(sherryId);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getValue());
            if (sherry == null || target == null || !dev.doctor4t.wathe.game.GameFunctions
                    .isPlayerPlayingAndAlive(target)) {
                it.remove();
                VISION_NEXT.remove(sherryId);
                continue;
            }
            // Reveal is client-side and private to Sherry: the component carries the target and a fresh window.
            // 揭示在客户端完成、只对雪莉可见：组件带着目标与一个刚刷新的窗口。
            dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent.KEY.get(sherry)
                    .setVision(target.getUuid(), (5 * 20));
            boolean killer = GameWorldComponent.KEY.get(sherry.getServerWorld()).canUseKillerFeatures(target);
            VISION_NEXT.put(sherryId, now + (long) (killer
                    ? (20 * 20)
                    : (40 * 20)));
        }
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
