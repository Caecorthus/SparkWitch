package dev.caecorthus.sparkwitch.item.defense;

import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Which Wathe trays hold a poured Defense Vial. One vial waits for exactly one serving: the next player who takes
 * anything from that tray gets the layer and the tray is clean again (Wathe's poison vial works the same way). A
 * second vial is refused while one waits, so it is never wasted.
 * <p>
 * The ledger is keyed by the tray's block entity (weakly) and stamped with the match id: a broken or rebuilt tray is a
 * new block entity, another dimension is another block entity, and a vial never survives into the next round even
 * though Wathe rebuilds the train on the same coordinates. Server thread only: pours come from
 * {@code FoodPlatterBlock#onUse} and takes from {@code GameRecordManager#recordPlatterTake}, both on the server
 * thread. Nothing is saved, so a server restart mid-round drops waiting vials.
 * <p>
 * 记录哪些 Wathe 托盘里倒有防御药剂。一瓶药剂只等一份：下一个从该托盘取用任何东西的玩家获得护盾，托盘随即恢复干净（与
 * Wathe 毒药瓶相同）。已有药剂在等待时拒绝第二瓶，因此不会白白浪费。
 * <p>
 * 账本以托盘的方块实体为键（弱引用），并记下对局 id：被破坏或重建的托盘是新的方块实体，其他维度也是不同的方块实体；即使
 * Wathe 每局都在相同坐标重建列车，药剂也绝不会带入下一局。仅限服务端线程：倒入来自 {@code FoodPlatterBlock#onUse}，取用来自
 * {@code GameRecordManager#recordPlatterTake}，两者都在服务端线程。不做存档，因此回合中途重启服务器会丢失等待中的药剂。
 */
public final class DefenseVialTrays {
    private static final Map<BeveragePlateBlockEntity, Pour> POURED = new WeakHashMap<>();

    private DefenseVialTrays() {
    }

    /**
     * Pours the vial in {@code player}'s main hand into {@code plate}. Returns false (the click falls through to
     * Wathe) outside a running round or for a non-participant; true when the click was handled, including a refused
     * second vial, which stays in hand.
     * 把 {@code player} 主手的药剂倒进 {@code plate}。不在进行中的回合或非参与者时返回 false（点击交给 Wathe 处理）；点击已
     * 处理时返回 true，包括被拒绝的第二瓶（留在手中）。
     */
    public static boolean tryPour(ServerPlayerEntity player, BeveragePlateBlockEntity plate, BlockPos platterPos) {
        UUID match = DefenseVialService.activeMatchId();
        if (match == null || !DefenseVialService.inActiveRound(player)) {
            return false;
        }
        Pour waiting = POURED.get(plate);
        if (waiting != null && DefenseVialRules.pourStillLive(waiting.matchId(), match)) {
            player.sendMessage(Text.translatable("message.sparkwitch.defense_vial.already_poured"), true);
            return true;
        }
        POURED.put(plate, new Pour(player.getUuid(), match));
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
        if (!(taker.getServerWorld().getBlockEntity(platterPos) instanceof BeveragePlateBlockEntity plate)) {
            return;
        }
        Pour waiting = POURED.get(plate);
        if (waiting == null) {
            return;
        }
        if (!DefenseVialRules.pourStillLive(waiting.matchId(), DefenseVialService.activeMatchId())) {
            POURED.remove(plate);
            return;
        }
        if (!DefenseVialService.inActiveRound(taker)) {
            return;
        }
        POURED.remove(plate);
        DefenseVialService.shieldTaker(taker, waiting.pourer(), platterPos);
    }

    /** Round edges: drop every waiting vial. / 回合两端：清空所有等待中的药剂。 */
    public static void clear() {
        POURED.clear();
    }

    private record Pour(UUID pourer, UUID matchId) {
    }
}
