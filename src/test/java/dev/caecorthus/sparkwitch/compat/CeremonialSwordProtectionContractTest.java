package dev.caecorthus.sparkwitch.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Bytecode checks intentionally use pinned jars, not the newer sibling source checkout. */
class CeremonialSwordProtectionContractTest {
    private static final Path MAIN = Path.of("src/main/java/dev/caecorthus/sparkwitch");
    private static final String RESULT = "dev/doctor4t/wathe/api/event/KillPlayer$KillResult";

    @Test
    void pinnedNoellesListenerStillHasTheAuditedInternalEarlyReturns() throws Exception {
        var type = pinned("noellesroles-1.7.6-h1.5.6-spark.jar", "org/agmas/noellesroles/Noellesroles");
        MethodNode listener = type.methods.stream().filter(m -> m.name.equals("lambda$registerEvents$5"))
                .findFirst().orElseThrow();
        assertEquals("(Lnet/minecraft/class_3222;Lnet/minecraft/class_3222;Lnet/minecraft/class_2960;)L"
                + RESULT + ";", listener.desc);
        var calls = calls(listener);
        assertEquals(8, calls.stream().filter(call -> call.owner.equals(RESULT)
                && call.name.equals("cancel")).count());
        assertEquals(1, calls.stream().filter(call -> call.owner.equals(RESULT)
                && call.name.equals("allowWithoutBody")).count());
        int iron = callIndex(calls, "org/agmas/noellesroles/professor/IronManPlayerComponent", "removeBuff");
        int whiskey = callIndex(calls, "org/agmas/noellesroles/effect/WhiskeyShieldEffect", "consumeShield");
        assertTrue(iron < whiskey);
        assertTrue(calls.subList(iron + 1, whiskey).stream()
                .anyMatch(call -> call.owner.equals(RESULT) && call.name.equals("cancel")));
    }

    @Test
    void pinnedWathePsychoBranchRunsBetweenBeforeEventAndNormalDeath() throws Exception {
        var type = pinned("wathe-1.5.6-spark-1.21.1.jar", "dev/doctor4t/wathe/game/GameFunctions");
        MethodNode kill = type.methods.stream().filter(m -> m.name.equals("killPlayer")
                && m.desc.equals("(Lnet/minecraft/class_3222;ZLnet/minecraft/class_3222;Lnet/minecraft/class_2960;Z)V"))
                .findFirst().orElseThrow();
        var calls = calls(kill);
        int before = callIndex(calls, "dev/doctor4t/wathe/api/event/KillPlayer$Before", "beforeKillPlayer");
        int pierce = callIndex(calls, "dev/doctor4t/wathe/api/event/ShouldPiercePsychoArmour", "pierces");
        int consume = callIndex(calls, "dev/doctor4t/wathe/cca/PlayerPsychoComponent", "setArmour");
        int mode = callIndex(calls, "net/minecraft/class_3222", "method_7336");
        assertTrue(before < pierce && pierce < consume && consume < mode);
        assertEquals(1, calls.stream().filter(call -> call.owner.equals(
                "dev/doctor4t/wathe/api/event/ShouldPiercePsychoArmour") && call.name.equals("pierces")).count());
    }

    @Test
    void costsPrecedeTheSwordOnlyCancellationDecision() throws Exception {
        ordered(source("roles/killer/ninja/NinjaFeatureService.java"),
                "component.finishNinjaParryWindow()", "CeremonialSwordProtectionPolicy.afterProtection(deathReason)");
        ordered(source("roles/civilian/guardianangel/GuardianAngelFeatureService.java")
                        .substring(source("roles/civilian/guardianangel/GuardianAngelFeatureService.java").indexOf("private static @Nullable KillPlayer.KillResult beforeKill")),
                "victim.removeStatusEffect(", "recordShieldActivation(",
                "CeremonialSwordProtectionPolicy.afterProtection(deathReason)");
        String noelles = source("compat/NoellesCeremonialSwordProtectionCompat.java");
        ordered(noelles, "ironMan.removeBuff()", "WhiskeyShieldEffect.consumeShield(victim)");
        assertFalse(noelles.contains("KillResult.allow"));
        String listener = source("mixin/NoellesCeremonialSwordProtectionMixin.java");
        ordered(listener, "!CeremonialSwordProtectionPolicy.pierces(deathReason)",
                "consumeProtections(victim, killer, deathReason)", "cir.setReturnValue(null)");
        String tofana = source("mixin/tofana/GameFunctionsTofanaProtectionMixin.java");
        assertTrue(tofana.contains("TofanaProtectionService.protect(victim, killer, force), deathReason)"));
        ordered(tofana, "TofanaProtectionService.protect(", "ci.cancel()");
        String service = source("item/tofana/TofanaProtectionService.java");
        ordered(service, "!consumeOne(holder)", "RETALIATIONS.enqueue(");
        assertFalse(service.contains("isPlayerPlayingAndAlive(currentHolder)"));
        assertFalse(service.contains("isPlayerPlayingAndAlive(holder"));
        assertTrue(service.contains("holder.player(),\n                    TofanaRules.DEATH_REASON_ID"));
        String wathe = source("mixin/GameFunctionsCeremonialSwordProtectionMixin.java");
        ordered(wathe, "!CeremonialSwordProtectionPolicy.pierces(deathReason)",
                "psycho.setArmour(psycho.getArmour() - 1)", "psycho.sync()", "event.record()", "return true");
        assertTrue(wathe.contains("SwallowedPlayerComponent.KEY.get(victim).isSwallowed()"));
        assertFalse(wathe.contains("ci.cancel()"));
    }

    private static ClassNode pinned(String jar, String className) throws Exception {
        try (var file = new JarFile(Path.of("libs", jar).toFile())) {
            try (var input = file.getInputStream(file.getJarEntry(className + ".class"))) {
                var node = new ClassNode();
                new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            }
        }
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static int callIndex(List<MethodInsnNode> calls, String owner, String name) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).owner.equals(owner) && calls.get(i).name.equals(name)) return i;
        }
        fail("Missing pinned call: " + owner + "." + name);
        return -1;
    }

    private static String source(String file) throws Exception {
        return Files.readString(MAIN.resolve(file));
    }

    private static void ordered(String source, String... snippets) {
        int previous = -1;
        for (String snippet : snippets) {
            int index = source.indexOf(snippet, previous + 1);
            assertTrue(index > previous, "Missing or out-of-order: " + snippet);
            previous = index;
        }
    }
}
