package org.teacon.slides.projector;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.teacon.slides.SlideShow;
import org.teacon.slides.network.SlideData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@ParametersAreNonnullByDefault
public final class ProjectorTileEntity extends BlockEntity implements ExtendedScreenHandlerFactory, BlockEntityClientSerializable {
    private static final Component TITLE = new TranslatableComponent("gui.slide_show.title");

    public SlideData currentSlide = new SlideData();

    public ProjectorTileEntity(BlockPos blockPos, BlockState blockState) {
        super(Objects.requireNonNull(SlideShow.PROJECTOR_TILE_ENTITY), blockPos, blockState);
    }

    // TODO: IS THIS CORRECT?
    private BlockPos eshPos;
    private BlockState eshState;

    public void openGUI(BlockState state, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer) {
            eshPos = pos;
            eshState = state;
            player.openMenu(this);
        }
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(eshPos);
        buffer.writeNbt(this.currentSlide.serializeNBT());
        buffer.writeEnum(eshState.getValue(ProjectorBlock.ROTATION));
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return ProjectorControlContainer.fromServer(id, inv, this);
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public CompoundTag save(CompoundTag data) {
        super.save(data);
        return data.merge(this.currentSlide.serializeNBT());
    }

    @Override
    public void load(CompoundTag data) {
        super.load(data);
        this.currentSlide.deserializeNBT(data);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 0, this.currentSlide.serializeNBT());
    }

    /* @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        this.currentSlide.deserializeNBT(packet.getTag());
    } */

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    /* @Override
    public void handleUpdateTag(CompoundTag data) {
        this.load(data);
    } */

        //TODO:距离
//    @OnlyIn(Dist.CLIENT)
//    @Override
//    public double getViewDistance() {
//        return 65536.0D;
//    }
//

    public Matrix4f getTransformation() {
        SlideData data = this.currentSlide;
        Direction facing = this.getBlockState().getValue(BlockStateProperties.FACING);
        ProjectorBlock.InternalRotation rotation = this.getBlockState().getValue(ProjectorBlock.ROTATION);
        // matrix 1: translation to block center
        final Matrix4f result = Matrix4f.createTranslateMatrix(0.5F, 0.5F, 0.5F);
        // matrix 2: rotation
        result.multiply(facing.getRotation());
        // matrix 3: translation to block surface
        result.multiply(Matrix4f.createTranslateMatrix(0.0F, 0.5F, 0.0F));
        // matrix 4: internal rotation
        result.multiply(rotation.getTransformation());
        // matrix 5: translation for slide
        result.multiply(Matrix4f.createTranslateMatrix(-0.5F, 0.0F, 0.5F - data.getSize().y));
        // matrix 6: offset for slide
        result.multiply(Matrix4f.createTranslateMatrix(data.getOffset().x(), -data.getOffset().z(), data.getOffset().y()));
        // matrix 7: scaling
        result.multiply(Matrix4f.createScaleMatrix(data.getSize().x, 1.0F, data.getSize().y));
        // TODO: cache transformation
        return result;
    }

    @Override
    public void fromClientTag(CompoundTag tag) {
        this.load(tag);
    }

    @Override
    public CompoundTag toClientTag(CompoundTag tag) {
        return this.save(new CompoundTag());
    }
}