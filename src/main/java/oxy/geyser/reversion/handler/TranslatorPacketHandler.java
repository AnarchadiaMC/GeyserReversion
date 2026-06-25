package oxy.geyser.reversion.handler;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v575.Bedrock_v575;
import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v589.Bedrock_v589;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;

import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;

import org.geysermc.geyser.text.GeyserLocale;

import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.handler.duplicated.UpstreamPacketHandler;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.ClientDataUtil;
import oxy.geyser.reversion.util.GeyserUtil;


import java.util.List;
import java.util.UUID;

public final class TranslatorPacketHandler extends UpstreamPacketHandler {
    @Getter
    private GeyserTranslatedUser user;

    public TranslatorPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private int clientProtocol = -1;
    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        this.clientProtocol = packet.getProtocolVersion();
        if (GameProtocol.getBedrockCodec(packet.getProtocolVersion()) != null) {
            return super.handle(packet);
        }

        if (checkCodec(packet.getProtocolVersion())) {
            return PacketSignal.HANDLED;
        }

        packet.setProtocolVersion(GeyserReversion.BRIDGE_GEYSER_CODEC.getProtocolVersion());
        session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));

        PacketCompressionAlgorithm algorithm = PacketCompressionAlgorithm.ZLIB;

        NetworkSettingsPacket responsePacket = new NetworkSettingsPacket();
        responsePacket.setCompressionAlgorithm(algorithm);
        responsePacket.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(responsePacket);
        session.getUpstream().getSession().getPeer().setCompression(compressionStrategy);
        networkSettingsRequested = true;

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        this.clientProtocol = packet.getProtocolVersion();
        if (GameProtocol.getBedrockCodec(packet.getProtocolVersion()) != null) {
            return super.handle(packet);
        }

        final int pv = packet.getProtocolVersion();
        if (checkCodec(pv)) {
            return PacketSignal.HANDLED;
        }

        this.user = new GeyserTranslatedUser(pv, GeyserReversion.BRIDGE_GEYSER_CODEC.getProtocolVersion(), this.session);
        packet.setProtocolVersion(GeyserReversion.BRIDGE_GEYSER_CODEC.getProtocolVersion());
        session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));
        GeyserUtil.hook(session);

        // The player is using the version before authentication change, damn it. Let's handle this ourselves...
        if (this.clientProtocol < Bedrock_v589.CODEC.getProtocolVersion()) {
            if (geyser.isShuttingDown() || geyser.isReloading()) {
                // Don't allow new players in if we're no longer operating
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
                return PacketSignal.HANDLED;
            }

            // Set the block translation based off of version
            session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(packet.getProtocolVersion()));
            session.setItemMappings(Registries.ITEMS.forVersion(packet.getProtocolVersion()));

            // Populate client/auth data before continuing with the legacy login path.
            ClientDataUtil.setClientData(session, packet);

            if (session.isClosed()) {
                return PacketSignal.HANDLED;
            }

            if (geyser.getSessionManager().reachedMaxConnectionsPerAddress(session)) {
                session.disconnect("Too many connections are originating from this location!");
                return PacketSignal.HANDLED;
            }

            if (geyser.getSessionManager().isXuidAlreadyPending(session.xuid()) || geyser.getSessionManager().sessionByXuid(session.xuid()) != null) {
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", session.bedrockUsername()));
                return PacketSignal.HANDLED;
            }

            geyser.eventBus().fire(new SessionInitializeEvent(session));

            PlayStatusPacket playStatus = new PlayStatusPacket();
            playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
            session.sendUpstreamPacket(playStatus);

            this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session);
            this.geyser.eventBus().fireEventElseKick(this.resourcePackLoadEvent, session);
            if (session.isClosed()) {
                // Can happen if an error occurs in the resource pack event; that'll disconnect the player
                return PacketSignal.HANDLED;
            }
            session.integratedPackActive(resourcePackLoadEvent.isIntegratedPackActive());

            // Let's just send player stuff, don't spawn them in yet....
            ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
            resourcePacksInfo.getResourcePackInfos().addAll(this.resourcePackLoadEvent.infoPacketEntries());
            resourcePacksInfo.setVibrantVisualsForceDisabled(!session.isAllowVibrantVisuals());

            resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().config().gameplay().forceResourcePacks()
                    || resourcePackLoadEvent.isIntegratedPackActive());
            resourcePacksInfo.setWorldTemplateId(UUID.randomUUID());
            resourcePacksInfo.setWorldTemplateVersion("*");
            session.sendUpstreamPacket(resourcePacksInfo);

            GeyserLocale.loadGeyserLocale(session.locale());
            return PacketSignal.HANDLED;
        }

        super.handle(packet);
        if (this.user != null) {
            this.user.setAuthenticated(true);
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (session.getUpstream().isClosed() || session.isClosed()) {
            return PacketSignal.HANDLED;
        }

        if (this.clientProtocol >= Bedrock_v589.CODEC.getProtocolVersion() || packet.getStatus() != ResourcePackClientResponsePacket.Status.COMPLETED) {
            return super.handle(packet); // New authentication supported, allow them to login!
        }

        if (this.finishedResourcePackSending) {
            session.disconnect("Illegal duplicate resource pack response packet received!");
            return PacketSignal.HANDLED;
        }

        this.finishedResourcePackSending = true;
        session.connect(); // We have to spawn player in the void world!

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        if (this.user != null && !authenticationStarted) {
            this.authenticate();
        }
        return super.handle(packet);
    }

    private boolean authenticationStarted = false;

    private void authenticate() {
        if (authenticationStarted) {
            return; // Already started authentication, don't restart
        }
        authenticationStarted = true;
        
        // Use Geyser's built-in Microsoft authentication system
        // This avoids classloader issues with MsaDeviceCode types
        session.authenticateWithMicrosoftCode();
        
        // Note: user.setAuthenticated(true) will be called when the session actually logs in.
        // Geyser will handle disconnecting the player if auth fails.
        // We set authenticated = true in handlePacket when session.isLoggedIn() becomes true.
    }


    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        if (this.user == null) {
            super.handlePacket(packet);
            return PacketSignal.HANDLED;
        }
        
        // Mark as authenticated only when Geyser confirms successful login
        if (!this.user.isAuthenticated() && session.isLoggedIn()) {
            this.user.setAuthenticated(true);
        }

        final ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
        try {
            this.user.encodeClient(packet, input);

            final int oldId = this.user.getCloudburstClientCodec().getPacketDefinition(packet.getClass()).getId();
            final Integer newId = this.user.translateServerbound(input, output, oldId);
            if (newId == null) {
                return PacketSignal.HANDLED;
            }

            super.handlePacket(this.user.decodeServer(output, newId));
        } catch (Exception exception) {
            if (GeyserReversion.CONFIG.debugMode()) {
                GeyserReversion.LOGGER.severe("Failed to translate " + packet.getPacketType() + " (serverbound)!", exception);
            }
        } finally {
            input.release();
            output.release();
        }
        return PacketSignal.HANDLED;
    }

    private boolean checkCodec(int protocolVersion) {
        int minProtocolVer = GeyserReversion.CONFIG.minProtocolId();
        if (GeyserReversion.INJECTION_FAILED) {
            minProtocolVer = Math.max(Bedrock_v575.CODEC.getProtocolVersion(), minProtocolVer);
        }

        if (minProtocolVer != -1) {
            BedrockCodec codec = DuplicatedProtocolInfo.getPacketCodec(minProtocolVer);
            if (codec != null && protocolVersion < minProtocolVer) {
                session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
                session.disconnect(GeyserReversion.CONFIG.minProtocolKick().replace("%version%", codec.getMinecraftVersion()));
                return true;
            }
        }

        List<Integer> blockProtocols = GeyserReversion.CONFIG.blockProtocols();
        if (blockProtocols != null && blockProtocols.contains(protocolVersion)) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.CONFIG.blockedProtocolKick());
            return true;
        }

        if (ProtocolInfo.getPacketCodec(protocolVersion) == null && GameProtocol.getBedrockCodec(protocolVersion) == null) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.CONFIG.versionNotSupportedKick());
            return true;
        }

        return false;
    }
}
