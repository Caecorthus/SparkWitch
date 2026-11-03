package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.roles.killer.natsume.NatsumeComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * A hypnotised player loses instinct highlighting entirely.
 * <p>The event has no "off" value, so a fully transparent highlight at a priority above Wathe's default is returned:
 * the entity is still highlighted as far as the renderer is concerned, it just draws nothing.
 * 被催眠者完全失去本能透视。
 * <p>事件没有“关闭”值，因此返回一个高于 Wathe 默认优先级、完全透明的高亮：
 * 对渲染器而言目标仍被高亮，只是画出来什么都没有。
 */
public final class NatsumeInstinctHooks {

    private static final int BLANK = 0x00000000;
    private static final int PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 60;
    private static boolean registered;

    private NatsumeInstinctHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(NatsumeInstinctHooks::blankWhileHypnotised);
    }

    private static GetInstinctHighlight.HighlightResult blankWhileHypnotised(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity) || viewer == target) {
            return null;
        }
        if (!NatsumeComponent.KEY.get(viewer).isHypnotised()) {
            return null;
        }
        return new GetInstinctHighlight.HighlightResult(BLANK, false, PRIORITY);
    }
}
