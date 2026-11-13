package net.bedrocknative.world.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

public final class BedrockBlockPalette {
   static final String MAPPINGS_DIR = "/assets/bedrocknative/mappings/";
   static final String DEFAULT = "bedrock-26.50";
   private static final ConcurrentHashMap<String, Set<String>> DATA_DRIVEN = new ConcurrentHashMap<>();
   private final List<BedrockBlockState> states;

   BedrockBlockPalette(List<BedrockBlockState> states) {
      this.states = Collections.unmodifiableList(states);
   }

   public List<BedrockBlockState> states() {
      return this.states;
   }

   public static BedrockBlockPalette loadBundled() {
      return loadBundled("bedrock-26.50");
   }

   public static BedrockBlockPalette loadBundled(String folder) {
      NbtMap root;
      try (
         InputStream raw = open("/assets/bedrocknative/mappings/" + folder + "/block_palette.nbt");
         NBTInputStream in = NbtUtils.createGZIPReader(raw);
      ) {
         root = (NbtMap)in.readTag();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }

      List<NbtMap> blocks = root.getList("blocks", NbtType.COMPOUND);
      List<BedrockBlockState> states = new ArrayList<>(blocks.size());

      for (NbtMap block : blocks) {
         String name = namespaced(block.getString("name"));
         TreeMap<String, Object> values = new TreeMap<>();

         for (Entry<String, Object> state : block.getCompound("states").entrySet()) {
            values.put(state.getKey(), state.getValue());
         }

         states.add(new BedrockBlockState(name, values, BedrockBlockHasher.hash(name, values)));
      }

      return new BedrockBlockPalette(states);
   }

   public static Set<String> dataDrivenBlocks() {
      return dataDrivenBlocks("bedrock-26.50");
   }

   public static Set<String> dataDrivenBlocks(String folder) {
      return DATA_DRIVEN.computeIfAbsent(folder, key -> {
         try (
            InputStream raw = open("/assets/bedrocknative/mappings/" + key + "/data_driven_blocks.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8));
         ) {
            return reader.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).collect(Collectors.toUnmodifiableSet());
         } catch (IOException e) {
            throw new UncheckedIOException(e);
         }
      });
   }

   static String namespaced(String name) {
      return name.indexOf(58) >= 0 ? name : "minecraft:" + name;
   }

   static JsonElement readGzipJson(String resource) {
      try (
         InputStream raw = open(resource);
         InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8);
      ) {
         return JsonParser.parseReader(reader);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   private static InputStream open(String resource) {
      InputStream stream = BedrockBlockPalette.class.getResourceAsStream(resource);
      if (stream == null) {
         throw new IllegalStateException("Missing bundled resource " + resource);
      } else {
         return stream;
      }
   }
}
