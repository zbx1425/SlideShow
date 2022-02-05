package org.teacon.slides;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.projector.ProjectorContainerMenu;

public final class Registries {

    public static Block PROJECTOR;
    public static BlockEntityType<ProjectorBlockEntity> BLOCK_ENTITY;
    public static MenuType<ProjectorContainerMenu> MENU;
}
