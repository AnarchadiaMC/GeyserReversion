package oxy.geyser.reversion.handler;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import org.cloudburstmc.protocol.bedrock.codec.v361.Bedrock_v361;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
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
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;

import org.geysermc.geyser.text.GeyserLocale;

import oxy.geyser.reversion.DuplicatedProtocolInfo;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.handler.duplicated.UpstreamPacketHandler;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.GeyserApiCompat;
import oxy.geyser.reversion.util.GeyserUtil;
import org.geysermc.geyser.util.LoginEncryptionUtils;


import java.util.List;
import java.util.UUID;

public final class TranslatorPacketHandler extends UpstreamPacketHandler {
    @Getter
    private GeyserTranslatedUser user;

    public TranslatorPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    private int clientProtocol = -1;
    private final oxy.geyser.reversion.util.TranslationFailures failures = new oxy.geyser.reversion.util.TranslationFailures();
    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        this.clientProtocol = packet.getProtocolVersion();
        if (checkCodec(this.clientProtocol)) {
            return PacketSignal.HANDLED;
        }
        if (GeyserApiCompat.getBedrockCodec(packet.getProtocolVersion()) != null) {
            return super.handle(packet);
        }

        if (checkCodec(packet.getProtocolVersion())) {
            return PacketSignal.HANDLED;
        }

        if (bridgeUnavailable(packet.getProtocolVersion())) {
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
        if (networkSettingsRequested && this.clientProtocol != packet.getProtocolVersion()) {
            session.disconnect("Bedrock login protocol does not match network settings.");
            return PacketSignal.HANDLED;
        }
        this.clientProtocol = packet.getProtocolVersion();
        if (checkCodec(this.clientProtocol)) {
            return PacketSignal.HANDLED;
        }
        if (GeyserApiCompat.getBedrockCodec(packet.getProtocolVersion()) != null) {
            return super.handle(packet);
        }

        final int pv = packet.getProtocolVersion();
        if (checkCodec(pv)) {
            return PacketSignal.HANDLED;
        }

        if (bridgeUnavailable(pv)) {
            return PacketSignal.HANDLED;
        }

        this.user = new GeyserTranslatedUser(pv, GeyserReversion.BRIDGE_GEYSER_CODEC.getProtocolVersion(), this.session);
        packet.setProtocolVersion(GeyserReversion.BRIDGE_GEYSER_CODEC.getProtocolVersion());
        session.getUpstream().getSession().setCodec(DuplicatedProtocolInfo.getPacketCodec(this.clientProtocol));
        if (!GeyserUtil.hook(session)) {
            session.disconnect("Failed to initialize this Bedrock version connection. Please update Minecraft; the server logged the reason.");
            return PacketSignal.HANDLED;
        }

        // The player is using the version before authentication change, damn it. Let's handle this ourselves...
        if (this.clientProtocol < Bedrock_v589.CODEC.getProtocolVersion()) {
            if (receivedLoginPacket) {
                session.disconnect("Received duplicate login packet!");
                session.forciblyCloseUpstream();
                return PacketSignal.HANDLED;
            }
            receivedLoginPacket = true;
            if (geyser.isShuttingDown() || geyser.isReloading()) {
                // Don't allow new players in if we're no longer operating
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
                return PacketSignal.HANDLED;
            }

            // Set the block translation based off of version
            session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(packet.getProtocolVersion()));
            session.setItemMappings(Registries.ITEMS.forVersion(packet.getProtocolVersion()));

            LoginEncryptionUtils.encryptPlayerConnection(session, packet);

            if (session.isClosed()) {
                session.forciblyCloseUpstream();
                return PacketSignal.HANDLED;
            }

            if (geyser.getSessionManager().isXuidAlreadyPending(session.xuid()) || geyser.getSessionManager().sessionByXuid(session.xuid()) != null) {
                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", session.bedrockUsername()));
                return PacketSignal.HANDLED;
            }

            geyser.getSessionManager().addPendingSession(session);
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
            resourcePacksInfo.setWorldTemplateId(new UUID(0, 0));
            resourcePacksInfo.setWorldTemplateVersion("");
            session.sendUpstreamPacket(resourcePacksInfo);

            GeyserLocale.loadGeyserLocale(session.locale());
            return PacketSignal.HANDLED;
        }

        super.handle(packet);

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

        // Authentication mode and saved online logins belong to Geyser, also for legacy clients.
        return super.handle(packet);
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        return super.handle(packet);
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
            failures.report(session, packet, "serverbound", exception);
        } finally {
            input.release();
            output.release();
        }
        return PacketSignal.HANDLED;
    }

    private boolean bridgeUnavailable(int protocolVersion) {
        if (GeyserReversion.BRIDGE_GEYSER_CODEC != null) {
            return false;
        }
        session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
        session.disconnect(GeyserReversion.config().versionNotSupportedKick());
        return true;
    }

    private boolean checkCodec(int protocolVersion) {
        int minProtocolVer = GeyserReversion.config().minProtocolId();
        if (GeyserReversion.INJECTION_FAILED) {
            minProtocolVer = Math.max(Bedrock_v575.CODEC.getProtocolVersion(), minProtocolVer);
        }

        if (minProtocolVer != -1) {
            BedrockCodec codec = DuplicatedProtocolInfo.getPacketCodec(minProtocolVer);
            if (codec != null && protocolVersion < minProtocolVer) {
                session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
                session.disconnect(GeyserReversion.config().minProtocolKick().replace("%version%", codec.getMinecraftVersion()));
                return true;
            }
        }

        List<Integer> blockProtocols = GeyserReversion.config().blockProtocols();
        if (blockProtocols != null && blockProtocols.contains(protocolVersion)) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.config().blockedProtocolKick());
            return true;
        }

        if (ProtocolInfo.getPacketCodec(protocolVersion) == null && GeyserApiCompat.getBedrockCodec(protocolVersion) == null) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.config().versionNotSupportedKick());
            return true;
        }

        // Legacy floor guard: the 1.9.0/1.10.0/1.11.0 codecs are only usable once their generated
        // vanilla/v<protocol> mapping data exists; without it session setup would fail at runtime,
        // so kick with the configured "not supported" message instead of hitting an NPE.
        if (protocolVersion < Bedrock_v361.CODEC.getProtocolVersion()
                && GeyserApiCompat.getBedrockCodec(protocolVersion) == null
                && !BridgeCodecSelector.hasMappingData(protocolVersion)) {
            session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));
            session.disconnect(GeyserReversion.config().versionNotSupportedKick());
            return true;
        }

        return false;
    }
}
