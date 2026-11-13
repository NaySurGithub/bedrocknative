package net.bedrocknative.world.state;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.FurnaceOptions;
import org.cloudburstmc.protocol.bedrock.data.PresenceConfiguration;
import org.cloudburstmc.protocol.bedrock.data.SerializableVoxelShape;
import org.cloudburstmc.protocol.bedrock.data.definitions.FeatureDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundTextureShiftPacket;
import org.cloudburstmc.protocol.bedrock.packet.CurrentStructureFeaturePacket;
import org.cloudburstmc.protocol.bedrock.packet.FeatureRegistryPacket;
import org.cloudburstmc.protocol.bedrock.packet.JigsawStructureDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SendPartyDestinationCookiePacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerPresenceInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerFurnaceOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.bedrock.packet.SendPartyDestinationCookiePacket.Intent;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerFurnaceOptionsPacket.FurnaceType;
import org.jspecify.annotations.Nullable;

public final class ServerKeeps {
   private final Map<FurnaceType, FurnaceOptions> furnaces = new EnumMap<>(FurnaceType.class);
   @Nullable
   private volatile PresenceConfiguration presence;
   private volatile List<SerializableVoxelShape> shapes = List.of();
   private volatile Map<String, Integer> shapeNames = Map.of();
   private volatile int customShapes;
   @Nullable
   private volatile NbtMap jigsaw;
   private volatile List<FeatureDefinition> features = List.of();
   private volatile String structureFeature = "";
   @Nullable
   private volatile ServerKeeps.PartyCookie partyCookie;
   private volatile int textureShifts;

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof SetPlayerFurnaceOptionsPacket
         || packet instanceof ServerPresenceInfoPacket
         || packet instanceof VoxelShapesPacket
         || packet instanceof JigsawStructureDataPacket
         || packet instanceof FeatureRegistryPacket
         || packet instanceof CurrentStructureFeaturePacket
         || packet instanceof SendPartyDestinationCookiePacket
         || packet instanceof ClientboundTextureShiftPacket;
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case SetPlayerFurnaceOptionsPacket options:
            if (options.getType() != null && options.getOptions() != null) {
               synchronized (this.furnaces) {
                  this.furnaces.put(options.getType(), options.getOptions());
               }
            }
            break;
         case ServerPresenceInfoPacket info:
            this.presence = info.getPresenceConfiguration();
            break;
         case VoxelShapesPacket voxel:
            this.shapes = voxel.getShapes() == null ? List.of() : List.copyOf(voxel.getShapes());
            this.shapeNames = voxel.getNameMap() == null ? Map.of() : Map.copyOf(voxel.getNameMap());
            this.customShapes = voxel.getCustomShapeCount();
            break;
         case JigsawStructureDataPacket data:
            this.jigsaw = data.getJigsawStructureDataTag();
            break;
         case FeatureRegistryPacket registry:
            this.features = registry.getFeatures() == null ? List.of() : List.copyOf(registry.getFeatures());
            break;
         case CurrentStructureFeaturePacket feature:
            this.structureFeature = feature.getCurrentStructureFeature() == null ? "" : feature.getCurrentStructureFeature();
            break;
         case SendPartyDestinationCookiePacket cookie:
            this.partyCookie = new ServerKeeps.PartyCookie(
               cookie.getCookie() == null ? "" : cookie.getCookie(), cookie.getIntent(), cookie.getDestinationName() == null ? "" : cookie.getDestinationName()
            );
            break;
         case ClientboundTextureShiftPacket ignored:
            this.textureShifts++;
            break;
         default:
      }
   }

   public void clear() {
      synchronized (this.furnaces) {
         this.furnaces.clear();
      }

      this.presence = null;
      this.shapes = List.of();
      this.shapeNames = Map.of();
      this.customShapes = 0;
      this.jigsaw = null;
      this.features = List.of();
      this.structureFeature = "";
      this.partyCookie = null;
      this.textureShifts = 0;
   }

   @Nullable
   public FurnaceOptions furnace(FurnaceType type) {
      synchronized (this.furnaces) {
         return this.furnaces.get(type);
      }
   }

   @Nullable
   public SetPlayerFurnaceOptionsPacket changeFurnace(FurnaceType type, FurnaceOptions options) {
      synchronized (this.furnaces) {
         if (options.equals(this.furnaces.get(type))) {
            return null;
         }

         this.furnaces.put(type, options);
      }

      SetPlayerFurnaceOptionsPacket packet = new SetPlayerFurnaceOptionsPacket();
      packet.setType(type);
      packet.setOptions(options);
      return packet;
   }

   @Nullable
   public PresenceConfiguration presence() {
      return this.presence;
   }

   public List<SerializableVoxelShape> shapes() {
      return this.shapes;
   }

   public Map<String, Integer> shapeNames() {
      return this.shapeNames;
   }

   public int customShapes() {
      return this.customShapes;
   }

   @Nullable
   public NbtMap jigsaw() {
      return this.jigsaw;
   }

   public List<FeatureDefinition> features() {
      return this.features;
   }

   public String structureFeature() {
      return this.structureFeature;
   }

   @Nullable
   public ServerKeeps.PartyCookie partyCookie() {
      return this.partyCookie;
   }

   public int textureShifts() {
      return this.textureShifts;
   }

   public record PartyCookie(String cookie, Intent intent, String destinationName) {
   }
}
