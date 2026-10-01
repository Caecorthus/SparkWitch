package dev.caecorthus.sparkwitch.mixin.seeker;

import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.WoodType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to vanilla's private fence-gate wood {@code type}, used only by {@code SeekerCarUseService} for the
 * gate sounds: the car swings a gate by its own heading and as the sound source itself, because vanilla's use turns
 * the gate by the acting body's facing and excludes that body from its own sound. Remapped vanilla field; no
 * behaviour of the target changes. Server only.
 * 只读访问原版栅栏门私有的木材 {@code type}，仅供 {@code SeekerCarUseService} 取栅栏门音效：小车按自身朝向、并以自身
 * 为声源开关栅栏门，因为原版的使用逻辑按行动本体的朝向转动栅栏门，并把该本体排除在其自身音效之外。
 * 原版字段按常规重映射；不改变目标的任何行为。仅服务端。
 */
@Mixin(FenceGateBlock.class)
public interface SeekerFenceGateBlockAccessor {
    @Accessor("type")
    WoodType sparkwitch$getWoodType();
}
