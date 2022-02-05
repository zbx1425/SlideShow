package org.teacon.slides;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.minecraft.client.renderer.RenderType;
import org.teacon.slides.projector.ProjectorScreen;
import org.teacon.slides.renderer.ProjectorRenderer;
import org.teacon.slides.renderer.SlideState;

public class SlideShowClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenRegistry.register(Registries.MENU, ProjectorScreen::new);
        BlockEntityRendererRegistry.register(Registries.BLOCK_ENTITY, ProjectorRenderer.INSTANCE::onCreate);
        BlockRenderLayerMap.INSTANCE.putBlock(Registries.PROJECTOR, RenderType.cutout());

        ClientTickEvents.START_CLIENT_TICK.register(SlideState::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(SlideState::onPlayerLeft);
    }
}
