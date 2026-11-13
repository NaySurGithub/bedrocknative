package net.bedrocknative.world.block.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.TextureData;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

public final class BlockCullingRules {
   public static final String FOLDER = "block_culling";
   public static final String UNDEFINED_LAYER = "minecraft:culling_layer.undefined";

   private BlockCullingRules() {
   }

   @Nullable
   public static String layer(String cullingLayer) {
      String trimmed = cullingLayer.trim();
      return !trimmed.isEmpty() && !trimmed.equalsIgnoreCase("minecraft:culling_layer.undefined") ? trimmed.toLowerCase(Locale.ROOT) : null;
   }

   public static Map<String, List<BlockCullingRules.Rule>> read(PackStack stack) {
      Map<String, List<BlockCullingRules.Rule>> rules = new HashMap<>();

      for (PackSource pack : stack.packs()) {
         for (String file : pack.list("block_culling")) {
            if (file.toLowerCase(Locale.ROOT).endsWith(".json")) {
               try {
                  Optional<byte[]> data = pack.read(file);
                  JsonObject root = data.isEmpty() ? null : TextureData.json(data.get(), file + " in " + pack);
                  if (root != null) {
                     parse(root).forEach(rules::putIfAbsent);
                  }
               } catch (RuntimeException | IOException var8) {
                  BedrockNative.LOGGER.warn("Couldn't read culling rules {} from {}", new Object[]{file, pack, var8});
               }
            }
         }
      }

      return rules;
   }

   public static Map<String, List<BlockCullingRules.Rule>> parse(JsonObject root) {
      Map<String, List<BlockCullingRules.Rule>> found = new HashMap<>();
      if (root.get("minecraft:block_culling_rules") instanceof JsonObject definition
         && definition.get("description") instanceof JsonObject description
         && string(description.get("identifier")) != null) {
         String identifier = string(description.get("identifier")).toLowerCase(Locale.ROOT);
         List<BlockCullingRules.Rule> rules = new ArrayList<>();
         JsonElement var7 = definition.get("rules");
         if (var7 instanceof JsonArray) {
            for (JsonElement element : (JsonArray)var7) {
               BlockCullingRules.Rule rule = element instanceof JsonObject object ? rule(object) : null;
               if (rule != null) {
                  rules.add(rule);
               }
            }
         }

         found.put(identifier, List.copyOf(rules));
         return found;
      } else {
         return found;
      }
   }

   @Nullable
   private static BlockCullingRules.Rule rule(JsonObject object) {
      String directionName = string(object.get("direction"));
      Direction direction = directionName == null ? null : Direction.byName(directionName.toLowerCase(Locale.ROOT));
      if (direction != null && object.get("geometry_part") instanceof JsonObject part && string(part.get("bone")) != null) {
         int cube = -1;
         JsonElement against = part.get("cube");
         if (against instanceof JsonElement) {
            JsonElement cubeElement = against;
            if (against.isJsonPrimitive()) {
               try {
                  cube = Math.max(-1, cubeElement.getAsInt());
               } catch (NumberFormatException var9) {
                  cube = -1;
               }
            }
         }

         String face = string(part.get("face"));
         JsonElement var8 = object.get("cull_against_full_and_opaque");
         boolean againstx = !(var8 instanceof JsonElement) || !var8.isJsonPrimitive() || bool(var8);
         return new BlockCullingRules.Rule(
            direction,
            string(part.get("bone")).toLowerCase(Locale.ROOT),
            cube,
            face == null ? null : face.toLowerCase(Locale.ROOT),
            BlockCullingRules.Condition.parse(string(object.get("condition"))),
            againstx
         );
      } else {
         return null;
      }
   }

   @Nullable
   private static String string(@Nullable JsonElement element) {
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   private static boolean bool(JsonElement element) {
      if (element.getAsJsonPrimitive().isBoolean()) {
         return element.getAsBoolean();
      } else {
         String text = element.getAsString();
         return !text.equalsIgnoreCase("false") && !text.equals("0");
      }
   }

   public record Check(Direction direction, BlockCullingRules.Condition condition, boolean againstFullAndOpaque) {
      public boolean culls(BlockCullingRules.Neighbour neighbour, String block, @Nullable String layer) {
         if (this.againstFullAndOpaque && neighbour.fullAndOpaque()) {
            return true;
         } else {
            return switch (this.condition) {
               case DEFAULT -> false;
               case SAME_BLOCK -> block.equals(neighbour.customBlock());
               case SAME_BLOCK_PERMUTATION -> neighbour.samePermutation();
               case SAME_CULLING_LAYER -> layer != null && layer.equals(neighbour.cullingLayer());
            };
         }
      }
   }

   public static enum Condition {
      DEFAULT,
      SAME_BLOCK,
      SAME_BLOCK_PERMUTATION,
      SAME_CULLING_LAYER;

      static BlockCullingRules.Condition parse(@Nullable String name) {
         if (name == null) {
            return DEFAULT;
         } else {
            String var1 = name.toLowerCase(Locale.ROOT);

            return switch (var1) {
               case "same_block" -> SAME_BLOCK;
               case "same_block_permutation" -> SAME_BLOCK_PERMUTATION;
               case "same_culling_layer" -> SAME_CULLING_LAYER;
               default -> DEFAULT;
            };
         }
      }
   }

   public record Neighbour(boolean fullAndOpaque, @Nullable String customBlock, boolean samePermutation, @Nullable String cullingLayer) {
   }

   public record Rule(Direction direction, String bone, int cube, @Nullable String face, BlockCullingRules.Condition condition, boolean againstFullAndOpaque) {
      public boolean matches(CustomBlockModel.ModelQuad quad) {
         return !this.bone.isEmpty()
            && this.bone.equalsIgnoreCase(quad.bone())
            && (this.cube < 0 || this.cube == quad.cube())
            && (this.face == null || this.face.equalsIgnoreCase(quad.face()));
      }
   }
}
