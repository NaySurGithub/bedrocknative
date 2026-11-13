package net.bedrocknative.oreui;

import java.nio.file.Path;
import java.util.List;
import net.bedrocknative.ui.PlayData;
import org.jspecify.annotations.Nullable;

public final class OrePlayModel {
   public static final int TAB_WORLDS = 0;
   public static final int TAB_REALMS = 1;
   public static final int TAB_SERVERS = 2;
   private List<OrePlayModel.World> worlds = List.of();
   private List<PlayData.Lan> lan = List.of();
   private List<PlayData.Server> servers = List.of();
   private List<OrePlayModel.Featured> featured = List.of();
   private boolean worldsLoaded;
   private long version = 1L;

   public List<OrePlayModel.World> worlds() {
      return this.worlds;
   }

   public boolean worldsLoaded() {
      return this.worldsLoaded;
   }

   public void worlds(List<OrePlayModel.World> list, boolean loaded) {
      this.worlds = List.copyOf(list);
      this.worldsLoaded = loaded;
      this.version++;
   }

   public List<PlayData.Lan> lan() {
      return this.lan;
   }

   public void lan(List<PlayData.Lan> list) {
      if (!list.equals(this.lan)) {
         this.lan = List.copyOf(list);
         this.version++;
      }
   }

   public List<PlayData.Server> servers() {
      return this.servers;
   }

   public void servers(List<PlayData.Server> list) {
      if (!list.equals(this.servers)) {
         this.servers = List.copyOf(list);
         this.version++;
      }
   }

   public List<OrePlayModel.Featured> featured() {
      return this.featured;
   }

   public void featured(List<OrePlayModel.Featured> list) {
      if (!list.equals(this.featured)) {
         this.featured = List.copyOf(list);
         this.version++;
      }
   }

   public int worldCount() {
      return this.worlds.size() + this.lan.size();
   }

   public long version() {
      return this.version;
   }

   public record Featured(FeaturedServers.Server server, PlayData.Status status) {
   }

   public record World(String id, String name, String date, String modeTag, String size, @Nullable Path icon, boolean locked) {
   }
}
