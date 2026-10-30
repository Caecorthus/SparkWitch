package dev.caecorthus.sparkwitch.client.mixin;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the currently bound key for the duel G-key block. / 读取当前绑定键，用于决斗期间屏蔽 G 键。 */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("boundKey")
    InputUtil.Key sparkwitch$getBoundKey();
}
