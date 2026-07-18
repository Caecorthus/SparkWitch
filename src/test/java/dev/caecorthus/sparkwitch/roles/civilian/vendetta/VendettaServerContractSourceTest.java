package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaServerContractSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    @Test
    void featureResponsibilitiesStaySplitAcrossFocusedOwners() throws Exception {
        assertFalse(Files.exists(MAIN.resolve("roles/civilian/vendetta/VendettaFeatureService.java")));
        assertTrue(source("roles/civilian/vendetta/VendettaLifecycleService.java")
                .contains("initializeForPromotion"));
        assertTrue(source("roles/civilian/vendetta/VendettaInteractionService.java")
                .contains("isExactPair"));
        assertTrue(source("roles/civilian/vendetta/VendettaDisconnectService.java")
                .contains("shouldPauseOfflineBoundKillerEscape"));
        assertTrue(source("roles/civilian/vendetta/VendettaTerminalService.java")
                .contains("tryResolveBoundKillerVictory"));
        assertTrue(source("roles/civilian/vendetta/VendettaReplayService.java")
                .contains("recordTerminal"));
    }

    @Test
    void separateComponentPersistsTheBondWithoutChangingWraithSchema() throws Exception {
        String component = compact(source("roles/civilian/vendetta/VendettaPlayerComponent.java"));
        String registry = source("component/SparkWitchComponents.java");
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        String wraith = source("component/WraithPlayerComponent.java");

        assertTrue(component.contains("SparkWitch.id(\"vendetta\")"));
        assertTrue(component.contains("recipient == player"));
        assertTrue(component.contains("recipient.getUuid().equals(state.boundKillerUuid())"));
        assertTrue(component.contains("tag.putUuid(\"BoundKiller\""));
        assertTrue(component.contains("tag.putBoolean(\"Active\""));
        assertTrue(component.contains("tag.putBoolean(\"KnifeAvailable\""));
        assertTrue(registry.contains("VendettaPlayerComponent.KEY"));
        assertTrue(registry.contains("RespawnCopyStrategy.NEVER_COPY"));
        assertTrue(metadata.contains("\"sparkwitch:vendetta\""));
        assertFalse(wraith.contains("BoundKiller"));
    }

    @Test
    void terminalPathUsesConditionalEconomiesAndDedicatedReplay() throws Exception {
        String terminal = source("roles/civilian/vendetta/VendettaTerminalService.java");
        String replay = source("roles/civilian/vendetta/VendettaReplayService.java");
        String mixin = source("mixin/VendettaTerminalKillMixin.java");
        String mightyForce = source(
                "roles/civilian/apprentice/abilities/MightyForce/MightyForceCombatService.java");

        assertTrue(terminal.contains("PlayerShopComponent.KEY.maybeGet(killer)"));
        assertTrue(terminal.contains("WitchPlayerComponent.KEY.maybeGet(killer)"));
        assertTrue(terminal.contains("mana.hasManaSystem()"));
        assertTrue(terminal.contains("ParticleTypes.SMOKE"));
        assertTrue(terminal.contains("ParticleTypes.SOUL"));
        assertTrue(terminal.contains("VendettaRules.TERMINAL_MONEY_REWARD"));
        assertTrue(terminal.contains("VendettaRules.TERMINAL_MANA_REWARD"));
        assertFalse(terminal.contains("KillPlayer.AFTER.invoker()"));
        assertTrue(replay.contains("recordGlobalEvent"));
        assertTrue(replay.contains("registerGlobalEventFormatter"));
        assertTrue(replay.contains("replay.global.sparkwitch.vendetta_terminal"));
        assertTrue(mixin.contains("VendettaTerminalService.tryResolveBoundKillerVictory"));
        assertTrue(mightyForce.contains("boolean terminalVendetta"));
        assertTrue(mightyForce.indexOf("GameFunctions.killPlayer")
                < mightyForce.lastIndexOf("GameRecordManager.recordSkillUse"));
    }

    @Test
    void promotionBlackoutAndPairInteractionUseTheirNarrowOwners() throws Exception {
        String interaction = source("roles/civilian/vendetta/VendettaInteractionService.java");
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        String participation = source("roles/special/wraith/runtime/WraithParticipation.java");
        String statusEffects = source("mixin/WraithStatusEffectMixin.java");

        assertTrue(interaction.contains("BlackoutEffect.BEFORE.register"));
        assertTrue(lifecycle.contains("VendettaLifecycleService.initializeForPromotion(player, role)"));
        assertTrue(lifecycle.contains("VendettaLifecycleService.resumePlayer(player)"));
        assertTrue(participation.contains("VendettaInteractionService.isExactPair(actor, target)"));
        assertTrue(statusEffects.contains("!VendettaInteractionService.isExactPair(actor, targetPlayer)"));
    }

    @Test
    void knifeHasServerValidationPrivateEquipmentAndCompleteResources() throws Exception {
        String knife = source("roles/civilian/vendetta/VendettaKnifeService.java");
        String loadout = source("roles/civilian/vendetta/VendettaKnifeLoadoutService.java");
        String filter = source("roles/civilian/vendetta/VendettaKnifeEquipmentFilter.java");
        String packet = source("roles/civilian/vendetta/UseVendettaKnifeC2SPacket.java");
        JsonObject model = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/sparkwitch/models/item/vendetta_knife.json"
        ))).getAsJsonObject();
        JsonObject chinese = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/sparkwitch/lang/zh_cn.json"
        ))).getAsJsonObject();

        assertTrue(knife.contains("consumeQualifiedRelease"));
        assertTrue(knife.contains("attacker.canSee(target)"));
        assertTrue(knife.contains("confirmedDeath(deadBefore, deadAfter)"));
        assertTrue(loadout.contains("component.isKnifeAvailable()"));
        assertTrue(loadout.contains("player.getInventory().selectedSlot"));
        assertTrue(filter.contains("canSeeKnifeEquipment"));
        assertTrue(packet.contains("targetEntityId"));
        assertEquals("wathe:item/template_knife", model.get("parent").getAsString());
        assertEquals("复仇刀", chinese.get("item.sparkwitch.vendetta_knife").getAsString());
    }

    @Test
    void swapperAttributionObservesConfirmedReplayWithoutProviderApi() throws Exception {
        String conversion = source("roles/special/wraith/conversion/WraithConversion.java");
        String attribution = source("roles/special/wraith/conversion/WraithSwapperFallAttribution.java");
        String recordMixin = source("mixin/GameRecordManagerItemUseMixin.java");
        String confirmedAfter = between(conversion,
                "public static void afterKill", "public static void beginRound");

        assertTrue(conversion.contains("WraithSwapperFallAttribution.peekResponsibleUuid(victim)"));
        assertTrue(confirmedAfter.contains("WraithSwapperFallAttribution.consumeResponsibleUuid(victim)"));
        assertTrue(attribution.contains("SWAPPER_SKILL_ID"));
        assertTrue(attribution.contains("\"swap\".equals(extra.getString(\"action\"))"));
        assertTrue(attribution.contains("extra.containsUuid(\"target1\")"));
        assertTrue(attribution.contains("extra.containsUuid(\"target2\")"));
        assertTrue(recordMixin.contains("at = @At(\"TAIL\")"));
        assertFalse(conversion.contains("NoellesSwapperFallAttributionBridge"));
        assertFalse(attribution.contains("org.agmas.noellesroles.api"));
        assertFalse(attribution.contains("getMethod("));
    }

    @Test
    void noellesCompatibilityIsConsumerOwnedAndExactPairOnly() throws Exception {
        String assassin = source("mixin/NoellesAssassinVendettaTargetMixin.java");
        String axe = source("mixin/NoellesThrowingAxeVendettaTargetMixin.java");
        String mixins = Files.readString(Path.of("src/main/resources/sparkwitch.mixins.json"));

        assertTrue(assassin.contains("lambda$registerPackets$6"));
        assertTrue(assassin.contains("lambda$registerPackets$37"));
        assertTrue(assassin.contains("ordinal = 1"));
        assertTrue(assassin.contains("isBoundKillerTargetingVendetta(context.player(), target)"));
        assertTrue(axe.contains("method = \"onEntityHit\""));
        assertTrue(axe.contains("isBoundKillerTargetingVendetta(player, target)"));
        assertTrue(mixins.contains("\"NoellesAssassinVendettaTargetMixin\""));
        assertTrue(mixins.contains("\"NoellesThrowingAxeVendettaTargetMixin\""));
    }

    @Test
    void deadParticipantTargetGatesStayShared() throws Exception {
        String interaction = source("roles/civilian/vendetta/VendettaInteractionService.java");
        assertTrue(interaction.contains("GameFunctions.isPlayerPlayingAndAlive(target)"));
        assertTrue(interaction.contains("isBoundKillerTargetingVendetta(actor, target)"));

        for (String relative : List.of(
                "entity/NinjaShurikenEntity.java",
                "item/ninja/NinjaKnifeItem.java",
                "item/firepoker/FirePokerCombatService.java",
                "item/ceremonialsword/CeremonialSwordCombatService.java",
                "roles/civilian/apprentice/abilities/MightyForce/MightyForceCombatService.java",
                "roles/killer/blackraven/BlackRavenTargeting.java",
                "roles/killer/blackraven/FeatherBladeItem.java",
                "roles/killer/blackraven/FeatherBladeMeleeService.java",
                "roles/killer/hunter/DoubleBarrelShotgunItem.java",
                "roles/neutral/murderouswitch/MurderousWitchDeathRay/MurderousWitchDeathRayService.java"
        )) {
            assertTrue(source(relative).contains("isOrdinaryAliveOrBoundKillerTarget"), relative);
        }

        String packetGuard = source("roles/civilian/vendetta/VendettaTargetingPacketGuard.java");
        assertTrue(packetGuard.contains("payload instanceof KnifeStabPayload"));
        assertTrue(packetGuard.contains("payload instanceof GunShootPayload"));
        assertTrue(packetGuard.contains("!VendettaInteractionService.isExactPair(actor, target)"));
    }

    @Test
    void disconnectGraceOwnsItsCacheAndPersistsStaleEntities() throws Exception {
        String disconnect = source("roles/civilian/vendetta/VendettaDisconnectService.java");
        String terminal = source("mixin/VendettaTerminalKillMixin.java");
        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");

        assertTrue(disconnect.contains("ServerPlayConnectionEvents.JOIN.register"));
        assertTrue(disconnect.contains("ServerTickEvents.END_SERVER_TICK.register"));
        assertTrue(disconnect.contains("ServerLifecycleEvents.SERVER_STOPPING.register"));
        assertTrue(disconnect.contains("victim.networkHandler.isConnectionOpen()"));
        assertTrue(disconnect.contains("PENDING_ESCAPES.putIfAbsent"));
        assertTrue(disconnect.contains("VendettaRules.hasReconnectGraceExpired"));
        assertTrue(disconnect.contains("persistDisconnectedPlayer(player)"));
        assertTrue(disconnect.contains("@Mixin(PlayerManager.class)")
                || source("mixin/PlayerManagerVendettaAccessor.java").contains("@Invoker(\"savePlayerData\")"));
        assertTrue(terminal.contains("VendettaDisconnectService.shouldPauseOfflineBoundKillerEscape"));
        assertTrue(lifecycle.contains("VendettaDisconnectService.clearRoundState()"));
        assertTrue(lifecycle.contains("VendettaReplayService.clearRoundState()"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", " ");
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, start);
        assertTrue(endIndex > startIndex, end);
        return source.substring(startIndex, endIndex);
    }
}
