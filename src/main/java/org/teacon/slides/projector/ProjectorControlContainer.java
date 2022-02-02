package org.teacon.slides.projector;

import com.google.common.base.MoreObjects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.network.SlideData;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;

@ParametersAreNonnullByDefault
public final class ProjectorControlContainer extends AbstractContainerMenu {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    public static ProjectorControlContainer fromServer(int id, Inventory inv, ProjectorTileEntity tileEntity) {
        BlockPos pos = tileEntity.getBlockPos();
        SlideData data = tileEntity.currentSlide;
        ProjectorBlock.InternalRotation rotation = tileEntity.getBlockState().getValue(ProjectorBlock.ROTATION);
        return new ProjectorControlContainer(id, pos, data, rotation);
    }

    public static ProjectorControlContainer fromClient(int id, Inventory inv, @Nullable FriendlyByteBuf buffer) {
        try {
            Objects.requireNonNull(buffer);
            SlideData data = new SlideData();
            BlockPos pos = buffer.readBlockPos();
            Optional.ofNullable(buffer.readNbt()).ifPresent(data::deserializeNBT);
            ProjectorBlock.InternalRotation rotation = buffer.readEnum(ProjectorBlock.InternalRotation.class);
            return new ProjectorControlContainer(id, pos, data, rotation);
        } catch (Exception e) {
            LOGGER.warn("Invalid data in packet buffer", e);
            return new ProjectorControlContainer(id, BlockPos.ZERO, new SlideData(), ProjectorBlock.InternalRotation.NONE);
        }
    }

    final BlockPos pos;
    final SlideData currentSlide;
    final ProjectorBlock.InternalRotation rotation;

    public ProjectorControlContainer(int id, BlockPos pos, SlideData data, ProjectorBlock.InternalRotation rotation) {
        super(SlideShow.PROJECTOR_SCREEN_HANDLER, id);
        this.pos = pos;
        this.currentSlide = data;
        this.rotation = rotation;
    }

    @Override
    public boolean stillValid(Player player) {
        // return PermissionAPI.hasPermission(player, "slide_show.interact.projector");
        return true;
    }
}