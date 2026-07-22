package dev.caecorthus.sparkwitch.client.vendetta;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.client.model.item.KnifeModel;
import dev.doctor4t.wathe.client.model.item.KnifeModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.util.ModelIdentifier;

/** Gives the Vendetta knife Wathe's dynamic ItemWithSkin model selection. */
public final class VendettaKnifeModelLoadingPlugin {
    public static final ModelIdentifier VENDETTA_KNIFE_MODEL_ID =
            ModelIdentifier.ofInventoryVariant(SparkWitchItems.VENDETTA_KNIFE_ID);

    private VendettaKnifeModelLoadingPlugin() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> {
            for (KnifeModelLoadingPlugin.Variant variant : KnifeModelLoadingPlugin.Variant.values()) {
                context.addModels(KnifeModelLoadingPlugin.getModelLocation(variant));
            }
            context.modifyModelOnLoad().register((model, loadContext) ->
                    VENDETTA_KNIFE_MODEL_ID.equals(loadContext.topLevelId())
                            ? new KnifeModel(model)
                            : model
            );
        });
    }
}
