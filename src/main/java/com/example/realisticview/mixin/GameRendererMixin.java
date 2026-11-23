package com.example.realisticview.mixin;

import com.example.realisticview.Config;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** バニラの view bobbing を無効化(HeadBob に置き換え) */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void realisticview$noVanillaBob(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (Config.HEAD_BOB.get()) ci.cancel();
    }
}
