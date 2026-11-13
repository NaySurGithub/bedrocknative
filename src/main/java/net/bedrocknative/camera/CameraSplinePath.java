package net.bedrocknative.camera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraEase;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineType;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction.SplineProgressOption;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction.SplineRotationOption;
import org.jspecify.annotations.Nullable;

public final class CameraSplinePath {
   private final float totalTime;
   private final CameraSplineType type;
   private final List<Vector3d> points = new ArrayList<>();
   private final List<CameraSplinePath.Key> progress = new ArrayList<>();
   private final List<CameraSplinePath.Key> rotation = new ArrayList<>();

   public CameraSplinePath(CameraSplineInstruction spline) {
      this.totalTime = Math.max(spline.getTotalTime(), 0.0F);
      this.type = spline.getType() == null ? CameraSplineType.CATMULL_ROM : spline.getType();

      for (Vector3f point : spline.getCurve()) {
         this.points.add(Vector3d.from(point.getX(), point.getY(), point.getZ()));
      }

      double scale = this.timeScale(spline);
      if (spline.getProgressKeyFrames() != null) {
         spline.getProgressKeyFrames()
            .forEach(frame -> this.progress.add(new CameraSplinePath.Key(frame.getTime() * scale, new double[]{frame.getValue()}, ease(frame.getEase()))));
      }

      if (spline.getRotationOption() != null) {
         spline.getRotationOption()
            .forEach(
               frame -> this.rotation
                  .add(
                     new CameraSplinePath.Key(
                        frame.getKeyFrameTimes() * scale,
                        new double[]{frame.getKeyFrameValues().getX(), frame.getKeyFrameValues().getY()},
                        ease(frame.getEase())
                     )
                  )
            );
      }

      this.progress.sort(Comparator.comparingDouble(CameraSplinePath.Key::time));
      this.rotation.sort(Comparator.comparingDouble(CameraSplinePath.Key::time));
   }

   public boolean empty() {
      return this.points.isEmpty();
   }

   public float totalTime() {
      return this.totalTime;
   }

   public boolean finished(double seconds) {
      return seconds >= this.totalTime;
   }

   public double progress(double seconds) {
      if (this.progress.isEmpty()) {
         return this.totalTime <= 0.0F ? 1.0 : Math.clamp(seconds / this.totalTime, 0.0, 1.0);
      } else {
         return Math.clamp(keyed(this.progress, seconds, new double[]{0.0})[0], 0.0, 1.0);
      }
   }

   public Vector3d position(double seconds) {
      return this.at(this.progress(seconds));
   }

   public float @Nullable [] rotation(double seconds, float startPitch, float startYaw) {
      if (this.rotation.isEmpty()) {
         return null;
      }

      double firstYaw = this.rotation.getFirst().value[1];
      double[] value = keyed(this.rotation, seconds, new double[]{startPitch, firstYaw + CameraDirector.wrapDegrees((float)(startYaw - firstYaw))});
      return new float[]{(float)value[0], (float)value[1]};
   }

   public Vector3d at(double share) {
      if (this.points.isEmpty()) {
         return Vector3d.ZERO;
      }

      if (this.points.size() == 1) {
         return this.points.getFirst();
      }

      double scaled = Math.clamp(share, 0.0, 1.0) * (this.points.size() - 1);
      int segment = Math.min((int)Math.floor(scaled), this.points.size() - 2);
      double t = scaled - segment;
      Vector3d p1 = this.points.get(segment);
      Vector3d p2 = this.points.get(segment + 1);
      if (this.type == CameraSplineType.LINEAR) {
         return p1.add(p2.sub(p1).mul(t));
      }

      Vector3d p0 = this.points.get(Math.max(segment - 1, 0));
      Vector3d p3 = this.points.get(Math.min(segment + 2, this.points.size() - 1));
      double t2 = t * t;
      double t3 = t2 * t;
      return p1.mul(2.0F)
         .add(p2.sub(p0).mul(t))
         .add(p0.mul(2.0F).sub(p1.mul(5.0F)).add(p2.mul(4.0F)).sub(p3).mul(t2))
         .add(p1.mul(3.0F).sub(p0).sub(p2.mul(3.0F)).add(p3).mul(t3))
         .mul(0.5);
   }

   private double timeScale(CameraSplineInstruction spline) {
      double latest = 0.0;
      if (spline.getProgressKeyFrames() != null) {
         latest = spline.getProgressKeyFrames().stream().mapToDouble(SplineProgressOption::getTime).max().orElse(0.0);
      }

      if (spline.getRotationOption() != null) {
         latest = Math.max(latest, spline.getRotationOption().stream().mapToDouble(SplineRotationOption::getKeyFrameTimes).max().orElse(0.0));
      }

      return this.totalTime > 1.0F && latest <= 1.0 ? this.totalTime : 1.0;
   }

   private static double[] keyed(List<CameraSplinePath.Key> keys, double seconds, double[] start) {
      double fromTime = 0.0;
      double[] from = start;

      for (CameraSplinePath.Key key : keys) {
         if (seconds < key.time) {
            double span = key.time - fromTime;
            double t = span <= 0.0 ? 1.0 : CameraEasing.apply(key.ease, (seconds - fromTime) / span);
            double[] value = new double[from.length];

            for (int i = 0; i < value.length; i++) {
               value[i] = from[i] + (key.value[i] - from[i]) * t;
            }

            return value;
         }

         fromTime = key.time;
         from = key.value;
      }

      return from;
   }

   private static CameraEase ease(@Nullable CameraEase ease) {
      return ease == null ? CameraEase.LINEAR : ease;
   }

   private record Key(double time, double[] value, CameraEase ease) {
   }
}
