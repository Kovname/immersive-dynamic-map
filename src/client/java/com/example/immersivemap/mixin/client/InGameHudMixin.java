package com.example.immersivemap.mixin.client;

import com.example.immersivemap.client.MapController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    private static final ItemStack IMMERSIVE_MAP_SLOT_STACK = new ItemStack(Items.FILLED_MAP);

    @Shadow
    @Final
    private static Identifier HOTBAR_OFFHAND_LEFT_TEXTURE;

    @Shadow
    @Final
    private static Identifier HOTBAR_OFFHAND_RIGHT_TEXTURE;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    protected abstract PlayerEntity getCameraPlayer();

    @Shadow
    protected abstract void renderHotbarItem(
            DrawContext context,
            int x,
            int y,
            RenderTickCounter tickCounter,
            PlayerEntity player,
            ItemStack stack,
            int seed);

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void immersiveMap$renderVirtualOffhandMap(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerEntity player = getCameraPlayer();
        if (player == null
                || !MapController.isCompactLeftHandActive()
                || !player.getOffHandStack().isEmpty()
                || player.getMainHandStack().isOf(Items.FILLED_MAP)
                || player.getMainHandStack().isOf(Items.MAP)) {
            return;
        }

        int centerX = context.getScaledWindowWidth() / 2;
        int itemY = context.getScaledWindowHeight() - 19;
        Arm offhandArm = player.getMainArm().getOpposite();

        if (offhandArm == Arm.LEFT) {
            context.drawGuiTexture(HOTBAR_OFFHAND_LEFT_TEXTURE, centerX - 120, context.getScaledWindowHeight() - 23, 29, 24);
            renderHotbarItem(context, centerX - 117, itemY, tickCounter, player, IMMERSIVE_MAP_SLOT_STACK, 10);
        } else {
            context.drawGuiTexture(HOTBAR_OFFHAND_RIGHT_TEXTURE, centerX + 91, context.getScaledWindowHeight() - 23, 29, 24);
            renderHotbarItem(context, centerX + 101, itemY, tickCounter, player, IMMERSIVE_MAP_SLOT_STACK, 10);
        }
    }
}
