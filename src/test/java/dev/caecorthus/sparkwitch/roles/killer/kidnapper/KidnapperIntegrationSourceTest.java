package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KidnapperIntegrationSourceTest {
    private static final Path ROOT = Path.of("src/main/java/dev/caecorthus/sparkwitch");

    @Test
    void keepsTargetingDraggingLifecycleCompatibilityAndPositioningInSeparateClasses() throws IOException {
        String targeting = read("roles/killer/kidnapper/KidnapperTargeting.java");
        String dragging = read("roles/killer/kidnapper/KidnapperDragService.java");
        String lifecycle = read("roles/killer/kidnapper/KidnapperDragLifecycle.java");
        String falseBodies = read("roles/killer/kidnapper/KidnapperFalseBodyPolicy.java");
        String positioning = read("roles/killer/kidnapper/KidnapperPassengerPositioning.java");
        String bridge = read("compat/SparkTraitsBodyDragBridge.java");
        String mixin = read("mixin/KidnapperPassengerPositionMixin.java");

        assertTrue(targeting.contains("ProjectileUtil.getCollision"));
        assertTrue(targeting.contains("CanTargetBody.EVENT"));
        assertFalse(dragging.contains("ProjectileUtil"));

        assertTrue(dragging.contains("startRiding(player, true)"));
        assertFalse(dragging.contains("startRiding(player)"));
        assertTrue(dragging.contains("body.stopRiding()"));
        assertTrue(dragging.contains("ADD_MULTIPLIED_TOTAL"));
        assertTrue(dragging.contains("KidnapperRules.isKidnapper(context.role())"));
        assertFalse(dragging.contains("ServerTickEvents"));
        assertTrue(dragging.contains("PlayerBodyEntity draggedBody = findDraggedBody(player)"));
        assertTrue(dragging.contains("movementSpeed.hasModifier(KidnapperRules.SPEED_MODIFIER_ID)"));
        assertFalse(dragging.contains("hasDragMarker"));

        assertTrue(lifecycle.contains("ServerTickEvents.END_WORLD_TICK"));
        assertTrue(lifecycle.contains("ServerPlayConnectionEvents.DISCONNECT"));
        assertTrue(lifecycle.contains("RoleAssigned.EVENT"));
        assertTrue(lifecycle.contains("ResetPlayer.EVENT"));

        assertTrue(falseBodies.contains("SparkTraitsBodyDragBridge.canDragBody"));
        assertTrue(falseBodies.contains("getCameraEntity() == body"));
        assertFalse(falseBodies.contains("sparktraits.impl"));
        assertTrue(bridge.contains("dev.caecorthus.sparktraits.api.SparkTraitsApi"));
        assertFalse(bridge.contains("dev.caecorthus.sparktraits.impl"));

        assertTrue(mixin.contains("KidnapperPassengerPositioning.behind"));
        assertFalse(mixin.contains("hasDragMarker"));
        assertFalse(mixin.contains("startRiding"));
        assertTrue(positioning.contains("KidnapperRules.FOLLOW_DISTANCE"));
    }

    @Test
    void syncsPassengerChangesBackToTheCarrierClient() throws IOException {
        String dragging = read("roles/killer/kidnapper/KidnapperDragService.java");
        Path passengerSyncPath = ROOT.resolve("roles/killer/kidnapper/KidnapperPassengerSync.java");

        assertTrue(Files.exists(passengerSyncPath));
        String passengerSync = Files.readString(passengerSyncPath);
        assertTrue(passengerSync.contains("new EntityPassengersSetS2CPacket(carrier)"));
        assertTrue(passengerSync.contains("carrier.networkHandler.sendPacket"));
        assertEquals(2, occurrences(dragging, "KidnapperPassengerSync.send(player);"));
    }

    @Test
    void registersOneStandardKillerSkillWithoutChangingSharedStateSchemas() throws IOException {
        String registry = read("registry/SparkWitchRoleRegistry.java");
        String skills = read("skill/SparkWitchBuiltInSkills.java");
        String events = read("impl/SparkWitchEvents.java");
        String mixins = Files.readString(Path.of("src/main/resources/sparkwitch.mixins.json"));

        int start = registry.indexOf("kidnapper = SparkFactionApi.registerRole");
        int end = registry.indexOf(".build());", start);
        assertTrue(start >= 0 && end > start);
        String registration = registry.substring(start, end + ".build());".length());
        assertTrue(registration.contains("builder(KIDNAPPER_ID, FactionIds.KILLER)"));
        assertTrue(registration.contains(".nativeWatheFaction(Faction.KILLER)"));
        assertTrue(registration.contains(".maxSprintTime(-1)"));
        assertFalse(registration.contains("minPlayers"));

        assertTrue(skills.contains("KidnapperRules.DRAG_BODY_SKILL_ID"));
        assertTrue(skills.contains("KidnapperDragService::use"));
        assertTrue(events.contains("KidnapperDragLifecycle.register()"));
        assertTrue(mixins.contains("KidnapperPassengerPositionMixin"));

        assertFalse(draggingSchemaTouched("component/WitchPlayerNbtCodec.java"));
        assertFalse(draggingSchemaTouched("component/WitchPlayerSyncCodec.java"));
    }

    @Test
    void localizesTheExactRejectedBodyAndTargetFeedback() throws IOException {
        String chinese = Files.readString(Path.of("src/main/resources/assets/sparkwitch/lang/zh_cn.json"));

        assertTrue(chinese.contains("\"announcement.role.kidnapper\": \"绑架者\""));
        assertTrue(chinese.contains("\"message.sparkwitch.kidnapper.no_target\": \"请对准两格内的一具尸体。\""));
        assertTrue(chinese.contains("\"message.sparkwitch.kidnapper.false_body\": \"TA 真的死了吗...？\""));
    }

    private static boolean draggingSchemaTouched(String relativePath) throws IOException {
        return read(relativePath).toLowerCase().contains("kidnapper");
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
