package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

public final class UiTreeDump {
   private UiTreeDump() {
   }

   public static List<String> lines(UiScreenInstance screen, UiTextures textures, int limit) {
      List<String> lines = new ArrayList<>();
      UiControl root = screen.root();
      if (root != null) {
         add(root, 0, textures, lines, limit);
      }

      return lines;
   }

   private static void add(UiControl control, int depth, UiTextures textures, List<String> lines, int limit) {
      if (control.visible() && lines.size() < limit) {
         StringBuilder line = new StringBuilder();
         line.append("  ".repeat(Math.min(depth, 24)))
            .append(control.name())
            .append(" (")
            .append(control.definition())
            .append(") ")
            .append(control.type().name().toLowerCase(Locale.ROOT))
            .append(String.format(Locale.ROOT, " @%.1f,%.1f %.1fx%.1f", control.x(), control.y(), control.width(), control.height()));
         Arrays.stream(control.size()).map(Object::toString).reduce((a, b) -> a + "," + b).ifPresent(size -> line.append(" size[").append(size).append(']'));
         if (!control.texture().isEmpty()) {
            UiTextureInfo info = textures.info(control.texture(), "InUserPackage");
            line.append(" texture ").append(control.texture()).append(info == null ? " (missing)" : " (" + info.width() + "x" + info.height() + ")");
         }

         if (!control.text().isEmpty()) {
            line.append(" text \"").append(escape(control.text())).append('"');
         }

         lines.add(line.toString());

         for (UiControl child : control.children()) {
            add(child, depth + 1, textures, lines, limit);
         }
      }
   }

   public static List<String> collections(UiScreenInstance screen, ToIntFunction<String> mapped, boolean withControls, int limit) {
      List<String> lines = new ArrayList<>();
      UiControl root = screen.root();
      if (root == null) {
         return lines;
      }

      Map<String, int[]> counts = new TreeMap<>();
      List<String> controls = new ArrayList<>();
      root.walk(
         controlx -> {
            String name = controlx.collectionName();
            String indexed = controlx.collectionIndex() >= 0 ? controlx.itemCollection() : null;
            StringBuilder bindings = new StringBuilder();

            for (UiBinding binding : controlx.bindings()) {
               if (binding.collection() != null) {
                  bindings.append(' ')
                     .append(binding.type().name().toLowerCase(Locale.ROOT))
                     .append(':')
                     .append(binding.name())
                     .append('@')
                     .append(binding.collection());
                  counts.computeIfAbsent(binding.collection(), key -> new int[1]);
               }
            }

            if (name != null) {
               counts.computeIfAbsent(name, key -> new int[1]);
            }

            if (controlx.collectionIndex() >= 0) {
               counts.computeIfAbsent(indexed == null ? "(none)" : indexed, key -> new int[1])[0]++;
            }

            if (name != null || controlx.collectionIndex() >= 0 || bindings.length() > 0) {
               controls.add(
                  String.format(
                     Locale.ROOT,
                     "%s (%s) %s%s collection_name=%s index=%d of %s%s",
                     controlx.name(),
                     controlx.definition(),
                     controlx.type().name().toLowerCase(Locale.ROOT),
                     controlx.visible() ? "" : " hidden",
                     name,
                     controlx.collectionIndex(),
                     indexed,
                     bindings
                  )
               );
            }
         }
      );
      counts.forEach((name, count) -> lines.add("collection " + name + ": " + count[0] + " indexed items in the layout, host maps " + mapped.applyAsInt(name)));

      for (String control : withControls ? controls : List.<String>of()) {
         if (lines.size() >= limit) {
            break;
         }

         lines.add(control);
      }

      return lines;
   }

   public static String escape(String text) {
      StringBuilder out = new StringBuilder();
      int i = 0;

      while (i < text.length()) {
         int codePoint = text.codePointAt(i);
         i += Character.charCount(codePoint);
         if (codePoint == 167) {
            out.append("\\u00A7");
         } else if (codePoint >= 32 && (codePoint < 57344 || codePoint > 63743)) {
            out.appendCodePoint(codePoint);
         } else {
            out.append(String.format(Locale.ROOT, "\\u%04X", codePoint));
         }
      }

      return out.length() > 120 ? out.substring(0, 120) + "..." : out.toString();
   }
}
