package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public final class UiBinding {
   final UiBinding.Type type;
   final UiBinding.Condition condition;
   @Nullable
   final String name;
   @Nullable
   final UiExpression expression;
   @Nullable
   final String target;
   @Nullable
   final String collection;
   @Nullable
   final String sourceControl;
   final boolean siblingScope;
   @Nullable
   UiControl resolvedSource;
   long resolvedTreeVersion = -1L;
   int reads;

   UiBinding(
      UiBinding.Type type,
      UiBinding.Condition condition,
      @Nullable String name,
      @Nullable UiExpression expression,
      @Nullable String target,
      @Nullable String collection,
      @Nullable String sourceControl,
      boolean siblingScope
   ) {
      this.type = type;
      this.condition = condition;
      this.name = name;
      this.expression = expression;
      this.target = target;
      this.collection = collection;
      this.sourceControl = sourceControl;
      this.siblingScope = siblingScope;
   }

   public UiBinding.Type type() {
      return this.type;
   }

   public UiBinding.Condition condition() {
      return this.condition;
   }

   @Nullable
   public String name() {
      return this.name;
   }

   @Nullable
   public String target() {
      return this.target;
   }

   @Nullable
   public String collection() {
      return this.collection;
   }

   public int reads() {
      return this.reads;
   }

   @Override
   public String toString() {
      return this.type + " " + (this.expression != null ? this.expression.source() : this.name) + " -> " + this.target;
   }

   public static enum Condition {
      ALWAYS,
      ALWAYS_WHEN_VISIBLE,
      VISIBLE,
      ONCE,
      NONE,
      VISIBILITY_CHANGED;

      static UiBinding.Condition of(@Nullable String name) {
         if (name == null) {
            return ALWAYS;
         } else {
            return switch (name) {
               case "always_when_visible" -> ALWAYS_WHEN_VISIBLE;
               case "visible" -> VISIBLE;
               case "once" -> ONCE;
               case "none" -> NONE;
               case "visibility_changed" -> VISIBILITY_CHANGED;
               default -> ALWAYS;
            };
         }
      }
   }

   public static enum Type {
      GLOBAL,
      COLLECTION,
      COLLECTION_DETAILS,
      VIEW,
      NONE;
   }
}
