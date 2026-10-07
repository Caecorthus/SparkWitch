package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindComponent;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherFishUse;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishKind;
import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintKarmaCooldownService;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerPlayerComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalLong;

/**
 * Removing a SparkWitch item's vanilla cooldown ({@code ItemCooldownManager.remove}: SparkFactionAPI
 * {@code clearCooldown}, Wathe's round reset) also releases the SparkWitch timer that gates the same item, so the item
 * is really usable when its slot shows ready. Server only: {@link #onRemoved} from {@code ItemCooldownRemovalMixin},
 * {@link #onAdminCleared} from {@code SparkFactionClearCooldownMixin}. Wathe's reset runs before {@code ResetPlayer},
 * which clears these timers anyway.
 * 移除 SparkWitch 物品的原版冷却（{@code ItemCooldownManager.remove}：SparkFactionAPI {@code clearCooldown}、Wathe
 * 开局重置）时，同时释放门控同一物品的 SparkWitch 计时，使栏位显示就绪时物品确实可用。仅服务端：{@link #onRemoved}
 * 由 {@code ItemCooldownRemovalMixin} 调用，{@link #onAdminCleared} 由 {@code SparkFactionClearCooldownMixin} 调用。
 * Wathe 的重置先于 {@code ResetPlayer}，后者本就会清除这些计时。
 */
public final class SparkWitchItemCooldownReleases {
    private SparkWitchItemCooldownReleases() {
    }

    /** The own timer behind an item's cooldown. / 物品冷却背后的自有计时。 */
    enum Release {
        NONE,
        /** {@code BlindComponent} cane ready tick. / 盲杖就绪刻。 */
        WHITE_CANE,
        /** {@code TimeStealerPlayerComponent} {@code ClockReadyAt}. / 怀表的 ClockReadyAt。 */
        CLOCK,
        /** {@code TimeStealerPlayerComponent} {@code GiftReadyAt}. / 赠时怀表的 GiftReadyAt。 */
        GIFT_WATCH,
        /** {@code GrandWitchRuntimeComponent} sword kill cooldown. / 仪礼剑击杀冷却。 */
        CEREMONIAL_SWORD,
        /** The five edible fish share one cooldown. / 五种可食用鱼共用一个冷却。 */
        FISH
    }

    public static void onRemoved(ServerPlayerEntity player, Item item) {
        switch (releaseFor(Registries.ITEM.getId(item))) {
            case WHITE_CANE -> {
                BlindComponent state = BlindComponent.KEY.get(player);
                // A running window is kept; only the ready tick moves. / 保留进行中的窗口，只移动就绪刻。
                releasedReadyTick(state.caneReadyTick(), player.getServerWorld().getTime())
                        .ifPresent(ready -> state.setCane(state.caneActiveUntilTick(), ready));
            }
            case CLOCK -> {
                TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(player);
                releasedReadyTick(state.clockReadyAt(), player.getServerWorld().getTime())
                        .ifPresent(state::setClockReadyAt);
            }
            case GIFT_WATCH -> {
                TimeStealerPlayerComponent state = TimeStealerPlayerComponent.KEY.get(player);
                releasedReadyTick(state.giftReadyAt(), player.getServerWorld().getTime())
                        .ifPresent(state::setGiftReadyAt);
            }
            case CEREMONIAL_SWORD -> GrandWitchRuntimeComponent.KEY.get(player).setSwordKillCooldownTicks(0);
            case FISH -> FisherFishUse.releaseSharedCooldown(player);
            case NONE -> {
            }
        }
    }

    /**
     * Admin clear only ({@code SparkFactionClearCooldownMixin}), after {@link #onRemoved} already ran: Saint Karma stops
     * re-covering the item for the rest of the running Karma.
     * 仅管理员清除（{@code SparkFactionClearCooldownMixin}），在 {@link #onRemoved} 之后调用：本次业障剩余时间内不再覆盖该物品。
     */
    public static void onAdminCleared(ServerPlayerEntity player, Item item) {
        SaintKarmaCooldownService.exemptAdminCleared(player, item);
    }

    static Release releaseFor(@Nullable Identifier itemId) {
        if (itemId == null) {
            return Release.NONE;
        }
        if (SparkWitchItems.WHITE_CANE_ID.equals(itemId)) {
            return Release.WHITE_CANE;
        }
        if (SparkWitchItems.TIME_STEALER_CLOCK_ID.equals(itemId)) {
            return Release.CLOCK;
        }
        if (SparkWitchItems.TIME_STEALER_GIFT_WATCH_ID.equals(itemId)) {
            return Release.GIFT_WATCH;
        }
        if (SparkWitchItems.CEREMONIAL_SWORD_ID.equals(itemId)) {
            return Release.CEREMONIAL_SWORD;
        }
        for (FisherFishKind kind : FisherFishKind.values()) {
            if (kind.itemId().equals(itemId)) {
                return Release.FISH;
            }
        }
        return Release.NONE;
    }

    /**
     * {@code now} for a ready tick still in the future, else empty (no write, no sync). Never 0: a positive ready tick
     * is also the "kit granted" / "same round" flag of the cane, the Clock and the Gift Watch.
     * 就绪刻仍在未来时返回 {@code now}，否则为空（不写入、不同步）。绝不为 0：正的就绪刻同时是盲杖、怀表与赠时怀表的
     * “已发放” / “同一回合”标记。
     */
    static OptionalLong releasedReadyTick(long readyTick, long now) {
        return readyTick > now ? OptionalLong.of(Math.max(1L, now)) : OptionalLong.empty();
    }
}
