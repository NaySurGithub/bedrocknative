package net.bedrocknative.client.world;

import java.util.List;
import java.util.function.LongFunction;
import net.bedrocknative.world.state.DebugShapes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.gizmos.TextGizmo.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugArrow;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugBox;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugCircle;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugCone;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugCylinder;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugEllipsoid;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugLine;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugPyramid;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugShape;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugSphere;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugText;
import org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockDebugShapes {
   private static final float LINE_WIDTH = 2.0F;
   private static final int DEFAULT_SEGMENTS = 16;
   private static final int MAX_SEGMENTS = 48;
   private static final int MAX_LINES_PER_TICK = 20000;
   private static final float TEXT_SCALE = 0.32F;
   private final Minecraft minecraft;
   private final LongFunction<Entity> entityByRuntimeId;
   private final DebugShapes shapes = new DebugShapes();
   private int lines;

   public BedrockDebugShapes(Minecraft minecraft, LongFunction<Entity> entityByRuntimeId) {
      this.minecraft = minecraft;
      this.entityByRuntimeId = entityByRuntimeId;
   }

   public DebugShapes shapes() {
      return this.shapes;
   }

   public void apply(DebugDrawerPacket packet) {
      this.shapes.apply(packet, System.currentTimeMillis());
   }

   public void clear() {
      this.shapes.clear();
   }

   public void tick() {
      if (this.shapes.size() != 0 && this.minecraft.level != null) {
         this.lines = 0;
         Vec3 eye = this.minecraft.player == null ? Vec3.ZERO : this.minecraft.player.position();

         try (TemporaryCollection ignored = this.minecraft.collectPerTickGizmos()) {
            for (DebugShape shape : this.shapes.live(System.currentTimeMillis(), dimension(this.minecraft.level.dimension()))) {
               try {
                  this.draw(shape, eye);
               } catch (RuntimeException var7) {
               }

               if (this.lines >= 20000) {
                  break;
               }
            }
         }
      }
   }

   static int dimension(ResourceKey<Level> level) {
      return level == Level.NETHER ? 1 : (level == Level.END ? 2 : 0);
   }

   private void draw(DebugShape shape, Vec3 eye) {
      Vec3 anchor = this.anchor(shape);
      if (anchor != null) {
         Vec3 at = anchor.add(vec(shape.getPosition()));
         Float maximum = shape.getMaximumRenderDistance();
         if (maximum == null || !(maximum > 0.0F) || !(at.distanceToSqr(eye) > (double)maximum.floatValue() * maximum.floatValue())) {
            int argb = shape.getColor() == null ? -1 : shape.getColor().getRGB();
            float scale = shape.getScale() != null && Float.isFinite(shape.getScale()) ? shape.getScale() : 1.0F;
            Vector3f rotation = shape.getRotation() == null ? Vector3f.ZERO : shape.getRotation();
            switch (shape) {
               case DebugLine line:
                  this.line(at, line.getLineEndPosition() == null ? at : anchor.add(vec(line.getLineEndPosition())), argb);
                  break;
               case DebugArrow arrow:
                  this.count(1);
                  Gizmos.arrow(at, arrow.getArrowEndPosition() == null ? at.add(0.0, 1.0, 0.0) : anchor.add(vec(arrow.getArrowEndPosition())), argb, 2.0F);
                  break;
               case DebugBox box:
                  this.box(at, box.getBoxBounds() == null ? Vector3f.ONE : box.getBoxBounds(), scale, rotation, argb);
                  break;
               case DebugCircle circle:
                  this.ring(at, scale, scale, rotation, segments(circle.getSegments()), argb, BedrockDebugShapes.Axis.Y);
                  break;
               case DebugSphere sphere: {
                  int segments = segments(sphere.getSegments());
                  this.ring(at, scale, scale, rotation, segments, argb, BedrockDebugShapes.Axis.Y);
                  this.ring(at, scale, scale, rotation, segments, argb, BedrockDebugShapes.Axis.X);
                  this.ring(at, scale, scale, rotation, segments, argb, BedrockDebugShapes.Axis.Z);
                  break;
               }
               case DebugText text:
                  GizmoProperties properties = Gizmos.billboardText(
                     ChatFormatting.stripFormatting(text.getText() == null ? "" : text.getText()), at, Style.forColorAndCentered(argb).withScale(0.32F * scale)
                  );
                  if (!text.isDepthTest()) {
                     properties.setAlwaysOnTop();
                  }
                  break;
               case DebugCylinder cylinder:
                  this.cylinder(at, cylinder, scale, rotation, argb);
                  break;
               case DebugCone cone: {
                  Vector2f radii = cone.getRadii() == null ? Vector2f.ONE : cone.getRadii();
                  Vec3 apex = at.add(rotate(new Vec3(0.0, cone.getHeight() * scale, 0.0), rotation));
                  this.ring(at, radii.getX() * scale, radii.getY() * scale, rotation, segments(cone.getSegments()), argb, BedrockDebugShapes.Axis.Y);

                  for (int i = 0; i < 4; i++) {
                     double angle = i * Math.PI / 2.0;
                     this.line(
                        at.add(rotate(new Vec3(Math.cos(angle) * radii.getX() * scale, 0.0, Math.sin(angle) * radii.getY() * scale), rotation)), apex, argb
                     );
                  }
                  break;
               }
               case DebugPyramid pyramid:
                  double halfWidth = pyramid.getWidth() * scale / 2.0F;
                  double halfDepth = (pyramid.getDepth() == null ? pyramid.getWidth() : pyramid.getDepth()) * scale / 2.0F;
                  Vec3 apex = at.add(rotate(new Vec3(0.0, pyramid.getHeight() * scale, 0.0), rotation));
                  Vec3[] base = new Vec3[]{
                     at.add(rotate(new Vec3(-halfWidth, 0.0, -halfDepth), rotation)),
                     at.add(rotate(new Vec3(halfWidth, 0.0, -halfDepth), rotation)),
                     at.add(rotate(new Vec3(halfWidth, 0.0, halfDepth), rotation)),
                     at.add(rotate(new Vec3(-halfWidth, 0.0, halfDepth), rotation))
                  };

                  for (int i = 0; i < 4; i++) {
                     this.line(base[i], base[(i + 1) % 4], argb);
                     this.line(base[i], apex, argb);
                  }
                  break;
               case DebugEllipsoid ellipsoid: {
                  Vector3f radii = ellipsoid.getRadii() == null ? Vector3f.ONE : ellipsoid.getRadii();
                  int segments = segments(ellipsoid.getSegments());
                  this.ring(at, radii.getX() * scale, radii.getZ() * scale, rotation, segments, argb, BedrockDebugShapes.Axis.Y);
                  this.ring(at, radii.getX() * scale, radii.getY() * scale, rotation, segments, argb, BedrockDebugShapes.Axis.Z);
                  this.ring(at, radii.getZ() * scale, radii.getY() * scale, rotation, segments, argb, BedrockDebugShapes.Axis.X);
                  break;
               }
               default:
            }
         }
      }
   }

   private @Nullable Vec3 anchor(DebugShape shape) {
      if (shape.getAttachedToEntityId() == null) {
         return Vec3.ZERO;
      }

      Entity entity = this.entityByRuntimeId.apply(shape.getAttachedToEntityId());
      return entity == null ? null : entity.position();
   }

   private static Vec3 vec(@Nullable Vector3f v) {
      return v != null && Float.isFinite(v.getX()) && Float.isFinite(v.getY()) && Float.isFinite(v.getZ()) ? new Vec3(v.getX(), v.getY(), v.getZ()) : Vec3.ZERO;
   }

   private void line(Vec3 from, Vec3 to, int argb) {
      this.count(1);
      Gizmos.line(from, to, argb, 2.0F);
   }

   private void box(Vec3 center, Vector3f bounds, float scale, Vector3f rotation, int argb) {
      double hx = bounds.getX() * scale / 2.0F;
      double hy = bounds.getY() * scale / 2.0F;
      double hz = bounds.getZ() * scale / 2.0F;
      if (rotation.lengthSquared() == 0.0F) {
         this.count(12);
         Gizmos.cuboid(new AABB(center.x - hx, center.y - hy, center.z - hz, center.x + hx, center.y + hy, center.z + hz), GizmoStyle.stroke(argb, 2.0F));
      } else {
         Vec3[] corners = new Vec3[8];

         for (int i = 0; i < 8; i++) {
            corners[i] = center.add(rotate(new Vec3((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz), rotation));
         }

         for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
               if ((i & bit) == 0) {
                  this.line(corners[i], corners[i | bit], argb);
               }
            }
         }
      }
   }

   private void cylinder(Vec3 base, DebugCylinder cylinder, float scale, Vector3f rotation, int argb) {
      Vector2f x = cylinder.getRadiusX() == null ? Vector2f.ONE : cylinder.getRadiusX();
      Vector2f z = cylinder.getRadiusZ() == null ? Vector2f.ONE : cylinder.getRadiusZ();
      int segments = segments(cylinder.getSegments());
      Vec3 top = base.add(rotate(new Vec3(0.0, cylinder.getHeight() * scale, 0.0), rotation));
      this.ring(base, x.getX() * scale, z.getX() * scale, rotation, segments, argb, BedrockDebugShapes.Axis.Y);
      this.ring(top, x.getY() * scale, z.getY() * scale, rotation, segments, argb, BedrockDebugShapes.Axis.Y);

      for (int i = 0; i < 4; i++) {
         double angle = i * Math.PI / 2.0;
         this.line(
            base.add(rotate(new Vec3(Math.cos(angle) * x.getX() * scale, 0.0, Math.sin(angle) * z.getX() * scale), rotation)),
            top.add(rotate(new Vec3(Math.cos(angle) * x.getY() * scale, 0.0, Math.sin(angle) * z.getY() * scale), rotation)),
            argb
         );
      }
   }

   private void ring(Vec3 center, double radiusA, double radiusB, Vector3f rotation, int segments, int argb, BedrockDebugShapes.Axis around) {
      Vec3 previous = null;

      for (int i = 0; i <= segments; i++) {
         double angle = i * 2 * Math.PI / segments;
         double a = Math.cos(angle) * radiusA;
         double b = Math.sin(angle) * radiusB;

         Vec3 local = switch (around) {
            case X -> new Vec3(0.0, a, b);
            case Y -> new Vec3(a, 0.0, b);
            case Z -> new Vec3(a, b, 0.0);
         };
         Vec3 point = center.add(rotate(local, rotation));
         if (previous != null) {
            this.line(previous, point, argb);
         }

         previous = point;
      }
   }

   private void count(int added) {
      this.lines += added;
   }

   private static int segments(@Nullable Integer segments) {
      return segments != null && segments >= 3 ? Math.min(segments, 48) : 16;
   }

   static Vec3 rotate(Vec3 v, Vector3f radians) {
      if (radians.lengthSquared() != 0.0F && Float.isFinite(radians.lengthSquared())) {
         double roll = radians.getZ();
         double pitch = radians.getX();
         double yaw = radians.getY();
         double x = v.x * Math.cos(roll) - v.y * Math.sin(roll);
         double y = v.x * Math.sin(roll) + v.y * Math.cos(roll);
         double z = v.z;
         double y2 = y * Math.cos(pitch) - z * Math.sin(pitch);
         double z2 = y * Math.sin(pitch) + z * Math.cos(pitch);
         return new Vec3(x * Math.cos(yaw) + z2 * Math.sin(yaw), y2, -x * Math.sin(yaw) + z2 * Math.cos(yaw));
      } else {
         return v;
      }
   }

   public List<DebugShape> live() {
      return this.minecraft.level == null ? List.of() : this.shapes.live(System.currentTimeMillis(), dimension(this.minecraft.level.dimension()));
   }

   private enum Axis {
      X,
      Y,
      Z;
   }
}
