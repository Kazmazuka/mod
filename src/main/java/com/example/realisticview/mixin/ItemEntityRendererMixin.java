package com.example.realisticview.mixin;

import com.example.realisticview.GroundItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Shadow protected float shadowRadius;
    @Shadow protected float shadowStrength;

    private static final String RENDER =
            "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private void realisticview$begin(ItemEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        GroundItems.begin(entity, partialTicks);
        if (GroundItems.isActive()) {
            this.shadowRadius = GroundItems.shadowRadius();
            this.shadowStrength = 0.9f;
        } else {
            this.shadowRadius = 0.15f;
            this.shadowStrength = 0.75f;
        }
    }

    /** vanilla の上下の浮遊(bobbing)を止める */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"), require = 0)
    private float realisticview$noBob(float value) {
        return GroundItems.isActive() ? -1.0F : Mth.sin(value);
    }

    /** vanilla のY軸スピンを、床置き/飛行中の向きに置き換える */
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V"), require = 0)
    private void realisticview$orient(PoseStack poseStack, Quaternionf q) {
        if (GroundItems.isActive()) {
            GroundItems.apply(poseStack);
        } else {
            poseStack.mulPose(q);
        }
    }
}
