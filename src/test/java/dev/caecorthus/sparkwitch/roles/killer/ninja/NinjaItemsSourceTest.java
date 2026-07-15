package dev.caecorthus.sparkwitch.roles.killer.ninja;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinjaItemsSourceTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");
    private static final Path CLIENT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");

    @Test
    void registersBothWeaponsAndTheShurikenProjectile() throws IOException {
        String items = readMain("SparkWitchItems.java");
        String entities = readMain("SparkWitchEntities.java");
        String initializer = readMain("SparkWitch.java");
        String client = Files.readString(CLIENT.resolve("SparkWitchClient.java"));

        assertTrue(items.contains("NINJA_KNIFE_ID = SparkWitch.id(\"ninja_knife\")"));
        assertTrue(items.contains("NINJA_SHURIKEN_ID = SparkWitch.id(\"ninja_shuriken\")"));
        assertTrue(items.contains("Item ninjaKnife()"));
        assertTrue(items.contains("Item ninjaShuriken()"));
        assertTrue(items.contains("AttackEntityCallback.EVENT.register"));
        assertTrue(items.contains("heldItem == ninjaKnife || heldItem == ninjaShuriken"));
        assertTrue(items.contains("? ActionResult.FAIL"));
        assertTrue(entities.contains("NINJA_SHURIKEN_ID = SparkWitch.id(\"ninja_shuriken\")"));
        assertTrue(entities.contains(".maxTrackingRange(4)"));
        assertTrue(initializer.contains("SparkWitchEntities.register()"));
        assertTrue(client.contains("EntityRendererRegistry.register("));
        assertTrue(client.contains("SparkWitchEntities.ninjaShuriken()"));
    }

    @Test
    void knifeUsesServerAuthorityFourBlockTargetingAndWatheKillPipeline() throws IOException {
        String knife = readMain("item/ninja/NinjaKnifeItem.java");

        assertTrue(knife.contains("ProjectileUtil.getCollision"));
        assertTrue(knife.contains("MAX_RANGE = 4.0"));
        assertTrue(knife.contains("GameFunctions.isPlayerPlayingAndAlive(attacker)"));
        assertTrue(knife.contains("GameFunctions.isPlayerPlayingAndAlive(victim)"));
        assertTrue(knife.contains("GameFunctions.killPlayer"));
        assertTrue(knife.contains("SparkWitchDeathReasons.NINJA_KNIFE_KILL"));
        assertTrue(knife.contains("KNIFE_COOLDOWN_TICKS = 30 * 20"));
        assertTrue(knife.contains("return false;") && knife.contains("postHit"));
        assertFalse(knife.contains("NinjaRules"));
        assertFalse(knife.contains("getRole("));
    }

    @Test
    void shurikenRequiresFourTicksAndSpawnsOnlyOnTheServer() throws IOException {
        String item = readMain("item/ninja/NinjaShurikenItem.java");

        assertTrue(item.contains("MIN_CHARGE_TICKS = 4"));
        assertTrue(item.contains("SHURIKEN_COOLDOWN_TICKS = 20"));
        assertTrue(item.contains("PROJECTILE_SPEED = 1.3F"));
        assertTrue(item.contains("PROJECTILE_INACCURACY = 1.0F"));
        assertTrue(item.contains("world.isClient()"));
        assertTrue(item.contains("GameFunctions.isPlayerPlayingAndAlive(thrower)"));
        assertTrue(item.contains("world.spawnEntity"));
        assertFalse(item.contains("ClientPlayNetworking"));
        assertFalse(item.contains("NinjaRules"));
        assertFalse(item.contains("getRole("));
    }

    @Test
    void projectileIsGravityFreeShortLivedAndUsesTheShurikenStackForRendering() throws IOException {
        String projectile = readMain("entity/NinjaShurikenEntity.java");

        assertTrue(projectile.contains("extends PersistentProjectileEntity"));
        assertTrue(projectile.contains("implements FlyingItemEntity"));
        assertTrue(projectile.contains("MAX_LIFETIME_TICKS = 8 * 20"));
        assertTrue(projectile.contains("protected double getGravity()"));
        assertTrue(projectile.contains("return 0.0"));
        assertTrue(projectile.contains("ParticleTypes.CRIT"));
        assertTrue(projectile.contains("SoundEvents.BLOCK_CHAIN_HIT"));
        assertTrue(projectile.contains("GameFunctions.isPlayerPlayingAndAlive(victim)"));
        assertTrue(projectile.contains("GameFunctions.killPlayer"));
        assertTrue(projectile.contains("SparkWitchDeathReasons.NINJA_SHURIKEN_KILL"));
        assertTrue(projectile.contains("SparkWitchItems.ninjaShuriken().getDefaultStack()"));
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath));
    }
}
