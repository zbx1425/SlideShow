package org.teacon.slides.projector;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public final class ProjectorUpdatePacket {

    private static final Marker MARKER = MarkerManager.getMarker("Network");

    private BlockPos mPos;
    private ProjectorBlockEntity mEntity;
    private final ProjectorBlock.InternalRotation mRotation;
    private final CompoundTag mTag;

    @Environment(EnvType.CLIENT)
    public ProjectorUpdatePacket(ProjectorBlockEntity entity, ProjectorBlock.InternalRotation rotation) {
        mEntity = entity;
        mRotation = rotation;
        mTag = new CompoundTag();
    }

    public ProjectorUpdatePacket(FriendlyByteBuf buf) {
        mPos = buf.readBlockPos();
        mRotation = ProjectorBlock.InternalRotation.VALUES[buf.readVarInt()];
        mTag = buf.readNbt();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(mPos);
        buffer.writeVarInt(mRotation.ordinal());
        buffer.writeNbt(mTag);
    }

    @Environment(EnvType.CLIENT)
    public void sendToServer() {
        mPos = mEntity.getBlockPos();
        mEntity.writeCustomTag(mTag);
        FriendlyByteBuf buffer = PacketByteBufs.create();
        this.write(buffer);
        ClientPlayNetworking.send(SlideShow.CHANNEL_NAME, buffer);
    }

    public static void handleServer(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
        ProjectorUpdatePacket packet = new ProjectorUpdatePacket(buf);
        server.execute(() -> {
            ServerLevel level = player.getLevel();
            if (level.isLoaded(packet.mPos) &&
                    level.getBlockEntity(packet.mPos) instanceof ProjectorBlockEntity tile) {
                BlockState state = tile.getBlockState().setValue(ProjectorBlock.ROTATION, packet.mRotation);
                tile.readCustomTag(packet.mTag);
                if (!level.setBlock(packet.mPos, state, Block.UPDATE_ALL)) {
                    // state is unchanged, but re-render it
                    level.sendBlockUpdated(packet.mPos, state, state, Block.UPDATE_CLIENTS);
                }
                tile.sync();
                // mark chunk unsaved
                tile.setChanged();
                return;
            }
            GameProfile profile = player.getGameProfile();
            SlideShow.LOGGER.debug(MARKER, "Received illegal packet: player = {}, pos = {}", profile, packet.mPos);
        });
    }
}
