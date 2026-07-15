package dev.caecorthus.sparkwitch.roles.civilian.perfumer;

import dev.caecorthus.sparkwitch.component.PerfumerPlayerComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Marks another living player in the owning Perfumer's private state.
 * 将另一名存活玩家写入调香师自己的私有标记状态。
 */
public final class PerfumeEssenceItem extends Item {
    public PerfumeEssenceItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity target)) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverUser)
                || !(target instanceof ServerPlayerEntity serverTarget)) {
            return ActionResult.PASS;
        }

        boolean allowed = PerfumerRules.canApplyPerfumeEssence(
                PerfumerRuntime.isActivePerfumer(serverUser),
                PerfumerRuntime.isActivePlayer(serverUser),
                PerfumerRuntime.isActivePlayer(serverTarget),
                serverUser.getUuid().equals(serverTarget.getUuid())
        );
        if (!allowed || !PerfumerPlayerComponent.KEY.get(serverUser).mark(serverTarget.getUuid())) {
            return ActionResult.FAIL;
        }

        stack.decrementUnlessCreative(1, serverUser);
        serverUser.sendMessage(
                Text.translatable("message.sparkwitch.perfumer.marked", serverTarget.getDisplayName()),
                true
        );
        return ActionResult.SUCCESS;
    }
}
