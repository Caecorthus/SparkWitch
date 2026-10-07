package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerLoadoutService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The Potion Gunner's bound anti-tank launcher. Holding use aims down the scope; loading happens in the inventory.
 * Firing is a separate C2S intent handled by {@link PotionLauncherFireService}. Use is not bound to the role: anyone
 * holding it may scope, load and fire (owner rule 2026-10-04, {@code OffMatchUse}). It is deliberately outside
 * {@code wathe:guns}, so Wathe gun punishment, gun pickup, and gun packets never apply.
 * 药炮手绑定的反坦克炮筒。按住使用键开镜；装填在背包中完成。发射是独立的 C2S 意图，由
 * {@link PotionLauncherFireService} 处理。使用不绑定职业：任何持有者都能开镜、装填与发射（所有者规则 2026-10-04，
 * {@code OffMatchUse}）。刻意不加入 {@code wathe:guns}，因此 Wathe 的误杀惩罚、拾枪与开枪数据包都不适用。
 */
public class PotionLauncherItem extends Item {
    /** Scope hold length: effectively endless, use ends on release. / 开镜持续时间：近乎无限，松开即结束。 */
    public static final int MAX_USE_TICKS = 72_000;
    static final String LOADED_TOOLTIP_KEY = "item.sparkwitch.anti_tank_launcher.tooltip.loaded";
    static final String EMPTY_TOOLTIP_KEY = "item.sparkwitch.anti_tank_launcher.tooltip.empty";
    static final String ALREADY_LOADED_KEY = "message.sparkwitch.potion_gunner.already_loaded";
    private static final int TOOLTIP_LINES = 2;

    public PotionLauncherItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    /**
     * Holding use only starts the scope (the client reads {@code isUsingItem()} for zoom); nothing else happens on
     * either side. Any holder may scope. The offhand never scopes, because only the main hand fires.
     * 按住使用键只会开始开镜（客户端读取 {@code isUsingItem()} 进行缩放）；两端都不做其他事。任何持有者都能开镜。
     * 副手不会开镜，因为只有主手能发射。
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
     * Fabric seam: loading, unloading, and firing only rewrite CUSTOM_DATA, so the held launcher never replays the
     * equip animation (it is not in {@code wathe:guns}, whose items Wathe already exempts).
     * Fabric 接缝：装填、退弹与发射只改写 CUSTOM_DATA，因此手持炮筒不会重播换手动画（它不在 Wathe 已豁免的
     * {@code wathe:guns} 中）。
     */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack,
                                                  ItemStack newStack) {
        return false;
    }

    /** Left-click fires; the held launcher never breaks blocks. / 左键用于发射；手持炮筒永不破坏方块。 */
    @Override
    public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
        return false;
    }

    /**
     * Inventory loading, modelled on the Hunter shotgun: right-click the launcher with a shell on the cursor to load
     * one, or with an empty cursor to unload. Both sides decide through {@link PotionLauncherLoadRules}; the client
     * only predicts whether the click is consumed and the server mutates, so the slot sync corrects any mismatch.
     * Eligibility never checks the role ({@link PotionGunnerLoadoutService#mayLoad}).
     * 背包装填，参照猎人霰弹枪：光标持炮弹右键炮筒装入一发，空光标右键退弹。两端都经 {@link PotionLauncherLoadRules}
     * 判定；客户端只预测点击是否被消耗，由服务端修改，栏位同步会纠正任何偏差。资格判定从不检查职业
     * （{@link PotionGunnerLoadoutService#mayLoad}）。
     */
    @Override
    public boolean onClicked(ItemStack launcher, ItemStack cursor, Slot slot, ClickType clickType,
                             PlayerEntity player, StackReference cursorStackReference) {
        PotionShellType cursorShell = cursor.getItem() instanceof PotionShellItem shell && !cursor.isEmpty()
                ? shell.shellType() : null;
        PotionShellType loaded = PotionLauncherLoad.loaded(launcher).orElse(null);
        PotionLauncherLoadRules.Action action = PotionLauncherLoadRules.decide(
                clickType == ClickType.RIGHT,
                cursorShell,
                cursor.isEmpty(),
                loaded,
                player.isCreative() || PotionGunnerLoadoutService.mayLoad(player),
                ControlExpertStun.isStunned(player) || SeekerRemoteSessionService.isLocked(player));
        if (player instanceof ServerPlayerEntity serverPlayer) {
            applyOnServer(serverPlayer, action, launcher, cursor, cursorShell, loaded, cursorStackReference);
        }
        return action.consumesClick();
    }

    private static void applyOnServer(ServerPlayerEntity player, PotionLauncherLoadRules.Action action,
                                      ItemStack launcher, ItemStack cursor, @Nullable PotionShellType cursorShell,
                                      @Nullable PotionShellType loaded, StackReference cursorStackReference) {
        switch (action) {
            case LOAD -> {
                PotionLauncherLoad.setLoaded(launcher, cursorShell);
                cursor.decrement(1);
                cursorStackReference.set(cursor);
                player.playSoundToPlayer(SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), SoundCategory.PLAYERS,
                        0.8F, 1.0F);
            }
            case UNLOAD -> {
                // Cursor semantics (D9): the shell lands on the empty cursor, the one place the player can always
                // reach while the screen is open. On close vanilla returns the cursor hotbar-first; a shell that still
                // ends up in a hidden slot is surfaced by the lifecycle sweep (PotionGunnerLoadoutService).
                // 光标语义（D9）：炮弹落在空光标上，界面打开时玩家总能触及。关闭界面时原版优先把光标物品放回快捷栏；
                // 仍落入隐藏栏位的炮弹由生命周期清扫移回快捷栏（PotionGunnerLoadoutService）。
                PotionLauncherLoad.setLoaded(launcher, null);
                cursorStackReference.set(new ItemStack(SparkWitchItems.potionShell(loaded)));
                player.playSoundToPlayer(SoundEvents.ITEM_CROSSBOW_LOADING_START.value(), SoundCategory.PLAYERS,
                        0.8F, 1.2F);
            }
            case REFUSE_LOADED -> player.sendMessage(
                    Text.translatable(ALREADY_LOADED_KEY).withColor(PotionGunnerRules.COLOR), true);
            default -> {
            }
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Optional<PotionShellType> loaded = PotionLauncherLoad.loaded(stack);
        tooltip.add(loaded
                .map(shell -> Text.translatable(LOADED_TOOLTIP_KEY,
                        SparkWitchItems.potionShell(shell).getName().copy().withColor(shell.color())))
                .orElseGet(() -> Text.translatable(EMPTY_TOOLTIP_KEY))
                .formatted(Formatting.GRAY));
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.anti_tank_launcher.tooltip.line" + line)
                    .formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
