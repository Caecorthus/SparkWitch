package dev.caecorthus.sparkwitch.roles.killer.magician;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** 给 Wathe mixin 和后续扩展职业复用的录制公开入口。 */
public final class MagicianServerHooks {
    private MagicianServerHooks() {}
    public static @Nullable MagicianPlayerComponent recording(@Nullable ServerPlayerEntity p) { if(p==null)return null; var game=GameWorldComponent.KEY.get(p.getWorld()); var role=game.getRole(p); if(role==null||!SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())||!game.isRunning()||!GameFunctions.isPlayerAliveAndSurvival(p))return null; var c=MagicianPlayerComponent.KEY.get(p); return c.stage()==MagicianStage.RECORDING?c:null; }
    public static void recordAttack(@Nullable ServerPlayerEntity p){ if(recording(p)!=null)recording(p).record(MagicianRecordedAction.Type.ATTACK); }
    public static void record(@Nullable ServerPlayerEntity p, MagicianRecordedAction.Type type){ if(recording(p)!=null)recording(p).record(type); }
    public static void recordUse(@Nullable ServerPlayerEntity p, Hand h){ if(recording(p)!=null)recording(p).recordUse(h); }
    public static void recordBlockUse(@Nullable ServerPlayerEntity p, Hand h, BlockHitResult hit){ if(recording(p)!=null)recording(p).recordBlockUse(h,hit); }
    public static void recordRelease(@Nullable ServerPlayerEntity p){ if(recording(p)!=null)recording(p).record(MagicianRecordedAction.Type.RELEASE_USE_ITEM); }
    public static void recordSwing(@Nullable ServerPlayerEntity p, Hand h){ if(recording(p)!=null)recording(p).recordSwing(h); }
    public static void recordSlot(@Nullable ServerPlayerEntity p, int slot){ if(recording(p)!=null)recording(p).recordSlot(slot); }
    public static boolean skipGenericUse(ItemStack stack){ return stack.isOf(dev.doctor4t.wathe.index.WatheItems.KNIFE)||stack.isOf(dev.doctor4t.wathe.index.WatheItems.REVOLVER); }
}
