package dev.caecorthus.sparkwitch.item.defense;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Which Wathe trays hold a poured Defense Vial. One vial waits for exactly one serving: the next living participant
 * who takes anything from that tray gets the layer and the tray is clean again (Wathe's poison vial works the same
 * way). A second vial is refused while one waits, so it is never wasted. A pour is a use of the vial: it is refused
 * while the vial cools down and starts the same cooldown as a drink, so a tray never bypasses it.
 * <p>
 * The ledger is keyed by dimension and position and stamped with the match id, so a chunk reload (a new block-entity
 * instance) keeps the vial, another dimension is another tray, and a vial never survives into the next round even
 * though Wathe rebuilds the train on the same coordinates. It is cleared at both round edges and never saved. Server
 * thread only: pours come from {@code FoodPlatterBlock#onUse} and takes from {@code GameRecordManager#recordPlatterTake},
 * both on the server thread.
 * <p>
 * 记录哪些 Wathe 托盘里倒有防御药剂。一瓶药剂只等一份：下一个从该托盘取用任何东西的存活参与者获得护盾，托盘随即恢复干净
 * （与 Wathe 毒药瓶相同）。已有药剂在等待时拒绝第二瓶，因此不会白白浪费。倒入也算一次使用：药剂冷却中拒绝倒入，倒入后进入
 * 与饮用相同的冷却，因此托盘绝不能绕过冷却。
 * <p>
 * 账本以维度与坐标为键并记下对局 id：区块重新加载（新的方块实体实例）不会丢失药剂，其他维度是另一个托盘；即使 Wathe 每局都在
 * 相同坐标重建列车，药剂也绝不会带入下一局。回合两端都会清空，且不做存档。仅限服务端线程：倒入来自
 * {@code FoodPlatterBlock#onUse}，取用来自 {@code GameRecordManager#recordPlatterTake}，两者都在服务端线程。
 */
public final class DefenseVialTrays {
    private static final Map<TrayKey, Pour> POURED = new HashMap<>();

    private DefenseVialTrays() {
    }

    /**
     * Pours the vial in {@code player}'s main hand into the tray at {@code platterPos}. Returns false (the click falls
     * through to Wathe) outside a running round or for a non-participant; true when the click was handled, including a
     * refused pour (vial cooling down, or a vial already waiting), which leaves the vial in hand.
     * 把 {@code player} 主手的药剂倒进 {@code platterPos} 处的托盘。不在进行中的回合或非参与者时返回 false（点击交给 Wathe
     * 处理）；点击已处理时返回 true，包括被拒绝的倒入（药剂冷却中或已有药剂在等待），药剂留在手中。
     */
    public static boolean tryPour(ServerPlayerEntity player, BlockPos platterPos) {
        UUID match = DefenseVialService.activeMatchId();
        if (match == null || !DefenseVialService.inActiveRound(player)) {
            return false;
        }
        if (player.getItemCooldownManager().isCoolingDown(SparkWitchItems.defenseVial())) {
            // Same as any vanilla item on cooldown: nothing happens; the hotbar shows the cooldown.
            // 与原版冷却中的物品一样：什么都不发生；快捷栏会显示冷却。
            return true;
        }
        TrayKey tray = TrayKey.of(player, platterPos);
        Pour waiting = POURED.get(tray);
        if (waiting != null && DefenseVialRules.pourStillLive(waiting.matchId(), match)) {
            player.sendMessage(Text.translatable("message.sparkwitch.defense_vial.already_poured"), true);
            return true;
        }
        POURED.put(tray, new Pour(player.getUuid(), match));
        player.getItemCooldownManager().set(SparkWitchItems.defenseVial(), DefenseVialRules.COOLDOWN_TICKS);
        player.getMainHandStack().decrementUnlessCreative(1, player);
        player.getInventory().markDirty();
        // Wathe's own poison-pour sound, private like Wathe's. / 沿用 Wathe 下毒的音效，与 Wathe 一样只给本人。
        player.playSoundToPlayer(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.5F, 1.0F);
        player.sendMessage(Text.translatable("message.sparkwitch.defense_vial.poured"), true);
        DefenseVialService.recordPour(player, platterPos);
        return true;
    }

    /**
     * A serving left the tray at {@code platterPos} (any provider's take path). A living participant takes the waiting
     * vial's layer; anyone else leaves it waiting.
     * 一份食物或饮品离开了 {@code platterPos} 处的托盘（任一提供方的取用路径）。存活参与者拿走等待中药剂的护盾；其他人则不会
     * 消耗它。
     */
    public static void onServingTaken(ServerPlayerEntity taker, BlockPos platterPos) {
        TrayKey tray = TrayKey.of(taker, platterPos);
        Pour waiting = POURED.get(tray);
        if (waiting == null) {
            return;
        }
        if (!(taker.getServerWorld().getBlockEntity(platterPos) instanceof BeveragePlateBlockEntity)
                || !DefenseVialRules.pourStillLive(waiting.matchId(), DefenseVialService.activeMatchId())) {
            POURED.remove(tray);
            return;
        }
        if (!DefenseVialService.inActiveRound(taker)) {
            return;
        }
        POURED.remove(tray);
        DefenseVialService.shieldTaker(taker, waiting.pourer(), platterPos);
    }

    /** Round edges: drop every waiting vial. / 回合两端：清空所有等待中的药剂。 */
    public static void clear() {
        POURED.clear();
    }

    private record TrayKey(RegistryKey<World> world, BlockPos pos) {
        static TrayKey of(ServerPlayerEntity player, BlockPos pos) {
            return new TrayKey(player.getServerWorld().getRegistryKey(), pos.toImmutable());
        }
    }

    private record Pour(UUID pourer, UUID matchId) {
    }
}
