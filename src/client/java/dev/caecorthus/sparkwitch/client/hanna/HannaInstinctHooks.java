package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * A floating 汉娜 cannot be picked up by instinct highlighting.
 * <p>The float flag is synced to everyone, so this filters per target rather than per viewer.
 * 浮游中的汉娜无法被本能透视看到。
 * <p>浮游标记同步给了所有人，因此这里按目标过滤，而不是按观察者。
 */
public final class HannaInstinctHooks {

    private static final int BLANK = 0x00000000;
    private static final int PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 70;
    private static boolean registered;

    private HannaInstinctHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(HannaInstinctHooks::hideWhileFloating);
    }

    private static GetInstinctHighlight.HighlightResult hideWhileFloating(Entity target) {
        if (!(target instanceof PlayerEntity player)) {
            return null;
        }
        if (HannaComponent.KEY.get(player).getFloatSeconds() <= 0) {
            return null;
        }
        return new GetInstinctHighlight.HighlightResult(BLANK, false, PRIORITY);
    }
}
