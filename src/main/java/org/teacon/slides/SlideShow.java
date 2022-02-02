package org.teacon.slides;

import com.mojang.datafixers.DSL;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.network.UpdateImageInfoPacket;
import org.teacon.slides.projector.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class SlideShow implements ModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("SlideShow");

    public static boolean sOptiFineLoaded;

    public static ResourceLocation CHANNEL_NAME = new ResourceLocation("silde_show", "network");

    static {
        try {
            Class.forName("optifine.Installer");
            sOptiFineLoaded = true;
        } catch (ClassNotFoundException ignored) {

        }

        PROJECTOR_SCREEN_HANDLER = ScreenHandlerRegistry.registerExtended(
                new ResourceLocation("slide_show", "projector"), ProjectorControlContainer::fromClient);
    }

    public static Block PROJECTOR_BLOCK;
    public static Item PROJECTOR_ITEM;
    public static BlockEntityType<ProjectorTileEntity> PROJECTOR_TILE_ENTITY;
    public static MenuType<ProjectorControlContainer> PROJECTOR_SCREEN_HANDLER;

    @Override
    public void onInitialize() {
        PROJECTOR_BLOCK = Registry.register(Registry.BLOCK, new ResourceLocation("slide_show", "projector"),
            new ProjectorBlock(Block.Properties.of(Material.METAL)
                .strength(20F)
                .lightLevel(s -> 15) // TODO Configurable
                .noCollission())
        );
        PROJECTOR_ITEM = Registry.register(Registry.ITEM, new ResourceLocation("slide_show", "projector"),
            new ProjectorItem(new Item.Properties()
                .tab(CreativeModeTab.TAB_MISC).rarity(Rarity.RARE))
        );
        PROJECTOR_TILE_ENTITY = Registry.register(Registry.BLOCK_ENTITY_TYPE, new ResourceLocation("slide_show", "projector"),
            FabricBlockEntityTypeBuilder.create(ProjectorTileEntity::new, PROJECTOR_BLOCK).build(DSL.remainderType())
        );

        ServerPlayNetworking.registerGlobalReceiver(CHANNEL_NAME, UpdateImageInfoPacket::handleServer);
    }
}