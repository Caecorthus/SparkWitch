package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaRenderingContractsSourceTest {
    @Test
    void outlineIsRecompositedAfterDesaturationWithoutAWorldPixelColorKey() throws IOException {
        String effects = readClient("blackraven/BlackRavenPerceptionScreenEffects.java");
        int shaderRender = effects.indexOf("activeProcessor.render(delta)");
        int framebufferRestore = effects.indexOf("client.getFramebuffer().beginWrite(false)", shaderRender);
        int outlineComposite = effects.indexOf("client.worldRenderer.drawEntityOutlinesFramebuffer()", framebufferRestore);

        assertTrue(shaderRender >= 0);
        assertTrue(framebufferRestore > shaderRender);
        assertTrue(outlineComposite > framebufferRestore);
        assertTrue(effects.contains("if (desaturation <= 0.0F)"));
        assertTrue(effects.contains("closeProcessor()"));
        assertFalse(effects.contains("0xFF0000"));
        assertFalse(effects.contains("red pixel"));
    }

    @Test
    void vendettaKnifeUsesOnlyWatheDynamicKnifeModels() throws IOException {
        String plugin = readClient("vendetta/VendettaKnifeModelLoadingPlugin.java");
        String client = readClient("SparkWitchClient.java");
        String item = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/vendetta/VendettaKnifeItem.java"
        )).replaceAll("\\s+", " ");
        String model = Files.readString(Path.of(
                "src/main/resources/assets/sparkwitch/models/item/vendetta_knife.json"
        ));

        assertTrue(item.contains("extends KnifeItem"));
        assertTrue(plugin.contains("ModelIdentifier.ofInventoryVariant(SparkWitchItems.VENDETTA_KNIFE_ID)"));
        assertTrue(plugin.contains("KnifeModelLoadingPlugin.Variant.values()"));
        assertTrue(plugin.contains("KnifeModelLoadingPlugin.getModelLocation(variant)"));
        assertTrue(plugin.contains("VENDETTA_KNIFE_MODEL_ID.equals(loadContext.topLevelId())"));
        assertTrue(plugin.contains("new KnifeModel(model)"));
        assertFalse(plugin.contains("KnifeModelLoadingPlugin.KNIFE_MODEL_ID.equals"));
        assertTrue(client.contains("VendettaKnifeModelLoadingPlugin.register()"));
        assertTrue(model.contains("\"parent\": \"wathe:item/template_knife\""));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/assets/sparkwitch/textures/item/vendetta_knife.png"
        )));
    }

    private static String readClient(String relativePath) throws IOException {
        Path path = Path.of("src/client/java/dev/caecorthus/sparkwitch/client").resolve(relativePath);
        assertTrue(Files.isRegularFile(path), "required client source must exist: " + path);
        return Files.readString(path).replaceAll("\\s+", " ");
    }
}
