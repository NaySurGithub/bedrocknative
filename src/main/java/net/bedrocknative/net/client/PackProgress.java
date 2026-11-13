package net.bedrocknative.net.client;

public record PackProgress(int pack, int packs, long bytes, long totalBytes, boolean finished) {
   public PackProgress(int pack, int packs, long bytes, long totalBytes) {
      this(pack, packs, bytes, totalBytes, false);
   }
}
