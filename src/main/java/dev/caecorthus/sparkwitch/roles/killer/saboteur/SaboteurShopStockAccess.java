package dev.caecorthus.sparkwitch.roles.killer.saboteur;

/** Mixin bridge for seeding promotion-owned lockpick stock only. / 仅补充晋升开锁器库存的 Mixin 桥接契约。 */
public interface SaboteurShopStockAccess {
    void sparkwitch$initializePromotionLockpickStock();
}
