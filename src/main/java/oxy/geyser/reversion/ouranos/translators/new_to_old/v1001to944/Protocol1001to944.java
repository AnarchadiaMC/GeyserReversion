package oxy.geyser.reversion.ouranos.translators.new_to_old.v1001to944;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import oxy.geyser.reversion.ouranos.base.WrappedBedrockPacket;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.GraphicsParameterOverridePacket;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

public class Protocol1001to944 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        // v1001 appends serverEditorConnectionPolicy + allowAnonymousBlockDropsInEditorWorlds to the level
        // settings and loggingChat after the network permissions. The v944 serializer never writes them,
        // so they are reset to their defaults to make the strip explicit.
        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            packet.setServerEditorConnectionPolicy(0);
            packet.setAllowAnonymousBlockDropsInEditorWorlds(false);
            packet.setLoggingChat(false);
        });

        // fireAtPosition (optional Vector3f) was added in v975; the v944 serializer predates the field.
        this.registerClientbound(EntityEventPacket.class, wrapped -> {
            ((EntityEventPacket) wrapped.getPacket()).setFireAtPosition(null);
        });

        // serverSoundHandle (optional long) was added in v975; the v944 serializer predates the field.
        this.registerClientbound(PlaySoundPacket.class, wrapped -> {
            ((PlaySoundPacket) wrapped.getPacket()).setServerSoundHandle(null);
        });

        // playerIdentifier (optional String) was added in v1001; the v944 serializer predates the field.
        this.registerClientbound(GraphicsParameterOverridePacket.class, wrapped -> {
            ((GraphicsParameterOverridePacket) wrapped.getPacket()).setPlayerIdentifier(null);
        });

        // v407 (used by v944) writes the enchant type as a byte while v975 reads it as a uint; types
        // above 255 cannot be expressed on 944, so the packet is dropped instead of being truncated.
        this.registerClientbound(PlayerEnchantOptionsPacket.class, wrapped -> {
            final PlayerEnchantOptionsPacket packet = (PlayerEnchantOptionsPacket) wrapped.getPacket();
            boolean unencodable = false;
            for (final EnchantOptionData option : packet.getOptions()) {
                unencodable |= hasUnencodableType(option);
            }
            if (unencodable) {
                wrapped.cancel();
            }
        });

        // v975 replaced the v944 textureId int with a texturePath String + iconSize Vec2f and no public
        // texture id -> path map exists, so the waypoints cannot be expressed on 944.
        this.registerClientbound(LocatorBarPacket.class, WrappedBedrockPacket::cancel);

        // v975 added the CYLINDER/PYRAMID/ELLIPSOID/CONE shape types that the v944 serializer cannot
        // encode. The packet is debug-only, so it is dropped rather than risking corruption.
        this.registerClientbound(DebugDrawerPacket.class, WrappedBedrockPacket::cancel);
    }

    private static boolean hasUnencodableType(EnchantOptionData option) {
        for (final EnchantData enchant : option.getEnchants0()) {
            if (enchant.getType() > 255) {
                return true;
            }
        }
        for (final EnchantData enchant : option.getEnchants1()) {
            if (enchant.getType() > 255) {
                return true;
            }
        }
        for (final EnchantData enchant : option.getEnchants2()) {
            if (enchant.getType() > 255) {
                return true;
            }
        }
        return false;
    }
}
