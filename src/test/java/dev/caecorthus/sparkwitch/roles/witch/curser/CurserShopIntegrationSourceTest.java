package dev.caecorthus.sparkwitch.roles.witch.curser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurserShopIntegrationSourceTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void activePromotedCurserGetsAnExclusiveOneLockpickShop() throws IOException {
        String shop = source("roles/witch/curser/CurserShopService.java");

        assertTrue(shop.contains("CurserFeatureService.isActivePromotedCurser(player)"));
        assertTrue(shop.contains("context.clearEntries()"));
        int lockpick = shop.indexOf("\"lockpick\"");
        assertTrue(lockpick >= 0);
        String entry = shop.substring(lockpick, shop.indexOf(".build()", lockpick));
        assertTrue(entry.contains("WatheItems.LOCKPICK.getDefaultStack()"));
        assertTrue(entry.contains("LOCKPICK_PRICE"));
        assertTrue(entry.contains(".stock(1)"));
    }

    @Test
    void promotionSeedsStockWithoutResettingWatheShopState() throws IOException {
        String shop = source("roles/witch/curser/CurserShopService.java");
        assertTrue(shop.contains("sparkwitch$initializePromotionLockpickStock()"));
        assertFalse(shop.contains("shop.initializeShop("));

        String feature = source("roles/witch/curser/CurserFeatureService.java");
        assertTrue(feature.contains("CurserShopService.register()"));
        assertTrue(feature.contains("CurserShopService.initializePromotionStock(player)"));

        String lifecycle = source("roles/special/wraith/runtime/WraithLifecycle.java");
        int transition = lifecycle.indexOf("transitionRole(player, role)");
        int initialization = lifecycle.indexOf("CurserFeatureService.initializeForPromotion(player)");
        assertTrue(transition >= 0 && initialization > transition);
    }

    @Test
    void deadRecordedCurserCanPurchaseWithoutChangingExistingGates() throws IOException {
        String mixin = source("mixin/WraithPlayerShopComponentMixin.java");
        int wind = mixin.indexOf("WindSpiritRules.canPassShopAliveGate");
        int saboteur = mixin.indexOf("SaboteurRules.canPassShopAliveGate");
        int curser = mixin.indexOf("CurserFeatureService.isActivePromotedCurser(player)");
        assertTrue(wind >= 0 && saboteur > wind && curser > saboteur);
        assertTrue(mixin.contains("return saboteurAllowed || CurserFeatureService.isActivePromotedCurser(player)"));
    }

    @Test
    void promotionAnnouncementDescribesThePrivateLockpick() throws IOException {
        String english = Files.readString(ROOT.resolve("src/main/resources/assets/sparkwitch/lang/en_us.json"));
        String chinese = Files.readString(ROOT.resolve("src/main/resources/assets/sparkwitch/lang/zh_cn.json"));
        assertTrue(english.contains("\"announcement.goals.curser\""));
        assertTrue(english.contains("only one lockpick for 50 coins"));
        assertTrue(chinese.contains("\"announcement.goals.curser\""));
        assertTrue(chinese.contains("只出售一个 50 金币的开锁器"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve("src/main/java/dev/caecorthus/sparkwitch").resolve(relativePath));
    }
}
