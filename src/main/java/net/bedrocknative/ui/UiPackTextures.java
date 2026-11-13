package net.bedrocknative.ui;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.content.PackStack;
import org.jspecify.annotations.Nullable;

public final class UiPackTextures implements UiTextures {
   private final PackStack stack;
   private final Map<String, Optional<UiTextureInfo>> cache = new ConcurrentHashMap<>();

   public UiPackTextures(PackStack stack) {
      this.stack = stack;
   }

   @Nullable
   @Override
   public UiTextureInfo info(String texture, String fileSystem) {
      return !texture.isEmpty() && (fileSystem.isEmpty() || fileSystem.equalsIgnoreCase("InUserPackage"))
         ? this.cache.computeIfAbsent(texture, this::load).orElse(null)
         : null;
   }

   private Optional<UiTextureInfo> load(String texture) {
      Optional<PackStack.Resource> resource = this.stack.texture(texture);
      if (resource.isEmpty()) {
         return Optional.empty();
      } else {
         int[] size = size(resource.get().data());
         if (size == null) {
            return Optional.empty();
         } else {
            String path = resource.get().path();
            int dot = path.lastIndexOf(46);
            String sidecarPath = (dot > path.lastIndexOf(47) ? path.substring(0, dot) : path) + ".json";
            JsonObject sidecar = this.stack.read(sidecarPath).map(file -> {
               try {
                  return UiJson.parseObject(file.data());
               } catch (UiJson.Error var2x) {
                  return null;
               }
            }).orElse(null);
            return Optional.of(UiTextureInfo.of(path, size[0], size[1], sidecar));
         }
      }
   }

   @Nullable
   public static int[] size(byte[] data) {
      if (data.length >= 24 && (data[0] & 255) == 137 && data[1] == 80 && data[2] == 78 && data[3] == 71) {
         return new int[]{int32(data, 16), int32(data, 20)};
      } else if (data.length >= 4 && (data[0] & 255) == 255 && (data[1] & 255) == 216) {
         return jpegSize(data);
      } else {
         if (data.length >= 18) {
            int type = data[2] & 255;
            if (type == 2 || type == 3 || type == 10 || type == 11) {
               int width = data[12] & 255 | (data[13] & 255) << 8;
               int height = data[14] & 255 | (data[15] & 255) << 8;
               return width > 0 && height > 0 ? new int[]{width, height} : null;
            }
         }

         return null;
      }
   }

   private static int int32(byte[] data, int offset) {
      return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
   }

   @Nullable
   private static int[] jpegSize(byte[] data) {
      int i = 2;

      while (i + 9 < data.length) {
         if ((data[i] & 255) != 255) {
            return null;
         }

         int marker = data[i + 1] & 255;
         int length = (data[i + 2] & 255) << 8 | data[i + 3] & 255;
         if (marker >= 192 && marker <= 207 && marker != 196 && marker != 200 && marker != 204) {
            int height = (data[i + 5] & 255) << 8 | data[i + 6] & 255;
            int width = (data[i + 7] & 255) << 8 | data[i + 8] & 255;
            return new int[]{width, height};
         }

         i += 2 + length;
      }

      return null;
   }
}
