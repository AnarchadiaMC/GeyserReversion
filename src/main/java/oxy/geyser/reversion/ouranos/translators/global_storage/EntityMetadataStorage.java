package oxy.geyser.reversion.ouranos.translators.global_storage;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.session.OuranosSession;
import oxy.geyser.reversion.ouranos.session.storage.OuranosStorage;
import lombok.Getter;
import lombok.SneakyThrows;
import org.cloudburstmc.protocol.bedrock.codec.BaseBedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.transformer.FlagTransformer;
import org.cloudburstmc.protocol.common.util.TypeMap;

import java.lang.reflect.Field;

@SuppressWarnings("ALL")
@Getter
public class EntityMetadataStorage extends OuranosStorage {
    private final EntityDataTypeMap dataTypeMap;
    private final TypeMap<EntityFlag> flags, flags_2;

    @SneakyThrows
    public EntityMetadataStorage(OuranosSession user) {
        super(user);

        final BaseBedrockCodecHelper helper = (BaseBedrockCodecHelper) ProtocolInfo.getPacketCodec(user.getProtocolId()).createHelper();

        final Field field = BaseBedrockCodecHelper.class.getDeclaredField("entityData");
        field.setAccessible(true);

        this.dataTypeMap = (EntityDataTypeMap) field.get(helper);

        final Field flagField = FlagTransformer.class.getDeclaredField("typeMap");
        flagField.setAccessible(true);

        flags = (TypeMap<EntityFlag>) flagField.get(this.dataTypeMap.fromType(EntityDataTypes.FLAGS).getTransformer());
        flags_2 = (TypeMap<EntityFlag>) flagField.get(this.dataTypeMap.fromType(EntityDataTypes.FLAGS_2).getTransformer());
    }
}
