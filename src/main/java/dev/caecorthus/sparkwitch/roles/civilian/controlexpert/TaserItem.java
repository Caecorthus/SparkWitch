package dev.caecorthus.sparkwitch.roles.civilian.controlexpert;

import dev.caecorthus.sparkwitch.compat.SparkTraitsControlExpertBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Control Expert Taser: a non-lethal, server-authoritative hitscan. It never joins Wathe's gun tag or gun packet, so
 * no forged client packet can turn it into a lethal shot, and nothing here runs a kill or its protections. The client
 * only recoils; vanilla's use-item packet (which carries the aim) is the whole request.
 * 控场专家的电击枪：非致命、由服务端权威判定的直射道具。它永不加入 Wathe 枪械标签或枪械数据包，
 * 因此伪造的客户端数据包无法把它变成致命射击，这里也不会触发任何击杀或其保护。客户端只做后坐反馈；
 * 原版使用物品数据包（已携带瞄准方向）就是全部请求。
 */
public final class TaserItem extends Item {
    private static final int TOOLTIP_LINES = 3;
    /** Local-only recoil, applied after vanilla captured the aim for the use packet. / 仅本地的后坐，在原版为使用数据包记录瞄准方向之后施加。 */
    private static final float RECOIL_PITCH = 2.0F;
    private static final SoundEvent FIRE_SOUND = SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT;
    private static final float FIRE_VOLUME = 0.6F;
    private static final float FIRE_PITCH = 1.8F;

    public TaserItem(Settings settings) {
        super(settings);
    }

    public static Item.Settings createSettings() {
        return new Item.Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            user.setPitch(user.getPitch() - RECOIL_PITCH);
            return TypedActionResult.consume(stack);
        }
        if (!(user instanceof ServerPlayerEntity ce) || !(world instanceof ServerWorld serverWorld)
                || !ControlExpertTargeting.canUse(ce)
                || SparkTraitsKillerBridge.blocksWeaponAction(ce, stack)) {
            return TypedActionResult.fail(stack);
        }
        double range = ControlExpertRules.taserRange(SparkTraitsControlExpertBridge.marksmanRangeMultiplier(ce));
        // Players the Control Expert may not affect are filtered out before the nearest pick, so they are transparent.
        // The shooter aimed at its delayed client view of others, so the server tests their rewound volumes.
        // 控场专家无法影响的玩家在选取最近者之前就被过滤，因此对射线透明。
        // 射手瞄准的是客户端延迟画面中的其他玩家，因此服务端改为检测其回溯后的命中体积。
        ServerPlayerEntity target = ControlExpertTaserTargeting.findTarget(ce, range, serverWorld.getPlayers(),
                candidate -> ControlExpertTargeting.canAffect(ce, candidate, ControlExpertRules.TASER_ACTION),
                candidate -> PlayerHitboxHistory.hitVolumes(ce, candidate, ControlExpertRules.TASER_BOX_EXPANSION));
        // Seeker seam: a nearer Seeker device absorbs the dart and breaks; nobody is stunned (replay hit:false).
        // 搜寻者接缝：更近的搜寻者设备吸收电击镖并被打坏；不眩晕任何人（回放 hit:false）。
        target = SeekerDeviceHits.onTaserFired(ce, target, range);
        if (target != null) {
            ControlExpertStun.apply(ce, target, ControlExpertRules.TASER_TAIL_TICKS);
        }
        // A miss costs the same as a hit. Plain vanilla cooldown so SparkTraits Fast Hands may shorten it.
        // 未命中与命中代价相同。普通原版冷却，SparkTraits 快手可缩短。
        ce.getItemCooldownManager().set(this, ControlExpertRules.TASER_COOLDOWN);
        NbtCompound extra = new NbtCompound();
        extra.putBoolean("hit", target != null);
        GameRecordManager.recordItemUse(ce, ControlExpertRules.TASER_ID, target, extra);
        serverWorld.playSound(null, ce.getX(), ce.getY(), ce.getZ(), FIRE_SOUND, SoundCategory.PLAYERS,
                FIRE_VOLUME, FIRE_PITCH);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= TOOLTIP_LINES; line++) {
            tooltip.add(Text.translatable("item.sparkwitch.taser.tooltip.line" + line).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
