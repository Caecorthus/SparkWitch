package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurServerIntegrationSourceTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void shopKeepsWatheBlackoutAndSeedsOnlyOnePromotionLockpick() throws IOException {
        String shop = saboteurSource("SaboteurShopService.java");
        int capture = shop.indexOf("\"blackout\".equals(entry.id())");
        int clear = shop.indexOf("context.clearEntries()");
        int lockpick = shop.indexOf("\"lockpick\"", clear);
        int restore = shop.indexOf("context.addEntry(blackoutEntry)");

        assertTrue(capture >= 0 && clear > capture && lockpick > clear && restore > lockpick);
        String lockpickEntry = shop.substring(lockpick, shop.indexOf(".build()", lockpick));
        assertTrue(lockpickEntry.contains("WatheItems.LOCKPICK.getDefaultStack()"));
        assertTrue(lockpickEntry.contains("SaboteurRules.LOCKPICK_PRICE"));
        assertTrue(lockpickEntry.contains(".stock(1)"));
        assertTrue(shop.contains("SaboteurShopStockAccess"));
        assertFalse(shop.contains("shop.initializeShop("));
        assertFalse(shop.contains("PlayerShopComponent::useBlackout"));

        String stockMixin = source("mixin/saboteur/PlayerShopComponentSaboteurMixin.java");
        assertTrue(stockMixin.contains("stock.put(\"lockpick\", 1)"));
        assertTrue(stockMixin.contains("maxStockCache.put(\"lockpick\", 1)"));
        assertFalse(stockMixin.contains("cooldowns.put"));
    }

    @Test
    void promotionInitializesDedicatedStateAfterRoleTransition() throws IOException {
        String promotion = source("roles/special/wraith/runtime/WraithLifecycle.java");
        int transition = promotion.indexOf("transitionRole(player, role)");
        int initialization = promotion.indexOf("SaboteurFeatureService.initializePromotion(player)");
        assertTrue(transition >= 0 && initialization > transition);

        String feature = saboteurSource("SaboteurFeatureService.java");
        assertTrue(feature.contains("SaboteurPlayerComponent.KEY.get(player)"));
        assertTrue(feature.contains("SaboteurRules.INITIAL_COOLDOWN_TICKS"));
        assertTrue(feature.contains("SaboteurShopService.initializePromotionStock(player)"));

        String cleanup = source("roles/special/wraith/runtime/WraithLifecycle.java");
        assertTrue(cleanup.contains("SaboteurPlayerComponent.KEY.get(player).clear()"));
    }

    @Test
    void dedicatedComponentAndPacketStayOutsideWitchSkillRuntime() throws IOException {
        String component = saboteurSource("SaboteurPlayerComponent.java");
        assertTrue(component.contains("SparkWitch.id(\"saboteur_player\")"));
        assertTrue(component.contains("recipient == player"));
        assertTrue(component.contains("ClientTickingComponent"));
        assertTrue(component.contains("public int getCooldownTicks()"));

        String packet = saboteurSource("UseSaboteurSkillC2SPacket.java");
        assertTrue(packet.contains("record UseSaboteurSkillC2SPacket()"));
        assertTrue(packet.contains("SparkWitch.id(\"use_saboteur_skill\")"));
        assertTrue(packet.contains("public static final PacketCodec"));

        String ability = saboteurSource("SaboteurAbilityService.java");
        assertTrue(ability.contains("SaboteurRules.isActivePromotedSaboteur(player)"));
        assertTrue(ability.contains("SaboteurLightOutageService.activate(player)"));
        assertTrue(ability.contains("SaboteurRules.COOLDOWN_TICKS"));

        assertFalse(source("skill/SparkWitchBuiltInSkills.java").contains("Saboteur"));
        assertFalse(source("skill/WitchSkillAssignmentService.java").contains("Saboteur"));
        assertFalse(source("skill/WitchSkillUseService.java").contains("Saboteur"));
    }

    @Test
    void componentPacketFearAndMixinsAreRegistered() throws IOException {
        String components = source("component/SparkWitchComponents.java");
        assertTrue(components.contains("PlayerEntity.class, SaboteurPlayerComponent.KEY"));
        assertTrue(Files.readString(ROOT.resolve("src/main/resources/fabric.mod.json"))
                .contains("\"sparkwitch:saboteur_player\""));
        assertTrue(source("net/SparkWitchPackets.java").contains("SaboteurNetworking.register()"));
        assertTrue(source("roles/witch/grandwitch/GrandWitchFearService.java")
                .contains("UseSaboteurSkillC2SPacket.PAYLOAD_ID"));

        String mixins = Files.readString(ROOT.resolve("src/main/resources/sparkwitch.mixins.json"));
        assertTrue(mixins.contains("\"saboteur.PlayerShopComponentSaboteurMixin\""));
        assertTrue(mixins.contains("\"saboteur.WorldBlackoutDetailsSaboteurMixin\""));
    }

    @Test
    void taskRewardChecksPromotionAtEventTimeAndShopGatePreservesWindSpirit() throws IOException {
        String feature = saboteurSource("SaboteurFeatureService.java");
        assertTrue(feature.contains("TaskComplete.EVENT.register"));
        assertTrue(feature.contains("SaboteurRules.isActivePromotedSaboteur(player)"));
        assertTrue(feature.contains("addToBalance(SaboteurRules.TASK_REWARD)"));

        String mixin = source("mixin/WraithPlayerShopComponentMixin.java");
        int wind = mixin.indexOf("WindSpiritRules.canPassShopAliveGate");
        int saboteur = mixin.indexOf("SaboteurRules.canPassShopAliveGate");
        assertTrue(wind >= 0 && saboteur > wind);
        assertTrue(mixin.contains("SaboteurRules.isActivePromotedSaboteur(player)"));
    }

    private static String saboteurSource(String filename) throws IOException {
        return source("roles/killer/saboteur/" + filename);
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve("src/main/java/dev/caecorthus/sparkwitch").resolve(relativePath));
    }
}
