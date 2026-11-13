package net.bedrocknative.client.inventory;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.bedrocknative.world.item.BedrockItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData.MapPatch;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundMapItemDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.MapInfoRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBClientRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBServerBroadcastPacket;
import org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBClientRequestPacket.Action;

public final class ItemWorldData {
   private static final Map<Integer, Holder<MapDecorationType>> ICONS = Map.ofEntries(
      Map.entry(0, MapDecorationTypes.PLAYER),
      Map.entry(1, MapDecorationTypes.FRAME),
      Map.entry(2, MapDecorationTypes.RED_MARKER),
      Map.entry(3, MapDecorationTypes.BLUE_MARKER),
      Map.entry(4, MapDecorationTypes.RED_X),
      Map.entry(5, MapDecorationTypes.TARGET_POINT),
      Map.entry(6, MapDecorationTypes.PLAYER_OFF_MAP),
      Map.entry(7, MapDecorationTypes.FRAME),
      Map.entry(13, MapDecorationTypes.PLAYER_OFF_LIMITS),
      Map.entry(14, MapDecorationTypes.WOODLAND_MANSION),
      Map.entry(15, MapDecorationTypes.OCEAN_MONUMENT),
      Map.entry(17, MapDecorationTypes.DESERT_VILLAGE),
      Map.entry(18, MapDecorationTypes.PLAINS_VILLAGE),
      Map.entry(19, MapDecorationTypes.SAVANNA_VILLAGE),
      Map.entry(20, MapDecorationTypes.SNOWY_VILLAGE),
      Map.entry(21, MapDecorationTypes.TAIGA_VILLAGE),
      Map.entry(22, MapDecorationTypes.JUNGLE_TEMPLE),
      Map.entry(23, MapDecorationTypes.SWAMP_HUT),
      Map.entry(24, MapDecorationTypes.TRIAL_CHAMBERS)
   );
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private final Set<Long> requestedMaps = ConcurrentHashMap.newKeySet();
   private final Set<Integer> requestedLodestones = ConcurrentHashMap.newKeySet();
   private final Map<Integer, GlobalPos> lodestones = new ConcurrentHashMap<>();
   private final Int2ByteOpenHashMap packedColors = new Int2ByteOpenHashMap();
   private Runnable refresh = () -> {};
   private int maps;

   public ItemWorldData(Minecraft minecraft, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.send = send;
      BedrockItemTags.useWorldData(this, this::mapSeen, this::lodestone);
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof ClientboundMapItemDataPacket || packet instanceof PositionTrackingDBServerBroadcastPacket;
   }

   public void onRefresh(Runnable refresh) {
      this.refresh = refresh;
   }

   public void reset() {
      this.requestedMaps.clear();
      this.requestedLodestones.clear();
      this.lodestones.clear();
      this.maps = 0;
   }

   public int mapUpdates() {
      return this.maps;
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case ClientboundMapItemDataPacket map:
            this.onMap(map);
            break;
         case PositionTrackingDBServerBroadcastPacket position:
            this.onLodestone(position);
            break;
         default:
      }
   }

   private void mapSeen(long id) {
      if (this.requestedMaps.add(id)) {
         MapInfoRequestPacket request = new MapInfoRequestPacket();
         request.setUniqueMapId(id);
         this.send.accept(request);
      }
   }

   private Optional<GlobalPos> lodestone(int handle) {
      GlobalPos pos = this.lodestones.get(handle);
      if (pos == null && this.requestedLodestones.add(handle)) {
         PositionTrackingDBClientRequestPacket request = new PositionTrackingDBClientRequestPacket();
         request.setAction(Action.QUERY);
         request.setTrackingId(handle);
         this.send.accept(request);
      }

      return Optional.ofNullable(pos);
   }

   private void onLodestone(PositionTrackingDBServerBroadcastPacket packet) {
      NbtMap tag = packet.getTag();
      List<Integer> pos = tag == null ? List.of() : tag.getList("pos", NbtType.INT);
      if (packet.getAction() == org.cloudburstmc.protocol.bedrock.packet.PositionTrackingDBServerBroadcastPacket.Action.UPDATE && pos.size() == 3) {
         ResourceKey<Level> dimension = switch (tag.getInt("dim", 0)) {
            case 1 -> Level.NETHER;
            case 2 -> Level.END;
            default -> Level.OVERWORLD;
         };
         this.lodestones.put(packet.getTrackingId(), GlobalPos.of(dimension, new BlockPos(pos.get(0), pos.get(1), pos.get(2))));
      } else {
         this.lodestones.remove(packet.getTrackingId());
      }

      this.refresh.run();
   }

   private void onMap(ClientboundMapItemDataPacket packet) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && this.minecraft.level != null) {
         MapPatch patch = null;
         int[] colors = packet.getColors();
         if (colors != null && colors.length > 0 && packet.getWidth() != null && packet.getHeight() != null) {
            int width = packet.getWidth();
            int height = packet.getHeight();
            byte[] packed = new byte[width * height];

            for (int i = 0; i < packed.length && i < colors.length; i++) {
               packed[i] = this.pack(colors[i]);
            }

            patch = new MapPatch(orZero(packet.getXOffset()), orZero(packet.getYOffset()), width, height, packed);
         }

         List<MapDecoration> decorations = null;
         if (packet.getDecorations() != null && !packet.getDecorations().isEmpty()) {
            decorations = new ArrayList<>();

            for (org.cloudburstmc.protocol.bedrock.data.MapDecoration decoration : packet.getDecorations()) {
               Holder<MapDecorationType> type = ICONS.get(decoration.getImage());
               if (type != null) {
                  decorations.add(
                     new MapDecoration(
                        type, (byte)decoration.getXOffset(), (byte)decoration.getYOffset(), (byte)(decoration.getRotation() & 15), Optional.empty()
                     )
                  );
               }
            }
         }

         byte scale = packet.getScale() == null ? 0 : packet.getScale();
         this.maps++;
         connection.handleMapItemData(
            new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(
               new MapId(BedrockItemTags.mapId(packet.getUniqueMapId())), scale, packet.isLocked(), decorations, patch
            )
         );
      }
   }

   private static int orZero(Integer value) {
      return value == null ? 0 : value;
   }

   private byte pack(int abgr) {
      if (abgr >>> 24 == 0) {
         return 0;
      }

      int rgb = ARGB.color(abgr & 0xFF, abgr >> 8 & 0xFF, abgr >> 16 & 0xFF) & 16777215;
      if (this.packedColors.containsKey(rgb)) {
         return this.packedColors.get(rgb);
      }

      byte best = 0;
      long bestDistance = Long.MAX_VALUE;

      for (int packedId = 4; packedId < 256; packedId++) {
         if (MapColor.byId(packedId >> 2) != MapColor.NONE) {
            int candidate = MapColor.getColorFromPackedId(packedId);
            long dr = ARGB.red(candidate) - ARGB.red(rgb);
            long dg = ARGB.green(candidate) - ARGB.green(rgb);
            long db = ARGB.blue(candidate) - ARGB.blue(rgb);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
               bestDistance = distance;
               best = (byte)packedId;
            }
         }
      }

      this.packedColors.put(rgb, best);
      return best;
   }
}
