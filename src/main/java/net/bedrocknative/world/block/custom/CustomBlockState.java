package net.bedrocknative.world.block.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.bedrocknative.world.block.BedrockBlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.jspecify.annotations.Nullable;

public final class CustomBlockState {
   public static final String FULL_BLOCK = "minecraft:geometry.full_block";
   public static final String CROSS = "minecraft:geometry.cross";
   public static final float DEFAULT_FRICTION = 0.4F;
   static final float DEFAULT_DESTROY_TIME = 0.0F;
   static final float UNBREAKABLE = -1.0F;
   private static final float MAX_COLLISION_HEIGHT = 24.0F;
   private static final float MAX_SELECTION_HEIGHT = 16.0F;
   private final BedrockBlockState state;
   private final CustomBlockState.Geometry geometry;
   private final CustomBlockState.Materials materials;
   private final BlockTransform transform;
   private final List<CustomBlockState.Box> collision;
   private final List<CustomBlockState.Box> selection;
   private final int lightEmission;
   private final int lightDampening;
   private final float destroyTime;
   private final float friction;
   @Nullable
   private final String soundType;
   private final boolean interactable;
   private final VoxelShape collisionShape;
   private final VoxelShape selectionShape;

   CustomBlockState(
      BedrockBlockState state,
      CustomBlockState.Geometry geometry,
      CustomBlockState.Materials materials,
      BlockTransform transform,
      List<CustomBlockState.Box> collision,
      List<CustomBlockState.Box> selection,
      int lightEmission,
      int lightDampening,
      float destroyTime,
      float friction,
      @Nullable String soundType,
      boolean interactable
   ) {
      this.state = state;
      this.geometry = geometry;
      this.materials = materials;
      this.transform = transform;
      this.collision = List.copyOf(collision);
      this.selection = List.copyOf(selection);
      this.lightEmission = lightEmission;
      this.lightDampening = lightDampening;
      this.destroyTime = destroyTime;
      this.friction = friction;
      this.soundType = soundType;
      this.interactable = interactable;
      this.collisionShape = shape(this.collision);
      this.selectionShape = shape(this.selection);
   }

   static CustomBlockState read(BedrockBlockState state, Map<String, Object> components) {
      CustomBlockState.Geometry geometry = geometry(components.get("minecraft:geometry"), components.containsKey("minecraft:unit_cube"), state.states());
      CustomBlockState.Materials materials = materials(components.get("minecraft:material_instances"));
      BlockTransform transform = BlockTransform.read(components.get("minecraft:transformation"));
      List<CustomBlockState.Box> collision = boxes(components.get("minecraft:collision_box"), 24.0F);
      List<CustomBlockState.Box> selection = boxes(components.get("minecraft:selection_box"), 16.0F);
      int emission = level(components.getOrDefault("minecraft:light_emission", components.get("minecraft:block_light_emission")), "emission", 0);
      int dampening = level(components.getOrDefault("minecraft:light_dampening", components.get("minecraft:block_light_absorption")), "lightLevel", 15);
      float destroyTime = destroyTime(components.get("minecraft:destructible_by_mining"));
      float friction = (float)value(components.get("minecraft:friction"), "value", 0.4F);
      return new CustomBlockState(
         state,
         geometry,
         materials,
         transform,
         collision,
         selection,
         emission,
         dampening,
         destroyTime,
         friction,
         soundType(components.get("minecraft:sound")),
         interactable(components)
      );
   }

   static boolean interactable(Map<String, Object> components) {
      return components.get("minecraft:custom_components") instanceof NbtMap custom && flag(custom.get("hasPlayerInteract"))
         || components.get("minecraft:on_interact") instanceof NbtMap;
   }

   @Nullable
   static String soundType(@Nullable Object component) {
      return (component instanceof NbtMap nbt ? nbt.getOrDefault("sound", nbt.get("value")) : component) instanceof String name && !name.isBlank()
         ? name.trim().toLowerCase(Locale.ROOT)
         : null;
   }

   private static CustomBlockState.Geometry geometry(@Nullable Object component, boolean unitCube, Map<String, ?> states) {
      if (component instanceof String identifier) {
         return new CustomBlockState.Geometry(identifier, Map.of(), false);
      } else if (component instanceof NbtMap nbt) {
         String identifier = nbt.getString("identifier", unitCube ? "minecraft:geometry.full_block" : "");
         Map<String, Boolean> visibility = new HashMap<>();
         if (nbt.get("bone_visibility") instanceof NbtMap bones) {
            bones.forEach((bone, value) -> visibility.put(bone.toLowerCase(Locale.ROOT), visible(value, states)));
         }

         boolean legacyTop = flag(nbt.get("legacyTopRotation")) || flag(nbt.get("needsLegacyTopRotation"));
         String culling = nbt.get("culling") instanceof String name ? name.trim().toLowerCase(Locale.ROOT) : "";
         String layer = nbt.get("culling_layer") instanceof String namex ? BlockCullingRules.layer(namex) : null;
         return new CustomBlockState.Geometry(
            identifier.isEmpty() ? "minecraft:geometry.full_block" : identifier, Map.copyOf(visibility), legacyTop, culling, layer
         );
      } else {
         return new CustomBlockState.Geometry("minecraft:geometry.full_block", Map.of(), false);
      }
   }

   private static boolean visible(Object value, Map<String, ?> states) {
      Objects.requireNonNull(value);

      return switch (value) {
         case String expressionx -> StateCondition.test(expressionx, states);
         case NbtMap nbt when nbt.get("expression") instanceof String expression -> StateCondition.test(expression, states);
         case NbtMap nbtx -> !nbtx.containsKey("value") || flag(nbtx.get("value"));
         default -> flag(value);
      };
   }

   private static CustomBlockState.Materials materials(@Nullable Object component) {
      if (component instanceof NbtMap nbt) {
         HashMap mappings = new HashMap();
         nbt.getCompound("mappings", NbtMap.EMPTY).forEach((alias, target) -> {
            if (target instanceof String name) {
               mappings.put(alias, name);
            }
         });
         HashMap materials = new HashMap();
         nbt.getCompound("materials", NbtMap.EMPTY).forEach((key, value) -> {
            if (value instanceof NbtMap material) {
               materials.put(key, BlockMaterial.read(material));
            } else if (value instanceof String alias) {
               mappings.put(key, alias);
            }
         });
         return new CustomBlockState.Materials(Map.copyOf(mappings), Map.copyOf(materials), true);
      } else {
         return CustomBlockState.Materials.NONE;
      }
   }

   static List<CustomBlockState.Box> boxes(@Nullable Object component, float maxHeight) {
      if (component == null) {
         return List.of(CustomBlockState.Box.FULL);
      } else if (component instanceof List<?> list) {
         List<CustomBlockState.Box> boxes = new ArrayList<>();

         for (Object item : list) {
            if (item instanceof NbtMap box) {
               addArrayBox(boxes, box, maxHeight);
            }
         }

         return boxes;
      } else if (!(component instanceof NbtMap nbt)) {
         return flag(component) ? List.of(CustomBlockState.Box.FULL) : List.of();
      } else if (nbt.containsKey("enabled") && !flag(nbt.get("enabled"))) {
         return List.of();
      } else {
         List<CustomBlockState.Box> boxes = new ArrayList<>();
         if (!nbt.containsKey("boxes")) {
            if (!nbt.containsKey("min") && !nbt.containsKey("max")) {
               float[] origin = vector(nbt.get("origin"), -8.0F, 0.0F, -8.0F);
               float[] size = vector(nbt.get("size"), 16.0F, 16.0F, 16.0F);
               add(
                  boxes,
                  new CustomBlockState.Box(
                     8.0F - (origin[0] + size[0]), origin[1], origin[2] + 8.0F, 8.0F - origin[0], origin[1] + size[1], origin[2] + 8.0F + size[2]
                  ),
                  maxHeight
               );
               return boxes;
            } else {
               addArrayBox(boxes, nbt, maxHeight);
               return boxes;
            }
         } else {
            for (NbtMap box : nbt.getList("boxes", NbtType.COMPOUND, List.of())) {
               if (!box.containsKey("min") && !box.containsKey("max")) {
                  float minX = (float)number(box.get("minX"), 0.0);
                  float maxX = (float)number(box.get("maxX"), 16.0);
                  add(
                     boxes,
                     new CustomBlockState.Box(
                        16.0F - maxX,
                        (float)number(box.get("minY"), 0.0),
                        (float)number(box.get("minZ"), 0.0),
                        16.0F - minX,
                        (float)number(box.get("maxY"), 16.0),
                        (float)number(box.get("maxZ"), 16.0)
                     ),
                     maxHeight
                  );
               } else {
                  addArrayBox(boxes, box, maxHeight);
               }
            }

            return boxes;
         }
      }
   }

   private static void addArrayBox(List<CustomBlockState.Box> boxes, NbtMap nbt, float maxHeight) {
      float[] min = vector(nbt.get("min"), 0.0F, 0.0F, 0.0F);
      float[] max = vector(nbt.get("max"), 16.0F, 16.0F, 16.0F);
      add(boxes, new CustomBlockState.Box(16.0F - max[0], min[1], min[2], 16.0F - min[0], max[1], max[2]), maxHeight);
   }

   private static void add(List<CustomBlockState.Box> boxes, CustomBlockState.Box box, float maxHeight) {
      CustomBlockState.Box clamped = new CustomBlockState.Box(
         clamp(Math.min(box.minX(), box.maxX()), 16.0F),
         clamp(Math.min(box.minY(), box.maxY()), maxHeight),
         clamp(Math.min(box.minZ(), box.maxZ()), 16.0F),
         clamp(Math.max(box.minX(), box.maxX()), 16.0F),
         clamp(Math.max(box.minY(), box.maxY()), maxHeight),
         clamp(Math.max(box.minZ(), box.maxZ()), 16.0F)
      );
      if (clamped.maxX() > clamped.minX() && clamped.maxY() > clamped.minY() && clamped.maxZ() > clamped.minZ()) {
         boxes.add(clamped);
      }
   }

   private static float clamp(float value, float max) {
      return Math.clamp(value, 0.0F, max);
   }

   private static VoxelShape shape(List<CustomBlockState.Box> boxes) {
      VoxelShape shape = Shapes.empty();

      for (CustomBlockState.Box box : boxes) {
         shape = box.equals(CustomBlockState.Box.FULL) ? Shapes.or(shape, Shapes.block()) : Shapes.or(shape, box.shape());
      }

      return shape.optimize();
   }

   private static float[] vector(@Nullable Object value, float x, float y, float z) {
      return value instanceof List<?> list && list.size() >= 3
         ? new float[]{(float)number(list.get(0), x), (float)number(list.get(1), y), (float)number(list.get(2), z)}
         : new float[]{x, y, z};
   }

   private static int level(@Nullable Object component, String key, int fallback) {
      return Math.clamp((long)((int)Math.round(value(component, key, fallback))), 0, 15);
   }

   static float destroyTime(@Nullable Object component) {
      Object value = component instanceof NbtMap nbt ? nbt.get("value") : component;
      if (!(value instanceof Boolean) && !(value instanceof Byte)) {
         return (float)number(value, 0.0);
      } else {
         return number(value, 1.0) == 0.0 ? -1.0F : 0.0F;
      }
   }

   private static double value(@Nullable Object component, String key, double fallback) {
      return component instanceof NbtMap nbt ? number(nbt.get(key), fallback) : number(component, fallback);
   }

   static double number(@Nullable Object value, double fallback) {
      return switch (value) {
         case Number number -> number.doubleValue();
         case Boolean bool -> bool ? 1.0 : 0.0;
         case null, default -> fallback;
      };
   }

   static int integer(@Nullable Object value, int fallback) {
      return (int)Math.round(number(value, fallback));
   }

   static boolean flag(@Nullable Object value) {
      return number(value, 0.0) != 0.0;
   }

   public BedrockBlockState state() {
      return this.state;
   }

   public CustomBlockState.Geometry geometry() {
      return this.geometry;
   }

   public CustomBlockState.Materials materials() {
      return this.materials;
   }

   public BlockTransform transform() {
      return this.transform;
   }

   public List<CustomBlockState.Box> collision() {
      return this.collision;
   }

   public List<CustomBlockState.Box> selection() {
      return this.selection;
   }

   public VoxelShape collisionShape() {
      return this.collisionShape;
   }

   public VoxelShape selectionShape() {
      return this.selectionShape;
   }

   public int lightEmission() {
      return this.lightEmission;
   }

   public int lightDampening() {
      return this.lightDampening;
   }

   public float destroyTime() {
      return this.destroyTime;
   }

   public float friction() {
      return this.friction;
   }

   public boolean interactable() {
      return this.interactable;
   }

   @Nullable
   public String soundType() {
      return this.soundType;
   }

   public boolean occludes() {
      if (this.geometry.fullBlock() && this.transform.keepsCell()) {
         for (Direction direction : Direction.values()) {
            if (!this.materials.face(direction).renderMethod().opaque()) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return this.state.toStateString();
   }

   public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      public static final CustomBlockState.Box FULL = new CustomBlockState.Box(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 16.0F);

      VoxelShape shape() {
         return Shapes.box(this.minX / 16.0, this.minY / 16.0, this.minZ / 16.0, this.maxX / 16.0, this.maxY / 16.0, this.maxZ / 16.0);
      }
   }

   public record Geometry(String identifier, Map<String, Boolean> boneVisibility, boolean legacyTopRotation, String culling, @Nullable String cullingLayer) {
      public Geometry(String identifier, Map<String, Boolean> boneVisibility, boolean legacyTopRotation) {
         this(identifier, boneVisibility, legacyTopRotation, "", null);
      }

      public boolean fullBlock() {
         return this.identifier.equals("minecraft:geometry.full_block")
            || this.identifier.equals("minecraft:geometry.full_block_v1")
            || this.identifier.isEmpty();
      }

      public boolean cross() {
         return this.identifier.equals("minecraft:geometry.cross");
      }

      public boolean builtIn() {
         return this.fullBlock() || this.cross();
      }

      public boolean visible(String bone) {
         return this.boneVisibility.getOrDefault(bone.toLowerCase(Locale.ROOT), true);
      }
   }

   public record Materials(Map<String, String> mappings, Map<String, BlockMaterial> materials, boolean defined) {
      static final CustomBlockState.Materials NONE = new CustomBlockState.Materials(Map.of(), Map.of(), false);

      public BlockMaterial resolve(String key) {
         String current = key;

         for (int hops = 0; hops < 16 && this.mappings.containsKey(current); hops++) {
            current = this.mappings.get(current);
         }

         BlockMaterial material = this.materials.get(current);
         if (material == null && !current.equals(key)) {
            material = this.materials.get(key);
         }

         return material != null ? material : this.materials.getOrDefault("*", BlockMaterial.DEFAULT);
      }

      public BlockMaterial face(Direction direction) {
         return this.resolve(direction.getSerializedName());
      }
   }
}
