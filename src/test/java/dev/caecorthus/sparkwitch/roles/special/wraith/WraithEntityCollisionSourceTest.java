package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithEntityCollisionSourceTest {
    private static final Path ROOT = Path.of("src");

    @Test
    void activeWraithPlayersLoseBothCollisionAndPushbackWithOtherPlayers() throws Exception {
        String entityMixin = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithEntityCollisionMixin.java"));
        String livingMixin = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithLivingEntityCollisionMixin.java"));
        String rules = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/roles/special/wraith/WraithCollisionRules.java"));

        assertTrue(entityMixin.contains("@Mixin(value = Entity.class, priority = 1100)"));
        assertTrue(entityMixin.contains("@WrapMethod(method = \"collidesWith\")"));
        assertTrue(entityMixin.contains("Operation<Boolean> original"));
        assertTrue(entityMixin.contains("WraithCollisionRules.shouldIgnorePlayerBodyCollision"));
        assertFalse(entityMixin.contains("pushAwayFrom"));
        assertTrue(entityMixin.contains("return false;"));
        assertTrue(entityMixin.contains("return original.call(other);"));
        assertFalse(entityMixin.contains("WraithStateService.isPromoted"));

        assertTrue(livingMixin.contains("@Mixin(value = LivingEntity.class, priority = 100)"));
        assertTrue(livingMixin.contains("isPushable"));
        assertTrue(livingMixin.contains("pushAway(Lnet/minecraft/entity/Entity;)V"));
        assertTrue(livingMixin.contains("WraithCollisionRules.isCollisionTransparent"));
        assertTrue(livingMixin.contains("WraithCollisionRules.shouldIgnorePlayerBodyCollision"));
        assertTrue(livingMixin.contains("cir.setReturnValue(false)"));
        assertTrue(livingMixin.contains("ci.cancel()"));
        assertFalse(livingMixin.contains("WraithStateService.isPromoted"));

        assertTrue(rules.contains("Identifier.of(\"noellesroles\", \"no_collision\")"));
        assertTrue(rules.contains("WraithStateService.isActive(player)"));
        assertTrue(rules.contains("living.hasStatusEffect"));
        assertTrue(rules.contains("instanceof PlayerEntity"));
        assertFalse(rules.contains("DataTracker"));
    }

    @Test
    void mixinConfigRegistersTheSharedEntityCollisionHook() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(ROOT.resolve(
                "main/resources/sparkwitch.mixins.json"))).getAsJsonObject();

        assertTrue(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithEntityCollisionMixin")));
        assertTrue(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithLivingEntityCollisionMixin")));
    }
}
