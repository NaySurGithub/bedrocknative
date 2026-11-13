package net.bedrocknative.net.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;

public interface SavedAccount {
   boolean exists();

   BedrockAuthManager load() throws IOException;

   void save(BedrockAuthManager var1) throws IOException;

   String where();

   static SavedAccount file(final Path file) {
      return new SavedAccount() {
         @Override
         public boolean exists() {
            return Files.isRegularFile(file);
         }

         @Override
         public BedrockAuthManager load() throws IOException {
            return XboxAccount.load(file);
         }

         @Override
         public void save(BedrockAuthManager account) throws IOException {
            XboxAccount.save(account, file);
         }

         @Override
         public String where() {
            return file.toAbsolutePath().toString();
         }
      };
   }

   static SavedAccount active(final Path configFolder) {
      final XboxAccountStore store = new XboxAccountStore(configFolder, XboxAccountStore.standardVault());
      return new SavedAccount() {
         @Override
         public boolean exists() {
            return store.active().isPresent();
         }

         @Override
         public BedrockAuthManager load() throws IOException {
            Optional<XboxAccountStore.Entry> active = store.active();
            if (active.isEmpty()) {
               throw new NoSuchFileException("No saved Xbox account in " + configFolder.toAbsolutePath());
            }

            try {
               return store.load(active.get().id());
            } catch (XboxAccountStore.AccountException e) {
               throw new IOException(e.getMessage(), e);
            }
         }

         @Override
         public void save(BedrockAuthManager account) throws IOException {
            Optional<XboxAccountStore.Entry> active = store.active();
            if (active.isPresent()) {
               store.save(active.get().id(), account);
            } else {
               store.add(account, true);
            }
         }

         @Override
         public String where() {
            return "the game's Xbox accounts of "
               + configFolder.toAbsolutePath()
               + (store.vault() != null ? " (protected store " + store.vault().root() + ")" : "");
         }
      };
   }
}
