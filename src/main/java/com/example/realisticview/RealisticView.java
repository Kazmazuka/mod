package com.example.realisticview;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = RealisticView.MODID, dist = Dist.CLIENT)
public class RealisticView {
    public static final String MODID = "realisticview";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealisticView(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(MotionBlur::registerShaders);

        NeoForge.EVENT_BUS.addListener(MotionBlur::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(HeadBob::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(HeadBob::onClientTick);
        NeoForge.EVENT_BUS.addListener(HandSway::onRenderHand);
        NeoForge.EVENT_BUS.addListener(HandSway::onClientTick);
        // FirstPersonBody(一人称ボディ表示)は不具合が多いため登録しない
    }
}
