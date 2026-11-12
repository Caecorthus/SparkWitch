package dev.caecorthus.sparkwitch.roles.neutral.koko;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** 泽渡可可's lightstick. 泽渡可可的应援棒。 */
public final class KokoItems {
    private KokoItems() {
    }

    /** 应援棒: 10 s of Speed II and doubled traffic while streaming. 应援棒：10 秒速度 II，直播时流量翻倍。 */
    public static final class Lightstick extends Item {
        public Lightstick(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
                return TypedActionResult.success(stack);
            }
            KokoService.useLightstick(player);
            return TypedActionResult.success(stack);
        }
    }
}
