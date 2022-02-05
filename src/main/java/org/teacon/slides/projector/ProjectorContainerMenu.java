package org.teacon.slides.projector;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.teacon.slides.Registries;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@ParametersAreNonnullByDefault
public final class ProjectorContainerMenu extends AbstractContainerMenu {

    ProjectorBlockEntity mEntity;

    public ProjectorContainerMenu(int containerId, ProjectorBlockEntity entity) {
        super(Registries.MENU, containerId);
        mEntity = entity;
    }

    public ProjectorContainerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(Registries.MENU, containerId);
        if (inventory.player.level.getBlockEntity(buf.readBlockPos()) instanceof ProjectorBlockEntity t) {
            CompoundTag tag = Objects.requireNonNull(buf.readNbt());
            t.readCustomTag(tag);
            mEntity = t;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
