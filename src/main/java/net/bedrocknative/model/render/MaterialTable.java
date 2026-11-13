package net.bedrocknative.model.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.ModelJson;
import org.jspecify.annotations.Nullable;

public final class MaterialTable {
   public static final String FALLBACK = "entity_alphatest";
   private static final String CORE = "{\"materials\": {\n  \"entity_static\": {},\n  \"entity:entity_static\": {\"+defines\": [\"USE_OVERLAY\"]},\n  \"entity_nocull:entity\": {\"+states\": [\"DisableCulling\"]},\n  \"entity_change_color:entity_nocull\": {\"+defines\": [\"USE_OVERLAY\", \"USE_COLOR_MASK\"]},\n  \"entity_alphatest:entity_nocull\": {\"+defines\": [\"ALPHA_TEST\"]},\n  \"entity_alphatest_one_sided:entity\": {\"+defines\": [\"ALPHA_TEST\"]},\n  \"entity_alphablend:entity\": {\"+states\": [\"Blending\"]},\n  \"entity_emissive:entity\": {\"+defines\": [\"USE_EMISSIVE\"]},\n  \"entity_emissive_alpha:entity_nocull\": {\"+defines\": [\"ALPHA_TEST\", \"USE_EMISSIVE\"]},\n  \"entity_glint:entity\": {\"+defines\": [\"GLINT\"]},\n  \"entity_alphatest_glint:entity_alphatest\": {\"+defines\": [\"GLINT\"]},\n  \"entity_alphatest_change_color:entity_change_color\": {\"+defines\": [\"ALPHA_TEST\"]},\n  \"entity_multitexture:entity\": {\"+defines\": [\"USE_MULTITEXTURE\"]},\n  \"armor:entity_alphatest\": {},\n  \"armor_enchanted:entity_alphatest_glint\": {},\n  \"armor_leather:entity_alphatest_change_color\": {},\n  \"entity_alphablend_nocolor:entity_alphablend\": {},\n  \"player_animated:entity_alphatest\": {\"+defines\": [\"USE_UV_ANIM\"]},\n  \"player_spectator:entity_alphablend_nocolor\": {\"+defines\": [\"USE_COLOR_MASK\", \"USE_UV_ANIM\"]},\n  \"mooshroom:entity\": {},\n  \"skeleton:entity_alphatest\": {},\n  \"particles_base\": {},\n  \"particles_alpha:particles_base\": {\"+defines\": [\"ALPHA_TEST\", \"ENABLE_FOG\"], \"+states\": [\"DisableAlphaWrite\"]},\n  \"particles_blend:particles_base\": {\"+defines\": [\"ENABLE_FOG\"], \"+states\": [\"Blending\", \"DisableCulling\", \"DisableDepthWrite\"]},\n  \"particles_add:particles_blend\": {\"blendSrc\": \"SourceAlpha\", \"blendDst\": \"One\"}\n}}";
   private final Map<String, MaterialTable.Raw> raws = new HashMap<>();
   private final Map<String, MaterialTable.MaterialInfo> resolved = new ConcurrentHashMap<>();

   public MaterialTable() {
      JsonObject core = ModelJson.parse(
         "{\"materials\": {\n  \"entity_static\": {},\n  \"entity:entity_static\": {\"+defines\": [\"USE_OVERLAY\"]},\n  \"entity_nocull:entity\": {\"+states\": [\"DisableCulling\"]},\n  \"entity_change_color:entity_nocull\": {\"+defines\": [\"USE_OVERLAY\", \"USE_COLOR_MASK\"]},\n  \"entity_alphatest:entity_nocull\": {\"+defines\": [\"ALPHA_TEST\"]},\n  \"entity_alphatest_one_sided:entity\": {\"+defines\": [\"ALPHA_TEST\"]},\n  \"entity_alphablend:entity\": {\"+states\": [\"Blending\"]},\n  \"entity_emissive:entity\": {\"+defines\": [\"USE_EMISSIVE\"]},\n  \"entity_emissive_alpha:entity_nocull\": {\"+defines\": [\"ALPHA_TEST\", \"USE_EMISSIVE\"]},\n  \"entity_glint:entity\": {\"+defines\": [\"GLINT\"]},\n  \"entity_alphatest_glint:entity_alphatest\": {\"+defines\": [\"GLINT\"]},\n  \"entity_alphatest_change_color:entity_change_color\": {\"+defines\": [\"ALPHA_TEST\"]},\n  \"entity_multitexture:entity\": {\"+defines\": [\"USE_MULTITEXTURE\"]},\n  \"armor:entity_alphatest\": {},\n  \"armor_enchanted:entity_alphatest_glint\": {},\n  \"armor_leather:entity_alphatest_change_color\": {},\n  \"entity_alphablend_nocolor:entity_alphablend\": {},\n  \"player_animated:entity_alphatest\": {\"+defines\": [\"USE_UV_ANIM\"]},\n  \"player_spectator:entity_alphablend_nocolor\": {\"+defines\": [\"USE_COLOR_MASK\", \"USE_UV_ANIM\"]},\n  \"mooshroom:entity\": {},\n  \"skeleton:entity_alphatest\": {},\n  \"particles_base\": {},\n  \"particles_alpha:particles_base\": {\"+defines\": [\"ALPHA_TEST\", \"ENABLE_FOG\"], \"+states\": [\"DisableAlphaWrite\"]},\n  \"particles_blend:particles_base\": {\"+defines\": [\"ENABLE_FOG\"], \"+states\": [\"Blending\", \"DisableCulling\", \"DisableDepthWrite\"]},\n  \"particles_add:particles_blend\": {\"blendSrc\": \"SourceAlpha\", \"blendDst\": \"One\"}\n}}"
            .getBytes(StandardCharsets.UTF_8),
         "core materials"
      );
      if (core != null) {
         this.add(core);
      }
   }

   public synchronized void add(JsonObject file) {
      JsonObject materials = ModelJson.object(file, "materials");
      if (materials != null) {
         for (Entry<String, JsonElement> entry : materials.entrySet()) {
            if (entry.getValue() instanceof JsonObject json) {
               String key = entry.getKey();
               int colon = key.indexOf(58);
               String name = (colon >= 0 ? key.substring(0, colon) : key).trim().toLowerCase(Locale.ROOT);
               String parent = colon >= 0 ? key.substring(colon + 1).trim().toLowerCase(Locale.ROOT) : null;
               List<String> addDefines = new ArrayList<>(strings(json.get("+defines")));
               addDefines.addAll(strings(json.get("defines")));
               List<String> addStates = new ArrayList<>(strings(json.get("+states")));
               addStates.addAll(strings(json.get("states")));
               this.raws
                  .put(
                     name,
                     new MaterialTable.Raw(
                        parent,
                        addDefines,
                        strings(json.get("-defines")),
                        addStates,
                        strings(json.get("-states")),
                        ModelJson.string(json, "depthFunc"),
                        ModelJson.string(json, "blendSrc"),
                        ModelJson.string(json, "blendDst")
                     )
                  );
            }
         }

         this.resolved.clear();
      }
   }

   public MaterialTable.MaterialInfo get(String name) {
      String key = name.toLowerCase(Locale.ROOT);
      MaterialTable.MaterialInfo info = this.resolved.get(key);
      if (info != null) {
         return info;
      }

      synchronized (this) {
         info = this.resolve(key, new HashSet<>());
         int dot = key.indexOf(46);
         if (info == null && dot > 0) {
            MaterialTable.MaterialInfo base = this.resolve(key.substring(0, dot), new HashSet<>());
            info = base != null
               ? new MaterialTable.MaterialInfo(key, base.defines(), base.states(), base.depthFunc(), true, base.blendSrc(), base.blendDst())
               : null;
         }

         if (info == null) {
            BedrockNative.LOGGER.debug("Unknown Bedrock material {}, drawn as {}", name, "entity_alphatest");
            MaterialTable.MaterialInfo fallback = this.resolve("entity_alphatest", new HashSet<>());
            info = fallback != null
               ? new MaterialTable.MaterialInfo(key, fallback.defines(), fallback.states(), fallback.depthFunc(), false)
               : new MaterialTable.MaterialInfo(key, Set.of("ALPHA_TEST"), Set.of("DisableCulling"), null, false);
         }

         this.resolved.put(key, info);
         return info;
      }
   }

   private MaterialTable.@Nullable MaterialInfo resolve(String name, Set<String> visiting) {
      MaterialTable.Raw raw = this.raws.get(name);
      if (raw != null && visiting.add(name)) {
         Set<String> defines = new LinkedHashSet<>();
         Set<String> states = new LinkedHashSet<>();
         String depthFunc = null;
         String blendSrc = null;
         String blendDst = null;
         if (raw.parent() != null) {
            MaterialTable.MaterialInfo parent = this.resolve(raw.parent(), visiting);
            if (parent != null) {
               defines.addAll(parent.defines());
               states.addAll(parent.states());
               depthFunc = parent.depthFunc();
               blendSrc = parent.blendSrc();
               blendDst = parent.blendDst();
            }
         }

         defines.addAll(raw.addDefines());
         raw.removeDefines().forEach(defines::remove);
         states.addAll(raw.addStates());
         raw.removeStates().forEach(states::remove);
         return new MaterialTable.MaterialInfo(
            name,
            Set.copyOf(defines),
            Set.copyOf(states),
            raw.depthFunc() != null ? raw.depthFunc() : depthFunc,
            true,
            raw.blendSrc() != null ? raw.blendSrc() : blendSrc,
            raw.blendDst() != null ? raw.blendDst() : blendDst
         );
      } else {
         return null;
      }
   }

   private static List<String> strings(@Nullable JsonElement json) {
      List<String> strings = new ArrayList<>();
      if (json != null && json.isJsonArray()) {
         json.getAsJsonArray().forEach(element -> {
            if (element.isJsonPrimitive()) {
               strings.add(element.getAsString());
            }
         });
      }

      return strings;
   }

   public record MaterialInfo(
      String name, Set<String> defines, Set<String> states, @Nullable String depthFunc, boolean known, @Nullable String blendSrc, @Nullable String blendDst
   ) {
      public MaterialInfo(String name, Set<String> defines, Set<String> states, @Nullable String depthFunc, boolean known) {
         this(name, defines, states, depthFunc, known, null, null);
      }

      public boolean additive() {
         return this.blending() && "One".equalsIgnoreCase(this.blendDst);
      }

      public boolean depthOnly() {
         return this.states.contains("DisableColorWrite");
      }

      public boolean throughWalls() {
         return this.states.contains("DisableDepthTest") || "Always".equalsIgnoreCase(this.depthFunc);
      }

      public boolean depthEqual() {
         return "Equal".equalsIgnoreCase(this.depthFunc);
      }

      public boolean alphaTest() {
         return this.defines.contains("ALPHA_TEST");
      }

      public boolean blending() {
         return this.states.contains("Blending");
      }

      public boolean disableCulling() {
         return this.states.contains("DisableCulling");
      }

      public boolean overlay() {
         return this.defines.contains("USE_OVERLAY");
      }

      public boolean colorMask() {
         return this.defines.contains("USE_COLOR_MASK");
      }

      public boolean glint() {
         return this.defines.contains("GLINT");
      }

      public boolean emissive() {
         return this.defines.contains("USE_EMISSIVE");
      }

      public boolean onlyEmissive() {
         return this.defines.contains("USE_ONLY_EMISSIVE");
      }

      public boolean uvAnimation() {
         return this.defines.contains("USE_UV_ANIM");
      }

      public boolean multitexture() {
         return this.defines.contains("USE_MULTITEXTURE") || this.defines.contains("MASKED_MULTITEXTURE");
      }
   }

   private record Raw(
      @Nullable String parent,
      List<String> addDefines,
      List<String> removeDefines,
      List<String> addStates,
      List<String> removeStates,
      @Nullable String depthFunc,
      @Nullable String blendSrc,
      @Nullable String blendDst
   ) {
   }
}
