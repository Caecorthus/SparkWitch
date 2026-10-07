package dev.caecorthus.sparkwitch.roles.civilian.usec;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The USEC bolt-action sniper rifle. Holding use only scopes (the client reads {@code isUsingItem()}); firing is the
 * {@code sparkwitch:fire_usec_rifle} C2S intent and attachments use {@code sparkwitch:usec_attachment}, both validated
 * by the server. Its state lives in {@link UsecRifleState}. It is deliberately outside {@code wathe:guns}, so Wathe's
 * gun packets, gun-drop and pickup rules never apply to it (plan conclusion 3).
 * USEC 栓动狙击步枪。按住使用键只会开镜（客户端读取 {@code isUsingItem()}）；开火是 {@code sparkwitch:fire_usec_rifle}
 * C2S 意图，配件操作走 {@code sparkwitch:usec_attachment}，均由服务端校验。状态存放在 {@link UsecRifleState}。
 * 刻意不加入 {@code wathe:guns}，因此 Wathe 的开枪数据包、掉枪与拾枪规则都不适用（计划结论 3）。
 */
public class UsecRifleItem extends Item {
    /** Scope hold length: effectively endless, use ends on release. / 开镜持续时间：近乎无限，松开即结束。 */
    public static final int MAX_USE_TICKS = 72_000;
    static final String CHAMBER_TOOLTIP_KEY = "item.sparkwitch.usec_rifle.tooltip.chamber";
    static final String MAGAZINE_TOOLTIP_KEY = "item.sparkwitch.usec_rifle.tooltip.magazine";
    static final String NO_MAGAZINE_TOOLTIP_KEY = "item.sparkwitch.usec_rifle.tooltip.no_magazine";
    static final String SUPPRESSOR_ON_TOOLTIP_KEY = "item.sparkwitch.usec_rifle.tooltip.suppressor_on";
    static final String SUPPRESSOR_OFF_TOOLTIP_KEY = "item.sparkwitch.usec_rifle.tooltip.suppressor_off";
    static final String HINT_TOOLTIP_KEY = "item.sparkwitch.usec_rifle.tooltip.hint";
    static final String EMPTY_KEY = "item.sparkwitch.usec.empty";

    public UsecRifleItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /**
     * Holding use only starts the scope; nothing else happens on either side. The offhand never scopes, because only
     * the main hand fires.
     * 按住使用键只会开始开镜；两端都不做其他事。副手不会开镜，因为只有主手能开火。
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    /** No bow-pull or eating animation while scoped. / 开镜时没有拉弓或进食动画。 */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_TICKS;
    }

    /**
     * Fabric seam: firing, bolting and attachments only rewrite CUSTOM_DATA, so the held rifle never replays the
     * equip animation.
     * Fabric 接缝：开火、拉栓与配件操作只改写 CUSTOM_DATA，因此手持步枪不会重播换手动画。
     */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    /** Left-click fires; the held rifle never breaks blocks. / 左键用于开火；手持步枪永不破坏方块。 */
    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        UsecRifleState state = UsecRifleState.read(stack);
        tooltip.add(Text.translatable(CHAMBER_TOOLTIP_KEY, state.chamber() == null
                ? Text.translatable(EMPTY_KEY) : state.chamber().label()).formatted(Formatting.GRAY));
        UsecMagazineContents magazine = state.magazine();
        if (magazine == null) {
            tooltip.add(Text.translatable(NO_MAGAZINE_TOOLTIP_KEY).formatted(Formatting.GRAY));
        } else {
            MutableText rounds = magazine.isEmpty()
                    ? Text.translatable(EMPTY_KEY) : UsecMagazineItem.roundsText(magazine.firingOrder());
            tooltip.add(Text.translatable(MAGAZINE_TOOLTIP_KEY, rounds, magazine.size(), UsecRules.MAGAZINE_CAPACITY)
                    .formatted(Formatting.GRAY));
        }
        tooltip.add(Text.translatable(state.suppressor() ? SUPPRESSOR_ON_TOOLTIP_KEY : SUPPRESSOR_OFF_TOOLTIP_KEY)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(HINT_TOOLTIP_KEY).formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
