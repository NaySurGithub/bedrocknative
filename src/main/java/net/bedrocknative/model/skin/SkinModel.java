package net.bedrocknative.model.skin;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.VanillaMobs;
import net.bedrocknative.model.geometry.GeometryDef;
import net.bedrocknative.model.geometry.GeometryLibrary;
import net.bedrocknative.model.geometry.GeometryMesher;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import net.bedrocknative.molang.VariableStore;
import org.jspecify.annotations.Nullable;

public final class SkinModel {
   public static final String PLAYER = "minecraft:player";
   public static final String TEXTURE_PREFIX = "bedrocknative:skin/";
   static final String CAPE_CONTROLLER = "controller.render.player.cape";
   static final String BLINK = "blink";
   public static final Set<String> PLAYER_VARIABLES = Set.of(
      "bob_animation",
      "is_horizontal_splitscreen",
      "is_vertical_splitscreen",
      "is_sneaking",
      "is_tooting_goat_horn",
      "is_using_brush",
      "is_using_vr",
      "player_arm_height",
      "short_arm_offset_left",
      "short_arm_offset_right",
      "use_blinking_animation",
      "animation_frames_face",
      "animation_frames_32x32",
      "animation_frames_128x128",
      "first_person_item_rotation_factor",
      "melee_spear_equipped",
      "fp_melee_spear_attack_item_position_x",
      "fp_melee_spear_attack_item_position_y",
      "fp_melee_spear_attack_item_position_z",
      "fp_melee_spear_attack_item_rotation_y",
      "fp_melee_spear_attack_item_rotation_z",
      "fp_melee_spear_use_item_position_x",
      "fp_melee_spear_use_item_position_y",
      "fp_melee_spear_use_item_position_z",
      "fp_melee_spear_use_item_rotation_y",
      "fp_melee_spear_use_item_rotation_z",
      "tp_melee_spear_attack_arm_rotation_x",
      "tp_melee_spear_attack_item_rotation_x",
      "tp_melee_spear_base_arm_rotation_x",
      "tp_melee_spear_use_arm_rotation_x",
      "tp_melee_spear_use_arm_rotation_y",
      "tp_melee_spear_use_arm_rotation_z",
      "tp_melee_spear_use_item_rotation_x"
   );
   static final float SLIM_ARM_OFFSET = 0.5F;
   static final int MAX_BONES = 256;
   static final int MAX_QUADS = 20000;
   private static final int USE_BLINKING_ANIMATION = MolangNames.id("use_blinking_animation");
   private static final int SHORT_ARM_OFFSET_LEFT = MolangNames.id("short_arm_offset_left");
   private static final int SHORT_ARM_OFFSET_RIGHT = MolangNames.id("short_arm_offset_right");
   private static final int[] FRAMES = new int[]{
      MolangNames.id(PlayerSkin.Animated.FACE.framesVariable()),
      MolangNames.id(PlayerSkin.Animated.BODY_32.framesVariable()),
      MolangNames.id(PlayerSkin.Animated.BODY_128.framesVariable())
   };
   private final PlayerSkin skin;
   private final PlayerSkin drawn;
   private final ClientEntityDef definition;
   private final Map<String, MeshedGeometry> meshes;
   private final Set<PlayerSkin.Animated> animated;
   private final boolean cape;
   private final boolean blinking;
   @Nullable
   private final String fallback;
   private final float[] frames = new float[PlayerSkin.Animated.values().length];
   private static final Set<String> CAPE_LOGGED = ConcurrentHashMap.newKeySet();

   private SkinModel(
      PlayerSkin skin,
      PlayerSkin drawn,
      ClientEntityDef definition,
      Map<String, MeshedGeometry> meshes,
      Set<PlayerSkin.Animated> animated,
      boolean cape,
      boolean blinking,
      @Nullable String fallback
   ) {
      this.skin = skin;
      this.drawn = drawn;
      this.definition = definition;
      this.meshes = meshes;
      this.animated = animated;
      this.cape = cape;
      this.blinking = blinking;
      this.fallback = fallback;

      for (PlayerSkin.Animated type : PlayerSkin.Animated.values()) {
         PlayerSkin.Animation animation = drawn.animations().get(type);
         this.frames[type.ordinal()] = animation != null ? animation.frames() : 1.0F;
      }
   }

   @Nullable
   public static SkinModel build(ModelRegistry registry, PlayerSkin skin) {
      ClientEntityDef base = registry.entity("minecraft:player");
      if (base == null) {
         return null;
      } else if (skin.isDefault()) {
         return assemble(registry, base, skin, skin, null, null);
      } else {
         GeometryLibrary library = null;
         String reason = null;
         if (!skin.geometryData().isBlank()) {
            JsonObject json = ModelJson.parse(skin.geometryData().getBytes(StandardCharsets.UTF_8), "skin geometry");
            if (json == null) {
               reason = "the skin's geometry data is not readable JSON ("
                  + skin.geometryData().length()
                  + " characters, starting "
                  + snippet(skin.geometryData())
                  + ")";
            } else {
               library = new GeometryLibrary(registry.geometryLibrary());

               try {
                  library.add(json, false);
               } catch (RuntimeException var9) {
                  reason = "the skin's geometry data is not readable: " + var9;
                  library = null;
               }
            }
         }

         if (reason == null) {
            String name = skin.geometryNames().get("default");
            GeometryDef geometry = resolve(registry, library, name);
            if (geometry == null) {
               reason = "no geometry " + name + " in the skin or the pack stack";
            } else {
               try {
                  SkinModel model = assemble(registry, base, skin, skin, library, null);
                  reason = model.tooComplex();
                  if (reason == null) {
                     return model;
                  }
               } catch (RuntimeException var8) {
                  reason = "the skin's geometry can't be built: " + var8;
               }
            }
         }

         PlayerSkin fallback = PlayerSkin.defaultSkin(skin.slim(), reason);
         return assemble(registry, base, skin, fallback, null, reason);
      }
   }

   static String snippet(String text) {
      StringBuilder out = new StringBuilder("\"");

      for (int i = 0; i < text.length() && out.length() < 25; i++) {
         char c = text.charAt(i);
         out.append(c >= ' ' && c < 127 && c != '"' ? c : '?');
      }

      return out.append('"').toString();
   }

   @Nullable
   private String tooComplex() {
      for (MeshedGeometry mesh : this.meshes.values()) {
         if (mesh.boneCount() > 256) {
            return "the skin's geometry has " + mesh.boneCount() + " bones (at most 256 are drawn)";
         }

         if (mesh.quadCount() > 20000) {
            return "the skin's geometry has " + mesh.quadCount() + " faces (at most 20000 are drawn)";
         }
      }

      return null;
   }

   private static void logCapeBones(PlayerSkin drawn, Map<String, MeshedGeometry> meshes) {
      if (CAPE_LOGGED.add(drawn.key())) {
         for (MeshedGeometry mesh : meshes.values()) {
            for (int i = 0; i < mesh.boneCount(); i++) {
               MeshedGeometry.Bone bone = mesh.bone(i);
               boolean capeBone = bone.name().toLowerCase(Locale.ROOT).contains("cape");
               StringBuilder locators = new StringBuilder();

               for (MeshedGeometry.Locator locator : bone.locators()) {
                  if (locator.name().toLowerCase(Locale.ROOT).contains("cape")) {
                     locators.append(locator.name()).append('=').append(Arrays.toString(locator.offset())).append(' ');
                  }
               }

               if (capeBone || locators.length() > 0) {
                  BedrockNative.LOGGER
                     .info(
                        "Cape probe {} geometry {}: bone {} parent {} pivot {} quads {} locators {}",
                        new Object[]{drawn.key(), mesh.identifier(), bone.name(), bone.parent(), Arrays.toString(bone.pivot()), bone.quadCount(), locators}
                     );
               }
            }
         }
      }
   }

   @Nullable
   private static GeometryDef resolve(ModelRegistry registry, @Nullable GeometryLibrary library, @Nullable String name) {
      if (name == null) {
         return null;
      } else {
         return library != null ? library.get(name) : registry.geometry(name);
      }
   }

   private static SkinModel assemble(
      ModelRegistry registry, ClientEntityDef base, PlayerSkin skin, PlayerSkin drawn, @Nullable GeometryLibrary library, @Nullable String fallback
   ) {
      Map<String, String> geometries = new LinkedHashMap<>(base.geometries());
      Map<String, String> textures = new LinkedHashMap<>(base.textures());
      Map<String, MeshedGeometry> meshes = new HashMap<>();
      List<ClientEntityDef.RenderControllerRef> controllers = new ArrayList<>(base.renderControllers());
      List<ClientEntityDef.AnimateEntry> animate = new ArrayList<>(base.scripts().animate());
      String prefix = "bedrocknative:skin/" + drawn.key() + "/";
      geometries.put("default", geometry(library, drawn, drawn.geometryNames().get("default"), meshes));
      textures.put("default", drawn.isDefault() ? drawn.defaultTexture() : prefix + "skin");
      boolean cape = drawn.hasCape() && registry.renderController("controller.render.player.cape") != null && base.geometry("cape") != null;
      if (cape) {
         textures.put("cape", prefix + "cape");
         controllers.add(new ClientEntityDef.RenderControllerRef("controller.render.player.cape", null));
         logCapeBones(drawn, meshes);
      }

      Set<PlayerSkin.Animated> animated = EnumSet.noneOf(PlayerSkin.Animated.class);
      boolean blinking = false;

      for (PlayerSkin.Animation animation : drawn.animations().values()) {
         PlayerSkin.Animated type = animation.type();
         String name = drawn.geometryNames().get(type.geometry());
         String third = "controller.render.persona_" + suffix(type) + ".third_person";
         String first = "controller.render.persona_" + suffix(type) + ".first_person";
         if (name != null && resolve(registry, library, name) != null && registry.renderController(third) != null) {
            animated.add(type);
            geometries.put(type.geometry(), geometry(library, drawn, name, meshes));
            textures.put(type.geometry(), prefix + type.part());
            controllers.add(new ClientEntityDef.RenderControllerRef(third, condition(registry, "!variable.is_first_person && !variable.map_face_icon")));
            if (registry.renderController(first) != null) {
               controllers.add(new ClientEntityDef.RenderControllerRef(first, condition(registry, "variable.is_first_person && !query.is_spectator")));
            }

            if (type == PlayerSkin.Animated.FACE && animation.blinking() && base.animation("blink") != null) {
               blinking = true;
               animate.add(new ClientEntityDef.AnimateEntry("blink", null));
            }
         }
      }

      ClientEntityDef.Scripts scripts = base.scripts();
      ClientEntityDef.Scripts skinScripts = new ClientEntityDef.Scripts(
         scripts.initialize(),
         scripts.preAnimation(),
         List.copyOf(animate),
         scripts.scale(),
         scripts.scaleX(),
         scripts.scaleY(),
         scripts.scaleZ(),
         scripts.publicVariables(),
         scripts.parentSetup(),
         scripts.shouldUpdateBonesAndEffectsOffscreen(),
         scripts.shouldUpdateEffectsOffscreen(),
         scripts.hideHeldItems()
      );
      ClientEntityDef definition = new ClientEntityDef(
         base.identifier(),
         false,
         base.minEngineVersion(),
         base.formatVersion(),
         base.materials(),
         Collections.unmodifiableMap(textures),
         Collections.unmodifiableMap(geometries),
         base.animations(),
         base.legacyControllers(),
         skinScripts,
         List.copyOf(controllers),
         base.enableAttachables(),
         base.hideArmor(),
         base.queryableGeometry(),
         null,
         base.pack(),
         base.serverPack(),
         base.particleEffects(),
         base.soundEffects()
      );
      return new SkinModel(skin, drawn, definition, Map.copyOf(meshes), Collections.unmodifiableSet(animated), cape, blinking, fallback);
   }

   private static String geometry(@Nullable GeometryLibrary library, PlayerSkin skin, @Nullable String name, Map<String, MeshedGeometry> meshes) {
      String identifier = name != null ? name : (skin.slim() ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
      if (library != null && library.owns(identifier)) {
         String own = "skin:" + skin.key() + "/" + identifier;
         if (!meshes.containsKey(own)) {
            GeometryDef geometry = library.get(identifier);
            if (geometry == null) {
               return identifier;
            }

            meshes.put(own, GeometryMesher.mesh(geometry));
         }

         return own;
      } else {
         return identifier;
      }
   }

   private static String suffix(PlayerSkin.Animated type) {
      return switch (type) {
         case FACE -> "animated_face";
         case BODY_32 -> "animated_32x32";
         case BODY_128 -> "animated_128x128";
      };
   }

   private static MolangProgram condition(ModelRegistry registry, String source) {
      return registry.molang().compile(source, MolangVersion.LATEST);
   }

   public static VanillaMobs.Support support(ModelRegistry registry) {
      ClientEntityDef base = registry.entity("minecraft:player");
      if (base == null) {
         return new VanillaMobs.Support("minecraft:player", false, List.of("no client entity in the pack stack"), List.of());
      } else {
         List<ClientEntityDef.RenderControllerRef> controllers = new ArrayList<>(base.renderControllers());
         List<ClientEntityDef.AnimateEntry> animate = new ArrayList<>(base.scripts().animate());

         for (String controller : List.of(
            "controller.render.player.cape", "controller.render.persona_animated_face.third_person", "controller.render.persona_animated_face.first_person"
         )) {
            if (registry.renderController(controller) != null) {
               controllers.add(new ClientEntityDef.RenderControllerRef(controller, null));
            }
         }

         if (base.animation("blink") != null) {
            animate.add(new ClientEntityDef.AnimateEntry("blink", null));
         }

         ClientEntityDef.Scripts scripts = base.scripts();
         ClientEntityDef full = new ClientEntityDef(
            base.identifier(),
            false,
            base.minEngineVersion(),
            base.formatVersion(),
            base.materials(),
            base.textures(),
            base.geometries(),
            base.animations(),
            base.legacyControllers(),
            new ClientEntityDef.Scripts(
               scripts.initialize(),
               scripts.preAnimation(),
               List.copyOf(animate),
               scripts.scale(),
               scripts.scaleX(),
               scripts.scaleY(),
               scripts.scaleZ(),
               scripts.publicVariables(),
               scripts.parentSetup(),
               scripts.shouldUpdateBonesAndEffectsOffscreen(),
               scripts.shouldUpdateEffectsOffscreen(),
               scripts.hideHeldItems()
            ),
            List.copyOf(controllers),
            base.enableAttachables(),
            base.hideArmor(),
            base.queryableGeometry(),
            null,
            base.pack(),
            base.serverPack(),
            base.particleEffects(),
            base.soundEffects()
         );
         return VanillaMobs.decide(registry, full, PLAYER_VARIABLES);
      }
   }

   public void setVariables(VariableStore variables) {
      variables.set(USE_BLINKING_ANIMATION, this.blinking ? 1.0F : 0.0F, null);

      for (int i = 0; i < this.frames.length; i++) {
         variables.set(FRAMES[i], this.frames[i], null);
      }

      float offset = this.drawn.slim() ? 0.5F : 0.0F;
      variables.set(SHORT_ARM_OFFSET_LEFT, offset, null);
      variables.set(SHORT_ARM_OFFSET_RIGHT, offset, null);
   }

   @Nullable
   public MeshedGeometry mesh(String identifier) {
      return this.meshes.get(identifier);
   }

   public PlayerSkin skin() {
      return this.skin;
   }

   public PlayerSkin drawn() {
      return this.drawn;
   }

   public ClientEntityDef definition() {
      return this.definition;
   }

   public String defaultGeometry() {
      return this.definition.geometry("default");
   }

   public Set<PlayerSkin.Animated> animated() {
      return this.animated;
   }

   public boolean cape() {
      return this.cape;
   }

   public boolean blinking() {
      return this.blinking;
   }

   @Nullable
   public String fallback() {
      return this.fallback != null ? this.fallback : this.skin.fallback();
   }

   public static String texture(PlayerSkin skin, String part) {
      return "bedrocknative:skin/" + skin.key() + "/" + part;
   }
}
