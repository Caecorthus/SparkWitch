package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/** Server-authoritative fishing on a Wathe drink tray. / 服务端权威的饮料托盘钓鱼。 */
public final class FisherFishingService {
    private static final Identifier FISHING_PHASE = SparkWitch.id("fisher_fishing");
    private static boolean registered;

    private FisherFishingService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Stun and Seeker's session/device phases precede DEFAULT; Wraith, Last Stand and Wathe guards use DEFAULT.
        // 眩晕与搜寻者会话/设备阶段先于 DEFAULT；冤魂、背水一战及 Wathe 的门槛位于 DEFAULT。
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, FISHING_PHASE);
        UseBlockCallback.EVENT.register(FISHING_PHASE, FisherFishingService::interact);
        FisherReplayFormatters.register();
    }

    private static ActionResult interact(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isOf(SparkWitchItems.fishingRod())
                || player.isSpectator()
                || !(world.getBlockEntity(hit.getBlockPos()) instanceof BeveragePlateBlockEntity tray)
                || !tray.isDrink()) {
            return ActionResult.PASS;
        }
        // Fabric 0.7.13 sends this packet only for SUCCESS. The owner accepts one swing (ordinary tray use also
        // swings); outcomes stay server-only, and server CONSUME never adds a second swing.
        // Fabric 0.7.13 只为 SUCCESS 发包。所有者接受一次挥手（普通取饮料也挥手）；服务端独占结果，CONSUME 不再挥手。
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity fisher) {
            fish(fisher, hit);
        }
        return ActionResult.CONSUME;
    }

    private static void fish(ServerPlayerEntity fisher, BlockHitResult hit) {
        GameWorldComponent game = GameWorldComponent.KEY.get(fisher.getWorld());
        if (!FisherParticipants.isLivingParticipant(fisher) || !FisherRules.isFisher(game.getRole(fisher))
                || fisher.getItemCooldownManager().isCoolingDown(SparkWitchItems.fishingRod())) {
            return;
        }
        ItemStack bait = FisherInventory.findBait(fisher.getInventory());
        if (bait.isEmpty()) {
            fisher.sendMessage(Text.translatable("message.sparkwitch.fisher.no_bait"), true);
            return;
        }

        // Capacity must not depend on the catch, or a full hotbar can filter the random outcomes for free.
        // 容量不得依赖渔获种类，否则满快捷栏可以免费筛选随机结果。
        int slot = FisherInventory.catchSlot(fisher.getInventory().main, bait);
        if (slot < 0) {
            fisher.getItemCooldownManager().set(SparkWitchItems.fishingRod(), FisherRules.ROD_COOLDOWN_TICKS);
            fisher.sendMessage(Text.translatable("message.sparkwitch.fisher.inventory_full"), true);
            return;
        }
        FisherCatch caught = FisherCatchTable.roll(fisher.getServerWorld().random);
        bait.decrement(1);
        if (caught.givesItem()) {
            FisherInventory.deliver(fisher.getInventory().main, slot, catchStack(caught));
        } else if (caught == FisherCatch.PUFFERFISH) {
            FisherPufferfishService.spawnAt(fisher.getServerWorld(), hit.getBlockPos(), fisher);
        }
        fisher.getItemCooldownManager().set(SparkWitchItems.fishingRod(), FisherRules.ROD_COOLDOWN_TICKS);
        FisherInventory.sync(fisher);
        Text feedback = switch (caught) {
            case SKUNK -> Text.translatable("message.sparkwitch.fisher.skunk");
            case PUFFERFISH -> Text.translatable("message.sparkwitch.fisher.pufferfish");
            default -> Text.translatable("message.sparkwitch.fisher.caught", Text.translatable(caught.translationKey()));
        };
        fisher.sendMessage(feedback, true);
        fisher.playSoundToPlayer(SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 1f, 1f);
        fisher.playSoundToPlayer(SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1f, 1f);
        NbtCompound extra = new NbtCompound();
        extra.putString("catch", caught.name());
        GameRecordManager.recordItemUse(fisher, FisherRules.FISHING_ROD_ID, null, extra);
    }

    static ItemStack catchStack(FisherCatch caught) {
        return switch (caught) {
            case SALMON -> SparkWitchItems.salmon().getDefaultStack();
            case COD -> SparkWitchItems.cod().getDefaultStack();
            case CLOWNFISH -> SparkWitchItems.clownfish().getDefaultStack();
            case GOLDFISH -> SparkWitchItems.goldfish().getDefaultStack();
            case GLIMMERFISH -> SparkWitchItems.glimmerfish().getDefaultStack();
            case KEY_FISH -> SparkWitchItems.keyFish().getDefaultStack();
            case SWORDFISH -> SparkWitchItems.swordfish().getDefaultStack();
            case PUFFERFISH, SKUNK -> ItemStack.EMPTY;
        };
    }
}
