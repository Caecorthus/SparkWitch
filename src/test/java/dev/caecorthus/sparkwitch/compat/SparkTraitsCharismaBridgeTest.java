package dev.caecorthus.sparkwitch.compat;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkTraitsCharismaBridgeTest {
    @Test
    void absentSparkTraitsLeavesShopEntryUntouched() {
        ShopEntry entry = new ShopEntry(new ItemStack(Items.STICK), 50, ShopEntry.Type.TOOL);

        assertSame(entry, SparkTraitsCharismaBridge.discountShopEntry(null, entry));
    }

    @Test
    void reflectionNamesOnlyThePublicSparkTraitsApiMethod() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/caecorthus/sparkwitch/compat/SparkTraitsCharismaBridge.java"
        ));

        assertTrue(source.contains("dev.caecorthus.sparktraits.api.SparkTraitsApi"));
        assertTrue(source.contains("discountShopEntryForCharisma"));
        assertFalse(source.contains("dev.caecorthus.sparktraits.impl"));
        assertFalse(source.contains("dev.caecorthus.sparktraits.component"));
    }
}
