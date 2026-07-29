package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VendettaKnifeStackFactorySourceTest {
    @Test
    void copiesOwnersWatheKnifeCosmeticWhenCreatingStack() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/roles/civilian/vendetta/VendettaKnifeStackFactory.java"
        ));

        assertTrue(source.contains("new ItemStack(SparkWitchItems.vendettaKnife())"));
        assertTrue(source.contains("CosmeticDataCache.getCosmetic(player.getUuid(), KnifeItem.ITEM_ID)"));
        assertTrue(source.contains("if (cosmetic != null)"));
        assertTrue(source.contains("knife.set(WatheDataComponentTypes.SKIN, cosmetic)"));
    }
}
