package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.bear.BearRules;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Client only. The Rys-T is a 3-D element model on the head and in hand, and a 2-D icon in GUI, ground and item-frame
 * contexts (art README section 3, the AXMC pattern of {@code UsecRifleModels}). {@code models/item/rys_t.json} (visor
 * down) chooses {@code item/rys_t_visor_up} through the {@code sparkwitch:visor_up} override, so the top-level
 * inventory model is wrapped with {@code item/rys_t_icon} and the override target with {@code item/rys_t_visor_up_icon}.
 * 仅客户端。Rys-T 在头部与手持时为 3D 元素模型，在背包、掉落物与物品展示框中为 2D 图标（美术 README 第 3 节，沿用
 * {@code UsecRifleModels} 的 AXMC 写法）。{@code models/item/rys_t.json}（面罩放下）通过 {@code sparkwitch:visor_up} 覆盖选择
 * {@code item/rys_t_visor_up}，因此顶层背包模型包上 {@code item/rys_t_icon}，覆盖目标包上 {@code item/rys_t_visor_up_icon}。
 */
public final class RysTModels {
    public static final ModelIdentifier INVENTORY_MODEL_ID = ModelIdentifier.ofInventoryVariant(BearRules.RYS_T_ITEM_ID);
    public static final Identifier ICON_MODEL_ID = SparkWitch.id("item/rys_t_icon");
    public static final Identifier VISOR_UP_MODEL_ID = SparkWitch.id("item/rys_t_visor_up");
    public static final Identifier VISOR_UP_ICON_MODEL_ID = SparkWitch.id("item/rys_t_visor_up_icon");

    /** Override targets and the icon each shows outside the hand. / 覆盖目标及其在手持以外显示的图标。 */
    static final Map<Identifier, Identifier> VARIANT_ICONS = Map.of(VISOR_UP_MODEL_ID, VISOR_UP_ICON_MODEL_ID);

    private static boolean registered;

    private RysTModels() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ModelLoadingPlugin.register(context -> {
            // Loaded as top-level models so their item/generated parents resolve before baking.
            // 作为顶层模型加载，确保 item/generated 父模型在烘焙前已解析。
            context.addModels(ICON_MODEL_ID, VISOR_UP_ICON_MODEL_ID);
            context.modifyModelOnLoad().register((model, loadContext) -> {
                Identifier icon = iconFor(loadContext);
                return icon == null || model instanceof RysTIconSwapModel ? model : new RysTIconSwapModel(model, icon);
            });
        });
    }

    private static @Nullable Identifier iconFor(ModelModifier.OnLoad.Context loadContext) {
        if (INVENTORY_MODEL_ID.equals(loadContext.topLevelId())) {
            return ICON_MODEL_ID;
        }
        Identifier resource = loadContext.resourceId();
        return resource == null ? null : VARIANT_ICONS.get(resource);
    }
}
