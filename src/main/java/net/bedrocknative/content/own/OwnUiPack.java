package net.bedrocknative.content.own;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import net.bedrocknative.content.BundledPack;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;

public final class OwnUiPack implements PackSource {
   public static final OwnUiPack INSTANCE = new OwnUiPack();
   static final String ROOT = "assets/bedrocknative/own_ui";
   private final BundledPack files = new BundledPack("assets/bedrocknative/own_ui");

   private OwnUiPack() {
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      String normalized = PackStack.normalize(path);
      if (normalized == null) {
         return Optional.empty();
      } else {
         String lower = normalized.toLowerCase(Locale.ROOT);
         if (lower.startsWith("textures/")) {
            return OwnArt.read(lower);
         } else {
            return !lower.startsWith("ui/") && !lower.startsWith("texts/") ? Optional.empty() : this.files.read(lower);
         }
      }
   }

   @Override
   public boolean has(String path) throws IOException {
      String normalized = PackStack.normalize(path);
      if (normalized == null) {
         return false;
      }

      String lower = normalized.toLowerCase(Locale.ROOT);
      return lower.startsWith("textures/")
         ? OwnArt.hasFile(lower)
         : (lower.startsWith("ui/") || lower.startsWith("texts/")) && this.files.read(lower).isPresent();
   }

   @Override
   public String toString() {
      return "own-menus";
   }
}
