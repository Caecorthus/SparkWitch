package dev.caecorthus.sparkwitch.roles.neutral.fiend;

import dev.caecorthus.sparkwitch.roles.neutral.fiend.FiendRules.Hit;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Dormant Fiend hit reactions (gold, notes, Speed III, cooldown aura) and the bomb-pass payout. Server only. Reactions
 * run only for a kill the immunity actually cancelled (C3), once per attack: same-tick repeats are collapsed by
 * {@link FiendHitLedger}, and a gun kill whose Wathe gun is already cooling down is a synthetic follow-up of a shot that
 * already paid (SparkTraits Niko burst repeats fire 2 and 4 ticks later without firing the gun again).
 * 休眠魔人的受击反应（金币、便条、速度 III、冷却光环）与炸弹转手奖励。仅服务端。只对免疫实际取消的击杀触发（C3），
 * 且每次攻击只触发一次：同一刻的重复由 {@link FiendHitLedger} 合并；若击杀时手中 Wathe 枪已在冷却，则视为已结算射击的
 * 合成补射（SparkTraits Niko 连射在 2、4 刻后补射，不会再次开枪）。
 */
public final class FiendReactionService {
    static final String GUN_MESSAGE = "message.sparkwitch.fiend.reward.gun";
    static final String KNIFE_MESSAGE = "message.sparkwitch.fiend.reward.knife";
    static final String BAT_MESSAGE = "message.sparkwitch.fiend.reward.bat";
    static final String SWORD_MESSAGE = "message.sparkwitch.fiend.reward.sword";
    static final String BOMB_MESSAGE = "message.sparkwitch.fiend.reward.bomb";

    private static final FiendHitLedger HITS = new FiendHitLedger();
    private static boolean registered;

    private FiendReactionService() {
    }

    /** The single WP1 runtime entry in {@link FiendFeatureService}. / WP1 在 {@link FiendFeatureService} 中的唯一运行时入口。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        FiendCooldownAura.register();
        FiendBombLedger.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HITS.clear());
    }

    /** What one qualifying hit grants. / 一次有效受击所获得的奖励。 */
    record Reward(int gold, int notes, boolean speed, boolean aura, String messageKey) {
    }

    static @Nullable Reward rewardFor(@Nullable Hit hit) {
        if (hit == null) {
            return null;
        }
        return switch (hit) {
            case GUN -> new Reward(FiendRules.GUN_GOLD, 0, true, true, GUN_MESSAGE);
            case KNIFE -> new Reward(FiendRules.KNIFE_GOLD, FiendRules.KNIFE_NOTES, false, false, KNIFE_MESSAGE);
            case BAT -> new Reward(FiendRules.BAT_GOLD, 0, false, false, BAT_MESSAGE);
            case SWORD -> new Reward(FiendRules.SWORD_GOLD, 0, false, false, SWORD_MESSAGE);
            case NONE -> null;
        };
    }

    /** A Wathe gun that is already cooling down cannot be firing a fresh shot. / 已在冷却的 Wathe 枪不可能正在开新的一枪。 */
    static boolean isFollowUpGunResolution(boolean heldWatheGun, boolean heldGunCoolingDown) {
        return heldWatheGun && heldGunCoolingDown;
    }

    /** Only {@link FiendImmunityService} calls this, after deciding to cancel. / 仅由 {@link FiendImmunityService} 在决定取消后调用。 */
    static void onBlockedHit(ServerPlayerEntity fiend, @Nullable ServerPlayerEntity attacker,
                             @Nullable Identifier deathReason) {
        if (attacker == null || attacker == fiend) {
            return;
        }
        Hit hit = FiendRules.classify(deathReason, FiendStabScope.isStabbing(attacker));
        Reward reward = rewardFor(hit);
        if (reward == null) {
            return;
        }
        if (hit == Hit.GUN && isFollowUpGunResolution(attacker)) {
            return;
        }
        if (!HITS.claim(fiend.getUuid(), attacker.getUuid(), hit, fiend.getServer().getTicks())) {
            return;
        }
        grant(fiend, reward);
    }

    /** Bomb-pass credit (C6); only {@link FiendBombLedger} calls this. / 炸弹转手奖励（C6）；仅由 {@link FiendBombLedger} 调用。 */
    static void rewardBombPass(ServerPlayerEntity fiend) {
        grant(fiend, new Reward(FiendRules.BOMB_PASS_GOLD, 0, false, false, BOMB_MESSAGE));
    }

    private static boolean isFollowUpGunResolution(ServerPlayerEntity attacker) {
        ItemStack held = attacker.getMainHandStack();
        boolean watheGun = held.isIn(WatheItemTags.GUNS);
        return isFollowUpGunResolution(watheGun,
                watheGun && attacker.getItemCooldownManager().isCoolingDown(held.getItem()));
    }

    private static void grant(ServerPlayerEntity fiend, Reward reward) {
        PlayerShopComponent.KEY.get(fiend).addToBalance(reward.gold());
        if (reward.speed()) {
            fiend.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FiendRules.GUN_SPEED_TICKS,
                    FiendRules.GUN_SPEED_AMPLIFIER, false, false, true));
        }
        if (reward.notes() > 0) {
            fiend.getInventory().offerOrDrop(new ItemStack(WatheItems.NOTE, reward.notes()));
        }
        if (reward.aura()) {
            FiendCooldownAura.enqueue(fiend);
        }
        MutableText message = reward.notes() > 0
                ? Text.translatable(reward.messageKey(), reward.gold(), reward.notes())
                : Text.translatable(reward.messageKey(), reward.gold());
        fiend.sendMessage(message.formatted(Formatting.GOLD), true);
    }
}
