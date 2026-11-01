package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanInstinctVeilComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPlayerComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Ferryman's own view: an attacker holding 瞥视 stays visible through walls in dark red, a ferried body carries
 * its 怜悯 mark in pale blue, and a body that was not ferried yet stays amber so the Ferryman can find what is still
 * fresh. The event is client-side and the marks are synced to the owner alone, so nobody else sees these colours;
 * {@code always} makes them independent of the instinct key.
 * 摆渡人自己的视野：带有「瞥视」的攻击者以深红色穿墙可见，已摆渡的遗体以淡蓝色显示「怜悯」标记，尚未摆渡的遗体以琥珀色
 * 显示，便于找到仍然新鲜的遗体。该事件仅客户端且标记只同步给拥有者，因此其他人看不到这些颜色；{@code always} 让它们
 * 不依赖本能键。
 */
public final class FerrymanClientHighlights {
    /** 瞥视：dark red, as the owner's spec writes it. / 瞥视：深红，按所有者设定。 */
    private static final int GAZE_COLOR = 0x8C1B1B;
    /** 怜悯：pale blue. / 怜悯：淡蓝。 */
    private static final int MERCY_COLOR = 0x8FD3FF;
    /** Fresh, not yet ferried. / 尚未摆渡的新鲜遗体。 */
    private static final int FRESH_BODY_COLOR = 0xFFD27A;
    private static final int PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 40;

    private FerrymanClientHighlights() {
    }

    static void register() {
        GetInstinctHighlight.EVENT.register(FerrymanClientHighlights::highlight);
        GetInstinctHighlight.EVENT.register(FerrymanClientHighlights::suppressVeiled);
    }

    /**
     * The 5 s after a feign death: no instinct outline for anyone, the Ferryman included. Returning {@code skip()} is
     * the only suppression the pinned Wathe offers; it carries no priority, so this listener must run before the ones
     * that colour the target.
     * 假死结束后的 5 秒：任何人都描不出他，摆渡人也不例外。固定版 Wathe 只提供 {@code skip()} 这一种抑制；它不带优先级，
     * 因此本监听器必须排在会给目标上色的监听器之前。
     */
    private static @Nullable GetInstinctHighlight.HighlightResult suppressVeiled(@Nullable Entity target) {
        if (!(target instanceof PlayerEntity player)) {
            return null;
        }
        return FerrymanInstinctVeilComponent.KEY.get(player).isVeiled()
                ? GetInstinctHighlight.HighlightResult.skip()
                : null;
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(@Nullable Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity self = client.player;
        if (self == null || target == null || target == self || !FerrymanClient.isLocalFerryman(client)) {
            return null;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(self);
        if (target instanceof PlayerEntity player && component.hasGaze(player.getUuid())) {
            return GetInstinctHighlight.HighlightResult.always(GAZE_COLOR, PRIORITY);
        }
        if (target instanceof PlayerBodyEntity body) {
            UUID owner = body.getPlayerUuid();
            if (owner == null) {
                return null;
            }
            return GetInstinctHighlight.HighlightResult.always(
                    component.hasFerried(owner) ? MERCY_COLOR : FRESH_BODY_COLOR,
                    PRIORITY
            );
        }
        return null;
    }
}
