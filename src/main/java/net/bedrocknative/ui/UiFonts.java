package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackStack;
import org.jspecify.annotations.Nullable;

public final class UiFonts {
   public static final String METADATA = "font/font_metadata.json";
   public static final UiFonts NONE = new UiFonts(Map.of(), Map.of(), path -> Optional.empty(), null);
   private static final int MAX_ALIAS_DEPTH = 4;
   private static final List<String> SMOOTH = List.of("smoothfontlatin", "oldsmoothfont");
   static final String LINE_HEIGHT = "bedrocknative_line_height";
   private static final float MIN_LINE_HEIGHT = 0.5F;
   private static final float MAX_LINE_HEIGHT = 3.0F;
   private final Map<String, UiFonts.Declared> fonts;
   private final Map<String, List<String>> aliases;
   private final Function<String, Optional<byte[]>> reader;
   private final Map<String, FutureTask<Optional<UiVectorFont>>> loaded = new ConcurrentHashMap<>();
   private final Map<String, Optional<UiVectorFont>> byType = new ConcurrentHashMap<>();
   private final @Nullable PackStack stack;
   private volatile @Nullable Optional<UiVectorFont> defaultFont;

   private UiFonts(Map<String, UiFonts.Declared> fonts, Map<String, List<String>> aliases, Function<String, Optional<byte[]>> reader, @Nullable PackStack stack) {
      this.fonts = fonts;
      this.aliases = aliases;
      this.reader = reader;
      this.stack = stack;
   }

   public @Nullable UiVectorFont defaultFont() {
      Optional<UiVectorFont> known = this.defaultFont;
      if (known == null) {
         synchronized (this) {
            known = this.defaultFont;
            if (known == null) {
               UiVectorFont font = null;

               try {
                  font = this.stack == null ? null : MojanglesFont.load(this.stack);
               } catch (RuntimeException e) {
                  BedrockNative.LOGGER.info("JSON UI default font is unreadable: {}", e.toString());
               }

               known = Optional.ofNullable(font);
               this.defaultFont = known;
            }
         }
      }

      return known.orElse(null);
   }

   public static UiFonts load(PackStack stack) {
      List<byte[]> metadata = new ArrayList<>();

      for (PackStack.PackFile copy : stack.readAll("font/font_metadata.json")) {
         metadata.add(copy.data());
      }

      return of(metadata, stack, path -> {
         List<PackStack.PackFile> copies = stack.readAll(path);

         for (int i = copies.size() - 1; i >= 0; i--) {
            if (copies.get(i).data().length > 0) {
               return Optional.of(copies.get(i).data());
            }
         }

         return Optional.empty();
      });
   }

   public static UiFonts of(List<byte[]> metadataBottomFirst, Function<String, Optional<byte[]>> reader) {
      return of(metadataBottomFirst, null, reader);
   }

   private static UiFonts of(List<byte[]> metadataBottomFirst, @Nullable PackStack stack, Function<String, Optional<byte[]>> reader) {
      Map<String, UiFonts.Declared> fonts = new HashMap<>();
      Map<String, List<String>> aliases = new HashMap<>();

      for (byte[] data : metadataBottomFirst) {
         JsonObject root;
         try {
            root = UiJson.parseObject(data);
         } catch (UiJson.Error e) {
            BedrockNative.LOGGER.debug("Unreadable {}: {}", "font/font_metadata.json", e.getMessage());
            continue;
         }

         JsonElement var9 = root.get("fonts");
         if (var9 instanceof JsonArray) {
            for (JsonElement element : (JsonArray)var9) {
               if (element instanceof JsonObject font) {
                  String name = UiModifications.string(font.get("font_name"));
                  String format = UiModifications.string(font.get("font_format"));
                  String file = UiModifications.string(font.get("font_file"));
                  if (name != null && file != null && format != null) {
                     String key = name.toLowerCase(Locale.ROOT);
                     fonts.put(key, new UiFonts.Declared(name, format.toLowerCase(Locale.ROOT), file, lineHeight(font)));
                     aliases.remove(key);
                  }
               }
            }
         }

         if (root.get("font_aliases") instanceof JsonArray list) {
            for (JsonElement element : list) {
               if (element instanceof JsonObject alias && UiModifications.string(alias.get("alias")) != null) {
                  List<String> references = new ArrayList<>();
                  JsonElement var27 = alias.get("fonts");
                  if (var27 instanceof JsonArray) {
                     for (JsonElement entry : (JsonArray)var27) {
                        if (entry instanceof JsonObject reference && !reference.has("font_ranges")) {
                           String name = UiModifications.string(reference.get("font_reference"));
                           if (name != null) {
                              references.add(name.toLowerCase(Locale.ROOT));
                           }
                        }
                     }
                  }

                  aliases.put(UiModifications.string(alias.get("alias")).toLowerCase(Locale.ROOT), references);
               }
            }
         }
      }

      return new UiFonts(Map.copyOf(fonts), Map.copyOf(aliases), reader, stack);
   }

   public int preload() {
      int ready = 0;
      this.defaultFont();
      List<String> names = new ArrayList<>(this.fonts.keySet());
      names.sort(Comparator.<String, Boolean>comparing(namex -> !"ttf".equals(this.fonts.get(namex).format())).thenComparing(namex -> namex));

      for (String name : names) {
         UiVectorFont font = this.resolve(name, 0);
         if (font != null) {
            try {
               font.warm();
               ready++;
            } catch (RuntimeException e) {
               BedrockNative.LOGGER.debug("JSON UI font {} could not be readied: {}", name, e.toString());
            }
         }
      }

      return ready;
   }

   public int declared() {
      return this.fonts.size();
   }

   private static float lineHeight(JsonObject font) {
      try {
         if (font.get("bedrocknative_line_height") instanceof JsonPrimitive value && value.isNumber()) {
            float height = value.getAsFloat();
            return height >= 0.5F && height <= 3.0F ? height : 0.0F;
         }
      } catch (RuntimeException var3) {
      }

      return 0.0F;
   }

   public @Nullable UiVectorFont font(@Nullable String fontType) {
      if (fontType != null && !fontType.isEmpty()) {
         Optional<UiVectorFont> known = this.byType.get(fontType);
         if (known == null) {
            known = Optional.ofNullable(this.lookup(fontType));
            this.byType.put(fontType, known);
         }

         return known.orElse(null);
      } else {
         return null;
      }
   }

   private @Nullable UiVectorFont lookup(String fontType) {
      String name = fontType.toLowerCase(Locale.ROOT);
      if (name.equals("smooth")) {
         for (String smooth : SMOOTH) {
            UiVectorFont font = this.resolve(smooth, 0);
            if (font != null) {
               return font;
            }
         }

         return null;
      } else {
         return this.resolve(name, 0);
      }
   }

   public @Nullable TtfFont ttf(@Nullable String fontType) {
      return this.font(fontType) instanceof TtfFont ttf ? ttf : null;
   }

   private @Nullable UiVectorFont resolve(String name, int depth) {
      UiFonts.Declared declared = this.fonts.get(name);
      if (declared != null) {
         return this.loadOnce(name, declared).orElse(null);
      }

      List<String> references = this.aliases.get(name);
      if (references != null && depth < 4) {
         for (String reference : references) {
            UiVectorFont font = this.resolve(reference, depth + 1);
            if (font != null) {
               return font;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private Optional<UiVectorFont> loadOnce(String name, UiFonts.Declared declared) {
      FutureTask<Optional<UiVectorFont>> task = this.loaded.get(name);
      if (task == null) {
         FutureTask<Optional<UiVectorFont>> mine = new FutureTask<>(() -> this.load(declared));
         task = this.loaded.putIfAbsent(name, mine);
         if (task == null) {
            task = mine;
            mine.run();
         }
      }

      try {
         return task.get();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return Optional.empty();
      } catch (ExecutionException e) {
         if (e.getCause() instanceof RuntimeException runtime) {
            throw runtime;
         } else {
            throw new IllegalStateException(e.getCause());
         }
      }
   }

   private Optional<UiVectorFont> load(UiFonts.Declared declared) {
      long start = System.nanoTime();
      Optional<UiVectorFont> loaded = this.read(declared);
      BedrockNative.LOGGER
         .info(
            "JSON UI font {} ({}) loaded in {} ms on {}",
            new Object[]{declared.name(), declared.format(), (System.nanoTime() - start) / 1000000L, Thread.currentThread().getName()}
         );
      return loaded;
   }

   private Optional<UiVectorFont> read(UiFonts.Declared declared) {
      UiVectorFont font = switch (declared.format()) {
         case "ttf" -> {
            String file = declared.file().endsWith(".ttf") ? declared.file() : declared.file() + ".ttf";

            Optional<byte[]> data;
            try {
               data = this.reader.apply(file);
            } catch (RuntimeException e) {
               data = Optional.empty();
            }

            TtfFont ttf = data.<TtfFont>map(bytes -> TtfFont.load(declared.name(), bytes)).orElse(null);
            UiVectorFont var11 = ttf != null && declared.lineHeight() > 0.0F && ttf.lineHeight() > 0.0F
               ? new ScaledVectorFont(ttf, declared.lineHeight() / ttf.lineHeight())
               : ttf;
            yield var11;
         }
         case "msdf" -> {
            MsdfFont msdf = new MsdfFont(declared.name(), declared.file(), path -> {
               try {
                  return this.reader.apply(path);
               } catch (RuntimeException e) {
                  return Optional.empty();
               }
            });
            MsdfFont var10 = msdf.has(65) ? msdf : null;
            yield var10;
         }
         default -> {
            yield null;
         }
      };
      if (font == null) {
         BedrockNative.LOGGER
            .info(
               "JSON UI font {} ({}, {}) is missing or unreadable; the bitmap font draws it", new Object[]{declared.name(), declared.format(), declared.file()}
            );
      }

      return Optional.ofNullable(font);
   }

   private record Declared(String name, String format, String file, float lineHeight) {
   }
}
