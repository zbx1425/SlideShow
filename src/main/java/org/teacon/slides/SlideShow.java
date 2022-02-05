package org.teacon.slides;

import com.mojang.datafixers.DSL;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Material;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.projector.*;
import org.teacon.slides.renderer.ProjectorRenderer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.Set;

@ParametersAreNonnullByDefault
public final class SlideShow implements ModInitializer {

    public static final String ID = "slide_show"; // as well as the namespace
    public static final Logger LOGGER = LogManager.getLogger("SlideShow");
    public static ResourceLocation CHANNEL_NAME = new ResourceLocation(ID, "network");

    public static boolean sOptiFineLoaded;

    static {
        try {
            Class.forName("optifine.Installer");
            sOptiFineLoaded = true;
        } catch (ClassNotFoundException ignored) {
        }

        Registries.MENU = ScreenHandlerRegistry.registerExtended(
                new ResourceLocation("slide_show", "projector"), ProjectorContainerMenu::new);
    }

    @Override
    public void onInitialize() {
        Registries.PROJECTOR = Registry.register(Registry.BLOCK, new ResourceLocation("slide_show", "projector"),
                new ProjectorBlock()
        );
        Registry.register(Registry.ITEM, new ResourceLocation("slide_show", "projector"),
                new ProjectorItem()
        );
        Registries.BLOCK_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, new ResourceLocation("slide_show", "projector"),
                FabricBlockEntityTypeBuilder.create(ProjectorBlockEntity::new, Registries.PROJECTOR).build(DSL.remainderType())
        );

        ServerPlayNetworking.registerGlobalReceiver(CHANNEL_NAME, ProjectorUpdatePacket::handleServer);
    }

}
