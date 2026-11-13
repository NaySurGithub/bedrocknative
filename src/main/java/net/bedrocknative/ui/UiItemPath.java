package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class UiItemPath {
   public static final UiItemPath NONE = new UiItemPath(new String[0], new int[0]);
   private final String[] collections;
   private final int[] indices;

   private UiItemPath(String[] collections, int[] indices) {
      this.collections = collections;
      this.indices = indices;
   }

   static UiItemPath of(UiControl control) {
      List<UiControl> items = new ArrayList<>(4);

      for (UiControl c = control; c != null; c = c.parent) {
         if (c.collectionIndex >= 0) {
            items.add(c);
         }
      }

      if (items.isEmpty()) {
         return NONE;
      } else {
         String[] collections = new String[items.size()];
         int[] indices = new int[items.size()];

         for (int i = 0; i < items.size(); i++) {
            collections[i] = items.get(i).indexCollection;
            indices[i] = items.get(i).collectionIndex;
         }

         return new UiItemPath(collections, indices);
      }
   }

   public static UiItemPath of(Object... collectionsAndIndices) {
      String[] collections = new String[collectionsAndIndices.length / 2];
      int[] indices = new int[collections.length];

      for (int i = 0; i < collections.length; i++) {
         collections[i] = (String)collectionsAndIndices[2 * i];
         indices[i] = (Integer)collectionsAndIndices[2 * i + 1];
      }

      return new UiItemPath(collections, indices);
   }

   public int size() {
      return this.indices.length;
   }

   @Nullable
   public String collection(int depth) {
      return depth >= 0 && depth < this.collections.length ? this.collections[depth] : null;
   }

   public int index(int depth) {
      return depth >= 0 && depth < this.indices.length ? this.indices[depth] : -1;
   }

   public int index(String collection) {
      for (int i = 0; i < this.indices.length; i++) {
         if (collection.equals(this.collections[i])) {
            return this.indices[i];
         }
      }

      return -1;
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof UiItemPath path
         && Arrays.equals((Object[])this.collections, (Object[])path.collections)
         && Arrays.equals(this.indices, path.indices);
   }

   @Override
   public int hashCode() {
      return Objects.hash(Arrays.hashCode((Object[])this.collections), Arrays.hashCode(this.indices));
   }

   @Override
   public String toString() {
      StringBuilder out = new StringBuilder("[");

      for (int i = 0; i < this.indices.length; i++) {
         out.append(i == 0 ? "" : ", ").append(this.collections[i]).append(' ').append(this.indices[i]);
      }

      return out.append(']').toString();
   }
}
