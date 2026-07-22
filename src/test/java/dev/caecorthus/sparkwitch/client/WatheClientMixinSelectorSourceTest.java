package dev.caecorthus.sparkwitch.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatheClientMixinSelectorSourceTest {
    private static final Path MIXIN_ROOT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin");
    private static final Path CLIENT_MIXIN_CONFIG = Path.of(
            "src/client/resources/sparkwitch.client.mixins.json");
    private static final Path WATHE_JAR = Path.of("libs/wathe-1.5.6-spark-1.21.1.jar");
    private static final String DRAW_CONTEXT_DESCRIPTOR =
            "Lnet/minecraft/class_332;IIF)V";

    @Test
    void descriptorQualifiedSourceSelectorsMatchTheExactWatheRuntimeMethods() throws Exception {
        String skill = Files.readString(MIXIN_ROOT.resolve("WitchSkillInventoryScreenMixin.java"));
        String shop = Files.readString(MIXIN_ROOT.resolve("WitchShopPriceMixin.java"));
        String maiden = Files.readString(
                MIXIN_ROOT.resolve("witchmaiden/WitchMaidenInventoryScreenMixin.java"));

        assertEquals(1, occurrences(skill, "method_25394(" + DRAW_CONTEXT_DESCRIPTOR));
        assertEquals(1, occurrences(shop, "method_48579(" + DRAW_CONTEXT_DESCRIPTOR));
        assertEquals(1, occurrences(maiden, "method_25426()V"));
        assertEquals(1, occurrences(maiden, "method_25394(" + DRAW_CONTEXT_DESCRIPTOR));
        assertTrue(shop.contains(
                "Lnet/minecraft/text/Text;literal(Ljava/lang/String;)Lnet/minecraft/text/MutableText;"));
        assertFalse(skill.contains("require = 0"));
        assertFalse(shop.contains("require = 0"));
        assertFalse(maiden.contains("require = 0"));

        JsonObject config = JsonParser.parseString(Files.readString(CLIENT_MIXIN_CONFIG))
                .getAsJsonObject();
        assertTrue(config.get("required").getAsBoolean());
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        JsonArray mixins = config.getAsJsonArray("client");
        assertContains(mixins, "WitchSkillInventoryScreenMixin");
        assertContains(mixins, "WitchShopPriceMixin");
        assertContains(mixins, "witchmaiden.WitchMaidenInventoryScreenMixin");

        try (ZipFile wathe = new ZipFile(WATHE_JAR.toFile())) {
            assertProviderMethod(
                    wathe,
                    "dev/doctor4t/wathe/client/gui/screen/ingame/LimitedInventoryScreen.class",
                    "method_25394",
                    "(Lnet/minecraft/class_332;IIF)V"
            );
            assertProviderMethod(
                    wathe,
                    "dev/doctor4t/wathe/client/gui/screen/ingame/LimitedInventoryScreen.class",
                    "method_25426",
                    "()V"
            );
            assertProviderMethod(
                    wathe,
                    "dev/doctor4t/wathe/client/gui/screen/ingame/"
                            + "LimitedInventoryScreen$StoreItemWidget.class",
                    "method_48579",
                    "(Lnet/minecraft/class_332;IIF)V"
            );
        }
    }

    private static void assertProviderMethod(
            ZipFile jar,
            String entryName,
            String methodName,
            String descriptor
    ) throws Exception {
        var entry = jar.getEntry(entryName);
        assertNotNull(entry, entryName);
        ClassNode owner = new ClassNode();
        new ClassReader(jar.getInputStream(entry)).accept(
                owner,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
        );
        assertTrue(
                owner.methods.stream().anyMatch(method ->
                        method.name.equals(methodName) && method.desc.equals(descriptor)),
                () -> entryName + " is missing " + methodName + descriptor
        );
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static void assertContains(JsonArray values, String expected) {
        assertTrue(values.asList().stream().anyMatch(value -> expected.equals(value.getAsString())));
    }
}
