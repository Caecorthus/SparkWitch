package dev.caecorthus.sparkwitch.client.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.client.render.ModelElementRotationRules;
import net.minecraft.util.JsonHelper;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Rescues element angles vanilla 1.21.1 refuses to parse but already bakes correctly (the 1.21.6 rule).
 * It wraps vanilla, any @Overwrite and every other mod's in-body injector, and rescues only vanilla's own rejection
 * of the raw JSON angle, so it never replaces a value another mod returns (for example Handcrafted's unbounded HEAD
 * relaxation) nor overrides an angle another mod rejected or rewrote. Other mods' own @WrapMethod handlers nest
 * around or inside this one by mixin priority.
 * 挽救原版 1.21.1 拒绝解析、但烘焙器已能正确渲染的元素角度（即 1.21.6 规则）。
 * 它包裹原版、任何 @Overwrite 以及其他模组在方法体内的注入，且只挽救原版对 JSON 原始角度的拒绝，
 * 因此既不替换其他模组返回的值（例如 Handcrafted 在 HEAD 处的无上限放宽），也不推翻其他模组拒绝或改写的角度。
 * 其他模组自己的 @WrapMethod 按 mixin 优先级嵌套在本处理器内外。
 */
@Mixin(targets = "net.minecraft.client.render.model.json.ModelElement$Deserializer")
public abstract class ModelElementRotationAngleMixin {
    @WrapMethod(method = "deserializeRotationAngle")
    private float sparkwitch$rescueBakeableAngle(JsonObject rotation, Operation<Float> original) {
        try {
            return original.call(rotation);
        } catch (JsonParseException rejected) {
            if (JsonHelper.hasNumber(rotation, "angle")
                    && (!rotation.has("rescale") || JsonHelper.hasPrimitive(rotation, "rescale"))) {
                float angle = JsonHelper.getFloat(rotation, "angle");
                if (ModelElementRotationRules.isVanillaRejection(angle, rejected.getMessage())
                        && ModelElementRotationRules.canRescue(angle, JsonHelper.getBoolean(rotation, "rescale", false))) {
                    return angle;
                }
            }
            throw rejected;
        }
    }
}
