package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreAction;
import org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreChange;
import org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreRemoval;
import org.cloudburstmc.protocol.bedrock.data.datastore.DataStoreUpdate;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUICloseScreenPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUIReloadPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundDataDrivenUIShowScreenPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundDataStorePacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerboundDataDrivenScreenClosedPacket.CloseReason;
import org.jspecify.annotations.Nullable;

public final class DataDrivenUi {
   private final Map<String, Map<String, Object>> stores = new LinkedHashMap<>();
   private static final int MAX_SHOWN = 256;
   private final List<String> shown = new ArrayList<>();
   private final Consumer<BedrockPacket> send;

   public DataDrivenUi(Consumer<BedrockPacket> send) {
      this.send = send;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof ClientboundDataStorePacket
         || packet instanceof ClientboundDataDrivenUIShowScreenPacket
         || packet instanceof ClientboundDataDrivenUICloseScreenPacket
         || packet instanceof ClientboundDataDrivenUIReloadPacket;
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case ClientboundDataStorePacket store:
            if (store.getUpdates() != null) {
               for (DataStoreAction action : store.getUpdates()) {
                  this.apply(action);
               }
            }
            break;
         case ClientboundDataDrivenUIShowScreenPacket show:
            synchronized (this.shown) {
               this.shown.add(show.getScreenId());
               if (this.shown.size() > 256) {
                  this.shown.remove(0);
               }
            }

            this.send.accept(closed(show.getFormId(), CloseReason.INVALID_FORM));
            return;
         default:
      }
   }

   private void apply(DataStoreAction action) {
      synchronized (this.stores) {
         switch (action) {
            case DataStoreUpdate update:
               if (update.getDataStoreName() != null && update.getProperty() != null) {
                  String key = update.getPath() != null && !update.getPath().isEmpty() ? update.getProperty() + "#" + update.getPath() : update.getProperty();
                  this.stores.computeIfAbsent(update.getDataStoreName(), name -> new LinkedHashMap<>()).put(key, update.getData());
               }
               break;
            case DataStoreChange change:
               if (change.getDataStoreName() != null && change.getProperty() != null) {
                  this.stores.computeIfAbsent(change.getDataStoreName(), name -> new LinkedHashMap<>()).put(change.getProperty(), change.getNewValue());
               }
               break;
            case DataStoreRemoval removal:
               this.stores.remove(removal.getDataStoreName());
               break;
            default:
         }
      }
   }

   public static ServerboundDataDrivenScreenClosedPacket closed(@Nullable Integer formId, CloseReason reason) {
      ServerboundDataDrivenScreenClosedPacket packet = new ServerboundDataDrivenScreenClosedPacket();
      packet.setFormId(formId);
      packet.setCloseReason(reason);
      return packet;
   }

   @Nullable
   public Object value(String store, String property) {
      synchronized (this.stores) {
         Map<String, Object> properties = this.stores.get(store);
         return properties == null ? null : properties.get(property);
      }
   }

   public int storeCount() {
      synchronized (this.stores) {
         return this.stores.size();
      }
   }

   public List<String> shownScreens() {
      synchronized (this.shown) {
         return List.copyOf(this.shown);
      }
   }

   public void clear() {
      synchronized (this.stores) {
         this.stores.clear();
      }

      synchronized (this.shown) {
         this.shown.clear();
      }
   }
}
