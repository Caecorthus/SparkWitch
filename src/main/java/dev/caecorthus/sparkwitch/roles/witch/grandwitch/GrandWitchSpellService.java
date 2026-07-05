package dev.caecorthus.sparkwitch.roles.witch.grandwitch;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Executes Grand Witch shop spells on server state.
 * 执行大魔女商店法术；金币购买流程只负责触发这里。
 */
public final class GrandWitchSpellService {
    private GrandWitchSpellService() {
    }

    public static boolean cast(ServerPlayerEntity caster, WitchFactionRules.GrandWitchSpell spell) {
        ServerWorld world = caster.getServerWorld();
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        if (!WitchFactionRules.isGrandWitch(gameComponent.getRole(caster))) {
            return false;
        }

        WitchPlayerComponent playerComponent = WitchPlayerComponent.KEY.get(caster);
        if (!playerComponent.spendMana(spell.manaCost())) {
            send(caster, "shop.error.sparkwitch.not_enough_mana");
            return false;
        }

        switch (spell) {
            case OBSCURE -> WitchWorldComponent.KEY.get(world).startInstinctObscure(spell.durationTicks());
            case BLINDNESS -> applyStatusToAffectedPlayers(world, spell, StatusEffects.BLINDNESS, 0);
            case FEAR -> WitchWorldComponent.KEY.get(world).startFear(spell.durationTicks());
            case HEAVINESS -> applyStatusToAffectedPlayers(world, spell, StatusEffects.SLOWNESS, 2);
        }
        send(caster, "message.sparkwitch.spell." + spell.path() + ".cast");
        return true;
    }

    public static boolean hasEnoughMana(ServerPlayerEntity player, WitchFactionRules.GrandWitchSpell spell) {
        return WitchPlayerComponent.KEY.get(player).getMana() >= spell.manaCost();
    }

    public static void sendObscureActionbars(ServerWorld world, int remainingTicks) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        int seconds = (int) Math.ceil(remainingTicks / 20.0);
        for (ServerPlayerEntity player : world.getPlayers()) {
            Role role = gameComponent.getRole(player);
            if (!GameFunctions.isPlayerPlayingAndAlive(player)
                    || !WitchFactionRules.isAffectedByWitchAreaSpell(role)
                    || !canUseInstinct(player, gameComponent)) {
                continue;
            }
            send(player, "message.sparkwitch.spell.obscure.actionbar", seconds);
        }
    }

    public static void tickFear(ServerWorld world, int remainingTicks) {
        if (!GrandWitchFearService.shouldPulseFear(remainingTicks)) {
            return;
        }
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            Role role = gameComponent.getRole(player);
            if (!GameFunctions.isPlayerPlayingAndAlive(player) || !GrandWitchFearService.isAffectedRole(role)) {
                continue;
            }
            GrandWitchFearService.applyMoodPulse(player, WitchFactionRules.GrandWitchSpell.FEAR.durationTicks());
        }
    }

    private static void applyStatusToAffectedPlayers(
            ServerWorld world,
            WitchFactionRules.GrandWitchSpell spell,
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect,
            int amplifier
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            Role role = gameComponent.getRole(player);
            if (!GameFunctions.isPlayerPlayingAndAlive(player)
                    || !WitchFactionRules.isAffectedByWitchAreaSpell(role)) {
                continue;
            }
            player.addStatusEffect(new StatusEffectInstance(
                    effect,
                    spell.durationTicks(),
                    amplifier,
                    false,
                    false,
                    true
            ));
        }
    }

    private static boolean canUseInstinct(ServerPlayerEntity player, GameWorldComponent gameComponent) {
        return SparkFactionApi.capabilities(SparkFactionApi.resolveEffectiveFaction(player, gameComponent)).canUseInstinct();
    }

    private static void send(ServerPlayerEntity player, String translationKey, Object... args) {
        player.sendMessage(Text.translatable(translationKey, args), true);
    }
}
