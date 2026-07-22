package dev.caecorthus.sparkwitch.mixin.saboteur;

import dev.caecorthus.sparkwitch.roles.killer.saboteur.SaboteurShopStockAccess;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Seeds only promoted Saboteur stock while preserving Wathe-owned cooldowns. / 仅补充晋升破坏者库存，保留 Wathe 自有冷却。 */
@Mixin(value = PlayerShopComponent.class, remap = false)
public abstract class PlayerShopComponentSaboteurMixin implements SaboteurShopStockAccess {
    @Shadow
    @Final
    private Map<String, Integer> stock;

    @Shadow
    @Final
    private Map<String, Integer> maxStockCache;

    @Override
    public void sparkwitch$initializePromotionLockpickStock() {
        stock.put("lockpick", 1);
        maxStockCache.put("lockpick", 1);
    }
}
