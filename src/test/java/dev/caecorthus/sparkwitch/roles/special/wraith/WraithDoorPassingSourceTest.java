package dev.caecorthus.sparkwitch.roles.special.wraith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WraithDoorPassingSourceTest {
    private static final Path ROOT = Path.of("src");

    @Test
    void wraithDoorPassingMixinCoversWatheAndVanillaDoorFamilies() throws Exception {
        String mixin = Files.readString(ROOT.resolve(
                "main/java/dev/caecorthus/sparkwitch/mixin/WraithDoorPassingMixin.java"));

        assertTrue(mixin.contains("@Mixin(AbstractBlock.AbstractBlockState.class)"));
        assertTrue(mixin.contains("getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;"));
        assertTrue(mixin.contains("WraithStateService.isActive(player)"));
        assertTrue(mixin.contains("VoxelShapes.empty()"));
        assertTrue(mixin.contains("isDoorFamilyBlock"));
        assertTrue(mixin.contains("DoorPartBlock"));
        assertTrue(mixin.contains("DoorBlock"));
        assertTrue(mixin.contains("TrapdoorBlock"));
        assertTrue(mixin.contains("FenceGateBlock"));
    }

    @Test
    void mixinConfigStillRegistersTheSharedDoorPassingHook() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(ROOT.resolve(
                "main/resources/sparkwitch.mixins.json"))).getAsJsonObject();

        assertTrue(config.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("WraithDoorPassingMixin")));
    }
}
