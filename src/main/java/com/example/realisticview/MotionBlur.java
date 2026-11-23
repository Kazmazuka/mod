package com.example.realisticview;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

/**
 * 深度バッファを使った再投影型のカメラモーションブラー。
 * 回転・移動・FOV変化(スプリント)すべてのカメラ運動がブラーになる。
 */
public final class MotionBlur {
    private static ShaderInstance shader;
    private static TextureTarget copy;
    private static final Matrix4f prevVP = new Matrix4f();
    private static Vec3 prevCam;
    private static long lastNs;

    private MotionBlur() {}

    public static void registerShaders(RegisterShadersEvent e) {
        try {
            e.registerShader(
                    new ShaderInstance(e.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(RealisticView.MODID, "motion_blur"),
                            DefaultVertexFormat.POSITION),
                    s -> shader = s);
        } catch (IOException ex) {
            RealisticView.LOGGER.error("Failed to load motion blur shader", ex);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        Minecraft mc = Minecraft.getInstance();
        Matrix4f vp = new Matrix4f(e.getProjectionMatrix()).mul(e.getModelViewMatrix());
        Vec3 cam = e.getCamera().getPosition();

        long now = Util.getNanos();
        float dt = Mth.clamp((now - lastNs) / 1.0e9f, 1f / 500f, 0.1f);

        boolean valid = prevCam != null && cam.distanceToSqr(prevCam) < 64.0; // テレポート除外
        Vec3 delta = prevCam == null ? Vec3.ZERO : cam.subtract(prevCam);
        Matrix4f prev = new Matrix4f(prevVP);

        prevVP.set(vp);
        prevCam = cam;
        lastNs = now;

        if (shader == null || !Config.MOTION_BLUR.get() || !valid || mc.player == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;

        if (copy == null) {
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }

        // メイン画面の色と深度をコピー(読みながら書けないため)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);

        float exposure = (float) (Config.EXPOSURE_MS.get() / 1000.0);
        float scale = Mth.clamp(exposure / dt, 0f, 6f); // fps に依存しない露光時間
        float maxLen = (float) (Config.MAX_BLUR_PERCENT.get() / 100.0);

        Matrix4f inv = new Matrix4f(vp).invert();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();

        shader.setSampler("DiffuseSampler", copy.getColorTextureId());
        shader.setSampler("DepthSampler", copy.getDepthTextureId());
        shader.safeGetUniform("InvViewProj").set(inv);
        shader.safeGetUniform("PrevViewProj").set(prev);
        shader.safeGetUniform("CamDelta").set((float) delta.x, (float) delta.y, (float) delta.z);
        shader.safeGetUniform("Params").set(scale, maxLen, (float) Config.BLUR_SAMPLES.get(), 0f);

        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.addVertex(-1f, -1f, 0f);
        b.addVertex(1f, -1f, 0f);
        b.addVertex(1f, 1f, 0f);
        b.addVertex(-1f, 1f, 0f);
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(b.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
