package dev.caecorthus.sparkwitch.roles.killer.saboteur;

/** Mixin bridge for seeding only promotion-owned stock. / 仅补充晋升库存的 Mixin 桥接契约。 */
public interface SaboteurShopStockAccess {
    void sparkwitch$initializeSaboteurLockpickStock();
}
