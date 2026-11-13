package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.render.MaterialTable;
import net.bedrocknative.model.render.RenderKind;
import net.bedrocknative.model.runtime.DrawList;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.bedrocknative.model.runtime.WearerBones;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class ActorSnapshot {
   private static final String RIGHT_ITEM = "rightItem";
   private static final String LEFT_ITEM = "leftItem";
   private static final String PERSONA_ANIMATED = "controller.render.persona_animated";
   private static final float DECAL_SCALE = 1.002F;
   private final DrawSnapshot draw = new DrawSnapshot();
   private String identifier = "";
   private float actorScale = 1.0F;
   private boolean compose;
   private int generation = -1;
   private int blendVersion = -1;
   private RenderType[][] types = new RenderType[0][];
   private RenderType[][] glowTypes = new RenderType[0][];
   private RenderType[][] outlineTypes = new RenderType[0][];
   private Identifier[][] textures = new Identifier[0][];
   private RenderKind[][] kinds = new RenderKind[0][];
   private MaterialTable.MaterialInfo[][] materials = new MaterialTable.MaterialInfo[0][];
   private String[][][] layers = new String[0][][];
   private int[][] tints = new int[0][];
   private int[][] secondTints = new int[0][];
   private String[][] masks = new String[0][];
   private boolean[][] dissolves = new boolean[0][];
   private boolean[][] composed = new boolean[0][];
   private float bodyYaw;
   private float deathProgress;
   private float glide;
   private MeshDraw[][] draws = new MeshDraw[0][];
   private MeshDraw[][] glowDraws = new MeshDraw[0][];
   private MeshDraw[][] outlineDraws = new MeshDraw[0][];
   private final Matrix4f bound = new Matrix4f();
   private MeshDraw[][] boundDraws = new MeshDraw[0][];
   private MeshDraw[][] boundOutlineDraws = new MeshDraw[0][];
   private MeshDraw[][] handDraws = new MeshDraw[0][];
   private final Matrix4f[] hands = new Matrix4f[]{new Matrix4f(), new Matrix4f()};
   private final float[][] handPivots = new float[][]{new float[3], new float[3]};
   private final boolean[] hasHand = new boolean[2];
   private final boolean[] handAttached = new boolean[2];
   @Nullable
   private MeshedGeometry handMesh;
   private int rightItem = -1;
   private int leftItem = -1;

   void identifier(String identifier) {
      this.identifier = identifier;
   }

   String identifier() {
      return this.identifier;
   }

   void fill(DrawList list, float actorScale) {
      this.fill(list, actorScale, false);
   }

   void fill(DrawList list, float actorScale, boolean compose) {
      this.draw.setPolish(compose);
      this.draw.copy(list);
      this.actorScale = actorScale;
      this.compose = compose;
      int calls = this.draw.calls();
      this.growCalls(calls);
      if (compose && this.generation != ComposedTextures.generation() || this.blendVersion != BedrockTextures.blendVersion()) {
         this.generation = ComposedTextures.generation();
         this.blendVersion = BedrockTextures.blendVersion();

         for (RenderType[] callTypes : this.types) {
            Arrays.fill(callTypes, null);
         }
      }

      for (int c = 0; c < calls; c++) {
         DrawSnapshot.Call call = this.draw.call(c);
         int groups = call.groups();
         this.growGroups(c, groups);
         String mask = compose ? this.dissolveMask(c) : null;

         for (int g = 0; g < groups; g++) {
            this.refresh(call, c, g, mask);
         }
      }
   }

   @Nullable
   private String dissolveMask(int c) {
      if (c == 0) {
         return null;
      } else {
         DrawSnapshot.Call previous = this.draw.call(c - 1);

         for (int g = 0; g < previous.groups(); g++) {
            if (previous.kind(g) == RenderKind.DEPTH_ONLY) {
               return previous.texture();
            }
         }

         return null;
      }
   }

   private void refresh(DrawSnapshot.Call call, int c, int g, @Nullable String mask) {
      MaterialTable.MaterialInfo material = call.material(g);
      String[] memo = this.layers[c][g];
      boolean same = this.types[c][g] != null
         && this.materials[c][g] == material
         && this.composed[c][g] == this.compose
         && this.kinds[c][g] == call.kind(g)
         && this.masks[c][g] == mask;

      for (int i = 0; same && i < 3; i++) {
         same = memo[i] == call.layer(i);
      }

      if (same && this.compose) {
         same = this.tints[c][g] == call.tint() && this.secondTints[c][g] == call.tint2();
      }

      if (!same) {
         this.materials[c][g] = material;
         this.composed[c][g] = this.compose;
         this.kinds[c][g] = call.kind(g);
         this.tints[c][g] = call.tint();
         this.secondTints[c][g] = call.tint2();
         this.masks[c][g] = mask;

         for (int i = 0; i < 3; i++) {
            memo[i] = call.layer(i);
         }

         this.outlineTypes[c][g] = null;
         this.glowTypes[c][g] = null;
         ComposedTextures.Entry entry = this.compose
            ? ComposedTextures.get(material, call.layer(0), call.layer(1), call.layer(2), call.tint(), call.tint2())
            : null;
         Identifier texture = entry != null ? entry.base() : MaterialTypes.texture(call.texture());
         this.textures[c][g] = texture;
         this.dissolves[c][g] = mask != null && material.depthEqual();
         RenderKind kind = MaterialTypes.unblended(MaterialTypes.probed(call.kind(g), call.texture()), texture, !this.compose || !material.colorMask());
         this.types[c][g] = this.dissolves[c][g] ? MaterialTypes.dissolve(texture, MaterialTypes.texture(mask)) : MaterialTypes.renderType(kind, texture);
         if (entry != null && entry.emissive() != null) {
            this.glowTypes[c][g] = MaterialTypes.glow(entry.emissive());
         }
      }
   }

   void hands(@Nullable Skeleton skeleton, @Nullable boolean[] attached) {
      this.hasHand[0] = false;
      this.hasHand[1] = false;
      if (skeleton != null) {
         MeshedGeometry mesh = skeleton.geometry();
         if (mesh != this.handMesh) {
            this.handMesh = mesh;
            this.rightItem = mesh.bone("rightItem");
            this.leftItem = mesh.bone("leftItem");
         }

         this.hand(0, skeleton, this.rightItem, attached);
         this.hand(1, skeleton, this.leftItem, attached);
      }
   }

   private void hand(int hand, Skeleton skeleton, int bone, @Nullable boolean[] attached) {
      if (bone >= 0) {
         this.hasHand[hand] = true;
         this.handAttached[hand] = attached != null && attached[hand];
         this.hands[hand].set(skeleton.world(bone));
         float[] pivot = skeleton.geometry().bone(bone).pivot();
         this.handPivots[hand][0] = pivot[0];
         this.handPivots[hand][1] = pivot[1];
         this.handPivots[hand][2] = pivot[2];
      }
   }

   boolean javaHand(int hand) {
      return this.hasHand[hand] && !this.handAttached[hand];
   }

   Matrix4f handMatrix(int hand) {
      return this.hands[hand];
   }

   float[] handPivot(int hand) {
      return this.handPivots[hand];
   }

   void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int hurtOverlay, int outlineColor) {
      this.submit(poseStack, collector, light, hurtOverlay, outlineColor, true, 0.0F, false);
   }

   void submit(
      PoseStack poseStack, SubmitNodeCollector collector, int light, int hurtOverlay, int outlineColor, boolean bodyVisible, float whiteOverlay, boolean red
   ) {
      int own = this.draw.ownCalls();

      for (int c = 0; c < this.draw.calls(); c++) {
         OrderedSubmitNodeCollector ordered = collector.order(c);
         DrawSnapshot.Call call = this.draw.call(c);
         int callLight = call.ignoreLighting() ? 15728880 : light;
         float white = this.compose ? Math.max(whiteOverlay, call.whiteOverlay()) : whiteOverlay;
         int callOverlay = white > 0.0F ? OverlayTexture.pack(OverlayTexture.u(white), OverlayTexture.v(red)) : hurtOverlay;
         boolean visible = bodyVisible || c >= own;
         boolean decal = decal(call);
         if (decal) {
            poseStack.pushPose();
            poseStack.scale(1.002F, 1.002F, 1.002F);
         }

         for (int g = 0; g < call.groups(); g++) {
            RenderKind kind = call.kind(g);
            if (visible && kind != RenderKind.DEPTH_ONLY) {
               int color = this.vertexColor(call, kind, call.material(g), this.dissolves[c][g]);
               int overlay = call.hurtFlash() && RenderKind.overlay(call.material(g)) ? callOverlay : OverlayTexture.NO_OVERLAY;
               if (color != 0) {
                  ordered.submitCustomGeometry(poseStack, this.types[c][g], this.draws[c][g].set(this.draw, c, g, color, overlay, callLight));
               }

               RenderType glow = this.glowTypes[c][g];
               if (glow != null && color != 0) {
                  ordered.submitCustomGeometry(poseStack, glow, this.glowDraws[c][g].set(this.draw, c, g, color, OverlayTexture.NO_OVERLAY, 15728880));
               }
            }

            if (outlineColor != 0 && kind != RenderKind.DEPTH_ONLY) {
               RenderType outline = this.outlineTypes[c][g];
               if (outline == null) {
                  outline = MaterialTypes.outline(this.textures[c][g]);
                  this.outlineTypes[c][g] = outline;
               }

               ordered.submitCustomGeometry(poseStack, outline, this.outlineDraws[c][g].set(this.draw, c, g, outlineColor, OverlayTexture.NO_OVERLAY, 15728880));
            }
         }

         if (decal) {
            poseStack.popPose();
         }
      }
   }

   private int vertexColor(DrawSnapshot.Call call, RenderKind kind, MaterialTable.MaterialInfo material, boolean dissolve) {
      if (!this.compose) {
         return -1;
      } else {
         float alpha = 1.0F;
         if (dissolve) {
            alpha = 1.0F - this.deathProgress;
         } else if (kind.blended() && material.colorMask()) {
            alpha = call.colorAlpha();
            if (alpha <= 0.0F) {
               return 0;
            }
         }

         int tint = material.overlay() ? call.overlayTint() : 16777215;
         return Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | tint;
      }
   }

   void bodyYaw(float yaw) {
      this.bodyYaw = yaw;
   }

   public float bodyYaw() {
      return this.bodyYaw;
   }

   void glide(float scale) {
      this.glide = scale;
   }

   public float glide() {
      return this.glide;
   }

   void deathProgress(float progress) {
      this.deathProgress = progress;
   }

   private static boolean decal(DrawSnapshot.Call call) {
      return call.controller().startsWith("controller.render.persona_animated");
   }

   void submitBound(PoseStack poseStack, SubmitNodeCollector collector, int light, int hurtOverlay, int outlineColor, WearerBones bones) {
      for (int c = 0; c < this.draw.calls(); c++) {
         DrawSnapshot.Call call = this.draw.call(c);
         int callLight = call.ignoreLighting() ? 15728880 : light;
         int groups = call.groups();
         this.growBound(c, groups == 0 ? 0 : call.rootStart(groups));

         for (int g = 0; g < groups; g++) {
            int overlay = call.hurtFlash() && RenderKind.overlay(call.material(g)) ? hurtOverlay : OverlayTexture.NO_OVERLAY;

            for (int r = call.rootStart(g); r < call.rootStart(g + 1); r++) {
               int root = call.root(r);
               String binding = call.binding(root);
               Matrix4f wearer = binding != null ? bones.bind(binding, call.mesh().bone(root), this.bound) : null;
               poseStack.pushPose();
               if (wearer != null) {
                  poseStack.mulPose(wearer);
               }

               collector.submitCustomGeometry(poseStack, this.types[c][g], this.boundDraws[c][r].set(this.draw, c, g, root, -1, overlay, callLight));
               if (outlineColor != 0) {
                  RenderType outline = this.outlineTypes[c][g];
                  if (outline == null) {
                     outline = MaterialTypes.outline(this.textures[c][g]);
                     this.outlineTypes[c][g] = outline;
                  }

                  collector.submitCustomGeometry(
                     poseStack, outline, this.boundOutlineDraws[c][r].set(this.draw, c, g, root, outlineColor, OverlayTexture.NO_OVERLAY, 15728880)
                  );
               }

               poseStack.popPose();
            }
         }
      }
   }

   void submitOwn(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay) {
      int own = Math.min(this.draw.ownCalls(), this.draw.calls());
      this.growHand(own);

      for (int c = 0; c < own; c++) {
         DrawSnapshot.Call call = this.draw.call(c);
         int callLight = call.ignoreLighting() ? 15728880 : light;
         this.growHandGroups(c, call.groups());
         boolean decal = decal(call);
         if (decal) {
            poseStack.pushPose();
            poseStack.scale(1.002F, 1.002F, 1.002F);
         }

         for (int g = 0; g < call.groups(); g++) {
            collector.order(c).submitCustomGeometry(poseStack, this.types[c][g], this.handDraws[c][g].set(this.draw, c, g, -1, overlay, callLight));
         }

         if (decal) {
            poseStack.popPose();
         }
      }
   }

   boolean armAnchor(String bone, float offsetX, float offsetY, float zRot, Matrix4f out) {
      for (int c = 0; c < Math.min(this.draw.ownCalls(), this.draw.calls()); c++) {
         DrawSnapshot.Call call = this.draw.call(c);
         int index = call.mesh().bone(bone);
         if (index >= 0 && drawn(call, index)) {
            float[] pivot = call.mesh().bone(index).pivot();
            call.boneMatrix(index, this.bound).invert();
            out.identity()
               .translate(offsetX / 16.0F, offsetY / 16.0F, 0.0F)
               .rotateZ(zRot)
               .scale(0.0625F)
               .scale(-1.0F, -1.0F, 1.0F)
               .translate(-pivot[0], -pivot[1], -pivot[2])
               .mul(this.bound);
            return true;
         }
      }

      return false;
   }

   private static boolean drawn(DrawSnapshot.Call call, int bone) {
      for (int g = 0; g < call.groups(); g++) {
         for (int i = call.groupStart(g); i < call.groupStart(g + 1); i++) {
            if (call.bone(i) == bone) {
               return true;
            }
         }
      }

      return false;
   }

   void submitSubtree(PoseStack poseStack, SubmitNodeCollector collector, int light, String bone) {
      int own = Math.min(this.draw.ownCalls(), this.draw.calls());
      this.growHand(own);

      for (int c = 0; c < own; c++) {
         DrawSnapshot.Call call = this.draw.call(c);
         int index = call.mesh().bone(bone);
         if (index >= 0) {
            int callLight = call.ignoreLighting() ? 15728880 : light;
            this.growHandGroups(c, call.groups());

            for (int g = 0; g < call.groups(); g++) {
               collector.order(c)
                  .submitCustomGeometry(poseStack, this.types[c][g], this.handDraws[c][g].subtree(this.draw, c, g, index, OverlayTexture.NO_OVERLAY, callLight));
            }
         }
      }
   }

   private void growHand(int calls) {
      if (this.handDraws.length < calls) {
         int old = this.handDraws.length;
         this.handDraws = Arrays.copyOf(this.handDraws, calls);

         for (int c = old; c < calls; c++) {
            this.handDraws[c] = new MeshDraw[0];
         }
      }
   }

   private void growHandGroups(int call, int groups) {
      if (this.handDraws[call].length < groups) {
         int old = this.handDraws[call].length;
         this.handDraws[call] = Arrays.copyOf(this.handDraws[call], groups);

         for (int g = old; g < groups; g++) {
            this.handDraws[call][g] = new MeshDraw();
         }
      }
   }

   private void growBound(int call, int roots) {
      if (this.boundDraws.length <= call) {
         int old = this.boundDraws.length;
         this.boundDraws = Arrays.copyOf(this.boundDraws, call + 1);
         this.boundOutlineDraws = Arrays.copyOf(this.boundOutlineDraws, call + 1);

         for (int c = old; c <= call; c++) {
            this.boundDraws[c] = new MeshDraw[0];
            this.boundOutlineDraws[c] = new MeshDraw[0];
         }
      }

      if (this.boundDraws[call].length < roots) {
         int old = this.boundDraws[call].length;
         this.boundDraws[call] = Arrays.copyOf(this.boundDraws[call], roots);
         this.boundOutlineDraws[call] = Arrays.copyOf(this.boundOutlineDraws[call], roots);

         for (int r = old; r < roots; r++) {
            this.boundDraws[call][r] = new MeshDraw();
            this.boundOutlineDraws[call][r] = new MeshDraw();
         }
      }
   }

   public DrawSnapshot draw() {
      return this.draw;
   }

   public float actorScale() {
      return this.actorScale;
   }

   @Nullable
   public String texturePath(int call) {
      return call < this.draw.calls() ? this.draw.call(call).texture() : null;
   }

   @Nullable
   public String layerPath(int call, int layer) {
      return call < this.draw.calls() && layer < 3 ? this.draw.call(call).layer(layer) : null;
   }

   public boolean javaHandItem(int hand) {
      return this.javaHand(hand);
   }

   @Nullable
   public Identifier texture(int call, int group) {
      return call < this.draw.calls() && group < this.draw.call(call).groups() ? this.textures[call][group] : null;
   }

   public boolean glows(int call, int group) {
      return call < this.draw.calls() && group < this.draw.call(call).groups() && this.glowTypes[call][group] != null;
   }

   private void growCalls(int calls) {
      if (this.types.length < calls) {
         int old = this.types.length;
         this.types = Arrays.copyOf(this.types, calls);
         this.glowTypes = Arrays.copyOf(this.glowTypes, calls);
         this.outlineTypes = Arrays.copyOf(this.outlineTypes, calls);
         this.textures = Arrays.copyOf(this.textures, calls);
         this.kinds = Arrays.copyOf(this.kinds, calls);
         this.materials = Arrays.copyOf(this.materials, calls);
         this.layers = Arrays.copyOf(this.layers, calls);
         this.tints = Arrays.copyOf(this.tints, calls);
         this.secondTints = Arrays.copyOf(this.secondTints, calls);
         this.masks = Arrays.copyOf(this.masks, calls);
         this.dissolves = Arrays.copyOf(this.dissolves, calls);
         this.composed = Arrays.copyOf(this.composed, calls);
         this.draws = Arrays.copyOf(this.draws, calls);
         this.glowDraws = Arrays.copyOf(this.glowDraws, calls);
         this.outlineDraws = Arrays.copyOf(this.outlineDraws, calls);

         for (int c = old; c < calls; c++) {
            this.types[c] = new RenderType[0];
            this.glowTypes[c] = new RenderType[0];
            this.outlineTypes[c] = new RenderType[0];
            this.textures[c] = new Identifier[0];
            this.kinds[c] = new RenderKind[0];
            this.materials[c] = new MaterialTable.MaterialInfo[0];
            this.layers[c] = new String[0][];
            this.tints[c] = new int[0];
            this.secondTints[c] = new int[0];
            this.masks[c] = new String[0];
            this.dissolves[c] = new boolean[0];
            this.composed[c] = new boolean[0];
            this.draws[c] = new MeshDraw[0];
            this.glowDraws[c] = new MeshDraw[0];
            this.outlineDraws[c] = new MeshDraw[0];
         }
      }
   }

   private void growGroups(int call, int groups) {
      if (this.types[call].length < groups) {
         int old = this.types[call].length;
         this.types[call] = Arrays.copyOf(this.types[call], groups);
         this.glowTypes[call] = Arrays.copyOf(this.glowTypes[call], groups);
         this.outlineTypes[call] = Arrays.copyOf(this.outlineTypes[call], groups);
         this.textures[call] = Arrays.copyOf(this.textures[call], groups);
         this.kinds[call] = Arrays.copyOf(this.kinds[call], groups);
         this.materials[call] = Arrays.copyOf(this.materials[call], groups);
         this.layers[call] = Arrays.copyOf(this.layers[call], groups);
         this.tints[call] = Arrays.copyOf(this.tints[call], groups);
         this.secondTints[call] = Arrays.copyOf(this.secondTints[call], groups);
         this.masks[call] = Arrays.copyOf(this.masks[call], groups);
         this.dissolves[call] = Arrays.copyOf(this.dissolves[call], groups);
         this.composed[call] = Arrays.copyOf(this.composed[call], groups);
         this.draws[call] = Arrays.copyOf(this.draws[call], groups);
         this.glowDraws[call] = Arrays.copyOf(this.glowDraws[call], groups);
         this.outlineDraws[call] = Arrays.copyOf(this.outlineDraws[call], groups);

         for (int g = old; g < groups; g++) {
            this.layers[call][g] = new String[3];
            this.draws[call][g] = new MeshDraw();
            this.glowDraws[call][g] = new MeshDraw();
            this.outlineDraws[call][g] = new MeshDraw();
         }
      }
   }
}
