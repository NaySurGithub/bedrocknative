package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public interface UiDataSource {
   UiDataSource EMPTY = new UiDataSource() {
      @Override
      public UiValue global(String name) {
         return UiValue.NULL;
      }

      @Override
      public int length(String collection) {
         return 0;
      }

      @Override
      public UiValue item(String collection, int index, String name) {
         return UiValue.NULL;
      }

      @Override
      public @Nullable String kind(String collection, int index) {
         return null;
      }

      @Override
      public long version() {
         return 0L;
      }
   };

   UiValue global(String var1);

   int length(String var1);

   UiValue item(String var1, int var2, String var3);

   @Nullable String kind(String var1, int var2);

   long version();

   default UiValue item(String collection, int index, String name, int outer) {
      return this.item(collection, index, name);
   }

   default void write(String collection, int index, String name, UiValue value) {
   }

   default void write(@Nullable String collection, int index, String name, UiValue value, int outer) {
      if (collection != null) {
         this.write(collection, index, name, value);
      }
   }

   default void event(String buttonId, @Nullable String collection, int index) {
   }

   default UiValue radioSelection(String toggleName) {
      return UiValue.NULL;
   }

   default void radioSelected(String toggleName, int index) {
   }

   default UiValue global(String name, UiItemPath path) {
      return this.global(name);
   }

   default void event(String buttonId, UiItemPath path) {
      this.event(buttonId, null, -1);
   }
}
