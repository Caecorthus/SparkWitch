package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.magician.MagicianTargetWidget;
import dev.caecorthus.sparkwitch.client.magician.MagicianTargetSelectionApi;
import dev.caecorthus.sparkwitch.client.magician.MagicianPageSwitchWidget;
import dev.caecorthus.sparkwitch.client.magician.MagicianActionWidget;
import dev.caecorthus.sparkwitch.roles.killer.magician.UseMagicianAbilityC2SPacket;
import dev.caecorthus.sparkwitch.client.inventory.SparkWitchInventoryButtonLayout;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Adds the Magician's paged disguise-target row to Wathe's limited inventory. */
@Mixin(LimitedInventoryScreen.class)
public abstract class MagicianInventoryButtonsMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Unique
    private final List<MagicianTargetWidget> sparkwitch$magicianTargets = new ArrayList<>();
    @Unique
    private MagicianPageSwitchWidget sparkwitch$magicianPrevious;
    @Unique
    private MagicianPageSwitchWidget sparkwitch$magicianNext;
    @Unique
    private final List<MagicianActionWidget> sparkwitch$magicianActions = new ArrayList<>();
    @Unique
    private int sparkwitch$magicianPage;

    protected MagicianInventoryButtonsMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    @Shadow @Final public ClientPlayerEntity player;

    @Inject(method="method_25426()V", at=@At("TAIL"))
    private void sparkwitch$addMagicianTargets(CallbackInfo ci) {
        if (player == null || player.networkHandler == null) return;
        var role = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (role == null || !dev.caecorthus.sparkwitch.SparkWitchRoles.MAGICIAN_ID.equals(role.identifier())) return;
        sparkwitch$magicianTargets.clear();
        sparkwitch$magicianPage = 0;
        int y = SparkWitchInventoryButtonLayout.getPlayerRowY(this.height);
        for (PlayerListEntry entry : MagicianTargetSelectionApi.onlinePlayers(player)) {
            sparkwitch$magicianTargets.add(this.addDrawableChild(new MagicianTargetWidget(0, y, entry)));
        }

        sparkwitch$magicianPrevious = this.addDrawableChild(new MagicianPageSwitchWidget(
                0,
                y,
                Items.PURPLE_DYE.getDefaultStack(),
                Text.translatable("ui.sparkwitch.magician.pagination.previous"),
                button -> {
                    sparkwitch$magicianPage--;
                    sparkwitch$refreshMagicianPage();
                }
        ));
        sparkwitch$magicianNext = this.addDrawableChild(new MagicianPageSwitchWidget(
                0,
                y,
                Items.LIME_DYE.getDefaultStack(),
                Text.translatable("ui.sparkwitch.magician.pagination.next"),
                button -> {
                    sparkwitch$magicianPage++;
                    sparkwitch$refreshMagicianPage();
                }
        ));
        sparkwitch$refreshMagicianPage();
        sparkwitch$addMagicianActions();
    }

    @Unique
    private void sparkwitch$addMagicianActions() {
        int y = SparkWitchInventoryButtonLayout.getPlayerRowY(this.height) + SparkWitchInventoryButtonLayout.SLOT_APART;
        ItemStack[] icons = {
                Items.GREEN_DYE.getDefaultStack(),
                Items.RED_DYE.getDefaultStack(),
                Items.BLUE_DYE.getDefaultStack(),
                Items.PURPLE_DYE.getDefaultStack()
        };
        int[] actions = {
                UseMagicianAbilityC2SPacket.START_RECORDING,
                UseMagicianAbilityC2SPacket.STOP_RECORDING,
                UseMagicianAbilityC2SPacket.START_PLAYBACK,
                UseMagicianAbilityC2SPacket.STOP_PLAYBACK
        };
        String[] labels = {"开始录制", "结束录制", "开始播放", "结束播放"};
        int startX = this.width / 2 - (icons.length * SparkWitchInventoryButtonLayout.SLOT_APART) / 2
                + SparkWitchInventoryButtonLayout.SLOT_X_OFFSET;
        for (int i = 0; i < icons.length; i++) {
            sparkwitch$magicianActions.add(this.addDrawableChild(new MagicianActionWidget(
                    startX + i * SparkWitchInventoryButtonLayout.SLOT_APART,
                    y,
                    icons[i],
                    Text.literal(labels[i]),
                    actions[i]
            )));
        }
    }

    @Unique
    private void sparkwitch$refreshMagicianPage() {
        int totalPages = SparkWitchInventoryButtonLayout.getTotalPageCount(sparkwitch$magicianTargets.size());
        sparkwitch$magicianPage = Math.max(0, Math.min(sparkwitch$magicianPage, totalPages - 1));

        int startIndex = sparkwitch$magicianPage * SparkWitchInventoryButtonLayout.PLAYERS_PER_PAGE;
        int endIndex = Math.min(
                startIndex + SparkWitchInventoryButtonLayout.PLAYERS_PER_PAGE,
                sparkwitch$magicianTargets.size()
        );
        int visibleCount = Math.max(0, endIndex - startIndex);
        boolean showPrevious = sparkwitch$magicianPage > 0;
        boolean showNext = sparkwitch$magicianPage < totalPages - 1;
        int groupStartX = SparkWitchInventoryButtonLayout.getCenteredGroupStartX(
                this.width,
                visibleCount,
                showPrevious,
                showNext
        );
        int playerStartX = groupStartX + (showPrevious ? SparkWitchInventoryButtonLayout.SLOT_APART : 0);
        int y = SparkWitchInventoryButtonLayout.getPlayerRowY(this.height);

        for (int index = 0; index < sparkwitch$magicianTargets.size(); index++) {
            MagicianTargetWidget widget = sparkwitch$magicianTargets.get(index);
            boolean visible = index >= startIndex && index < endIndex;
            widget.visible = visible;
            widget.active = visible;
            if (visible) {
                widget.setX(playerStartX + (index - startIndex) * SparkWitchInventoryButtonLayout.SLOT_APART);
                widget.setY(y);
            }
        }

        if (sparkwitch$magicianPrevious != null) {
            sparkwitch$magicianPrevious.visible = showPrevious;
            sparkwitch$magicianPrevious.active = showPrevious;
            sparkwitch$magicianPrevious.setX(groupStartX);
            sparkwitch$magicianPrevious.setY(y);
        }
        if (sparkwitch$magicianNext != null) {
            sparkwitch$magicianNext.visible = showNext;
            sparkwitch$magicianNext.active = showNext;
            sparkwitch$magicianNext.setX(playerStartX + visibleCount * SparkWitchInventoryButtonLayout.SLOT_APART);
            sparkwitch$magicianNext.setY(y);
        }
    }
}
