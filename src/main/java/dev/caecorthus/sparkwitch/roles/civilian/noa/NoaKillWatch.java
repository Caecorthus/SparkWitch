package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsNoaBridge;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Noa's two {@code KillPlayer.AFTER} rules, server only:
 * <ul>
 *   <li>Knife innocent kill: a Noa who kills a good player (SparkFactionAPI effective faction CIVILIAN) with Wathe's
 *   knife dies with Wathe's own SHOT_INNOCENT ("小脑", owner 2026-11-04), exactly like the Recruit. Skipped only for a
 *   confirmed SparkTraits Impostor. Her revolver keeps Wathe's own innocent-shot punishment.</li>
 *   <li>Big dog portrait: anyone except a Noa who kills within {@link NoaRules#DOG_RADIUS} of a Noa carrying the
 *   portrait (any inventory slot or the off hand) is slowed IV and blinded for 5 s. The carrier must be a living
 *   participant, or the victim herself at the moment she dies. Noa herself is immune (owner 2026-11-04); a Vigilante
 *   is not.</li>
 * </ul>
 * 诺亚的两条 {@code KillPlayer.AFTER} 规则，仅服务端：
 * 用刀误杀：诺亚用 Wathe 的刀杀死好人（SparkFactionAPI 有效阵营为 CIVILIAN）时，以 Wathe 自身的 SHOT_INNOCENT 处死
 * （“小脑”，所有者 2026-11-04），与新兵完全一致；只在确认是 SparkTraits 内鬼时跳过。左轮仍按 Wathe 自身的误杀惩罚。
 * 大狗画像：诺亚携带画像（任意背包格或副手）时，除诺亚以外任何在她 {@link NoaRules#DOG_RADIUS} 格内杀人的人都会被缓慢 IV
 * 并失明 5 秒。携带者须是存活的参与者，或正是在这一刻死去的受害者本人。诺亚本人免疫（所有者 2026-11-04）；义警不免疫。
 */
final class NoaKillWatch {
    /** SparkFactionAPI skill id for the dog's punishment. / 大狗惩罚的 SparkFactionAPI 技能 id。 */
    static final Identifier DOG_SKILL_ID = SparkWitch.id("noa_dog");

    private NoaKillWatch() {
    }

    static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        if (killer == null || killer == victim) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(killer.getServerWorld());
        if (NoaRules.isNoa(game.getRole(killer))) {
            punishKnifeInnocentKill(victim, killer, deathReason, game);
            return;
        }
        if (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            watchWithDog(victim, killer, game);
        }
    }

    private static void punishKnifeInnocentKill(ServerPlayerEntity victim, ServerPlayerEntity noa,
                                                Identifier deathReason, GameWorldComponent game) {
        if (!GameConstants.DeathReasons.KNIFE.equals(deathReason)
                || !FactionIds.CIVILIAN.equals(SparkFactionApi.resolveEffectiveFaction(victim, game))
                || Boolean.TRUE.equals(SparkTraitsNoaBridge.isImpostor(noa))
                || !GameFunctions.isPlayerPlayingAndAlive(noa)) {
            return;
        }
        // Non-forced, exactly Wathe's own SHOT_INNOCENT kill ("小脑" in zh_cn). / 非强制，即 Wathe 自身的
        // SHOT_INNOCENT 击杀（中文 lang 中显示为“小脑”）。
        GameFunctions.killPlayer(noa, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
    }

    private static void watchWithDog(ServerPlayerEntity victim, ServerPlayerEntity killer, GameWorldComponent game) {
        double radiusSquared = NoaRules.DOG_RADIUS * NoaRules.DOG_RADIUS;
        for (ServerPlayerEntity noa : killer.getServerWorld().getPlayers()) {
            if (!NoaRules.isNoa(game.getRole(noa))
                    || !(noa == victim || GameFunctions.isPlayerPlayingAndAlive(noa) && !noa.isSpectator())
                    || noa.squaredDistanceTo(killer) > radiusSquared
                    || !carriesPortrait(noa)
                    || !SparkFactionApi.canAffectPlayer(noa, killer, DOG_SKILL_ID, game)) {
                continue;
            }
            punish(noa, killer);
            return;
        }
    }

    private static void punish(ServerPlayerEntity noa, ServerPlayerEntity killer) {
        killer.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, NoaRules.DOG_PUNISH_TICKS, 3,
                false, false, true));
        killer.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, NoaRules.DOG_PUNISH_TICKS, 0,
                false, false, true));
        ServerWorld world = killer.getServerWorld();
        world.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.ENTITY_WOLF_GROWL,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
        killer.sendMessage(Text.translatable("message.sparkwitch.noa.dog.punished").formatted(Formatting.RED), true);
        noa.sendMessage(Text.translatable("message.sparkwitch.noa.dog.locked", killer.getDisplayName())
                .formatted(Formatting.GOLD), true);
        NoaReplay.recordDogWatch(noa, killer);
    }

    /** Main inventory (hotbar included) or the off hand. / 主背包（含快捷栏）或副手。 */
    static boolean carriesPortrait(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.main) {
            if (isPortrait(stack)) {
                return true;
            }
        }
        for (ItemStack stack : inventory.offHand) {
            if (isPortrait(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPortrait(ItemStack stack) {
        return !stack.isEmpty() && NoaRules.DOG_PORTRAIT_ITEM_ID.equals(Registries.ITEM.getId(stack.getItem()));
    }
}
