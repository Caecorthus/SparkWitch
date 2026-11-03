package dev.caecorthus.sparkwitch.item.consultant;

import dev.caecorthus.sparkwitch.component.ConsultantPlayerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.Set;

/** 记录册: right-click to list every identity this consultant has recorded. 右键查看已记录的身份。 */
public final class ConsultantRecordBookItem extends Item {

    public ConsultantRecordBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            Set<String> records = ConsultantPlayerComponent.KEY.get(serverPlayer).records();
            if (records.isEmpty()) {
                serverPlayer.sendMessage(Text.translatable("message.sparkwitch.consultant.empty"), true);
            } else {
                serverPlayer.sendMessage(Text.translatable("message.sparkwitch.consultant.header"), true);
                for (String line : records) {
                    serverPlayer.sendMessage(Text.literal("· " + line), true);
                }
            }
        }
        return TypedActionResult.success(stack);
    }
}
