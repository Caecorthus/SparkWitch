package dev.caecorthus.sparkwitch.client.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Client only. The AXMC rifle is a 3-D element model in hand (first/third person, head) and a 2-D icon in GUI,
 * ground and item-frame contexts: vanilla's trident/spyglass split, done through Fabric's ModelLoadingPlugin like
 * {@code VendettaKnifeModelLoadingPlugin}. {@code models/item/usec_rifle.json} chooses its 3-D variant through the
 * {@code sparkwitch:usec_magazine} / {@code sparkwitch:usec_suppressor} overrides, so every model it can resolve to
 * is wrapped with the icon that variant shows: the top-level inventory model and the three override targets. The
 * base resource {@code item/usec_rifle} itself is never wrapped: its override list names it again for
 * "magazine in, no suppressor", and vanilla only maps that self-reference back to the outer model while the resource
 * stays the plain JSON model.
 * 仅客户端。AXMC 狙击步枪在手持（第一/第三人称、头部）时为 3D 元素模型，在背包、掉落物与物品展示框中为 2D 图标：
 * 即原版三叉戟/望远镜的分流，沿用 {@code VendettaKnifeModelLoadingPlugin} 的 Fabric ModelLoadingPlugin 写法。
 * {@code models/item/usec_rifle.json} 通过 {@code sparkwitch:usec_magazine} / {@code sparkwitch:usec_suppressor} 覆盖
 * 选择 3D 变体，因此它可能解析到的每个模型都包上对应图标：顶层背包模型与三个覆盖目标。基础资源
 * {@code item/usec_rifle} 本身绝不包装：它的覆盖列表为“装弹匣、无消音器”再次指向自己，原版只有在该资源仍是普通 JSON
 * 模型时才会把这个自引用映射回外层模型。
 */
public final class UsecRifleModels {
    public static final ModelIdentifier RIFLE_INVENTORY_MODEL_ID =
            ModelIdentifier.ofInventoryVariant(UsecRules.RIFLE_ITEM_ID);
    public static final Identifier ICON_MODEL_ID = SparkWitch.id("item/usec_rifle_icon");
    public static final Identifier SUPPRESSED_ICON_MODEL_ID = SparkWitch.id("item/usec_rifle_suppressed_icon");
    public static final Identifier NO_MAGAZINE_MODEL_ID = SparkWitch.id("item/usec_rifle_no_mag");
    public static final Identifier SUPPRESSED_MODEL_ID = SparkWitch.id("item/usec_rifle_suppressed");
    public static final Identifier SUPPRESSED_NO_MAGAZINE_MODEL_ID = SparkWitch.id("item/usec_rifle_suppressed_no_mag");

    /**
     * Override targets of {@code models/item/usec_rifle.json} and the icon each shows outside the hand.
     * {@code models/item/usec_rifle.json} 的覆盖目标及其在手持以外显示的图标。
     */
    static final Map<Identifier, Identifier> VARIANT_ICONS = Map.of(
            NO_MAGAZINE_MODEL_ID, ICON_MODEL_ID,
            SUPPRESSED_MODEL_ID, SUPPRESSED_ICON_MODEL_ID,
            SUPPRESSED_NO_MAGAZINE_MODEL_ID, SUPPRESSED_ICON_MODEL_ID);

    private static boolean registered;

    private UsecRifleModels() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ModelLoadingPlugin.register(context -> {
            // Loaded as top-level models so their item/generated parents resolve before baking.
            // 作为顶层模型加载，确保 item/generated 父模型在烘焙前已解析。
            context.addModels(ICON_MODEL_ID, SUPPRESSED_ICON_MODEL_ID);
            context.modifyModelOnLoad().register((model, loadContext) -> {
                Identifier icon = iconFor(loadContext);
                return icon == null || model instanceof UsecRifleIconSwapModel
                        ? model
                        : new UsecRifleIconSwapModel(model, icon);
            });
        });
    }

    private static Identifier iconFor(ModelModifier.OnLoad.Context loadContext) {
        if (RIFLE_INVENTORY_MODEL_ID.equals(loadContext.topLevelId())) {
            return ICON_MODEL_ID;
        }
        Identifier resource = loadContext.resourceId();
        return resource == null ? null : VARIANT_ICONS.get(resource);
    }
}
