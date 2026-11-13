package net.bedrocknative.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class QrCode {
   public static final int MAX_VERSION = 10;
   private static final int MODE_BYTE = 4;
   private static final int FORMAT_MASK = 21522;
   private static final int[] EXP = new int[512];
   private static final int[] LOG = new int[256];
   private final int version;
   private final int size;
   private final QrCode.Level level;
   private final int mask;
   private final boolean[][] modules;
   private static final boolean[] FINDER;

   private QrCode(int version, QrCode.Level level, int mask, boolean[][] modules) {
      this.version = version;
      this.size = modules.length;
      this.level = level;
      this.mask = mask;
      this.modules = modules;
   }

   public static QrCode encode(String text, QrCode.Level level) {
      return encode(text.getBytes(StandardCharsets.UTF_8), level);
   }

   public static QrCode encode(byte[] data, QrCode.Level level) {
      int version = 1;

      while (version <= 10 && bitsNeeded(data.length, version) > dataCodewords(version, level) * 8) {
         version++;
      }

      if (version > 10) {
         throw new IllegalArgumentException("Too long for a version 10 QR code at level " + level + ": " + data.length + " bytes");
      } else {
         int[] codewords = interleave(version, level, dataCodewordsOf(data, version, level));
         boolean[][] function = functionPatterns(version);
         boolean[][] base = new boolean[size(version)][size(version)];
         drawFunctionPatterns(base, version);
         placeData(base, function, codewords);
         int bestMask = 0;
         long bestPenalty = Long.MAX_VALUE;
         boolean[][] best = null;

         for (int candidate = 0; candidate < 8; candidate++) {
            boolean[][] masked = copy(base);
            applyMask(masked, function, candidate);
            drawFormat(masked, level, candidate);
            long penalty = penalty(masked);
            if (penalty < bestPenalty) {
               bestPenalty = penalty;
               bestMask = candidate;
               best = masked;
            }
         }

         return new QrCode(version, level, bestMask, best);
      }
   }

   public int version() {
      return this.version;
   }

   public int size() {
      return this.size;
   }

   public QrCode.Level level() {
      return this.level;
   }

   public int mask() {
      return this.mask;
   }

   public boolean dark(int x, int y) {
      return x >= 0 && y >= 0 && x < this.size && y < this.size && this.modules[y][x];
   }

   static int size(int version) {
      return version * 4 + 17;
   }

   private static int bitsNeeded(int bytes, int version) {
      return 4 + (version < 10 ? 8 : 16) + bytes * 8;
   }

   static int rawCodewords(int version) {
      boolean[][] function = functionPatterns(version);
      int free = 0;

      for (boolean[] row : function) {
         for (boolean used : row) {
            if (!used) {
               free++;
            }
         }
      }

      return free / 8;
   }

   static int dataCodewords(int version, QrCode.Level level) {
      return rawCodewords(version) - eccPerBlock(version, level) * blockCount(version, level);
   }

   static int eccPerBlock(int version, QrCode.Level level) {
      return level.eccPerBlock[version - 1];
   }

   static int blockCount(int version, QrCode.Level level) {
      return level.blocks[version - 1];
   }

   static int byteCapacity(int version, QrCode.Level level) {
      return (dataCodewords(version, level) * 8 - 4 - (version < 10 ? 8 : 16)) / 8;
   }

   private static int[] dataCodewordsOf(byte[] data, int version, QrCode.Level level) {
      int capacity = dataCodewords(version, level) * 8;
      QrCode.BitWriter bits = new QrCode.BitWriter();
      bits.write(4, 4);
      bits.write(data.length, version < 10 ? 8 : 16);

      for (byte value : data) {
         bits.write(value & 255, 8);
      }

      bits.write(0, Math.min(4, capacity - bits.length()));
      bits.write(0, (8 - bits.length() % 8) % 8);

      for (int pad = 236; bits.length() < capacity; pad ^= 253) {
         bits.write(pad, 8);
      }

      return bits.bytes();
   }

   static int[] interleave(int version, QrCode.Level level, int[] data) {
      int blockCount = level.blocks[version - 1];
      int eccLength = level.eccPerBlock[version - 1];
      int raw = rawCodewords(version);
      int shortBlocks = blockCount - raw % blockCount;
      int shortLength = raw / blockCount;
      List<int[]> dataBlocks = new ArrayList<>();
      List<int[]> eccBlocks = new ArrayList<>();
      int offset = 0;

      for (int i = 0; i < blockCount; i++) {
         int length = shortLength - eccLength + (i < shortBlocks ? 0 : 1);
         int[] block = new int[length];
         System.arraycopy(data, offset, block, 0, length);
         offset += length;
         dataBlocks.add(block);
         eccBlocks.add(reedSolomon(block, eccLength));
      }

      int[] result = new int[raw];
      int at = 0;

      for (int i = 0; i <= shortLength - eccLength; i++) {
         for (int[] block : dataBlocks) {
            if (i < block.length) {
               result[at++] = block[i];
            }
         }
      }

      for (int i = 0; i < eccLength; i++) {
         for (int[] blockx : eccBlocks) {
            result[at++] = blockx[i];
         }
      }

      return result;
   }

   static int[] reedSolomon(int[] data, int eccLength) {
      int[] generator = new int[eccLength + 1];
      generator[0] = 1;

      for (int root = 0; root < eccLength; root++) {
         for (int i = root + 1; i > 0; i--) {
            generator[i] ^= multiply(generator[i - 1], EXP[root]);
         }
      }

      int[] remainder = new int[eccLength];

      for (int value : data) {
         int factor = value ^ remainder[0];
         System.arraycopy(remainder, 1, remainder, 0, eccLength - 1);
         remainder[eccLength - 1] = 0;

         for (int i = 0; i < eccLength; i++) {
            remainder[i] ^= multiply(generator[i + 1], factor);
         }
      }

      return remainder;
   }

   static int multiply(int a, int b) {
      return a != 0 && b != 0 ? EXP[LOG[a] + LOG[b]] : 0;
   }

   static int power(int exponent) {
      return EXP[Math.floorMod(exponent, 255)];
   }

   static boolean[][] functionPatterns(int version) {
      int size = size(version);
      boolean[][] used = new boolean[size][size];

      for (int[] corner : new int[][]{{3, 3}, {size - 4, 3}, {3, size - 4}}) {
         for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
               mark(used, corner[0] + dx, corner[1] + dy);
            }
         }
      }

      for (int i = 0; i < size; i++) {
         mark(used, 6, i);
         mark(used, i, 6);
      }

      for (int[] centre : alignmentCentres(version)) {
         for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
               mark(used, centre[0] + dx, centre[1] + dy);
            }
         }
      }

      for (int i = 0; i < 9; i++) {
         mark(used, 8, i);
         mark(used, i, 8);
      }

      for (int i = 0; i < 8; i++) {
         mark(used, size - 1 - i, 8);
         mark(used, 8, size - 1 - i);
      }

      if (version >= 7) {
         for (int i = 0; i < 18; i++) {
            mark(used, size - 11 + i % 3, i / 3);
            mark(used, i / 3, size - 11 + i % 3);
         }
      }

      return used;
   }

   private static void mark(boolean[][] used, int x, int y) {
      if (x >= 0 && y >= 0 && x < used.length && y < used.length) {
         used[y][x] = true;
      }
   }

   static int[] alignmentPositions(int version) {
      if (version == 1) {
         return new int[0];
      } else {
         int count = version / 7 + 2;
         int step = (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2;
         int[] positions = new int[count];
         positions[0] = 6;
         int i = count - 1;

         for (int position = size(version) - 7; i >= 1; position -= step) {
            positions[i] = position;
            i--;
         }

         return positions;
      }
   }

   private static List<int[]> alignmentCentres(int version) {
      int[] positions = alignmentPositions(version);
      List<int[]> centres = new ArrayList<>();
      int last = positions.length - 1;

      for (int i = 0; i < positions.length; i++) {
         for (int j = 0; j < positions.length; j++) {
            boolean finder = i == 0 && j == 0 || i == 0 && j == last || i == last && j == 0;
            if (!finder) {
               centres.add(new int[]{positions[i], positions[j]});
            }
         }
      }

      return centres;
   }

   private static void drawFunctionPatterns(boolean[][] modules, int version) {
      int size = modules.length;

      for (int i = 0; i < size; i++) {
         modules[6][i] = i % 2 == 0;
         modules[i][6] = i % 2 == 0;
      }

      for (int[] corner : new int[][]{{3, 3}, {size - 4, 3}, {3, size - 4}}) {
         for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
               int x = corner[0] + dx;
               int y = corner[1] + dy;
               if (x >= 0 && y >= 0 && x < size && y < size) {
                  int ring = Math.max(Math.abs(dx), Math.abs(dy));
                  modules[y][x] = ring != 2 && ring != 4;
               }
            }
         }
      }

      for (int[] centre : alignmentCentres(version)) {
         for (int dy = -2; dy <= 2; dy++) {
            for (int dxx = -2; dxx <= 2; dxx++) {
               modules[centre[1] + dy][centre[0] + dxx] = Math.max(Math.abs(dxx), Math.abs(dy)) != 1;
            }
         }
      }

      if (version >= 7) {
         int bits = versionBits(version);

         for (int i = 0; i < 18; i++) {
            boolean dark = (bits >>> i & 1) != 0;
            int a = size - 11 + i % 3;
            int b = i / 3;
            modules[b][a] = dark;
            modules[a][b] = dark;
         }
      }
   }

   static int versionBits(int version) {
      int remainder = version;

      for (int i = 0; i < 12; i++) {
         remainder = remainder << 1 ^ (remainder >>> 11) * 7973;
      }

      return version << 12 | remainder & 4095;
   }

   static int formatBits(QrCode.Level level, int mask) {
      int data = level.bits << 3 | mask;
      int remainder = data;

      for (int i = 0; i < 10; i++) {
         remainder = remainder << 1 ^ (remainder >>> 9) * 1335;
      }

      return (data << 10 | remainder & 1023) ^ 21522;
   }

   private static void drawFormat(boolean[][] modules, QrCode.Level level, int mask) {
      int size = modules.length;
      int bits = formatBits(level, mask);

      for (int i = 0; i <= 5; i++) {
         modules[i][8] = bit(bits, i);
      }

      modules[7][8] = bit(bits, 6);
      modules[8][8] = bit(bits, 7);
      modules[8][7] = bit(bits, 8);

      for (int i = 9; i < 15; i++) {
         modules[8][14 - i] = bit(bits, i);
      }

      for (int i = 0; i < 8; i++) {
         modules[8][size - 1 - i] = bit(bits, i);
      }

      for (int i = 8; i < 15; i++) {
         modules[size - 15 + i][8] = bit(bits, i);
      }

      modules[size - 8][8] = true;
   }

   private static boolean bit(int value, int index) {
      return (value >>> index & 1) != 0;
   }

   private static void placeData(boolean[][] modules, boolean[][] function, int[] codewords) {
      int size = modules.length;
      int index = 0;
      int total = codewords.length * 8;

      for (int right = size - 1; right >= 1; right -= 2) {
         if (right == 6) {
            right = 5;
         }

         boolean upward = (right + 1 & 2) == 0;

         for (int step = 0; step < size; step++) {
            int y = upward ? size - 1 - step : step;

            for (int column = 0; column < 2; column++) {
               int x = right - column;
               if (!function[y][x] && index < total) {
                  modules[y][x] = (codewords[index >>> 3] >>> 7 - (index & 7) & 1) != 0;
                  index++;
               }
            }
         }
      }
   }

   static boolean masked(int mask, int x, int y) {
      return switch (mask) {
         case 0 -> (x + y) % 2 == 0;
         case 1 -> y % 2 == 0;
         case 2 -> x % 3 == 0;
         case 3 -> (x + y) % 3 == 0;
         case 4 -> (x / 3 + y / 2) % 2 == 0;
         case 5 -> x * y % 2 + x * y % 3 == 0;
         case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
         case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
         default -> throw new IllegalArgumentException("mask " + mask);
      };
   }

   private static void applyMask(boolean[][] modules, boolean[][] function, int mask) {
      for (int y = 0; y < modules.length; y++) {
         for (int x = 0; x < modules.length; x++) {
            if (!function[y][x] && masked(mask, x, y)) {
               modules[y][x] = !modules[y][x];
            }
         }
      }
   }

   private static boolean[][] copy(boolean[][] modules) {
      boolean[][] copy = new boolean[modules.length][];

      for (int i = 0; i < modules.length; i++) {
         copy[i] = (boolean[])modules[i].clone();
      }

      return copy;
   }

   static long penalty(boolean[][] modules) {
      int size = modules.length;
      long penalty = 0L;

      for (int line = 0; line < size; line++) {
         penalty += linePenalty(modules, line, true) + linePenalty(modules, line, false);
      }

      int dark = 0;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            if (modules[y][x]) {
               dark++;
            }

            if (x + 1 < size
               && y + 1 < size
               && modules[y][x] == modules[y][x + 1]
               && modules[y][x] == modules[y + 1][x]
               && modules[y][x] == modules[y + 1][x + 1]) {
               penalty += 3L;
            }
         }
      }

      int total = size * size;
      return penalty + 10L * (Math.abs(dark * 100 / total - 50) / 5);
   }

   private static long linePenalty(boolean[][] modules, int line, boolean row) {
      int size = modules.length;
      long penalty = 0L;
      int run = 0;
      boolean colour = false;

      for (int i = 0; i < size; i++) {
         boolean dark = row ? modules[line][i] : modules[i][line];
         if (i > 0 && dark == colour) {
            run++;
         } else {
            if (run >= 5) {
               penalty += run - 2;
            }

            run = 1;
            colour = dark;
         }
      }

      if (run >= 5) {
         penalty += run - 2;
      }

      for (int start = -4; start < size; start++) {
         if (finderLike(modules, line, row, start)) {
            penalty += 40L;
         }
      }

      return penalty;
   }

   private static boolean finderLike(boolean[][] modules, int line, boolean row, int start) {
      for (int i = 0; i < 7; i++) {
         if (at(modules, line, row, start + i) != FINDER[i]) {
            return false;
         }
      }

      return lightRun(modules, line, row, start - 4) || lightRun(modules, line, row, start + 7);
   }

   private static boolean lightRun(boolean[][] modules, int line, boolean row, int start) {
      for (int i = 0; i < 4; i++) {
         if (at(modules, line, row, start + i)) {
            return false;
         }
      }

      return true;
   }

   private static boolean at(boolean[][] modules, int line, boolean row, int index) {
      if (index >= 0 && index < modules.length) {
         return row ? modules[line][index] : modules[index][line];
      } else {
         return false;
      }
   }

   static {
      int value = 1;

      for (int i = 0; i < 255; i++) {
         EXP[i] = value;
         LOG[value] = i;
         value <<= 1;
         if (value >= 256) {
            value ^= 285;
         }
      }

      for (int ix = 255; ix < EXP.length; ix++) {
         EXP[ix] = EXP[ix - 255];
      }

      FINDER = new boolean[]{true, false, true, true, true, false, true};
   }

   private static final class BitWriter {
      private final List<Integer> bits = new ArrayList<>();

      void write(int value, int count) {
         for (int i = count - 1; i >= 0; i--) {
            this.bits.add(value >>> i & 1);
         }
      }

      int length() {
         return this.bits.size();
      }

      int[] bytes() {
         int[] bytes = new int[this.bits.size() / 8];

         for (int i = 0; i < this.bits.size(); i++) {
            bytes[i >>> 3] = bytes[i >>> 3] | this.bits.get(i) << 7 - (i & 7);
         }

         return bytes;
      }
   }

   public static enum Level {
      L(1, new int[]{7, 10, 15, 20, 26, 18, 20, 24, 30, 18}, new int[]{1, 1, 1, 1, 1, 2, 2, 2, 2, 4}),
      M(0, new int[]{10, 16, 26, 18, 24, 16, 18, 22, 22, 26}, new int[]{1, 1, 1, 2, 2, 4, 4, 4, 5, 5}),
      Q(3, new int[]{13, 22, 18, 26, 18, 24, 18, 22, 20, 24}, new int[]{1, 1, 2, 2, 4, 4, 6, 6, 8, 8}),
      H(2, new int[]{17, 28, 22, 16, 22, 28, 26, 26, 24, 28}, new int[]{1, 1, 2, 4, 4, 4, 5, 6, 8, 8});

      final int bits;
      private final int[] eccPerBlock;
      private final int[] blocks;

      private Level(int bits, int[] eccPerBlock, int[] blocks) {
         this.bits = bits;
         this.eccPerBlock = eccPerBlock;
         this.blocks = blocks;
      }
   }
}
