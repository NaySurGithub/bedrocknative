package net.bedrocknative.protocol;

import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.codec.v2193.BedrockCodecHelper_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.codec.v2225.BedrockCodecHelper_v2225;
import org.cloudburstmc.protocol.bedrock.codec.v2225.Bedrock_v2225;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.TextProcessingEventOrigin;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovementPredictionSyncPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.cloudburstmc.protocol.common.util.TypeMap;

final class CodecFixes {
   private CodecFixes() {
   }

   static BedrockCodec v2193() {
      return fix(Bedrock_v2193.CODEC, CodecFixes.V2193Maps::helper, CodecFixes.V2193Maps.LEVEL_EVENTS_MAP, CodecFixes.V2193Maps.ENTITY_FLAGS_MAP);
   }

   static BedrockCodec v2225() {
      return fix(Bedrock_v2225.CODEC, CodecFixes.V2225Maps::helper, CodecFixes.V2225Maps.LEVEL_EVENTS_MAP, CodecFixes.V2225Maps.ENTITY_FLAGS_MAP);
   }

   private static BedrockCodec fix(BedrockCodec codec, Supplier<BedrockCodecHelper> helper, TypeMap<LevelEventType> events, TypeMap<EntityFlag> entityFlags) {
      BedrockPacketDefinition<PacketViolationWarningPacket> warning = codec.getPacketDefinition(PacketViolationWarningPacket.class);
      BedrockPacketDefinition<LevelSoundEventPacket> sound = codec.getPacketDefinition(LevelSoundEventPacket.class);
      return codec.toBuilder()
         .deregisterPacket(PacketViolationWarningPacket.class)
         .registerPacket(PacketViolationWarningPacket::new, warning.getSerializer(), warning.getId(), PacketRecipient.BOTH)
         .deregisterPacket(LevelSoundEventPacket.class)
         .registerPacket(NamedLevelSoundEventPacket::new, NamedLevelSoundEventSerializer.INSTANCE, sound.getId(), sound.getRecipient())
         .updateSerializer(LevelEventGenericPacket.class, new LevelEventGenericSerializer(events))
         .updateSerializer(MovementPredictionSyncPacket.class, new MovementPredictionSyncSerializer(entityFlags))
         .helper(helper)
         .build();
   }

   private static final class V2193Maps extends Bedrock_v2193 {
      static final TypeMap<LevelEventType> LEVEL_EVENTS_MAP = LEVEL_EVENTS;
      static final TypeMap<EntityFlag> ENTITY_FLAGS_MAP = ENTITY_FLAGS;

      static BedrockCodecHelper helper() {
         return new CodecFixes.Wide2193(ENTITY_DATA, GAME_RULE_TYPES, ITEM_STACK_REQUEST_TYPES, CONTAINER_SLOT_TYPES, PLAYER_ABILITIES, TEXT_PROCESSING_ORIGINS);
      }
   }

   private static final class V2225Maps extends Bedrock_v2225 {
      static final TypeMap<LevelEventType> LEVEL_EVENTS_MAP = LEVEL_EVENTS;
      static final TypeMap<EntityFlag> ENTITY_FLAGS_MAP = ENTITY_FLAGS;

      static BedrockCodecHelper helper() {
         return new CodecFixes.Wide2225(ENTITY_DATA, GAME_RULE_TYPES, ITEM_STACK_REQUEST_TYPES, CONTAINER_SLOT_TYPES, PLAYER_ABILITIES, TEXT_PROCESSING_ORIGINS);
      }
   }

   static final class Wide2193 extends BedrockCodecHelper_v2193 {
      Wide2193(
         EntityDataTypeMap entityData,
         TypeMap<Class<?>> gameRulesTypes,
         TypeMap<ItemStackRequestActionType> stackRequestActionTypes,
         TypeMap<ContainerSlotType> containerSlotTypes,
         TypeMap<Ability> abilities,
         TypeMap<TextProcessingEventOrigin> textProcessingEventOrigins
      ) {
         super(entityData, gameRulesTypes, stackRequestActionTypes, containerSlotTypes, abilities, textProcessingEventOrigins);
      }

      @Override
      public ImageData readImage(ByteBuf buffer, int maxSize) {
         return super.readImage(buffer, Math.max(maxSize, 4194304));
      }

      @Override
      protected void writeRequestActionData(ByteBuf buffer, ItemStackRequestAction action) {
         int legacyByte = buffer.writerIndex();
         super.writeRequestActionData(buffer, action);
         switch (action.getType()) {
            case CRAFT_NON_IMPLEMENTED_DEPRECATED:
               buffer.setByte(legacyByte, 18);
               break;
            case CRAFT_RESULTS_DEPRECATED:
               buffer.setByte(legacyByte, 19);
         }
      }
   }

   static final class Wide2225 extends BedrockCodecHelper_v2225 {
      Wide2225(
         EntityDataTypeMap entityData,
         TypeMap<Class<?>> gameRulesTypes,
         TypeMap<ItemStackRequestActionType> stackRequestActionTypes,
         TypeMap<ContainerSlotType> containerSlotTypes,
         TypeMap<Ability> abilities,
         TypeMap<TextProcessingEventOrigin> textProcessingEventOrigins
      ) {
         super(entityData, gameRulesTypes, stackRequestActionTypes, containerSlotTypes, abilities, textProcessingEventOrigins);
      }

      @Override
      public ImageData readImage(ByteBuf buffer, int maxSize) {
         return super.readImage(buffer, Math.max(maxSize, 4194304));
      }
   }
}
