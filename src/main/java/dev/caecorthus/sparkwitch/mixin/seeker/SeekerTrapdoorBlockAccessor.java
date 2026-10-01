package dev.caecorthus.sparkwitch.mixin.seeker;

import net.minecraft.block.BlockSetType;
import net.minecraft.block.TrapdoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to vanilla's private trapdoor {@code blockSetType}, used only by the car's own use
 * ({@code SeekerCarUseRules} for the hand-openable check, {@code SeekerCarUseService} for the toggle sounds): the
 * car flips a trapdoor as the source itself, because vanilla's use excludes the acting body from its own sound.
 * Remapped vanilla field; no behaviour of the target changes. Both sides.
 * 只读访问原版活板门私有的 {@code blockSetType}，仅供小车自身交互使用（{@code SeekerCarUseRules} 判断能否徒手开启，
 * {@code SeekerCarUseService} 取开关音效）：小车以自身为声源翻转活板门，因为原版的使用逻辑会把行动的本体排除在
 * 其自身音效之外。原版字段按常规重映射；不改变目标的任何行为。两端通用。
 */
@Mixin(TrapdoorBlock.class)
public interface SeekerTrapdoorBlockAccessor {
    @Accessor("blockSetType")
    BlockSetType sparkwitch$getBlockSetType();
}
