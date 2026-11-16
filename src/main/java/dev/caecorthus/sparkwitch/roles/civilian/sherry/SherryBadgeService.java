package dev.caecorthus.sparkwitch.roles.civilian.sherry;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 侦探徽章: 橘雪莉 hands a badge to another living player by using it on them. The holder is outlined in gold for her
 * alone (client side, from her owner-synced component). When a holder dies she gets a note naming the dead holder and,
 * if somebody killed them, that killer lights up for her every {@link SherryRules#BADGE_REVEAL_INTERVAL_TICKS} (owner
 * rule 2026-11-11: the killer's name and role are no longer told). The holder is never told about the badge.
 * 侦探徽章：橘雪莉手持徽章对另一名存活玩家使用即可赠出。持有者只对她显示金色高亮（客户端，依据只同步给她的组件）。持有者死亡时
 * 她获得一张写着死者名字的纸条；若死者是被人杀死的，凶手每 {@link SherryRules#BADGE_REVEAL_INTERVAL_TICKS} 刻对她高亮一次
 * （所有者规则 2026-11-11：不再告知凶手的名字与职业）。持有者不会收到任何关于徽章的提示。
 */
public final class SherryBadgeService {
    private static final String ACTION_KEY = "action";
    private static final String ACTION_HANDED = "handed";
    private static final String ACTION_HOLDER_DIED = "holder_died";

    private static boolean registered;

    private SherryBadgeService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseEntityCallback.EVENT.register(SherryBadgeService::onUseEntity);
        KillPlayer.AFTER.register(SherryBadgeService::afterKill);
        ReplayRegistry.registerSkillFormatter(SherryRules.BADGE_SKILL_ID, SherryBadgeService::formatBadge);
    }

    /**
     * Server authority: PASS for anything that is not Sherry using her badge on a player, so every other interaction
     * reaches the next listener untouched. A refused hand-over keeps the badge.
     * 服务端权威：只要不是雪莉手持徽章对玩家使用，一律 PASS，其他交互原样交给后续监听器。被拒绝的赠送不消耗徽章。
     */
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity,
                                            @Nullable net.minecraft.util.hit.EntityHitResult hit) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sherry)
                || !(entity instanceof ServerPlayerEntity target) || target == sherry) {
            return ActionResult.PASS;
        }
        ItemStack held = sherry.getStackInHand(hand);
        if (!held.isOf(SparkWitchItems.sherryBadge()) || !SherryDeductionService.mayUse(sherry)) {
            return ActionResult.PASS;
        }
        if (!GameFunctions.isPlayerPlayingAndAlive(target) || target.isSpectator()
                || GameFunctions.isPlayerSpectatingOrCreative(target)) {
            return ActionResult.PASS;
        }
        if (GrandWitchFearService.denyRoleSkillIfFeared(sherry)) {
            return ActionResult.FAIL;
        }
        SherryComponent component = SherryComponent.KEY.get(sherry);
        if (!component.addBadgeHolder(target.getUuid())) {
            sherry.sendMessage(Text.translatable("message.sparkwitch.sherry.badge.already",
                    target.getGameProfile().getName()).formatted(Formatting.GRAY), true);
            return ActionResult.FAIL;
        }
        held.decrement(1);
        sherry.sendMessage(Text.translatable("message.sparkwitch.sherry.badge.handed",
                target.getGameProfile().getName()).formatted(Formatting.GOLD), true);
        NbtCompound extra = new NbtCompound();
        extra.putString(ACTION_KEY, ACTION_HANDED);
        GameRecordManager.recordSkillUse(sherry, SherryRules.BADGE_SKILL_ID, target, extra);
        return ActionResult.SUCCESS;
    }

    /**
     * A badge holder really died (a SparkTraits Last Stand interception is not a death): every living Sherry who gave
     * them a badge gets the note and starts tracking the killer. No killer (a fall, a breakdown) or a killer who is
     * already dead means the note only; her own kill of her own holder tracks nobody.
     * 徽章持有者确实死亡（SparkTraits 背水一战拦截不算死亡）：每个给过其徽章的存活雪莉获得纸条并开始追踪凶手。没有凶手
     * （坠落、精神崩溃）或凶手已死时只发纸条；她自己杀死自己的持有者则不追踪任何人。
     */
    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                  Identifier deathReason) {
        if (!(victim.getWorld() instanceof ServerWorld world) || WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity sherry : world.getPlayers()) {
            if (sherry == victim || !SherryRules.isSherry(game.getRole(sherry))
                    || !GameFunctions.isPlayerPlayingAndAlive(sherry)) {
                continue;
            }
            SherryComponent component = SherryComponent.KEY.get(sherry);
            if (!component.removeBadgeHolder(victim.getUuid())) {
                continue;
            }
            String victimName = victim.getGameProfile().getName();
            giveNote(sherry, victimName);
            boolean tracked = killer != null && killer != sherry && killer != victim
                    && GameFunctions.isPlayerPlayingAndAlive(killer)
                    && component.track(killer.getUuid(), SherryRules.BADGE_REVEAL_INTERVAL_TICKS);
            sherry.sendMessage(Text.translatable(tracked
                            ? "message.sparkwitch.sherry.badge.holder_killed"
                            : "message.sparkwitch.sherry.badge.holder_died", victimName)
                    .formatted(Formatting.GOLD), true);
            NbtCompound extra = new NbtCompound();
            extra.putString(ACTION_KEY, ACTION_HOLDER_DIED);
            GameRecordManager.recordSkillUse(sherry, SherryRules.BADGE_SKILL_ID, victim, extra);
        }
    }

    /** The note carries the dead holder's name only; a full inventory drops it at her feet. */
    /** 纸条只写死去持有者的名字；物品栏已满时掉在她脚边。 */
    private static void giveNote(ServerPlayerEntity sherry, String victimName) {
        ItemStack note = new ItemStack(SparkWitchItems.sherryNote());
        NbtCompound data = new NbtCompound();
        data.putString(SherryRules.NOTE_VICTIM_KEY, victimName);
        note.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
        if (!sherry.giveItemStack(note)) {
            sherry.dropItem(note, false);
        }
    }

    private static @Nullable Text formatBadge(
            GameRecordEvent event,
            GameRecordManager.MatchRecord match,
            ServerWorld world
    ) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        UUID actor = data.getUuid("actor");
        UUID target = data.getUuid("target");
        return Text.translatable(
                ACTION_HOLDER_DIED.equals(data.getString(ACTION_KEY))
                        ? "replay.skill.sparkwitch.sherry_badge.holder_died"
                        : "replay.skill.sparkwitch.sherry_badge.handed",
                ReplayGenerator.formatPlayerName(actor, playerInfo),
                ReplayGenerator.formatPlayerName(target, playerInfo)
        );
    }
}
