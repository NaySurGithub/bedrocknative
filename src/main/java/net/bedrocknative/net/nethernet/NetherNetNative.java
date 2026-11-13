package net.bedrocknative.net.nethernet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;
import tel.schich.libdatachannel.DataChannel;
import tel.schich.libdatachannel.DataChannelInitSettings;
import tel.schich.libdatachannel.DataChannelReliability;
import tel.schich.libdatachannel.GatheringState;
import tel.schich.libdatachannel.LibDataChannel;
import tel.schich.libdatachannel.PeerConnection;
import tel.schich.libdatachannel.PeerConnectionConfiguration;

public final class NetherNetNative {
   static final String LIBRARY = "datachannel-java";
   static final String PATH_PROPERTY = "libdatachannel.native.datachannel-java.path";
   @Nullable
   private static NetherNetNative.Status status;

   private NetherNetNative() {
   }

   public static synchronized NetherNetNative.Status load(@Nullable Path cacheFolder) {
      if (status != null) {
         return status;
      } else {
         long start = System.nanoTime();
         String version = version();
         String platform = platform();
         Path path = null;

         try {
            if (cacheFolder != null && System.getProperty("libdatachannel.native.datachannel-java.path") == null) {
               path = cachedCopy(cacheFolder.toAbsolutePath(), platform);
               if (path != null) {
                  System.setProperty("libdatachannel.native.datachannel-java.path", path.toString());
               }
            }

            LibDataChannel.initialize();
            status = new NetherNetNative.Status(true, version, platform, (System.nanoTime() - start) / 1000000L, null, path);
         } catch (RuntimeException | LinkageError | IOException var8) {
            String problem = var8.getClass().getSimpleName() + (var8.getMessage() == null ? "" : ": " + var8.getMessage());
            status = new NetherNetNative.Status(false, version, platform, (System.nanoTime() - start) / 1000000L, problem, path);
         }

         BedrockNative.LOGGER.info("{}{}", status.describe(), status.path() == null ? "" : " from " + status.path());
         return status;
      }
   }

   @Nullable
   public static synchronized NetherNetNative.Status status() {
      return status;
   }

   public static NetherNetNative.SelfCheck selfCheck(long timeoutMillis) throws Exception {
      long start = System.nanoTime();
      CountDownLatch gathered = new CountDownLatch(1);

      NetherNetNative.SelfCheck var17;
      try (PeerConnection peer = PeerConnection.createPeer(PeerConnectionConfiguration.DEFAULT.withMaxMessageSize(262144).withDisableAutoNegotiation(true))) {
         peer.onGatheringStateChange.register((connection, state) -> {
            if (state == GatheringState.RTC_GATHERING_COMPLETE) {
               gathered.countDown();
            }
         });
         DataChannel reliable = peer.createDataChannel("ReliableDataChannel", DataChannelInitSettings.DEFAULT);
         DataChannel unreliable = peer.createDataChannel(
            "UnreliableDataChannel", DataChannelInitSettings.DEFAULT.withReliability(new DataChannelReliability(true, true, 0L, 0))
         );
         peer.setLocalDescription("offer");
         if (!gathered.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("ICE gathering did not complete in " + timeoutMillis + " ms");
         }

         String offer = peer.localDescription();
         List<String> types = new ArrayList<>();

         for (String line : offer.split("\r\n|\n")) {
            if (line.startsWith("a=candidate:")) {
               int typ = line.indexOf(" typ ");
               types.add(typ < 0 ? "?" : line.substring(typ + 5).split(" ")[0]);
            }
         }

         reliable.close();
         unreliable.close();
         var17 = new NetherNetNative.SelfCheck(
            types.size(),
            String.join(",", types),
            offer.contains("m=application ") && offer.contains("webrtc-datachannel"),
            offer.contains("a=end-of-candidates"),
            (System.nanoTime() - start) / 1000000L
         );
      }

      return var17;
   }

   static String platform() {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
      String cpu = !arch.equals("aarch64") && !arch.equals("arm64")
         ? (!arch.contains("86") && !arch.contains("amd") ? arch : (arch.contains("64") ? "x86_64" : "x86_32"))
         : (os.contains("mac") ? "arm64" : "aarch64");
      String prefix = os.contains("win") ? "windows-" : (os.contains("mac") ? "macos-" : (os.contains("linux") ? "linux-" : ""));
      return prefix + cpu;
   }

   static String version() {
      try {
         URL classFile = LibDataChannel.class.getResource("LibDataChannel.class");
         if (classFile != null && classFile.toString().startsWith("jar:")) {
            String jar = classFile.toString().substring(0, classFile.toString().indexOf("!/") + 2);

            try (InputStream in = URI.create(jar + "META-INF/MANIFEST.MF").toURL().openStream()) {
               String version = new Manifest(in).getMainAttributes().getValue("Implementation-Version");
               if (version != null) {
                  return shortVersion(version);
               }

               return "unknown";
            }
         }
      } catch (RuntimeException | LinkageError | IOException var7) {
      }

      return "unknown";
   }

   static String shortVersion(String version) {
      String plain = version.replace("-SNAPSHOT", "");
      String[] parts = plain.split("\\.");
      return parts.length == 4 && parts[3].equals("0") ? parts[0] + "." + parts[1] + "." + parts[2] : plain;
   }

   @Nullable
   static Path cachedCopy(Path folder, String platform) throws IOException {
      String file = fileName();
      URL resource = LibDataChannel.class.getResource("/" + platform + "/native/" + file);
      if (resource == null) {
         resource = LibDataChannel.class.getResource("/native/" + file);
      }

      if (resource == null) {
         return null;
      } else {
         byte[] bytes;
         try (InputStream in = resource.openStream()) {
            bytes = in.readAllBytes();
         }

         String hash = sha256(bytes);
         int dot = file.lastIndexOf(46);
         String name = file.substring(0, dot) + "-" + hash.substring(0, 16) + file.substring(dot);
         Path cached = shared(folder, name, bytes, hash);
         if (cached != null) {
            return cached;
         } else {
            Path own = Files.createTempDirectory("bedrocknative-natives-");
            Path copy = own.resolve(name);
            Files.write(copy, bytes);
            if (!hasHash(copy, hash)) {
               throw new IOException("The copy of " + file + " in " + own + " does not have the expected SHA-256");
            } else {
               copy.toFile().deleteOnExit();
               own.toFile().deleteOnExit();
               BedrockNative.LOGGER.warn("NetherNet native folder {} is not usable; using a temporary copy in {}", folder, own);
               return copy;
            }
         }
      }
   }

   @Nullable
   private static Path shared(Path folder, String name, byte[] bytes, String hash) {
      Path target = folder.resolve(name);
      if (hasHash(target, hash)) {
         return target;
      } else {
         Path temporary = null;

         try {
            Files.createDirectories(folder);
            temporary = Files.createTempFile(folder, ".native-", ".tmp");
            Files.write(temporary, bytes);

            try {
               Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var16) {
               Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (RuntimeException | IOException var17) {
            BedrockNative.LOGGER.debug("NetherNet native cache {}: {}", folder, var17.toString());
         } finally {
            if (temporary != null) {
               try {
                  Files.deleteIfExists(temporary);
               } catch (IOException var15) {
               }
            }
         }

         return hasHash(target, hash) ? target : null;
      }
   }

   private static boolean hasHash(Path file, String hash) {
      try {
         return Files.isRegularFile(file) && sha256(Files.readAllBytes(file)).equals(hash);
      } catch (RuntimeException | IOException var3) {
         return false;
      }
   }

   private static String fileName() {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      return "libdatachannel-java" + (os.contains("win") ? ".dll" : (os.contains("mac") ? ".dylib" : ".so"));
   }

   private static String sha256(byte[] bytes) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      } catch (NoSuchAlgorithmException var2) {
         throw new IllegalStateException(var2);
      }
   }

   public record SelfCheck(int candidates, String types, boolean dataChannelSection, boolean endOfCandidates, long gatherMillis) {
   }

   public record Status(boolean loaded, String version, String platform, long millis, @Nullable String problem, @Nullable Path path) {
      public String describe() {
         return this.loaded
            ? "NetherNet native loaded (libdatachannel " + this.version + ", " + this.platform + ") in " + this.millis + " ms"
            : "NetherNet native NOT loaded (libdatachannel " + this.version + ", " + this.platform + "): " + this.problem;
      }
   }
}
