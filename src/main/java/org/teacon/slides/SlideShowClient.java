package org.teacon.slides;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.renderer.RenderType;
import org.teacon.slides.network.UpdateImageInfoPacket;
import org.teacon.slides.projector.ProjectorControlScreen;
import org.teacon.slides.renderer.ProjectorTileEntityRenderer;
import org.teacon.slides.renderer.SlideState;

public class SlideShowClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenRegistry.register(SlideShow.PROJECTOR_SCREEN_HANDLER, ProjectorControlScreen::new);
        BlockEntityRendererRegistry.register(SlideShow.PROJECTOR_TILE_ENTITY, ProjectorTileEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(SlideShow.PROJECTOR_BLOCK, RenderType.cutout());

        ClientTickEvents.START_CLIENT_TICK.register(SlideState::tick);
    }
}
