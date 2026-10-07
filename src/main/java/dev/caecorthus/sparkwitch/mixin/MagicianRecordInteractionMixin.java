package dev.caecorthus.sparkwitch.mixin;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerPlayerInteractionManager.class)
public abstract class MagicianRecordInteractionMixin {
 @Shadow @Final protected ServerPlayerEntity player;
 @Inject(method="interactItem",at=@At("RETURN")) private void sparkwitch$recordItem(ServerPlayerEntity p,World world,ItemStack stack,Hand hand,CallbackInfoReturnable<ActionResult> cir){if(cir.getReturnValue().isAccepted()&&!MagicianServerHooks.skipGenericUse(stack)) MagicianServerHooks.recordUse(player,hand);}
 @Inject(method="interactBlock",at=@At("RETURN")) private void sparkwitch$recordBlock(ServerPlayerEntity p,World world,ItemStack stack,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> cir){
  // Crowbar 的 Wathe 实现完成开门后返回 PASS，不能只看 ActionResult，否则该动作永远不会进入录制。
  boolean watheDoorTool = stack.isOf(dev.doctor4t.wathe.index.WatheItems.LOCKPICK)
          || stack.isOf(dev.doctor4t.wathe.index.WatheItems.CROWBAR);
  if((cir.getReturnValue().isAccepted() || watheDoorTool)&&!MagicianServerHooks.skipGenericUse(stack)) MagicianServerHooks.recordBlockUse(player,hand,hit);
 }
}
