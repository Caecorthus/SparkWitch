package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsShopEntryPreserver;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindRules;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The Blind's role-gated shop (C14): SparkTraits entries are preserved and the only own entry is the ComTac VIII,
 * {@link BlindRules#COMTAC_PRICE} coins, stock 1, shown with the {@code shop.sparkwitch.comtac_viii} name and its
 * "one per round" description. Wathe caches stock while STARTING, so the gate is the synced role alone; stock is
 * unenforced for a role gained mid-round, so the buy gate also refuses a buyer who already owns one (inventory, head
 * slot or cursor) or already bought one in this match (a server-only mark in {@link BlindComponent}). A bought ComTac
 * lands on the head when the head slot is empty, else in the first empty hotbar slot; with no room the purchase fails
 * and charges nothing. The custom handler has no physical shop output, so Grand Witch recruitment refunds a ComTac at
 * the unknown-item price.
 * 盲人的职业限定商店（C14）：保留 SparkTraits 条目，自有条目只有 ComTac VIII，{@link BlindRules#COMTAC_PRICE} 金币、
 * 库存 1，以 {@code shop.sparkwitch.comtac_viii} 名称及其“每局一件”说明显示。Wathe 在 STARTING 阶段缓存库存，因此只按
 * 同步职业筛选；中途获得的职业不受库存约束，因此购买门槛还会拒绝已持有者（背包、头部槽或光标）以及本局已买过者
 * （{@link BlindComponent} 中仅服务端的记录）。购得的 ComTac 在头部槽为空时直接戴上，否则放入第一个空快捷栏位；没有空间时
 * 购买失败且不扣费。自定义处理没有实体商店产物，因此大魔女招募按未知物品价格退还 ComTac。
 */
public final class BlindShopService {
    /** Lang key of the "already owned" refusal. / “已持有”拒绝提示的语言键。 */
    public static final String ALREADY_OWNED_KEY = "shop.error.sparkwitch.comtac_owned";
    private static final String NAME_KEY = "shop.sparkwitch." + BlindRules.COMTAC_SHOP_ENTRY_ID;
    private static final String DESCRIPTION_KEY = NAME_KEY + ".description";
    private static final int DESCRIPTION_COLOR = 0x808080;
    private static boolean registered;

    private BlindShopService() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BuildShopEntries.EVENT.register(BlindShopService::buildEntries);
        ShopPurchase.BEFORE.register(BlindShopService::denyDuplicate);
    }

    private static void buildEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        if (!BlindRules.isBlind(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))) {
            return;
        }
        List<ShopEntry> preserved = SparkTraitsShopEntryPreserver.capture(context);
        context.clearEntries();
        context.addEntry(new ShopEntry.Builder(BlindRules.COMTAC_SHOP_ENTRY_ID, displayStack(), BlindRules.COMTAC_PRICE,
                ShopEntry.Type.TOOL)
                .stock(1)
                .onBuy(BlindShopService::buyComTac)
                .build());
        SparkTraitsShopEntryPreserver.restore(context, preserved);
    }

    /** Fiend shop precedent: a plain item name and one grey, non-italic lore line. / 沿用魔人商店：普通名称与一行灰色非斜体说明。 */
    private static ItemStack displayStack() {
        ItemStack display = SparkWitchItems.comTac().getDefaultStack();
        display.set(DataComponentTypes.ITEM_NAME, Text.translatable(NAME_KEY));
        display.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.translatable(DESCRIPTION_KEY)
                .styled(style -> style.withItalic(false).withColor(DESCRIPTION_COLOR)))));
        return display;
    }

    private static @Nullable ShopPurchase.PurchaseResult denyDuplicate(ServerPlayerEntity player, ShopEntry entry,
                                                                       int index) {
        return BlindRules.COMTAC_SHOP_ENTRY_ID.equals(entry.id()) && refusesPurchase(player)
                ? ShopPurchase.PurchaseResult.deny(ALREADY_OWNED_KEY) : null;
    }

    private static boolean refusesPurchase(ServerPlayerEntity buyer) {
        return BlindKitRules.refusesComTacPurchase(BlindLoadoutService.ownsComTac(buyer),
                BlindComponent.KEY.get(buyer).comTacPurchaseMatch(), BlindParticipants.currentMatchId());
    }

    /**
     * Server-side buy handler ({@code PlayerShopComponent.tryBuy}); false charges nothing. The head-slot write is a
     * plain inventory write, so no equip sound is played.
     * 服务端购买处理（{@code PlayerShopComponent.tryBuy}）；返回 false 不扣费。头部槽写入是普通背包写入，因此不播放穿戴音效。
     */
    private static boolean buyComTac(PlayerEntity player) {
        // Normally already denied with a message by denyDuplicate; this is the authoritative backstop.
        // 通常已由 denyDuplicate 带提示拒绝；此处为权威兜底。
        if (!(player instanceof ServerPlayerEntity buyer) || !BlindParticipants.isActiveBlind(buyer)
                || refusesPurchase(buyer)) {
            return false;
        }
        PlayerInventory inventory = buyer.getInventory();
        int slot = BlindKitRules.comTacLanding(inventory.getStack(BlindKitRules.HEAD_SLOT).isEmpty(),
                hotbar -> inventory.getStack(hotbar).isEmpty());
        if (slot == BlindKitRules.NO_SLOT) {
            return false;
        }
        inventory.setStack(slot, new ItemStack(SparkWitchItems.comTac()));
        BlindComponent.KEY.get(buyer).markComTacBought(BlindParticipants.currentMatchId());
        inventory.markDirty();
        buyer.currentScreenHandler.sendContentUpdates();
        return true;
    }
}
