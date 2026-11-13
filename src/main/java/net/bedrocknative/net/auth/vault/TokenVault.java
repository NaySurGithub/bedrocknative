package net.bedrocknative.net.auth.vault;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class TokenVault {
   public static final String PROPERTY = "bedrocknative.vault";
   static final byte[] MAGIC = new byte[]{66, 78, 84, 86};
   static final int FORMAT = 1;
   static final String EXTENSION = ".bnv";
   static final String BACKUP = ".migrated.json";
   static final int MAX_FILE = 1048576;
   private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,96}");
   private static final Pattern XUID = Pattern.compile("[0-9]{1,20}");
   private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
   private static final SecureRandom RANDOM = new SecureRandom();
   private final Path root;
   private final Protection protection;
   private final TokenVault.Freshness freshness;

   private TokenVault(Path root, Protection protection, TokenVault.Freshness freshness) {
      this.root = root.toAbsolutePath().normalize();
      this.protection = protection;
      this.freshness = freshness;
   }

   public static TokenVault standard(TokenVault.Freshness freshness) {
      return new TokenVault(defaultRoot(), Protection.standard(), freshness);
   }

   public static TokenVault at(Path root, Protection protection, TokenVault.Freshness freshness) {
      return new TokenVault(root, protection, freshness);
   }

   public static Path defaultRoot() {
      String override = System.getProperty("bedrocknative.vault");
      if (override != null && !override.isBlank()) {
         return Path.of(override);
      } else {
         String home = System.getProperty("user.home", ".");
         String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
         if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(home, "AppData", "Local");
            return base.resolve("BedrockNative").resolve("accounts");
         } else if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "BedrockNative", "accounts");
         } else {
            String data = System.getenv("XDG_DATA_HOME");
            return (data != null && !data.isBlank() ? Path.of(data) : Path.of(home, ".local", "share")).resolve("bedrocknative").resolve("accounts");
         }
      }
   }

   public static String nameFor(@Nullable String xuid, String title, String fallbackId) {
      String suffix = "ANDROID".equals(title) ? "" : "-" + title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
      if (xuid != null && XUID.matcher(xuid).matches()) {
         return "xuid-" + xuid + suffix;
      } else {
         String id = fallbackId.replaceAll("[^A-Za-z0-9]", "");
         return "local-" + (id.isEmpty() ? "account" : id.substring(0, Math.min(16, id.length()))) + "-" + HexFormat.of().formatHex(bytes(4));
      }
   }

   public static boolean validName(String name) {
      return NAME.matcher(name).matches();
   }

   public Path root() {
      return this.root;
   }

   public Path file(String name) {
      return this.root.resolve(checked(name) + ".bnv");
   }

   public boolean exists(String name) {
      return Files.isRegularFile(this.file(name));
   }

   public Optional<byte[]> read(String name) throws IOException {
      Path file = this.file(name);
      return this.locked(name, () -> this.readUnlocked(file));
   }

   public TokenVault.Write write(String name, byte[] content) throws IOException {
      Path file = this.file(name);
      return this.locked(name, () -> {
         if (Files.exists(file)) {
            byte[] existing = null;

            try {
               existing = this.readUnlocked(file).orElse(null);
            } catch (VaultUnreadableException var5) {
               this.moveAside(file);
            }

            if (existing != null && this.freshness.of(existing) > this.freshness.of(content)) {
               return new TokenVault.Write(false, existing);
            }
         }

         this.writeAtomically(file, this.seal(content));
         return new TokenVault.Write(true, (byte[])content.clone());
      });
   }

   public void delete(String name) throws IOException {
      Path file = this.file(name);
      this.locked(name, () -> {
         Files.deleteIfExists(file);
         Files.deleteIfExists(this.backupFile(name));
         return null;
      });
   }

   public void backup(String name, byte[] oldFile) throws IOException {
      Path backup = this.backupFile(name);
      this.locked(name, () -> {
         this.writeAtomically(backup, this.seal(oldFile));
         return null;
      });
   }

   public Optional<byte[]> readBackup(String name) throws IOException {
      Path backup = this.backupFile(name);
      return this.locked(name, () -> this.readUnlocked(backup));
   }

   public boolean hasBackup(String name) {
      return Files.isRegularFile(this.backupFile(name));
   }

   public void dropBackup(String name) throws IOException {
      Path backup = this.backupFile(name);
      this.locked(name, () -> Files.deleteIfExists(backup));
   }

   Path backupFile(String name) {
      return this.root.resolve(checked(name) + ".migrated.json");
   }

   private static String checked(String name) {
      if (!validName(name)) {
         throw new IllegalArgumentException("Not a vault name");
      } else {
         return name;
      }
   }

   private Optional<byte[]> readUnlocked(Path file) throws IOException {
      if (!Files.isRegularFile(file)) {
         return Optional.empty();
      } else {
         byte[] stored;
         try (InputStream in = Files.newInputStream(file)) {
            stored = in.readNBytes(1048577);
         }

         if (stored.length <= 1048576 && stored.length >= MAGIC.length + 2) {
            for (int i = 0; i < MAGIC.length; i++) {
               if (stored[i] != MAGIC[i]) {
                  throw new VaultUnreadableException("The saved sign-in file is not one of the vault's");
               }
            }

            if (stored[MAGIC.length] == 1 && stored[MAGIC.length + 1] == this.protection.kind()) {
               byte[] sealed = new byte[stored.length - MAGIC.length - 2];
               System.arraycopy(stored, MAGIC.length + 2, sealed, 0, sealed.length);
               return Optional.of(this.protection.open(sealed));
            } else {
               throw new VaultUnreadableException("The saved sign-in file has another format or protection");
            }
         } else {
            throw new VaultUnreadableException("The saved sign-in file is not one of the vault's");
         }
      }
   }

   private byte[] seal(byte[] content) throws IOException {
      byte[] sealed = this.protection.seal(content);
      byte[] stored = new byte[MAGIC.length + 2 + sealed.length];
      System.arraycopy(MAGIC, 0, stored, 0, MAGIC.length);
      stored[MAGIC.length] = 1;
      stored[MAGIC.length + 1] = (byte)this.protection.kind();
      System.arraycopy(sealed, 0, stored, MAGIC.length + 2, sealed.length);
      return stored;
   }

   private void moveAside(Path file) throws IOException {
      String name = file.getFileName().toString();
      String base = name.substring(0, name.length() - ".bnv".length());
      move(file, this.root.resolve(base + ".unreadable-" + System.currentTimeMillis() + ".bnv"));
   }

   private void writeAtomically(Path target, byte[] content) throws IOException {
      Path temp = this.root.resolve(target.getFileName() + "." + HexFormat.of().formatHex(bytes(6)) + ".tmp");

      try {
         Files.write(temp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
         this.protection.restrict(temp);
         move(temp, target);
      } finally {
         Files.deleteIfExists(temp);
      }
   }

   private static void move(Path from, Path to) throws IOException {
      int attempt = 0;

      while (true) {
         try {
            try {
               Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException var5) {
               Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }

            return;
         } catch (FileSystemException var7) {
            if (attempt >= 9 || !(var7 instanceof AccessDeniedException) && var7.getClass() != FileSystemException.class) {
               throw var7;
            }

            try {
               Thread.sleep(25L * (attempt + 1));
            } catch (InterruptedException var6) {
               Thread.currentThread().interrupt();
               throw var7;
            }

            attempt++;
         }
      }
   }

   private <T> T locked(String name, TokenVault.Action<T> action) throws IOException {
      Files.createDirectories(this.root);
      this.protection.restrict(this.root);
      Path lockFile = this.root.resolve(checked(name) + ".lock");
      ReentrantLock local = LOCKS.computeIfAbsent(lockFile, key -> new ReentrantLock());
      local.lock();

      Object var7;
      try (
         FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         FileLock ignored = channel.lock();
      ) {
         var7 = action.run();
      } finally {
         local.unlock();
      }

      return (T)var7;
   }

   private static byte[] bytes(int count) {
      byte[] bytes = new byte[count];
      RANDOM.nextBytes(bytes);
      return bytes;
   }

   @FunctionalInterface
   private interface Action<T> {
      T run() throws IOException;
   }

   @FunctionalInterface
   public interface Freshness {
      long of(byte[] var1);
   }

   public record Write(boolean ours, byte[] stored) {
   }
}
