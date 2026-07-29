package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithGroundPickupSourceTest {
    private static final Path ROOT = Path.of("src");

    @Test
    void itemCollisionGateUsesOnlyServerSideActiveWraithState() throws Exception {
        String mixin = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithGroundPickupMixin.java"));

        assertTrue(mixin.contains("@Mixin(value = ItemEntity.class, priority = 1500)"));
        assertTrue(mixin.contains(
                "method = \"onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V\""));
        assertTrue(mixin.contains("@At(\"HEAD\")"));
        assertTrue(mixin.contains("cancellable = true"));
        assertTrue(mixin.contains("!player.getWorld().isClient"));
        assertTrue(mixin.contains("WraithStateService.isActive(player)"));
        assertTrue(mixin.contains("WraithParticipationRules.mayPickUpGroundItems("));
        assertTrue(mixin.contains("ci.cancel()"));
        assertFalse(mixin.contains("getRole("));
        assertFalse(mixin.contains("insertStack("));
        assertFalse(mixin.contains("ItemStack"));
    }

    @Test
    void commonMixinConfigRequiresTheGroundPickupGate() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(ROOT.resolve(
                "main/resources/sparkwitch.mixins.json"))).getAsJsonObject();

        assertTrue(config.get("required").getAsBoolean());
        assertTrue(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithGroundPickupMixin")));
    }
}
