package net.bedrocknative.molang;

import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;

public final class MolangEnv {
   public MolangActor actor = MolangActor.NONE;
   public VariableStore variables = new VariableStore();
   public VariableStore context = new VariableStore(8);
   public final VariableStore temps = new VariableStore();
   public float thisValue;
   public @Nullable MolangResources resources;
   public @Nullable MolangControllerScope controller;
   public @Nullable MolangGeometry geometry;
   public @Nullable EffectSink effects;
   public SplittableRandom random = new SplittableRandom();
   public float animTime;
   public float deltaTime;
   public float frameAlpha;
   public float keyFrameLerpTime;
   public boolean attachable;
   @Nullable Object object;
   @Nullable Object pending;
   int flow;
   float returnValue;
   @Nullable Object returnObject;
   int depth;
   long steps = 100000L;
   public static final long MAX_STEPS = 100000L;
   static final int NONE = 0;
   static final int BREAK = 1;
   static final int CONTINUE = 2;
   static final int RETURN = 3;

   void charge(long amount) {
      this.steps -= amount;
      if (this.steps < 0L) {
         throw new MolangBudgetException();
      }
   }

   public @Nullable Object result() {
      return this.object;
   }

   public void setResult(@Nullable Object value) {
      this.pending = value;
   }
}
