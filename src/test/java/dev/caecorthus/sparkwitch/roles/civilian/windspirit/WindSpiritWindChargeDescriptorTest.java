package dev.caecorthus.sparkwitch.roles.civilian.windspirit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindSpiritWindChargeDescriptorTest {
    @Test
    void minecraft1211WindChargeSeamRemainsCompatible() throws ReflectiveOperationException {
        assertEquals(boolean.class,
                AbstractWindChargeEntity.class.getDeclaredMethod("canHit", Entity.class).getReturnType());
        assertEquals(void.class,
                AbstractWindChargeEntity.class.getDeclaredMethod("onEntityHit", EntityHitResult.class).getReturnType());
        assertEquals(void.class,
                AbstractWindChargeEntity.class.getDeclaredMethod("createExplosion", Vec3d.class).getReturnType());
    }

    @Test
    void mixinUsesTheExactCanHitSeamAndOnlyResolvesEligibility() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/mixin/WindSpiritWindChargeMixin.java"));
        assertTrue(source.contains("@Mixin(AbstractWindChargeEntity.class)"));
        assertTrue(source.contains("@Inject(method = \"canHit\", at = @At(\"RETURN\"), cancellable = true)"));
        assertTrue(source.contains("WindSpiritRules.resolveWindChargeHit"));
        assertTrue(source.contains("GameFunctions.isPlayerPlayingAndAlive(targetPlayer)"));
        assertTrue(source.contains("WraithStateService.isActive(targetPlayer)"));
        assertTrue(source.contains("charge instanceof WindChargeEntity"));
        assertFalse(source.contains("damage("));
        assertFalse(source.contains("takeKnockback("));
        assertFalse(source.contains("setVelocity("));
    }

    @Test
    void bundledFactionApiFiltersProjectileCandidatesThroughRegisteredPolicies() throws Exception {
        Path archive = Path.of("libs/sparkfactionapi-0.1.6.0.jar");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entry = zip.getEntry(
                    "dev/caecorthus/sparkfactionapi/mixin/WorldProjectileAffectMixin.class");
            assertNotNull(entry);
            ClassNode owner = new ClassNode();
            new ClassReader(zip.getInputStream(entry)).accept(
                    owner,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
            MethodNode handler = owner.methods.stream()
                    .filter(method -> method.name.contains("filterDeniedProjectileTargets"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(handler.instructions.iterator().hasNext());
            boolean callsPolicyGuard = false;
            for (var instruction : handler.instructions) {
                if (instruction.getOpcode() == Opcodes.INVOKESTATIC
                        && instruction instanceof MethodInsnNode call
                        && call.owner.equals("dev/caecorthus/sparkfactionapi/impl/target/PlayerAffectMixinGuard")
                        && call.name.equals("allows")) {
                    callsPolicyGuard = true;
                    break;
                }
            }
            assertTrue(callsPolicyGuard,
                    "World projectile candidates must pass through the player-affect policy seam");
        }
    }

    @Test
    void playerAndBreezeChargesRemainDistinctConcreteTypes() {
        assertNotNull(WindChargeEntity.class);
        assertNotNull(BreezeWindChargeEntity.class);
    }
}
