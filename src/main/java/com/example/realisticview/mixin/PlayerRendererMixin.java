package com.example.realisticview.mixin;

import com.example.realisticview.FirstPersonBody;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    /** 一人称ボディ描画中だけ、頭・帽子・腕・袖を完全に非表示にする(スケール縮小より確実) */
    @Inject(method = "setModelProperties", at = @At("TAIL"), require = 0)
    private void realisticview$hideParts(AbstractClientPlayer player, CallbackInfo ci) {
        if (!FirstPersonBody.isRenderingBody()) return;
        PlayerModel<AbstractClientPlayer> m = ((PlayerRenderer) (Object) this).getModel();
        m.head.visible = false;
        m.hat.visible = false;
        m.leftArm.visible = false;
        m.rightArm.visible = false;
        m.leftSleeve.visible = false;
        m.rightSleeve.visible = false;
    }
}
