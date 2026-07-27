package dev.caecorthus.sparkwitch.client.curser;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Pure client gate for promoted Curser Witch-instinct brightness. / 晋升诅咒师魔女本能亮度的纯客户端门禁。 */
public final class CurserInstinctClientRules {
    private CurserInstinctClientRules() {
    }

    public static boolean shouldUseWitchInstinctLight(
            @Nullable Identifier roleId,
            boolean confirmedServer,
            boolean activeWraith,
            boolean promotedWraith,
            boolean viewerSpectatingOrCreative,
            boolean instinctEnabled
    ) {
        return confirmedServer
                && SparkWitchRoles.CURSER_ID.equals(roleId)
                && activeWraith
                && promotedWraith
                && !viewerSpectatingOrCreative
                && instinctEnabled;
    }
}
