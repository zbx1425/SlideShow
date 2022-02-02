package org.teacon.slides.network;

import java.util.Optional;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.Marker;
import org.teacon.slides.SlideShow;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorTileEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class UpdateImageInfoPacket {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);
    private static final Marker MARKER = MarkerManager.getMarker("Network");

    public BlockPos pos = BlockPos.ZERO;
    public SlideData data = new SlideData();
    public ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    public UpdateImageInfoPacket() {
        // No-op because we need it.
    }

    public UpdateImageInfoPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        Optional.ofNullable(buffer.readNbt()).ifPresent(this.data::deserializeNBT);
        this.rotation = buffer.readEnum(ProjectorBlock.InternalRotation.class);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeNbt(this.data.serializeNBT());
        buffer.writeEnum(this.rotation);
    }

    @SuppressWarnings("deprecation") // Heck, Mojang what do you mean by this @Deprecated here this time?
    public static void handleServer(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, PacketSender responseSender) {
        UpdateImageInfoPacket packet = new UpdateImageInfoPacket(buf);
        server.execute(() -> {
            ServerLevel world = player.getLevel();
            BlockEntity tileEntity = world.getBlockEntity(packet.pos);
            if (tileEntity instanceof ProjectorTileEntity) {
                BlockState newBlockState = world.getBlockState(packet.pos).setValue(ProjectorBlock.ROTATION, packet.rotation);
                ((ProjectorTileEntity) tileEntity).currentSlide = packet.data;
                world.setBlock(packet.pos, newBlockState, 0b0000001);
                tileEntity.setChanged();
                ((ProjectorTileEntity) tileEntity).sync();
            }
            // Silently drop invalid packets and log them
            GameProfile profile = player.getGameProfile();
            LOGGER.debug(MARKER, "Received invalid packet: player = {}, pos = {}", profile, packet.pos);
        });
    }
}
