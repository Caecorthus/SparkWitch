package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

import dev.doctor4t.wathe.block.DoorPartBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;

/** Only actual doors, never trapdoors or gates. / 仅门，不含活板门或栅栏门。 */
public final class FisherDoorPassingRules {
    private FisherDoorPassingRules() {
    }

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorPartBlock || state.getBlock() instanceof DoorBlock;
    }
}
