package com.github.blackjack200.ouranos.translators;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.converter.ChunkRewriteException;
import com.github.blackjack200.ouranos.converter.TypeConverter;
import com.github.blackjack200.ouranos.utils.SimpleBlockDefinition;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.util.ArrayList;
import java.util.List;

import static org.cloudburstmc.protocol.bedrock.data.LevelEvent.*;
import static org.cloudburstmc.protocol.bedrock.data.LevelEvent.PARTICLE_BREAK_BLOCK_EAST;
import static org.cloudburstmc.protocol.bedrock.data.LevelEvent.PARTICLE_BREAK_BLOCK_SOUTH;
import static org.cloudburstmc.protocol.bedrock.data.LevelEvent.PARTICLE_BREAK_BLOCK_WEST;

// These will be translated directly instead of passing through multiple translators.
public class GlobalWorldTranslator extends ProtocolToProtocol {
    private static final List<LevelEventType> BLOCK_BREAK_PARTICLE_EVENTS = List.of(
            PARTICLE_BREAK_BLOCK_DOWN, PARTICLE_BREAK_BLOCK_UP, PARTICLE_BREAK_BLOCK_NORTH,
            PARTICLE_BREAK_BLOCK_SOUTH, PARTICLE_BREAK_BLOCK_WEST, PARTICLE_BREAK_BLOCK_EAST,
            PARTICLE_DESTROY_BLOCK
    );

    @Override
    protected void registerProtocol() {
        this.registerClientbound(UpdateBlockPacket.class, wrapped -> {
            final UpdateBlockPacket packet = (UpdateBlockPacket) wrapped.getPacket();
            packet.setDefinition(new SimpleBlockDefinition(TypeConverter.translateBlockRuntimeId(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), packet.getDefinition().getRuntimeId())));
        });

        this.registerClientbound(LevelEventPacket.class, wrapped -> {
            final LevelEventPacket packet = (LevelEventPacket) wrapped.getPacket();
            final int input = wrapped.getInput(), output = wrapped.getOutput();

            LevelEventType type = packet.getType();
            int data = packet.getData();

            if (type == ParticleType.ICON_CRACK) {
                var newItem = TypeConverter.translateItemRuntimeId(wrapped.session(), input, output, data >> 16, data & 0xFFFF);
                data = newItem[0] << 16 | newItem[1];
            } else if (BLOCK_BREAK_PARTICLE_EVENTS.contains(type)) {
                data = TypeConverter.translateBlockRuntimeId(wrapped.session(), input, output, data);
            } else if (type == LevelEvent.PARTICLE_CRACK_BLOCK) {
                var face = data >> 24;
                var runtimeId = data & ~(face << 24);
                data = TypeConverter.translateBlockRuntimeId(wrapped.session(), input, output, runtimeId) | face << 24;
            }
            packet.setData(data);
        });

        this.registerClientbound(LevelSoundEventPacket.class, wrapped -> {
            final LevelSoundEventPacket packet = (LevelSoundEventPacket) wrapped.getPacket();
            final SoundEvent sound = packet.getSound();
            if (sound == SoundEvent.PLACE || sound == SoundEvent.BREAK_BLOCK || sound == SoundEvent.ITEM_USE_ON) {
                packet.setExtraData(TypeConverter.translateBlockRuntimeId(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), packet.getExtraData()));
            }
        });

        this.registerClientbound(EntityEventPacket.class, wrapped -> {
            final EntityEventPacket packet = (EntityEventPacket) wrapped.getPacket();
            if (packet.getType() == EntityEventType.EATING_ITEM) {
                final int data = packet.getData();
                int[] newItem = TypeConverter.translateItemRuntimeId(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), data >> 16, data & 0xFFFF);
                packet.setData((newItem[0] << 16) | newItem[1]);
            }
        });

        this.registerClientbound(AddEntityPacket.class, wrapped -> {
            final AddEntityPacket packet = (AddEntityPacket) wrapped.getPacket();

            if (!packet.getIdentifier().equals("minecraft:falling_block") || packet.getMetadata() == null) {
                return;
            }

            final EntityDataMap data = packet.getMetadata();
            final Integer runtimeId = data.get(EntityDataTypes.VARIANT);
            if (runtimeId != null) {
                data.put(EntityDataTypes.VARIANT, TypeConverter.translateBlockRuntimeId(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), runtimeId));
            }
        });

        this.registerClientbound(UpdateSubChunkBlocksPacket.class, wrapped -> {
            final UpdateSubChunkBlocksPacket packet = (UpdateSubChunkBlocksPacket) wrapped.getPacket();

            final List<BlockChangeEntry> newExtraBlocks = new ArrayList<>(packet.getExtraBlocks().size());
            for (final BlockChangeEntry entry : packet.getExtraBlocks()) {
                newExtraBlocks.add(new BlockChangeEntry(entry.getPosition(), TypeConverter.translateBlockDefinition(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), entry.getDefinition()), entry.getUpdateFlags(), entry.getMessageEntityId(), entry.getMessageType()));
            }
            packet.getExtraBlocks().clear();
            packet.getExtraBlocks().addAll(newExtraBlocks);

            final List<BlockChangeEntry> newStandardBlock = new ArrayList<>(packet.getStandardBlocks().size());
            for (final BlockChangeEntry entry : packet.getStandardBlocks()) {
                newStandardBlock.add(new BlockChangeEntry(entry.getPosition(), TypeConverter.translateBlockDefinition(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), entry.getDefinition()), entry.getUpdateFlags(), entry.getMessageEntityId(), entry.getMessageType()));
            }
            packet.getStandardBlocks().clear();
            packet.getStandardBlocks().addAll(newStandardBlock);
        });

        this.registerClientbound(LevelChunkPacket.class, wrapped -> {
            final LevelChunkPacket packet = (LevelChunkPacket) wrapped.getPacket();

            final ByteBuf from = packet.getData();
            final ByteBuf to = AbstractByteBufAllocator.DEFAULT.buffer(from.readableBytes()).touch();
            try {
                var newSubChunkCount = TypeConverter.rewriteFullChunk(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), from, to, packet.getDimension(), packet.getSubChunksLength());
                packet.setSubChunksLength(newSubChunkCount);
                packet.setData(to.retain());
            } catch (ChunkRewriteException ignored) {
            } finally {
                ReferenceCountUtil.release(from);
                ReferenceCountUtil.release(to);
            }
        });

        this.registerClientbound(SubChunkPacket.class, wrapped -> {
            final SubChunkPacket packet = (SubChunkPacket) wrapped.getPacket();

            for (final SubChunkData subChunk : packet.getSubChunks()) {
                if (subChunk.getData().readableBytes() > 0) {
                    final ByteBuf from = subChunk.getData();
                    final ByteBuf to = AbstractByteBufAllocator.DEFAULT.buffer(from.readableBytes());
                    try {
                        TypeConverter.rewriteSubChunk(wrapped.session(), wrapped.getInput(), wrapped.getOutput(), from, to);
                        TypeConverter.rewriteBlockEntities(wrapped.getInput(), wrapped.getOutput(), from, to);
                        to.writeBytes(from);
                        subChunk.setData(to.retain());
                    } catch (ChunkRewriteException ignored) {
                    } finally {
                        ReferenceCountUtil.release(from);
                        ReferenceCountUtil.release(to);
                    }
                }
            }

            // I don't like hardcoded code, but since we're already doing this... this make more sense anyway.
            if (wrapped.getOutput() < Bedrock_v475.CODEC.getProtocolVersion()) {
                packet.getSubChunks().subList(0, 4).clear();
            }
        });
    }
}
