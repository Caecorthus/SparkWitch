package dev.caecorthus.sparkwitch.roles.civilian.seeker;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerCarItem;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Round-start loadout at ON_FINISH_INITIALIZE: revolver + Search Car, exact 60 s car cooldown, starting balance.
 * The grant is driven only by {@code SeekerLifecycleService} (WP-02), after SparkTraits Conscience compensation and
 * Wathe's per-Item revolver start cooldown, while the round is still STARTING; this module registers no round-start
 * listener of its own, so the loadout can never be granted twice.
 * ON_FINISH_INITIALIZE 时的开局装备：左轮 + 搜寻小车、精确 60 秒小车冷却、开局余额。发放只由
 * {@code SeekerLifecycleService}（WP-02）驱动，时机在 SparkTraits 良心补偿与 Wathe 按物品写入的左轮开局冷却之后、
 * 对局仍处于 STARTING 时；本模块不注册任何开局监听，因此装备不会被发放两次。
 */
public final class SeekerLoadoutService {
    private SeekerLoadoutService() {
    }

    /** Registers no round-start listener: the grant is driven by SeekerLifecycleService. / 不注册开局监听：发放由生命周期服务驱动。 */
    public static void register() {
        // Intentionally empty: see the class Javadoc. / 刻意留空：见类注释。
    }

    /**
     * Called exactly once per final Seeker by {@code SeekerLifecycleService} at ON_FINISH_INITIALIZE, after
     * {@code bindMatch}: revolver + car item, {@code component.apply(state.grant())} (INITIAL cooldown written by
     * {@code apply}) and the starting balance. Never called for a mid-round Seeker.
     * 由 {@code SeekerLifecycleService} 在 ON_FINISH_INITIALIZE、{@code bindMatch} 之后对每名最终搜寻者恰好调用一次：
     * 发放左轮与小车物品、{@code component.apply(state.grant())}（INITIAL 冷却由 {@code apply} 写入）以及开局余额。对局中途成为搜寻者时不调用。
     */
    public static void grant(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        // Defensive final-role check; the round is still STARTING, so no running-state helper is used here.
        // 防御性的最终身份检查；此时对局仍处于 STARTING，因此不使用运行态判定。
        if (!receivesGrant(game.hasAnyRole(player), game.isPlayerDead(player.getUuid()), game.getRole(player))) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        // The revolver shares Wathe's per-Item start cooldown. SparkTraits refuses free guns to an Impostor at this
        // point; that trait rule is deliberately not bypassed.
        // 左轮沿用 Wathe 按物品写入的开局冷却。SparkTraits 此时会拒绝向内鬼发放免费枪械；刻意不绕过该词条规则。
        inventory.insertStack(new ItemStack(WatheItems.REVOLVER));
        if (needsCarItem(holdsCarItem(inventory))) {
            inventory.insertStack(new ItemStack(SparkWitchItems.seekerCar()));
        }
        // READY + INITIAL: apply() is the only writer of the car cooldown (exact + max, 60 s).
        // READY + INITIAL：apply() 是小车冷却的唯一写入方（精确 + 取大，60 秒）。
        SeekerStatusComponent component = SeekerStatusComponent.KEY.get(player);
        component.apply(component.state().grant());
        SeekerEconomyService.initialize(player);
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    /** Only a living final Seeker with a role receives the loadout. / 只有拥有身份且存活的最终搜寻者获得装备。 */
    static boolean receivesGrant(boolean hasRole, boolean dead, @Nullable Role finalRole) {
        return hasRole && !dead && SeekerRules.isSeeker(finalRole);
    }

    /** One car item per Seeker, ever. / 每名搜寻者始终只有一个小车物品。 */
    static boolean needsCarItem(boolean alreadyHoldsCar) {
        return !alreadyHoldsCar;
    }

    private static boolean holdsCarItem(PlayerInventory inventory) {
        return inventory.contains(stack -> stack.getItem() instanceof SeekerCarItem);
    }
}
