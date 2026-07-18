package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BlackRavenServerContractSourceTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void delayedKillSpawnsNormalCorpseAndDoesNotRetry() throws IOException {
        String source = source("roles/killer/blackraven/BlackRavenMarkRuntime.java");
        int clear = source.indexOf("component.clear();");
        int kill = source.indexOf("GameFunctions.killPlayer(victim, true, killer, GameConstants.DeathReasons.KNIFE);");
        assertTrue(clear >= 0 && kill > clear);
        assertFalse(source.contains("killPlayer(victim, false"));
    }

    @Test
    void perceptionScanExcludesSpectatorAndCreativeTargets() throws IOException {
        String source = source("roles/killer/blackraven/BlackRavenPerceptionService.java");

        assertTrue(source.contains("GameFunctions.isPlayerSpectatingOrCreative(target)"));
    }

    @Test
    void perceptionActivationReconcilesCurrentMatchBeforeBeginningWindow() throws IOException {
        String source = source("roles/killer/blackraven/BlackRavenPerceptionService.java");
        int activeGate = source.indexOf("if (component.isActive())");
        int matchGate = source.indexOf("if (!matchId.equals(component.matchId()))", activeGate);
        int bind = source.indexOf("component.bindMatch(matchId);", matchGate);
        int begin = source.indexOf("component.begin(BlackRavenRules.PERCEPTION_ACTIVE_TICKS", bind);

        assertTrue(activeGate >= 0);
        assertTrue(matchGate > activeGate);
        assertTrue(bind > matchGate);
        assertTrue(begin > bind);
    }

    @Test
    void inactivePerceptionReconcilesMatchRoleCooldownAndBoundLoadout() throws IOException {
        String source = source("roles/killer/blackraven/BlackRavenPerceptionService.java");
        int inactiveGate = source.indexOf("if (!component.isActive())");
        int preserveDecision = source.indexOf(
                "BlackRavenRules.shouldPreservePerceptionRoundState",
                inactiveGate
        );
        int clearStateAndDeferredCooldown = source.indexOf("clearForRoleLossOrDeath(player)", preserveDecision);
        int removeBoundLoadout = source.indexOf("BlackRavenLoadoutService.removeOwnedItems(player)", preserveDecision);
        int preserveSameMatchLedger = source.indexOf("BlackRavenLoadoutService.restoreLedgerIfNeeded(player)", inactiveGate);

        assertTrue(inactiveGate >= 0);
        assertTrue(preserveDecision > inactiveGate);
        assertTrue(clearStateAndDeferredCooldown > preserveDecision);
        assertTrue(removeBoundLoadout > preserveDecision);
        assertTrue(preserveSameMatchLedger > inactiveGate);
    }

    @Test
    void separateComponentsStayOutOfSharedWitchSchema() throws IOException {
        String component = source("component/WitchPlayerComponent.java");
        String sync = source("component/WitchPlayerSyncCodec.java");
        String nbt = source("component/WitchPlayerNbtCodec.java");
        assertFalse(component.contains("blackRavenPerception"));
        assertFalse(component.contains("blackRavenMark"));
        assertFalse(sync.contains("BlackRaven"));
        assertFalse(nbt.contains("BlackRaven"));
        assertTrue(component.contains("WitchSkillRegistry.activeWindowTicks(activeSkillId, player)"));
    }

    @Test
    void ledgerPacketIsEmptyAndOwnerSnapshotsRemainInComponentSync() throws IOException {
        String packet = source("net/OpenBlackRavenLedgerS2CPacket.java");
        String component = source("roles/killer/blackraven/BlackRavenPerceptionPlayerComponent.java");
        assertFalse(packet.contains("BlackRavenIdentitySnapshot"));
        assertFalse(packet.contains("UUID"));
        assertTrue(component.contains("recipient == player"));
        assertTrue(component.contains("state.completedSnapshots()"));
        assertFalse(component.substring(
                component.indexOf("writeSyncPacket"),
                component.indexOf("applySyncPacket")
        ).contains("state.progress()"));
    }

    @Test
    void ledgerOnlyVisibilityFallbackRunsAfterNoellesFiltering() throws IOException {
        String mixin = source("mixin/NoellesHiddenEquipmentBlackRavenLedgerMixin.java");
        String main = source("SparkWitch.java");
        assertTrue(mixin.contains("@At(\"RETURN\")"));
        assertTrue(mixin.contains("filteredByNoelles == null ? original : filteredByNoelles"));
        assertTrue(main.contains("NoellesHiddenEquipment.register(SparkWitchItems.blackRavenLedger())"));
        assertFalse(main.contains("NoellesHiddenEquipment.register(SparkWitchItems.featherBlade())"));
    }

    @Test
    void loadoutIsExcludedBeforeDeathDropAndContainerTransfer() throws IOException {
        String deathMixin = source("mixin/GameFunctionsBlackRavenDropMixin.java");
        String inventory = source("roles/killer/blackraven/BlackRavenInventoryRules.java");
        assertTrue(deathMixin.contains("stack.isOf(SparkWitchItems.featherBlade())"));
        assertTrue(deathMixin.contains("stack.isOf(SparkWitchItems.blackRavenLedger())"));
        assertTrue(inventory.contains("player.currentScreenHandler != player.playerScreenHandler"));
        assertTrue(inventory.contains("SlotActionType.QUICK_MOVE"));
    }

    @Test
    void featherBladeUsesRoleOwnedWatheMeleeAdmission() throws IOException {
        String items = source("SparkWitchItems.java");
        String suppression = items.substring(items.indexOf("AttackEntityCallback.EVENT.register"));
        String service = source("roles/killer/blackraven/FeatherBladeMeleeService.java");

        assertFalse(suppression.contains("heldItem == featherBlade"));
        assertTrue(service.contains("AllowPlayerPunching.EVENT.register"));
        assertTrue(service.contains("attacker.getMainHandStack().isOf(SparkWitchItems.featherBlade())"));
        assertTrue(source("roles/killer/blackraven/BlackRavenFeatureService.java")
                .contains("FeatherBladeMeleeService.register()"));
    }

    @Test
    void shopAddsSingleStockFiftyCoinLockpickImmediatelyBeforeCrowbarAndPreservesBlackout() throws IOException {
        String rules = source("roles/killer/blackraven/BlackRavenRules.java");
        String shop = source("roles/killer/blackraven/BlackRavenShopService.java");
        int captureBlackout = shop.indexOf("\"blackout\".equals(entry.id())");
        int clearEntries = shop.indexOf("context.clearEntries()");
        int lockpick = shop.indexOf("\"lockpick\"");
        int crowbar = shop.indexOf("\"crowbar\"");
        int restoreBlackout = shop.indexOf("context.addEntry(blackout)");

        assertTrue(rules.contains("public static final int LOCKPICK_PRICE = 50;"));
        assertTrue(captureBlackout >= 0 && clearEntries > captureBlackout);
        assertTrue(lockpick > clearEntries && crowbar > lockpick);
        String lockpickEntry = shop.substring(lockpick, crowbar);
        assertTrue(lockpickEntry.contains("WatheItems.LOCKPICK.getDefaultStack()"));
        assertTrue(lockpickEntry.contains("BlackRavenRules.LOCKPICK_PRICE"));
        assertTrue(lockpickEntry.contains("ShopEntry.Type.TOOL"));
        assertTrue(lockpickEntry.contains(".stock(1).build()"));
        assertTrue(restoreBlackout > crowbar);
    }

    @Test
    void assignmentStartsFeatherBladeOnItsOpeningCooldown() throws IOException {
        String loadout = source("roles/killer/blackraven/BlackRavenLoadoutService.java");
        int exactRoleGate = loadout.indexOf("BlackRavenRules.isBlackRaven(role)");
        int grantBlade = loadout.indexOf(
                "player.giveItemStack(new ItemStack(SparkWitchItems.featherBlade()))"
        );
        int startCooldown = loadout.indexOf(
                "player.getItemCooldownManager().set("
                        + "SparkWitchItems.featherBlade(), BlackRavenRules.FEATHER_COOLDOWN_TICKS)"
        );

        assertTrue(exactRoleGate >= 0 && grantBlade > exactRoleGate);
        assertTrue(startCooldown > grantBlade);
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve("src/main/java/dev/caecorthus/sparkwitch").resolve(relativePath));
    }
}
