package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GrandWitchRecruitmentContractsTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    private static String source(String file) throws IOException {
        return Files.readString(MAIN.resolve(file));
    }

    @Test
    void usesAuthoritativeSharedRayAndTaskGateWithNoRoleCategoryWhitelist() throws IOException {
        String service = source("roles/witch/grandwitch/recruitment/GrandWitchRecruitmentService.java");
        assertTrue(service.contains("GrandWitchTargeting.findTarget(recruiter, targetId)"));
        assertTrue(service.contains("getGrandWitchCeremonialSwordTasks() < 2"));
        assertTrue(service.contains("GameFunctions.isPlayerPlayingAndAlive(target)"));
        assertTrue(service.contains("game.isRole(target, SparkWitchRoles.accomplice())"));
        assertFalse(service.contains("targetRole.isInnocent"));
        assertFalse(service.contains("targetRole.canUseKiller"));
        assertTrue(service.contains("WitchSkillUseResult.success(0,"));
    }

    @Test
    void economySnapshotPrecedesRoleExitAndNewShopInitializationRetainsBalance() throws IOException {
        String service = source("roles/witch/grandwitch/recruitment/GrandWitchRecruitmentService.java");
        assertTrue(service.indexOf("RecruitmentInventorySnapshot.capture(") < service.indexOf("inventory.detachScreenInputs()"));
        assertTrue(service.indexOf("inventory.detachScreenInputs()") < service.indexOf("exitOldRole(target)"));
        assertTrue(service.indexOf("target.getInventory().clear()") < service.indexOf("RoleAssigned.EVENT.invoker()"));
        assertTrue(service.indexOf("game.addRole(") < service.indexOf("RoleAssigned.EVENT.invoker()"));
        assertTrue(service.indexOf("RoleAssigned.EVENT.invoker()") < service.indexOf("shop.setBalance(inventory.finalBalance())"));
        assertTrue(service.contains("shop.initializeShop(ShopUtils.getShopEntriesForPlayer(target))"));
        assertFalse(service.contains("ResetPlayer.EVENT.invoker()"));
        assertFalse(service.contains("shop.reset()"));
        assertTrue(service.contains("round.finishConversion()"));
    }

    @Test
    void inventoryRetentionIsExactAndFoodDoesNotBypassRefund() throws IOException {
        String inventory = source("roles/witch/grandwitch/recruitment/RecruitmentInventorySnapshot.java");
        for (String item : new String[]{"WatheItems.KEY", "ModItems.MASTER_KEY", "ModItems.NEUTRAL_MASTER_KEY",
                "WatheItems.REVOLVER", "WatheItems.LETTER"}) {
            assertTrue(inventory.contains("stack.isOf(" + item + ")"));
        }
        assertFalse(inventory.contains("WatheItems.NOTE"));
        assertFalse(inventory.contains("DataComponentTypes.FOOD"));
        assertTrue(inventory.contains("getCursorStack().copy()"));
    }

    @Test
    void outputProvenanceIncludesCachedOffersAndDoesNotExecutePurchasesOrInspectTraitsInternals() throws IOException {
        String outputs = source("compat/recruitment/RecruitmentShopOutputs.java");
        assertTrue(outputs.contains("new WeakHashMap<>()"));
        assertTrue(outputs.contains("entry.getClass() == ShopEntry.class"));
        assertTrue(outputs.contains("entry.getActualStack() == delegate.getActualStack()"));
        assertTrue(outputs.contains("waiter_random_food_or_drink"));
        assertTrue(outputs.contains("entry.price(), output.getCount()"));
        assertFalse(outputs.contains(".onBuy("));
        assertFalse(outputs.contains("ShopPurchase.BEFORE"));
        assertFalse(outputs.contains("sparktraits.impl"));
        assertFalse(outputs.contains("sparktraits.component"));
        String bridge = source("compat/recruitment/SparkTraitsRecruitmentBridge.java");
        assertTrue(bridge.contains("dev.caecorthus.sparktraits.api.SparkTraitsApi"));
        assertTrue(bridge.contains("discountShopEntryForCharisma"));
        assertFalse(bridge.contains("sparktraits.impl"));
        assertFalse(bridge.contains("sparktraits.component"));
    }

    @Test
    void exitsTaotieBeforeResetAndPersistsOfflineReleaseAndCumulativeQuota() throws IOException {
        String cleanup = source("compat/recruitment/NoellesRecruitmentCleanup.java");
        assertTrue(cleanup.indexOf(".release(player.getPos())") < cleanup.indexOf("taotie.reset()"));
        assertTrue(cleanup.contains("cancelProjection(\"recruited\")"));
        assertTrue(cleanup.contains("queueRelease(uuid, player.getPos())"));
        assertTrue(cleanup.contains("ServerPlayConnectionEvents.JOIN.register"));
        assertFalse(cleanup.contains("InfectedPlayerComponent.KEY"));
        assertFalse(cleanup.contains("SilencedPlayerComponent.KEY"));
        String round = source("roles/witch/grandwitch/recruitment/GrandWitchRecruitmentRoundComponent.java");
        assertTrue(round.contains("tag.put(\"Recruited\", list)"));
        assertTrue(round.contains("tag.put(\"PendingReleases\", releases)"));
        assertFalse(round.contains("getAllAlivePlayers"));
        assertFalse(round.contains("recruited.remove"));
        String assignment = source("registry/WitchRoleAssignmentService.java");
        assertFalse(assignment.contains("assignRole(gameComponent, availablePlayers, accomplice"));
    }
}
