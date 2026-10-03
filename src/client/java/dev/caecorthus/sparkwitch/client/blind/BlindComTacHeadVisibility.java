package dev.caecorthus.sparkwitch.client.blind;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * D5 / C15: a worn ComTac VIII ({@code sparkwitch:comtac_viii}) is never drawn on a head while a Wathe round runs, for
 * every viewer (third person, F5, inventory previews), so nobody can spot the headset. Only the ComTac's head model is
 * skipped; every other head item and all armor keep vanilla rendering, and outside rounds it renders normally. The
 * server still sends the head slot, the wearer keeps the item and its icon in the armor slot, and gameplay reads
 * ({@code BlindParticipants.wearsComTac}) are untouched. Presentation only.
 * D5 / C15：Wathe 对局进行时，戴着的 ComTac VIII（{@code sparkwitch:comtac_viii}）对所有观察者（第三人称、F5、背包预览）
 * 都不绘制在头上，因此无人能看出耳机。只跳过 ComTac 的头部模型；其他头部物品与所有护甲保持原版渲染，对局外正常显示。
 * 服务器仍同步头部槽，佩戴者仍持有该物品并在护甲槽看到图标，玩法读取（{@code BlindParticipants.wearsComTac}）不受影响。
 * 仅为展示。
 */
public final class BlindComTacHeadVisibility {
    private BlindComTacHeadVisibility() {
    }

    /** True when the head feature must skip {@code wearer}'s head item. / 头部特征层需要跳过该实体头部物品时为真。 */
    public static boolean hidesHeadItem(@Nullable LivingEntity wearer) {
        if (wearer == null || !SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        return hides(GameWorldComponent.KEY.get(wearer.getWorld()).isRunning(),
                wearer.getEquippedStack(EquipmentSlot.HEAD).isOf(SparkWitchItems.comTac()));
    }

    /** Pure rule behind {@link #hidesHeadItem}. / {@link #hidesHeadItem} 背后的纯规则。 */
    public static boolean hides(boolean roundRunning, boolean wearsComTac) {
        return roundRunning && wearsComTac;
    }
}
