package com.github.blackjack200.ouranos;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.data.bedrock.GlobalItemDataHandlers;
import com.github.blackjack200.ouranos.translators.new_to_old.v388to361.Protocol388to361;
import com.github.blackjack200.ouranos.translators.new_to_old.v407to390.Protocol407to390;
import com.github.blackjack200.ouranos.translators.new_to_old.v419to408.Protocol419to408;
import com.github.blackjack200.ouranos.translators.new_to_old.v428to422.Protocol428to422;
import com.github.blackjack200.ouranos.translators.new_to_old.v527to503.Protocol527to503;
import com.github.blackjack200.ouranos.translators.new_to_old.v534to527.Protocol534to527;
import com.github.blackjack200.ouranos.translators.new_to_old.v544to534.Protocol544to534;
import com.github.blackjack200.ouranos.translators.new_to_old.v560to577.Protocol560to577;
import com.github.blackjack200.ouranos.translators.new_to_old.v575to568.Protocol575to568;
import com.github.blackjack200.ouranos.translators.new_to_old.v582to575.Protocol582to575;
import com.github.blackjack200.ouranos.translators.new_to_old.v589to582.Protocol589to582;
import com.github.blackjack200.ouranos.translators.new_to_old.v618to594.Protocol618to594;
import com.github.blackjack200.ouranos.translators.new_to_old.v685to671.Protocol685to671;
import com.github.blackjack200.ouranos.translators.new_to_old.v712to686.Protocol712to686;
import com.github.blackjack200.ouranos.translators.new_to_old.v729to712.Protocol729to712;
import com.github.blackjack200.ouranos.translators.new_to_old.v748to729.Protocol748to729;
import com.github.blackjack200.ouranos.translators.new_to_old.v766to748.Protocol766to748;
import com.github.blackjack200.ouranos.translators.new_to_old.v776to766.Protocol776to766;
import com.github.blackjack200.ouranos.translators.new_to_old.v818to800.Protocol818to800;
import com.github.blackjack200.ouranos.translators.new_to_old.v844to827.Protocol844to827;
import com.github.blackjack200.ouranos.translators.old_to_new.v408to419.Protocol408to419;
import com.github.blackjack200.ouranos.translators.old_to_new.v503to527.Protocol503to527;
import com.github.blackjack200.ouranos.translators.old_to_new.v545to554.Protocol545to554;
import com.github.blackjack200.ouranos.translators.old_to_new.v582to589.Protocol582to589;
import com.github.blackjack200.ouranos.translators.old_to_new.v671to685.Protocol671to685;
import com.github.blackjack200.ouranos.translators.old_to_new.v712to729.Protocol712to729;
import com.github.blackjack200.ouranos.translators.old_to_new.v766to776.Protocol766to776;
import com.github.blackjack200.ouranos.utils.CodecUtil;
import com.github.blackjack200.ouranos.utils.Pair;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v361.Bedrock_v361;
import org.cloudburstmc.protocol.bedrock.codec.v388.Bedrock_v388;
import org.cloudburstmc.protocol.bedrock.codec.v389.Bedrock_v389;
import org.cloudburstmc.protocol.bedrock.codec.v390.Bedrock_v390;
import org.cloudburstmc.protocol.bedrock.codec.v407.Bedrock_v407;
import org.cloudburstmc.protocol.bedrock.codec.v408.Bedrock_v408;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.codec.v422.Bedrock_v422;
import org.cloudburstmc.protocol.bedrock.codec.v428.Bedrock_v428;
import org.cloudburstmc.protocol.bedrock.codec.v431.Bedrock_v431;
import org.cloudburstmc.protocol.bedrock.codec.v440.Bedrock_v440;
import org.cloudburstmc.protocol.bedrock.codec.v448.Bedrock_v448;
import org.cloudburstmc.protocol.bedrock.codec.v465.Bedrock_v465;
import org.cloudburstmc.protocol.bedrock.codec.v471.Bedrock_v471;
import org.cloudburstmc.protocol.bedrock.codec.v475.Bedrock_v475;
import org.cloudburstmc.protocol.bedrock.codec.v486.Bedrock_v486;
import org.cloudburstmc.protocol.bedrock.codec.v503.Bedrock_v503;
import org.cloudburstmc.protocol.bedrock.codec.v527.Bedrock_v527;
import org.cloudburstmc.protocol.bedrock.codec.v534.Bedrock_v534;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v545.Bedrock_v545;
import org.cloudburstmc.protocol.bedrock.codec.v554.Bedrock_v554;
import org.cloudburstmc.protocol.bedrock.codec.v557.Bedrock_v557;
import org.cloudburstmc.protocol.bedrock.codec.v560.Bedrock_v560;
import org.cloudburstmc.protocol.bedrock.codec.v567.Bedrock_v567;
import org.cloudburstmc.protocol.bedrock.codec.v568.Bedrock_v568;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v582.Bedrock_v582;
import org.cloudburstmc.protocol.bedrock.codec.v589.Bedrock_v589;
import org.cloudburstmc.protocol.bedrock.codec.v594.Bedrock_v594;
import org.cloudburstmc.protocol.bedrock.codec.v618.Bedrock_v618;
import org.cloudburstmc.protocol.bedrock.codec.v622.Bedrock_v622;
import org.cloudburstmc.protocol.bedrock.codec.v630.Bedrock_v630;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.codec.v662.Bedrock_v662;
import org.cloudburstmc.protocol.bedrock.codec.v671.Bedrock_v671;
import org.cloudburstmc.protocol.bedrock.codec.v685.Bedrock_v685;
import org.cloudburstmc.protocol.bedrock.codec.v686.Bedrock_v686;
import org.cloudburstmc.protocol.bedrock.codec.v712.Bedrock_v712;
import org.cloudburstmc.protocol.bedrock.codec.v729.Bedrock_v729;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v819.Bedrock_v819;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;
import org.cloudburstmc.protocol.bedrock.codec.v859.Bedrock_v859;
import org.cloudburstmc.protocol.bedrock.codec.v860.Bedrock_v860;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.codec.v924.Bedrock_v924;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtocolInfo {
    private static final TreeMap<Integer, Pair<ProtocolToProtocol, ProtocolToProtocol>> PROTOCOL_TRANSLATORS = new TreeMap<>();
    private static final Set<BedrockCodec> PACKET_CODECS = ConcurrentHashMap.newKeySet();
    private static final Set<BedrockCodec> UNMODIFIABLE_PACKET_CODECS = Collections.unmodifiableSet(PACKET_CODECS);

    public static void init() {
    }

    static {
        // 1.26.x
        addPacketCodec(Bedrock_v944.CODEC, 251);
        addPacketCodec(Bedrock_v924.CODEC, 251);

        // 1.21.x
        addPacketCodec(Bedrock_v898.CODEC, 251);
        addPacketCodec(Bedrock_v860.CODEC, 251);
        addPacketCodec(Bedrock_v859.CODEC, 251);
        addPacketCodec(Bedrock_v844.CODEC, 251);

        addPacketCodec(Bedrock_v844.CODEC, 241, new Protocol844to827(), null);
        addPacketCodec(Bedrock_v827.CODEC, 241);
        addPacketCodec(Bedrock_v819.CODEC, 231);
        addPacketCodec(Bedrock_v818.CODEC, 231, new Protocol818to800(), null);
        addPacketCodec(Bedrock_v800.CODEC, 231);
        addPacketCodec(Bedrock_v786.CODEC, 231);
        addPacketCodec(Bedrock_v776.CODEC, 231, new Protocol776to766(), null);
        addPacketCodec(Bedrock_v766.CODEC, 231, new Protocol766to748(), new Protocol766to776());
        addPacketCodec(Bedrock_v748.CODEC, 221, new Protocol748to729(), null);
        addPacketCodec(Bedrock_v729.CODEC, 211, new Protocol729to712(), null);
        addPacketCodec(Bedrock_v712.CODEC, 201, new Protocol712to686(), new Protocol712to729());
        addPacketCodec(Bedrock_v686.CODEC, 201, new Protocol685to671(), null);
        addPacketCodec(Bedrock_v685.CODEC, 191);

        // 1.20.x
        addPacketCodec(Bedrock_v671.CODEC, 181, null, new Protocol671to685());
        addPacketCodec(Bedrock_v662.CODEC, 171);
        addPacketCodec(Bedrock_v649.CODEC, 161);
        addPacketCodec(Bedrock_v630.CODEC, 151);
        addPacketCodec(Bedrock_v622.CODEC, 141);
        addPacketCodec(Bedrock_v618.CODEC, 131, new Protocol618to594(), null);
        addPacketCodec(Bedrock_v594.CODEC, 121);
        addPacketCodec(Bedrock_v589.CODEC, 121, new Protocol589to582(), null);

        // 1.19.x
        addPacketCodec(Bedrock_v582.CODEC, 101, new Protocol582to575(), new Protocol582to589());
        addPacketCodec(Bedrock_v575.CODEC, 91, new Protocol575to568(), null);
        addPacketCodec(Bedrock_v568.CODEC, 81);
        addPacketCodec(Bedrock_v567.CODEC, 81);

        addPacketCodec(Bedrock_v560.CODEC, 81, new Protocol560to577(), null);

        addPacketCodec(Bedrock_v557.CODEC, 81);
        addPacketCodec(Bedrock_v554.CODEC, 81);

        addPacketCodec(Bedrock_v545.CODEC, 71, null, new Protocol545to554());
        addPacketCodec(Bedrock_v544.CODEC, 71, new Protocol544to534(), null);
        addPacketCodec(Bedrock_v534.CODEC, 71, new Protocol534to527(), null);
        addPacketCodec(Bedrock_v527.CODEC, 71, new Protocol527to503(), null);

        // 1.18.x
        addPacketCodec(Bedrock_v503.CODEC, 71, null, new Protocol503to527());
        addPacketCodec(Bedrock_v486.CODEC, 61);
        addPacketCodec(Bedrock_v475.CODEC, 51);

        // 1.17.x
        addPacketCodec(Bedrock_v471.CODEC, 41);
        addPacketCodec(Bedrock_v465.CODEC, 31);
        addPacketCodec(Bedrock_v448.CODEC, 31);
        addPacketCodec(Bedrock_v440.CODEC, 31);

        // 1.16.x
        addPacketCodec(Bedrock_v431.CODEC, 31);
        addPacketCodec(Bedrock_v428.CODEC, 31, new Protocol428to422(), null);
        addPacketCodec(Bedrock_v422.CODEC, 31);
        addPacketCodec(Bedrock_v419.CODEC, 21, new Protocol419to408(), null);

        //1.16.40 partially playable
        addPacketCodec(Bedrock_v408.CODEC, 11, null, new Protocol408to419());
        addPacketCodec(Bedrock_v407.CODEC, 11, new Protocol407to390(), null);

        // 1.14.x partially playable
        addPacketCodec(Bedrock_v390.CODEC, 11);
        addPacketCodec(Bedrock_v389.CODEC, 11);

        // 1.13.0 partially playable
        addPacketCodec(Bedrock_v388.CODEC, 11, new Protocol388to361(), null);

        // 1.12.0 partially playable
        addPacketCodec(Bedrock_v361.CODEC, 11);

        // 1.11.0 not playable
        // addPacketCodec(Bedrock_v354.CODEC, 1);
        // addPacketCodec(Bedrock_v340.CODEC, 21);
        // addPacketCodec(Bedrock_v332.CODEC, 21);
        // addPacketCodec(Bedrock_v313.CODEC, 21);
        // addPacketCodec(Bedrock_v291.CODEC, 21);
    }

    public static List<ProtocolToProtocol> getTranslators(int target, int client) {
        if (target == client) {
            return List.of();
        }

        boolean isDescending = target > client;

        final List<ProtocolToProtocol> translators = new ArrayList<>();
        for (Map.Entry<Integer, Pair<ProtocolToProtocol, ProtocolToProtocol>> protocol : (isDescending ? PROTOCOL_TRANSLATORS.descendingMap() : PROTOCOL_TRANSLATORS).entrySet()) {
            final int protocolVersion = protocol.getKey();
            if (isDescending ? protocolVersion < client : protocolVersion > client) {
                break;
            }
            if (isDescending == (protocolVersion > target)) {
                continue;
            }

            final ProtocolToProtocol protocolToProtocol = isDescending ? protocol.getValue().a() : protocol.getValue().b();
            if (protocolToProtocol != null) {
                translators.add(protocolToProtocol);
            }
        }

        return translators;
    }

    public static BedrockCodec getPacketCodec(int protocolVersion) {
        for (var packetCodec : PACKET_CODECS) {
            if (packetCodec.getProtocolVersion() == protocolVersion) {
                return packetCodec;
            }
        }
        return null;
    }

    public static void addPacketCodec(BedrockCodec packetCodec, int schemaId) {
        addPacketCodec(packetCodec, schemaId, null, null);
    }

    public static void addPacketCodec(BedrockCodec packetCodec, int schemaId, ProtocolToProtocol toOlder, ProtocolToProtocol toNewer) {
        PACKET_CODECS.add(CodecUtil.rebuildCodec(packetCodec));

        GlobalItemDataHandlers.SCHEMA_ID.put(packetCodec.getProtocolVersion(), schemaId);

        if (toOlder != null || toNewer != null) {
            PROTOCOL_TRANSLATORS.put(packetCodec.getProtocolVersion(), new Pair<>(toOlder, toNewer));
        }
    }

    public static Set<BedrockCodec> getPacketCodecs() {
        return UNMODIFIABLE_PACKET_CODECS;
    }
}