package net.bedrocknative.molang;

import java.util.Arrays;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

public final class VariableStore {
   private int[] keys;
   private float[] values;
   private @Nullable Object[] objects;
   private int size;
   private @Nullable VariableStore parent;

   public VariableStore() {
      this(16);
   }

   public VariableStore(int capacity) {
      int size = Integer.highestOneBit(Math.max(4, capacity - 1)) << 1;
      this.keys = new int[size];
      this.values = new float[size];
      this.objects = new Object[size];
   }

   public int size() {
      return this.size;
   }

   public @Nullable VariableStore parent() {
      return this.parent;
   }

   public void setParent(@Nullable VariableStore parent) {
      this.parent = parent;
   }

   public boolean has(int id) {
      return this.index(id) >= 0;
   }

   public boolean has(String name) {
      return this.has(MolangNames.id(name));
   }

   public float get(int id) {
      int index = this.index(id);
      return index >= 0 ? this.values[index] : 0.0F;
   }

   public float get(String name) {
      return this.get(MolangNames.id(name));
   }

   public @Nullable Object object(int id) {
      int index = this.index(id);
      return index >= 0 ? this.objects[index] : null;
   }

   public @Nullable Object object(String name) {
      return this.object(MolangNames.id(name));
   }

   public void set(int id, float value, @Nullable Object object) {
      int mask = this.keys.length - 1;
      int slot = hash(id) & mask;

      while (true) {
         int key = this.keys[slot];
         if (key == id) {
            this.values[slot] = value;
            this.objects[slot] = object;
            return;
         }

         if (key == 0) {
            this.keys[slot] = id;
            this.values[slot] = value;
            this.objects[slot] = object;
            if (++this.size * 2 > this.keys.length) {
               this.grow();
            }

            return;
         }

         slot = slot + 1 & mask;
      }
   }

   public void set(String name, float value) {
      this.set(MolangNames.id(name), value, null);
   }

   public void set(String name, Object object) {
      this.set(MolangNames.id(name), 0.0F, object);
   }

   public void clear() {
      if (this.size > 0) {
         Arrays.fill(this.keys, 0);
         Arrays.fill(this.objects, null);
         this.size = 0;
      }
   }

   public void forEachId(IntConsumer action) {
      for (int key : this.keys) {
         if (key != 0) {
            action.accept(key);
         }
      }
   }

   public VariableStore copy() {
      VariableStore copy = new VariableStore(this.keys.length);

      for (int i = 0; i < this.keys.length; i++) {
         if (this.keys[i] != 0) {
            Object object = this.objects[i];
            copy.set(this.keys[i], this.values[i], object instanceof MolangStruct struct ? struct.copy() : object);
         }
      }

      return copy;
   }

   int index(int id) {
      int mask = this.keys.length - 1;
      int slot = hash(id) & mask;

      while (true) {
         int key = this.keys[slot];
         if (key == id) {
            return slot;
         }

         if (key == 0) {
            return -1;
         }

         slot = slot + 1 & mask;
      }
   }

   float valueAt(int index) {
      return this.values[index];
   }

   @Nullable Object objectAt(int index) {
      return this.objects[index];
   }

   private void grow() {
      int[] oldKeys = this.keys;
      float[] oldValues = this.values;
      Object[] oldObjects = this.objects;
      this.keys = new int[oldKeys.length * 2];
      this.values = new float[this.keys.length];
      this.objects = new Object[this.keys.length];
      this.size = 0;

      for (int i = 0; i < oldKeys.length; i++) {
         if (oldKeys[i] != 0) {
            this.set(oldKeys[i], oldValues[i], oldObjects[i]);
         }
      }
   }

   private static int hash(int id) {
      return id * -1640531527 >>> 7 ^ id;
   }
}
