package org.teacon.slides.projector;

import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.teacon.slides.Registries;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("ConstantConditions")
@ParametersAreNonnullByDefault
public final class ProjectorBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, BlockEntityClientSerializable {

    private static final Component TITLE = new TranslatableComponent("gui.slide_show.title");

    public String mLocation = "";
    public int mColor = ~0;
    public float mWidth = 1;
    public float mHeight = 1;
    public float mOffsetX = 0;
    public float mOffsetY = 0;
    public float mOffsetZ = 0;
    public boolean mDoubleSided = true;

    public ProjectorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(Registries.BLOCK_ENTITY, blockPos, blockState);
    }

    private BlockPos eshPos;

    public void openGui(BlockPos pos, Player player) {
        if (player instanceof ServerPlayer) {
            eshPos = pos;
            player.openMenu(this);
        }
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(eshPos);
        CompoundTag tag = new CompoundTag();
        writeCustomTag(tag);
        buffer.writeNbt(tag);
    }

    @Nonnull
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectorContainerMenu(containerId, this);
    }

    @Nonnull
    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    public void writeCustomTag(CompoundTag tag) {
        tag.putString("ImageLocation", mLocation);
        tag.putInt("Color", mColor);
        tag.putFloat("Width", mWidth);
        tag.putFloat("Height", mHeight);
        tag.putFloat("OffsetX", mOffsetX);
        tag.putFloat("OffsetY", mOffsetY);
        tag.putFloat("OffsetZ", mOffsetZ);
        tag.putBoolean("DoubleSided", mDoubleSided);
    }

    public void readCustomTag(CompoundTag tag) {
        mLocation = tag.getString("ImageLocation");
        mColor = tag.getInt("Color");
        mWidth = tag.getFloat("Width");
        mHeight = tag.getFloat("Height");
        mOffsetX = tag.getFloat("OffsetX");
        mOffsetY = tag.getFloat("OffsetY");
        mOffsetZ = tag.getFloat("OffsetZ");
        mDoubleSided = tag.getBoolean("DoubleSided");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readCustomTag(tag);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        super.save(tag);
        writeCustomTag(tag);
        return tag;
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
