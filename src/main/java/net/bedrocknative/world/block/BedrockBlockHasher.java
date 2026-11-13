package net.bedrocknative.world.block;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.SortedMap;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtUtils;

public final class BedrockBlockHasher {
   public static final int UNKNOWN_BLOCK_HASH = -2;

   private BedrockBlockHasher() {
   }

   public static int hash(String name, SortedMap<String, Object> states) {
      if (name.equals("minecraft:unknown")) {
         return -2;
      } else {
         NbtMapBuilder statesTag = NbtMap.builder();
         states.forEach((key, value) -> {
            switch (value) {
               case Byte b:
                  statesTag.putByte(key, b);
                  break;
               case Integer i:
                  statesTag.putInt(key, i);
                  break;
               case String s:
                  statesTag.putString(key, s);
                  break;
               default:
                  throw new IllegalArgumentException("Unsupported block state value " + value.getClass() + " for " + key);
            }
         });
         NbtMap tag = NbtMap.builder().putString("name", name).putCompound("states", statesTag.build()).build();
         ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);

         try (NBTOutputStream out = NbtUtils.createWriterLE(bytes)) {
            out.writeTag(tag);
         } catch (IOException var10) {
            throw new UncheckedIOException(var10);
         }

         return fnv1a32(bytes.toByteArray());
      }
   }

   static int fnv1a32(byte[] data) {
      int hash = -2128831035;

      for (byte b : data) {
         hash ^= b & 255;
         hash *= 16777619;
      }

      return hash;
   }
}
