package com.github.blackjack200.ouranos.converter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v776.Bedrock_v776;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;

import java.util.*;

public class ItemIdentifierConverter {
    private static TreeMap<Integer, Map<String, String>> ID_TO_ITEMS = new TreeMap<>();

    static {
        ID_TO_ITEMS.put(Bedrock_v844.CODEC.getProtocolVersion(), loadItemMappingsFromFile("itemIdentifiers_v844to827.json"));
        ID_TO_ITEMS.put(Bedrock_v827.CODEC.getProtocolVersion(), loadItemMappingsFromFile("itemIdentifiers_v827to819.json"));
        ID_TO_ITEMS.put(Bedrock_v800.CODEC.getProtocolVersion(), loadItemMappingsFromFile("itemIdentifiers_v800to786.json"));
        ID_TO_ITEMS.put(Bedrock_v776.CODEC.getProtocolVersion(), loadItemMappingsFromFile("itemIdentifiers_v776to766.json"));
        ID_TO_ITEMS.put(Bedrock_v766.CODEC.getProtocolVersion(), loadItemMappingsFromFile("itemIdentifiers_v766to748.json"));
        ID_TO_ITEMS.put(Bedrock_v748.CODEC.getProtocolVersion(), loadItemMappingsFromFile("itemIdentifiers_v748to729.json"));
    }

    public static String translate(String identifier, int target, int client) {
        if (target <= client) {
            return identifier;
        }

        for (Map.Entry<Integer, Map<String, String>> mapper : ID_TO_ITEMS.descendingMap().entrySet()) {
            final int protocolVersion = mapper.getKey();
            if (protocolVersion < client) {
                break;
            }
            if (protocolVersion > target) {
                continue;
            }

            identifier = mapper.getValue().getOrDefault(identifier, identifier);
        }

        return identifier;
    }

    private static Map<String, String> loadItemMappingsFromFile(String name) {
        final Map<String, String> identifierToIdentifier = new HashMap<>();
        try {
            final String jsonString = new String(Objects.requireNonNull(ItemIdentifierConverter.class.getResourceAsStream("/itemIdentifiers/" + name)).readAllBytes());
            final JsonObject object = JsonParser.parseString(jsonString).getAsJsonObject();
            for (String key : object.keySet()) {
                identifierToIdentifier.put(key, object.get(key).getAsString());
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        return Collections.unmodifiableMap(identifierToIdentifier);
    }
}
