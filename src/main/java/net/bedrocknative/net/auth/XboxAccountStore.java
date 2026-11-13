package net.bedrocknative.net.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.vault.TokenVault;
import net.bedrocknative.net.auth.vault.VaultUnreadableException;
import net.lenni0451.commons.httpclient.exceptions.HttpRequestException;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.msa.exception.MsaRequestException;
import net.raphimc.minecraftauth.util.http.exception.ApiHttpRequestException;
import org.jspecify.annotations.Nullable;

public final class XboxAccountStore {
   public static final String INDEX = "accounts.json";
   public static final String LEGACY = "account.json";
   public static final String VAULT_PREFIX = "vault:";
   private static final String FOLDER = "accounts";
   private static final int VERSION = 1;
   private final Path folder;
   @Nullable
   private final TokenVault vault;
   private final List<XboxAccountStore.Entry> entries = new ArrayList<>();
   private final Set<String> backupsOfThisRun = ConcurrentHashMap.newKeySet();
   @Nullable
   private String active;
   @Nullable
   private volatile XboxPrivileges privileges;
   private final Map<String, Long> tokenVersions = new HashMap<>();

   public static TokenVault standardVault() {
      return TokenVault.standard(XboxAccount::freshness);
   }

   public XboxAccountStore(Path folder) {
      this(folder, null);
   }

   public XboxAccountStore(Path folder, @Nullable TokenVault vault) {
      this.folder = folder;
      this.vault = vault;
      this.load();
      if (vault != null) {
         this.migrate();
      }
   }

   public Path folder() {
      return this.folder;
   }

   @Nullable
   public TokenVault vault() {
      return this.vault;
   }

   public void usePrivileges(@Nullable XboxPrivileges check) {
      this.privileges = check;
   }

   public static Path defaultAccountFile(Path configFolder) {
      return new XboxAccountStore(configFolder).activeFile().orElse(configFolder.resolve("account.json"));
   }

   public synchronized List<XboxAccountStore.Entry> accounts() {
      return List.copyOf(this.entries);
   }

   public synchronized Optional<XboxAccountStore.Entry> active() {
      return this.find(this.active);
   }

   public synchronized Optional<XboxAccountStore.Entry> find(@Nullable String id) {
      return this.entries.stream().filter(entry -> entry.id().equals(id)).findFirst();
   }

   public synchronized Optional<Path> activeFile() {
      return this.active().filter(entry -> entry.vaultName() == null).map(entry -> this.folder.resolve(entry.file()));
   }

   public synchronized void setActive(String id) {
      if (this.find(id).isEmpty()) {
         throw new IllegalArgumentException("No saved account " + id);
      } else {
         String previous = this.active;
         this.active = id;

         try {
            this.saveIndex();
         } catch (UncheckedIOException var4) {
            this.active = previous;
            throw var4;
         }
      }
   }

   public synchronized XboxAccountStore.Entry add(BedrockAuthManager account, boolean activate) throws IOException {
      String[] identity = identity(account);
      XboxAccountStore.Entry existing = identity[1] == null
         ? null
         : this.entries.stream().filter(entryx -> identity[1].equals(entryx.xuid())).findFirst().orElse(null);
      String id = existing != null ? existing.id() : UUID.randomUUID().toString().substring(0, 8);
      String title = XboxTitle.of(account).name();
      XboxAccountStore.Entry entry;
      if (existing != null) {
         entry = existing.withIdentity(identity[0], identity[1]);
      } else {
         entry = new XboxAccountStore.Entry(id, "accounts/" + id + ".json", identity[0], identity[1], title, System.currentTimeMillis());
      }

      XboxAccountStore.Entry plain = entry.vaultName() != null ? entry.withFile("accounts/" + id + ".json") : entry;
      String previousFile = existing == null ? null : existing.file();
      boolean wroteVault = false;
      String createdVaultName = null;
      if (this.vault != null) {
         String name = TokenVault.nameFor(identity[1], title, id);

         try {
            boolean existedBefore = this.vault.exists(name);
            this.vault.write(name, XboxAccount.toJson(account));
            entry = entry.withFile("vault:" + name);
            wroteVault = true;
            if (!existedBefore) {
               createdVaultName = name;
            }
         } catch (RuntimeException | IOException var20) {
            BedrockNative.LOGGER
               .warn("Couldn't put the Xbox sign-in in the protected store ({}); it stays in the game folder", var20.getClass().getSimpleName());
            entry = plain;
         }
      } else {
         entry = plain;
      }

      Path tokens = wroteVault ? null : this.folder.resolve(entry.file());
      if (tokens != null) {
         XboxAccount.save(account, tokens);
      }

      this.tokenVersions.merge(entry.id(), 1L, Long::sum);
      List<XboxAccountStore.Entry> previousEntries = List.copyOf(this.entries);
      String previousActive = this.active;
      if (existing != null) {
         this.entries.set(this.entries.indexOf(existing), entry);
      } else {
         this.entries.add(entry);
      }

      if (this.active == null || activate) {
         this.active = entry.id();
      }

      try {
         this.saveIndex();
      } catch (UncheckedIOException var21) {
         this.entries.clear();
         this.entries.addAll(previousEntries);
         this.active = previousActive;
         if (existing == null && tokens != null) {
            try {
               Files.deleteIfExists(tokens);
            } catch (IOException var18) {
            }
         }

         if (createdVaultName != null) {
            try {
               this.vault.delete(createdVaultName);
            } catch (RuntimeException | IOException var17) {
            }
         }

         throw var21.getCause();
      }

      if (wroteVault && previousFile != null && !previousFile.startsWith("vault:")) {
         try {
            Files.deleteIfExists(this.folder.resolve(previousFile));
         } catch (RuntimeException | IOException var19) {
            BedrockNative.LOGGER.warn("Couldn't delete the old Xbox sign-in file of the game folder ({})", var19.getClass().getSimpleName());
         }
      }

      this.forgetPrivileges(entry.id());
      return entry;
   }

   private void forgetPrivileges(String id) {
      XboxPrivileges check = this.privileges;
      if (check != null) {
         check.forget(id);
      }
   }

   public synchronized void remove(String id) throws IOException {
      XboxAccountStore.Entry entry = this.find(id).orElseThrow(() -> new IllegalArgumentException("No saved account " + id));
      this.tokenVersions.merge(id, 1L, Long::sum);
      List<XboxAccountStore.Entry> previousEntries = List.copyOf(this.entries);
      String previousActive = this.active;
      this.entries.remove(entry);
      if (id.equals(this.active)) {
         this.active = this.entries.isEmpty() ? null : this.entries.getFirst().id();
      }

      try {
         this.saveIndex();
      } catch (UncheckedIOException var6) {
         this.entries.clear();
         this.entries.addAll(previousEntries);
         this.active = previousActive;
         throw var6.getCause();
      }

      String name = entry.vaultName();
      if (name == null) {
         Files.deleteIfExists(this.folder.resolve(entry.file()));
      } else if (this.vault != null && TokenVault.validName(name)) {
         this.vault.delete(name);
      }

      this.forgetPrivileges(id);
   }

   public synchronized BedrockAuthManager load(String id) throws XboxAccountStore.AccountException {
      XboxAccountStore.Entry entry = this.find(id).orElseThrow(() -> new IllegalArgumentException("No saved account " + id));

      try {
         return XboxAccount.fromJson(this.readTokens(entry));
      } catch (VaultUnreadableException var4) {
         throw new XboxAccountStore.AccountException(
            XboxAccountStore.Problem.SIGN_IN_REQUIRED, entry, "The saved sign-in can't be opened by this Windows user on this PC", var4
         );
      } catch (RuntimeException | IOException var5) {
         throw new XboxAccountStore.AccountException(
            XboxAccountStore.Problem.SIGN_IN_REQUIRED, entry, "The saved sign-in can't be read (" + var5.getClass().getSimpleName() + ")", var5
         );
      }
   }

   private byte[] readTokens(XboxAccountStore.Entry entry) throws IOException {
      String name = entry.vaultName();
      if (name == null) {
         return Files.readAllBytes(this.folder.resolve(entry.file()));
      } else if (this.vault != null && TokenVault.validName(name)) {
         return this.vault.read(name).orElseThrow(() -> new NoSuchFileException("The protected store has no file for this sign-in"));
      } else {
         throw new IOException("This list keeps its sign-ins in the protected store, which this tool doesn't open");
      }
   }

   private synchronized long tokenVersion(String id) {
      return this.tokenVersions.getOrDefault(id, 0L);
   }

   private synchronized boolean saveIfUnchanged(String id, BedrockAuthManager account, long loadedAt) throws IOException {
      if (this.tokenVersion(id) != loadedAt) {
         BedrockNative.LOGGER.info("Xbox tokens renewed for an account that was signed in again meanwhile: not saved over the new ones");
         return false;
      } else {
         this.save(id, account);
         return true;
      }
   }

   public synchronized void save(String id, BedrockAuthManager account) throws IOException {
      XboxAccountStore.Entry entry = this.find(id).orElseThrow(() -> new IllegalArgumentException("No saved account " + id));
      String name = entry.vaultName();
      if (name == null) {
         XboxAccount.save(account, this.folder.resolve(entry.file()));
      } else {
         if (this.vault == null || !TokenVault.validName(name)) {
            throw new IOException("This list keeps its sign-ins in the protected store, which this tool doesn't open");
         }

         this.vault.write(name, XboxAccount.toJson(account));
      }

      String[] identity = identity(account);
      if (identity[1] != null && (!identity[0].equals(entry.gamertag()) || !identity[1].equals(entry.xuid()))) {
         this.entries.set(this.entries.indexOf(entry), entry.withIdentity(identity[0], identity[1]));
         this.saveIndexOrKeepInMemory();
      }
   }

   public XboxAccountStore.Prepared prepare(String id) throws XboxAccountStore.AccountException {
      return this.prepare(id, () -> {});
   }

   XboxAccountStore.Prepared prepare(String id, Runnable afterRenewal) throws XboxAccountStore.AccountException {
      BedrockAuthManager account;
      XboxAccountStore.Entry entry;
      long loadedAt;
      synchronized (this) {
         account = this.load(id);
         entry = this.find(id).orElseThrow();
         loadedAt = this.tokenVersion(id);
      }

      PrivilegeCheck started;
      try {
         account.getMinecraftMultiplayerToken().getUpToDate();
         afterRenewal.run();
         if (this.saveIfUnchanged(id, account, loadedAt)) {
            this.dropBackupAfterUse(entry);
         }

         XboxPrivileges check = this.privileges;
         started = check == null ? PrivilegeCheck.none() : check.start(id, entry, () -> {
            boolean renewing = XboxPrivileges.Tokens.renewalNeeded(account);
            XboxPrivileges.Tokens tokens = XboxPrivileges.Tokens.of(account);
            if (renewing) {
               this.saveQuietly(id, account, loadedAt);
            }

            return tokens;
         });
      } catch (RuntimeException | IOException var10) {
         throw classify(entry, var10);
      }

      return new XboxAccountStore.Prepared(account, started);
   }

   private void dropBackupAfterUse(XboxAccountStore.Entry entry) {
      String name = entry.vaultName();
      TokenVault store = this.vault;
      if (name != null && store != null && TokenVault.validName(name) && !this.backupsOfThisRun.contains(name) && store.hasBackup(name)) {
         try {
            store.dropBackup(name);
            BedrockNative.LOGGER.info("Xbox account {}: its protected copy works, the backup of the old file is deleted", entry.id());
         } catch (RuntimeException | IOException var5) {
            BedrockNative.LOGGER.warn("Couldn't delete the backup of an old Xbox sign-in file ({})", var5.getClass().getSimpleName());
         }
      }
   }

   private synchronized void migrate() {
      TokenVault store = this.vault;
      if (store != null) {
         int moved = 0;
         int left = 0;

         for (XboxAccountStore.Entry entry : List.copyOf(this.entries)) {
            if (entry.vaultName() == null) {
               String step = "read";

               try {
                  Path old = this.folder.resolve(entry.file());
                  byte[] plain = Files.readAllBytes(old);
                  step = "parse";
                  BedrockAuthManager account = XboxAccount.fromJson(plain);
                  String name = TokenVault.nameFor(identity(account)[1], XboxTitle.of(account).name(), entry.id());
                  step = "write";
                  TokenVault.Write written = store.write(name, plain);
                  step = "read back";
                  byte[] back = store.read(name).orElseThrow(() -> new NoSuchFileException("vault file"));
                  if (written.ours() ? !Arrays.equals(back, plain) : !Arrays.equals(back, written.stored())) {
                     throw new IOException("the vault copy differs from what was written");
                  }

                  XboxAccount.fromJson(back);
                  step = "backup";
                  store.backup(name, plain);
                  this.backupsOfThisRun.add(name);
                  step = "list";
                  XboxAccountStore.Entry movedEntry = entry.withFile("vault:" + name);
                  int index = this.entries.indexOf(entry);
                  this.entries.set(index, movedEntry);

                  try {
                     this.saveIndex();
                  } catch (UncheckedIOException var17) {
                     this.entries.set(index, entry);
                     throw var17.getCause();
                  }

                  step = "delete";
                  moved++;

                  try {
                     Files.delete(old);
                  } catch (IOException var16) {
                     BedrockNative.LOGGER
                        .warn(
                           "Xbox account {} is in the protected store; its old file could not be deleted yet ({})",
                           entry.id(),
                           var16.getClass().getSimpleName()
                        );
                  }
               } catch (RuntimeException | IOException var18) {
                  left++;
                  BedrockNative.LOGGER
                     .warn("Xbox account {} stays in the game folder for now (step {}: {})", new Object[]{entry.id(), step, var18.getClass().getSimpleName()});
               }
            }
         }

         int stale = this.deleteMovedLeftovers();
         long plainLeft = this.plainTokenFiles().size();
         long protectedCount = this.entries.stream().filter(entryx -> entryx.vaultName() != null).count();
         if (moved <= 0 && left <= 0 && stale <= 0 && plainLeft <= 0L) {
            BedrockNative.LOGGER.info("Xbox accounts: {} protected, 0 plaintext left", protectedCount);
         } else {
            BedrockNative.LOGGER
               .info(
                  "Xbox accounts: {} protected, {} plaintext left ({} moved now, {} left over deleted; store {})",
                  new Object[]{protectedCount, plainLeft, moved, stale, store.root()}
               );
         }
      }
   }

   private List<Path> plainTokenFiles() {
      List<Path> files = new ArrayList<>();
      Path legacy = this.folder.resolve("account.json");
      if (Files.isRegularFile(legacy)) {
         files.add(legacy);
      }

      Path accounts = this.folder.resolve("accounts");
      if (Files.isDirectory(accounts)) {
         try (Stream<Path> stream = Files.list(accounts)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path)).sorted().forEach(files::add);
         } catch (IOException var9) {
         }
      }

      return files;
   }

   private int deleteMovedLeftovers() {
      TokenVault store = this.vault;
      int deleted = 0;

      for (Path file : this.plainTokenFiles()) {
         String relative = this.folder.relativize(file).toString().replace('\\', '/');
         if (store != null && !this.entries.stream().anyMatch(entry -> entry.file().equals(relative))) {
            try {
               BedrockAuthManager account = XboxAccount.fromJson(Files.readAllBytes(file));
               String xuid = identity(account)[1];
               if (xuid != null) {
                  String name = TokenVault.nameFor(xuid, XboxTitle.of(account).name(), "");
                  boolean held = this.entries.stream().anyMatch(entry -> name.equals(entry.vaultName())) && store.read(name).isPresent();
                  if (held) {
                     Files.delete(file);
                     deleted++;
                  }
               }
            } catch (RuntimeException | IOException var10) {
            }
         }
      }

      return deleted;
   }

   private void saveQuietly(String id, BedrockAuthManager account, long loadedAt) {
      try {
         this.saveIfUnchanged(id, account, loadedAt);
      } catch (RuntimeException | IOException var6) {
         BedrockNative.LOGGER.warn("Couldn't save the Xbox tokens the multiplayer check renewed: {}", var6.getClass().getSimpleName());
      }
   }

   public XboxAccountStore.Prepared prepareActive() throws XboxAccountStore.AccountException {
      return this.prepare(this.activeId());
   }

   public BedrockAuthManager authenticate(String id) throws XboxAccountStore.AccountException {
      XboxAccountStore.Prepared prepared = this.prepare(id);
      XboxPrivileges.Result privilege = prepared.privileges().await();
      if (privilege.blocksJoining()) {
         throw prepared.privileges().refusal();
      } else {
         return prepared.account();
      }
   }

   public BedrockAuthManager authenticateActive() throws XboxAccountStore.AccountException {
      return this.authenticate(this.activeId());
   }

   private String activeId() throws XboxAccountStore.AccountException {
      return this.active()
         .orElseThrow(() -> new XboxAccountStore.AccountException(XboxAccountStore.Problem.NONE_ACTIVE, null, "No Xbox account is saved", null))
         .id();
   }

   static XboxAccountStore.AccountException classify(@Nullable XboxAccountStore.Entry entry, Throwable failure) {
      XboxProblem.Diagnosis diagnosis = XboxProblem.classify(failure, entry == null ? null : titleOf(entry));

      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
         if (cause instanceof MsaRequestException msa && !XboxProblem.temporaryOAuth(msa.getError())) {
            return new XboxAccountStore.AccountException(
               XboxAccountStore.Problem.SIGN_IN_REQUIRED, entry, "Microsoft refused the saved sign-in: " + msa.getError(), failure, diagnosis
            );
         }

         if (cause instanceof ApiHttpRequestException api) {
            return new XboxAccountStore.AccountException(
               XboxAccountStore.Problem.FAILED, entry, "Xbox or Minecraft services refused: " + api.getMessage(), failure, diagnosis
            );
         }

         if (cause instanceof HttpRequestException || cause instanceof SocketException || cause instanceof UnknownHostException) {
            return new XboxAccountStore.AccountException(
               XboxAccountStore.Problem.FAILED, entry, "Couldn't reach Microsoft: " + cause.getMessage(), failure, diagnosis
            );
         }
      }

      return new XboxAccountStore.AccountException(XboxAccountStore.Problem.FAILED, entry, String.valueOf(failure.getMessage()), failure, diagnosis);
   }

   @Nullable
   private static XboxTitle titleOf(XboxAccountStore.Entry entry) {
      try {
         return XboxTitle.valueOf(entry.title());
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   private static String[] identity(BedrockAuthManager account) {
      if (account.getMinecraftMultiplayerToken().hasValue()) {
         try {
            MinecraftMultiplayerToken token = account.getMinecraftMultiplayerToken().getCached();
            return new String[]{token.getDisplayName(), token.getXuid()};
         } catch (RuntimeException var2) {
         }
      }

      return new String[]{null, null};
   }

   private void load() {
      Path index = this.folder.resolve("accounts.json");
      if (Files.isRegularFile(index)) {
         try {
            this.read(index);
         } catch (RuntimeException | IOException var3) {
            quarantine(index);
            this.entries.clear();
            this.active = null;
            this.recover();
         }
      } else {
         this.recover();
      }
   }

   private void read(Path index) throws IOException {
      JsonObject json = JsonParser.parseString(Files.readString(index)).getAsJsonObject();

      for (JsonElement element : json.getAsJsonArray("accounts")) {
         JsonObject account = element.getAsJsonObject();
         this.entries
            .add(
               new XboxAccountStore.Entry(
                  account.get("id").getAsString(),
                  account.get("file").getAsString(),
                  string(account, "gamertag"),
                  string(account, "xuid"),
                  account.get("title").getAsString(),
                  account.get("added").getAsLong()
               )
            );
      }

      this.active = string(json, "active");
      if (this.active != null && this.find(this.active).isEmpty()) {
         this.active = this.entries.isEmpty() ? null : this.entries.getFirst().id();
      }
   }

   @Nullable
   private static String string(JsonObject json, String key) {
      JsonElement value = json.get(key);
      return value != null && !value.isJsonNull() ? value.getAsString() : null;
   }

   private void recover() {
      List<Path> files = new ArrayList<>();
      Path legacy = this.folder.resolve("account.json");
      if (Files.isRegularFile(legacy)) {
         files.add(legacy);
      }

      Path accounts = this.folder.resolve("accounts");
      if (Files.isDirectory(accounts)) {
         try (Stream<Path> stream = Files.list(accounts)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().forEach(files::add);
         } catch (IOException var13) {
         }
      }

      for (Path file : files) {
         try {
            BedrockAuthManager account = XboxAccount.load(file);
            String[] identity = identity(account);
            String relative = this.folder.relativize(file).toString().replace('\\', '/');
            String id = relative.equals("account.json") ? "default" : file.getFileName().toString().replace(".json", "");
            this.entries.add(new XboxAccountStore.Entry(id, relative, identity[0], identity[1], XboxTitle.of(account).name(), System.currentTimeMillis()));
         } catch (RuntimeException | IOException var10) {
         }
      }

      if (!this.entries.isEmpty()) {
         this.active = this.entries.getFirst().id();
         this.saveIndexOrKeepInMemory();
      }
   }

   private static void quarantine(Path index) {
      try {
         Files.move(index, index.resolveSibling("accounts.unreadable.json"), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var2) {
      }
   }

   private void saveIndexOrKeepInMemory() {
      try {
         this.saveIndex();
      } catch (UncheckedIOException var2) {
         BedrockNative.LOGGER.warn("Couldn't save the Xbox account list ({}); using it from memory", var2.getCause().getMessage());
      }
   }

   private void saveIndex() {
      JsonObject json = new JsonObject();
      json.addProperty("version", 1);
      json.addProperty("active", this.active);
      JsonArray accounts = new JsonArray();

      for (XboxAccountStore.Entry entry : this.entries) {
         JsonObject account = new JsonObject();
         account.addProperty("id", entry.id());
         account.addProperty("file", entry.file());
         account.addProperty("gamertag", entry.gamertag());
         account.addProperty("xuid", entry.xuid());
         account.addProperty("title", entry.title());
         account.addProperty("added", entry.added());
         accounts.add(account);
      }

      json.add("accounts", accounts);

      try {
         Files.createDirectories(this.folder);
         Path temp = this.folder.resolve("accounts.json.tmp");
         Files.writeString(temp, json.toString());
         Files.move(temp, this.folder.resolve("accounts.json"), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var6) {
         throw new UncheckedIOException(var6);
      }
   }

   public static final class AccountException extends Exception {
      private final XboxAccountStore.Problem problem;
      @Nullable
      private final XboxAccountStore.Entry account;
      @Nullable
      private final XboxProblem.Diagnosis diagnosis;

      public AccountException(XboxAccountStore.Problem problem, @Nullable XboxAccountStore.Entry account, String detail, @Nullable Throwable cause) {
         this(problem, account, detail, cause, null);
      }

      public AccountException(
         XboxAccountStore.Problem problem,
         @Nullable XboxAccountStore.Entry account,
         String detail,
         @Nullable Throwable cause,
         @Nullable XboxProblem.Diagnosis diagnosis
      ) {
         super(detail, cause);
         this.problem = problem;
         this.account = account;
         this.diagnosis = diagnosis;
      }

      public XboxAccountStore.Problem problem() {
         return this.problem;
      }

      @Nullable
      public XboxAccountStore.Entry account() {
         return this.account;
      }

      @Nullable
      public XboxProblem.Diagnosis diagnosis() {
         return this.diagnosis;
      }
   }

   public record Entry(String id, String file, @Nullable String gamertag, @Nullable String xuid, String title, long added) {
      public XboxAccountStore.Entry withIdentity(@Nullable String gamertag, @Nullable String xuid) {
         return new XboxAccountStore.Entry(this.id, this.file, gamertag, xuid, this.title, this.added);
      }

      public XboxAccountStore.Entry withFile(String newFile) {
         return new XboxAccountStore.Entry(this.id, newFile, this.gamertag, this.xuid, this.title, this.added);
      }

      @Nullable
      public String vaultName() {
         return this.file.startsWith("vault:") ? this.file.substring("vault:".length()) : null;
      }
   }

   public record Prepared(BedrockAuthManager account, PrivilegeCheck privileges) {
   }

   public static enum Problem {
      NONE_ACTIVE,
      SIGN_IN_REQUIRED,
      FAILED;
   }
}
