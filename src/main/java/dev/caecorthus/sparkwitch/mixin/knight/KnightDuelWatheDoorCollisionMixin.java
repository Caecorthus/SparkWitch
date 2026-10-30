package dev.caecorthus.sparkwitch.mixin.knight;

import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelClientState;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelRules;
import dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelWorldComponent;
import dev.doctor4t.wathe.block.DoorPartBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wathe doors block movement in {@link DoorPartBlock#getCollisionShape}; duelists get an empty shape there, the
 * same seam the Professor's door-passing serum uses. Door rendering, state and interaction stay untouched.
 * Wathe 门真正挡人的位置是 {@link DoorPartBlock#getCollisionShape}；决斗双方在这里拿到空形状，
 * 与教授穿门试剂使用同一接缝。门的渲染、状态和交互不变。
 */
@Mixin(DoorPartBlock.class)
public abstract class KnightDuelWatheDoorCollisionMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$passDuelDoors(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityShapeContext entityContext)) {
            return;
        }
        Entity entity = entityContext.getEntity();
        if (entity instanceof PlayerEntity player
                && (KnightDuelWorldComponent.KEY.get(player.getWorld()).isDuelist(player.getUuid())
                || KnightDuelClientState.isDuelist(player.getUuid())
                || KnightDuelRules.isDuelTeam(player))) {
            cir.setReturnValue(VoxelShapes.empty());
        }
    }
}
