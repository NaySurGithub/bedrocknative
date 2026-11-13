package net.bedrocknative.content;

public final class UrlImageBudget {
   public static final int MAX_URLS = 128;
   public static final long MAX_PIXELS_PER_IMAGE = 4194304L;
   public static final long MAX_PIXELS_TOTAL = 33554432L;
   public static final long BASE_BYTES_PER_IMAGE = 2097152L;
   public static final long MAX_BYTES_PER_IMAGE = 16777216L;
   public static final long MAX_LARGE_EXTRA_BYTES = 67108864L;
   private final int maxUrls;
   private final long maxPixels;
   private final long maxLargeExtra;
   private int urls;
   private long pixels;
   private long largeExtra;

   public UrlImageBudget() {
      this(128, 33554432L, 67108864L);
   }

   UrlImageBudget(int maxUrls, long maxPixels) {
      this(maxUrls, maxPixels, 67108864L);
   }

   UrlImageBudget(int maxUrls, long maxPixels, long maxLargeExtra) {
      this.maxUrls = maxUrls;
      this.maxPixels = maxPixels;
      this.maxLargeExtra = maxLargeExtra;
   }

   public synchronized boolean tryStartUrl() {
      if (this.urls >= this.maxUrls) {
         return false;
      } else {
         this.urls++;
         return true;
      }
   }

   public synchronized boolean tryAddImage(long imagePixels) {
      if (imagePixels > 0L && imagePixels <= 4194304L && this.pixels + imagePixels <= this.maxPixels) {
         this.pixels += imagePixels;
         return true;
      } else {
         return false;
      }
   }

   public synchronized long bodyCap(int status, long declaredLength) {
      if (status / 100 == 2 && declaredLength > 2097152L && declaredLength <= 16777216L) {
         long extra = declaredLength - 2097152L;
         if (this.largeExtra + extra > this.maxLargeExtra) {
            return 2097152L;
         } else {
            this.largeExtra += extra;
            return declaredLength;
         }
      } else {
         return 2097152L;
      }
   }

   public synchronized long largeExtraBytes() {
      return this.largeExtra;
   }

   public synchronized void reset() {
      this.urls = 0;
      this.pixels = 0L;
      this.largeExtra = 0L;
   }
}
