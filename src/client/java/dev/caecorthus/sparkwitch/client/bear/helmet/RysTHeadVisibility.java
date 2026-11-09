package dev.caecorthus.sparkwitch.client.bear.helmet;

import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * D7: a worn Rys-T is not drawn on a wearer whose body this viewer does not see at all (invisible, and not shown
 * translucent to this viewer by team or spectator rules), so no helmet floats in the air. Every other head item, all
 * armor and every visible wearer keep vanilla rendering; Wathe's psycho, the Blind view and the SparkTraits Pig body
 * already skip head features on their own. Presentation only.
 * D7：当观察者完全看不到佩戴者身体时（隐身，且按队伍或旁观规则不对该观察者半透明显示），不绘制其所戴的 Rys-T，避免头盔悬空。
 * 其他头部物品、所有护甲与所有可见的佩戴者保持原版渲染；Wathe 疯魔、盲人视图与 SparkTraits 猪身本就会自行跳过头部特征层。
 * 仅为展示。
 */
public final class RysTHeadVisibility {
    private RysTHeadVisibility() {
    }

    /** True when the head feature must skip {@code wearer}'s Rys-T. / 头部特征层需要跳过该实体的 Rys-T 时为真。 */
    public static boolean hidesHelmet(@Nullable LivingEntity wearer) {
        if (wearer == null || !wearer.isInvisible() || RysTHelmetItem.wornHelmet(wearer).isEmpty()) {
            return false;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        return hides(true, true, viewer == null || wearer.isInvisibleTo(viewer));
    }

    /** Pure rule behind {@link #hidesHelmet}. / {@link #hidesHelmet} 背后的纯规则。 */
    public static boolean hides(boolean wearsRysT, boolean invisible, boolean invisibleToViewer) {
        return wearsRysT && invisible && invisibleToViewer;
    }
}
