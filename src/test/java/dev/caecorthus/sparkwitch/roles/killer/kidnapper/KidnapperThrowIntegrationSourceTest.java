package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.net.ThrowKidnapperBodyC2SPacket;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KidnapperThrowIntegrationSourceTest {
    private static final Path ROOT = Path.of("src/main/java/dev/caecorthus/sparkwitch");
    private static final Path CLIENT_ROOT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");

    @Test
    void keepsCarryQueriesSharedAndTheServerAuthoritativeThrowSeparate() throws IOException {
        String carryState = read("roles/killer/kidnapper/KidnapperCarryState.java");
        String dragging = read("roles/killer/kidnapper/KidnapperDragService.java");
        String throwing = read("roles/killer/kidnapper/KidnapperThrowService.java");
        String lifecycle = read("roles/killer/kidnapper/KidnapperDragLifecycle.java");

        assertTrue(carryState.contains("passenger instanceof PlayerBodyEntity body"));
        assertTrue(dragging.contains("KidnapperCarryState.findCarriedBody(player)"));
        assertFalse(dragging.contains("for (Entity passenger"));

        assertTrue(throwing.contains("GameWorldComponent.KEY.get(player.getWorld()).getRole(player)"));
        assertTrue(throwing.contains("KidnapperRules.isKidnapper(role)"));
        assertTrue(throwing.contains("GameFunctions.isPlayerPlayingAndAlive(player)"));
        assertTrue(throwing.contains("player.isSneaking()"));
        assertTrue(throwing.contains("KidnapperCarryState.findCarriedBody(player)"));
        assertTrue(throwing.contains("KidnapperDragService.release(player);"));
        assertTrue(throwing.contains("body.setVelocity(velocity);"));
        assertTrue(throwing.contains("body.setYaw(throwYaw);"));
        assertTrue(throwing.contains("body.setBodyYaw(throwYaw);"));
        assertTrue(throwing.contains("body.setHeadYaw(throwYaw);"));
        assertTrue(throwing.contains("body.prevYaw = throwYaw;"));
        assertTrue(throwing.contains("body.prevBodyYaw = throwYaw;"));
        assertTrue(throwing.contains("body.prevHeadYaw = throwYaw;"));
        assertTrue(throwing.contains("body.velocityModified = true;"));
        assertTrue(throwing.indexOf("KidnapperDragService.release(player);")
                < throwing.indexOf("body.setYaw(throwYaw);"));
        assertTrue(throwing.indexOf("body.prevHeadYaw = throwYaw;")
                < throwing.indexOf("body.setVelocity(velocity);"));

        assertTrue(dragging.contains("release(player);\n            return WitchSkillUseResult.success(0);"));
        assertFalse(dragging.contains("setVelocity("));
        assertFalse(dragging.contains("velocityModified"));

        assertFalse(lifecycle.contains("KidnapperThrowService"));
        assertFalse(lifecycle.contains("UseItemCallback"));
        assertFalse(lifecycle.contains("UseBlockCallback"));
        assertFalse(lifecycle.contains("UseEntityCallback"));
    }

    @Test
    void registersTheEmptyThrowPacketAndDelegatesItsReceiver() throws IOException {
        String packet = read("net/ThrowKidnapperBodyC2SPacket.java");
        String packets = read("net/SparkWitchPackets.java");

        assertTrue(packet.contains("public record ThrowKidnapperBodyC2SPacket() implements CustomPayload"));
        assertTrue(packet.contains("SparkWitch.id(\"throw_kidnapper_body\")"));
        assertTrue(packet.contains("PacketCodec.of(ThrowKidnapperBodyC2SPacket::write,"));
        assertTrue(packet.contains("public void write(PacketByteBuf buf) {\n    }"));
        assertTrue(packet.contains("return new ThrowKidnapperBodyC2SPacket();"));
        assertFalse(packet.contains("Hand"));

        assertTrue(packets.contains("PayloadTypeRegistry.playC2S().register(\n"
                + "                ThrowKidnapperBodyC2SPacket.ID,\n"
                + "                ThrowKidnapperBodyC2SPacket.CODEC\n"
                + "        );"));
        assertTrue(packets.contains("ServerPlayNetworking.registerGlobalReceiver(ThrowKidnapperBodyC2SPacket.ID,\n"
                + "                (payload, context) -> KidnapperThrowService.throwCarriedBody(context.player()));"));
    }

    @Test
    void blocksTheThrowPacketThroughTheSharedGrandWitchFearPolicy() {
        assertTrue(GrandWitchFearService.isBlockedRoleSkillPayload(
                ThrowKidnapperBodyC2SPacket.PAYLOAD_ID
        ));
    }

    @Test
    void ownsTheRawUseGestureOncePerHeldPressBeforeVanillaItemUse() throws IOException {
        String hook = readClient("hooks/KidnapperThrowClientHooks.java");
        String mixin = readClient("mixin/kidnapper/KidnapperThrowUseMixin.java");
        String client = readClient("SparkWitchClient.java");
        String clientMixins = readText(Path.of("src/client/resources/sparkwitch.client.mixins.json"));

        assertTrue(hook.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(hook.contains("ClientPlayNetworking.canSend(ThrowKidnapperBodyC2SPacket.ID)"));
        assertTrue(hook.contains("KidnapperRules.isKidnapper(role)"));
        assertTrue(hook.contains("GameFunctions.isPlayerPlayingAndAlive(player)"));
        assertTrue(hook.contains("player.isSneaking()"));
        assertTrue(hook.contains("KidnapperCarryState.findCarriedBody(player) != null"));
        assertTrue(hook.contains("useHeld = true;\n        ClientPlayNetworking.send(new ThrowKidnapperBodyC2SPacket());"));
        assertTrue(hook.contains("!client.options.useKey.isPressed()"));
        assertTrue(hook.contains("public static void reset() {\n        useHeld = false;\n    }"));
        assertEquals(1, occurrences(hook, "ClientPlayNetworking.send(new ThrowKidnapperBodyC2SPacket())"));
        assertFalse(hook.contains("Hand.MAIN_HAND"));
        assertFalse(hook.contains("getStackInHand"));
        assertFalse(hook.contains("crosshairTarget"));

        assertTrue(mixin.contains("@Mixin(MinecraftClient.class)"));
        assertTrue(mixin.contains("@Inject(method = \"doItemUse()V\", at = @At(\"HEAD\"), cancellable = true)"));
        assertTrue(mixin.contains("KidnapperThrowClientHooks.tryThrow"));
        assertTrue(mixin.contains("ci.cancel();"));
        assertTrue(clientMixins.contains("kidnapper.KidnapperThrowUseMixin"));

        int confirmationGuard = hook.indexOf("if (!SparkWitchServerConnection.isConfirmedServer())");
        int confirmationReset = hook.indexOf("useHeld = false;", confirmationGuard);
        int confirmationReturn = hook.indexOf("return false;", confirmationReset);
        int heldClaim = hook.indexOf("if (useHeld) {\n            return true;\n        }", confirmationReturn);
        int freshPlayerState = hook.indexOf("ClientPlayerEntity player = client.player;", heldClaim);
        int freshEligibility = hook.indexOf("!isEligible(player)", freshPlayerState);
        assertTrue(confirmationGuard >= 0);
        assertTrue(confirmationGuard < confirmationReset);
        assertTrue(confirmationReset < confirmationReturn);
        assertTrue(confirmationReturn < heldClaim);
        assertTrue(heldClaim < freshPlayerState);
        assertTrue(freshPlayerState < freshEligibility);

        int clientTickLoop = client.indexOf("ClientTickEvents.END_CLIENT_TICK.register");
        int confirmedServerGate = client.indexOf(
                "if (!SparkWitchServerConnection.isConfirmedServer())",
                clientTickLoop
        );
        int unconfirmedReset = client.indexOf("KidnapperThrowClientHooks.reset();", confirmedServerGate);
        int unconfirmedReturn = client.indexOf("return;", unconfirmedReset);
        int confirmedTick = client.indexOf("KidnapperThrowClientHooks.tick(client);", unconfirmedReturn);
        assertTrue(clientTickLoop >= 0);
        assertTrue(clientTickLoop < confirmedServerGate);
        assertTrue(confirmedServerGate < unconfirmedReset);
        assertTrue(unconfirmedReset < unconfirmedReturn);
        assertTrue(unconfirmedReturn < confirmedTick);
        assertEquals(1, occurrences(client, "KidnapperThrowClientHooks.tick(client);"));
        assertEquals(2, occurrences(client, "KidnapperThrowClientHooks.reset();"));
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static String read(String relativePath) throws IOException {
        return readText(ROOT.resolve(relativePath));
    }

    private static String readClient(String relativePath) throws IOException {
        return readText(CLIENT_ROOT.resolve(relativePath));
    }

    private static String readText(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
    }
}
