package dev.caecorthus.sparkwitch.client.wraith;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithClientMixinInventorySourceTest {
    private static final Path MIXIN_ROOT = Path.of(
            "src/client/java/dev/caecorthus/sparkwitch/client/mixin/wraith"
    );

    @Test
    void completeWraithClientMixinSliceExists() {
        for (String name : List.of(
                "WraithCapeFeatureRendererMixin",
                "WraithChatRestrictionMixin",
                "WraithCorpseModelMixin",
                "WraithCorpseSkinMixin",
                "WraithElytraFeatureRendererMixin",
                "WraithEntityInvisibilityMixin",
                "WraithGameRendererMixin",
                "WraithHeldItemFeatureRendererMixin",
                "WraithHiddenBodiesMixin",
                "WraithMinecraftClientMixin",
                "WraithMoodRendererMixin",
                "WraithNameMixin",
                "WraithPlayerModelMixin",
                "WraithPlayerSkinMixin",
                "WraithPlayerSkinTexturesMixin",
                "WraithRendererDispatchMixin",
                "WraithRoundTextRendererMixin",
                "WraithWatheHighlightMixin"
        )) {
            Path source = MIXIN_ROOT.resolve(name + ".java");
            assertTrue(Files.isRegularFile(source), "missing Wraith client mixin " + source);
        }
    }
}
