package dev.caecorthus.sparkwitch.registry;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.block.props.ShiningNeonBlock;
import dev.caecorthus.sparkwitch.net.props.NeonSettingsC2SPayload;
import dev.caecorthus.sparkwitch.net.props.NeonSettingsS2CPayload;
import dev.caecorthus.sparkwitch.prop.NeonSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registers the blinking neon pillar and the settings channel it needs. The block itself only flips a {@code lit}
 * state, so no custom block entity has to be ticked.
 * <p>
 * 注册闪烁霓虹灯柱，以及它需要的设置通道。方块本身只翻转 {@code lit} 状态，因此不需要额外的方块实体。
 */
public final class SparkWitchNeonBlocks {
    public static final Identifier SHINING_NEON_ID = SparkWitch.id("shining_neon");

    private static ShiningNeonBlock shiningNeon;
    private static boolean registered;

    private SparkWitchNeonBlocks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        shiningNeon = new ShiningNeonBlock(AbstractBlock.Settings.copy(Blocks.SEA_LANTERN));
        Registry.register(Registries.BLOCK, SHINING_NEON_ID, shiningNeon);
        Registry.register(Registries.ITEM, SHINING_NEON_ID, new BlockItem(shiningNeon, new Item.Settings()));

        PayloadTypeRegistry.playC2S().register(NeonSettingsC2SPayload.ID, NeonSettingsC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NeonSettingsS2CPayload.ID, NeonSettingsS2CPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NeonSettingsC2SPayload.ID, (payload, context) ->
                context.server().execute(() -> NeonSettings.apply(context.player(), payload.pos(),
                        payload.onTicks(), payload.offTicks(), payload.enabled(), payload.random())));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getOverworld() != null) {
                NeonSettings.tick(server.getOverworld());
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register((server, world) -> NeonSettings.clear());
    }

    public static ShiningNeonBlock shiningNeon() {
        return shiningNeon;
    }
}
