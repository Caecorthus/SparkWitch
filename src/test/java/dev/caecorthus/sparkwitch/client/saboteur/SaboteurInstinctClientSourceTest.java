package dev.caecorthus.sparkwitch.client.saboteur;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaboteurInstinctClientSourceTest {
    private static final Path CLIENT_ROOT = Path.of("src/client/java/dev/caecorthus/sparkwitch/client");
    private static final Path MIXIN = CLIENT_ROOT.resolve("mixin/saboteur/SaboteurInstinctMixin.java");
    private static final String WATHE_CLIENT = "dev/doctor4t/wathe/client/WatheClient";
    private static final String KILLER_GATE = "isInstinctEnabledAndIsKiller()Z";
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";

    @Test
    void compiledMixinChainPinsExactKillerGateVetoOrderAndPreservesOriginalResult() throws Exception {
        assertKillerGateMixin(
                "dev/caecorthus/sparkwitch/client/mixin/CurserInstinctGateMixin",
                "sparkwitch$blockConfusedKillerInstinct",
                1600,
                "dev/caecorthus/sparkwitch/client/curser/CurserClientHooks",
                "isLocallyConfused",
                List.of(0, 0)
        );
        assertKillerGateMixin(
                "dev/caecorthus/sparkwitch/client/mixin/WatheClientFearInstinctMixin",
                "sparkwitch$disableKillerInstinctWhenFeared",
                1500,
                "dev/caecorthus/sparkwitch/client/hooks/GrandWitchFearClientHooks",
                "shouldBlockInstinct",
                List.of(0)
        );
        assertKillerGateMixin(
                "dev/caecorthus/sparkwitch/client/mixin/saboteur/SaboteurInstinctMixin",
                "sparkwitch$enablePromotedSaboteurInstinct",
                1000,
                "dev/caecorthus/sparkwitch/client/saboteur/SaboteurInstinctClientRules",
                "shouldEnableNativeInstinct",
                List.of(1)
        );
    }

    @Test
    void saboteurExtensionUsesOnlyTheExactPromotedRoleGate() throws Exception {
        String mixin = source(MIXIN);
        String rules = source(CLIENT_ROOT.resolve("saboteur/SaboteurInstinctClientRules.java"));

        assertTrue(mixin.contains("SaboteurInstinctClientRules.shouldEnableNativeInstinct("));
        assertTrue(mixin.contains("SparkWitchServerConnection.isConfirmedServer()"));
        assertTrue(mixin.contains("WraithClientState.isActive(player)"));
        assertTrue(mixin.contains("WraithClientState.isPromoted(player)"));
        assertTrue(mixin.contains("WatheClient.instinctKeybind.isPressed()"));
        assertTrue(rules.contains("return originalAllowed || confirmedServer"));
        assertTrue(rules.contains("SaboteurRole.ID.equals(roleId)"));
        assertFalse(mixin.contains("getInstinctHighlight"));
        assertFalse(mixin.contains("ModifyConstant"));
    }

    @Test
    void clientConfigRegistersTheSaboteurInstinctMixin() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(
                Path.of("src/client/resources/sparkwitch.client.mixins.json"))).getAsJsonObject();
        JsonArray mixins = config.getAsJsonArray("client");

        assertTrue(mixins.asList().stream()
                .anyMatch(value -> "saboteur.SaboteurInstinctMixin".equals(value.getAsString())));
    }

    @Test
    void exactDescriptorExistsInPinnedWatheProvider() throws Exception {
        try (ZipFile wathe = new ZipFile("libs/wathe-1.5.6-spark-1.21.1.jar")) {
            var entry = wathe.getEntry("dev/doctor4t/wathe/client/WatheClient.class");
            assertNotNull(entry);
            ClassNode owner = new ClassNode();
            new ClassReader(wathe.getInputStream(entry)).accept(
                    owner,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
            assertTrue(owner.methods.stream().anyMatch(method ->
                    method.name.equals("isInstinctEnabledAndIsKiller") && method.desc.equals("()Z")));
        }
    }

    private static void assertKillerGateMixin(
            String mixinClass,
            String injectorName,
            int expectedPriority,
            String guardOwner,
            String guardName,
            List<Integer> expectedSetReturnValues
    ) throws Exception {
        ClassNode node = compiledClass(mixinClass);
        AnnotationNode mixin = annotation(node.visibleAnnotations, node.invisibleAnnotations, MIXIN_DESC);
        assertNotNull(mixin, "compiled @Mixin metadata must exist for " + mixinClass);
        assertEquals(expectedPriority, annotationValue(mixin, "priority"));
        assertEquals(List.of(Type.getObjectType(WATHE_CLIENT)), annotationValue(mixin, "value"));

        MethodNode injector = node.methods.stream()
                .filter(method -> injectorName.equals(method.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing compiled injector " + injectorName));
        AnnotationNode inject = annotation(injector.visibleAnnotations, injector.invisibleAnnotations, INJECT_DESC);
        assertNotNull(inject, "compiled @Inject metadata must exist for " + injectorName);
        assertEquals(List.of(KILLER_GATE), annotationValue(inject, "method"));
        assertEquals(Boolean.TRUE, annotationValue(inject, "cancellable"));

        Object atValue = annotationValue(inject, "at");
        assertTrue(atValue instanceof List<?>);
        List<?> injectionPoints = (List<?>) atValue;
        assertEquals(1, injectionPoints.size());
        assertTrue(injectionPoints.getFirst() instanceof AnnotationNode);
        AnnotationNode at = (AnnotationNode) injectionPoints.getFirst();
        assertEquals("HEAD", annotationValue(at, "value"));
        assertTrue(invokes(injector, guardOwner, guardName), "injector must invoke its exact guard");
        assertTrue(guardResultConditionallySkipsOverride(injector, guardOwner, guardName),
                "false guard result must leave the original method result untouched");
        assertFalse(invokes(injector,
                "org/spongepowered/asm/mixin/injection/callback/CallbackInfo",
                "cancel"), "injector must preserve the original result when its guard is false");
        assertEquals(expectedSetReturnValues, callbackReturnConstants(injector));
    }

    private static ClassNode compiledClass(String internalName) throws Exception {
        String resource = internalName + ".class";
        try (InputStream input = SaboteurInstinctClientSourceTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(input, "compiled client class must be on the test runtime classpath: " + resource);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static AnnotationNode annotation(
            List<AnnotationNode> visible,
            List<AnnotationNode> invisible,
            String descriptor
    ) {
        if (visible != null) {
            for (AnnotationNode annotation : visible) {
                if (descriptor.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        if (invisible != null) {
            for (AnnotationNode annotation : invisible) {
                if (descriptor.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values == null) {
            return null;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static boolean invokes(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invocation
                    && owner.equals(invocation.owner)
                    && name.equals(invocation.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean guardResultConditionallySkipsOverride(
            MethodNode method,
            String guardOwner,
            String guardName
    ) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode invocation)
                    || !guardOwner.equals(invocation.owner)
                    || !guardName.equals(invocation.name)) {
                continue;
            }
            AbstractInsnNode next = nextOpcodeInstruction(invocation);
            return next instanceof JumpInsnNode
                    && (next.getOpcode() == Opcodes.IFEQ || next.getOpcode() == Opcodes.IFNE);
        }
        return false;
    }

    private static List<Integer> callbackReturnConstants(MethodNode method) {
        List<Integer> constants = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode setter)
                    || !"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable".equals(setter.owner)
                    || !"setReturnValue".equals(setter.name)) {
                continue;
            }
            AbstractInsnNode boxing = previousOpcodeInstruction(setter);
            assertTrue(boxing instanceof MethodInsnNode, "setReturnValue must receive a boxed boolean literal");
            MethodInsnNode valueOf = (MethodInsnNode) boxing;
            assertEquals("java/lang/Boolean", valueOf.owner);
            assertEquals("valueOf", valueOf.name);
            assertEquals("(Z)Ljava/lang/Boolean;", valueOf.desc);
            AbstractInsnNode literal = previousOpcodeInstruction(valueOf);
            assertNotNull(literal);
            assertTrue(literal.getOpcode() == 3 || literal.getOpcode() == 4,
                    "return override must be an exact false/true literal");
            constants.add(literal.getOpcode() - 3);
        }
        return constants;
    }

    private static AbstractInsnNode nextOpcodeInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
    }

    private static AbstractInsnNode previousOpcodeInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static String source(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "required client source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
