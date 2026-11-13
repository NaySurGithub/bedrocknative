package net.bedrocknative.net.client;

import java.util.List;
import java.util.UUID;

public record PackOffer(boolean serverRequired, List<PackOffer.Pack> packs) {
   public long totalBytes() {
      return this.packs.stream().mapToLong(PackOffer.Pack::size).sum();
   }

   public long requiredBytes() {
      return this.packs.stream().filter(PackOffer.Pack::required).mapToLong(PackOffer.Pack::size).sum();
   }

   public boolean anyRequired() {
      return this.packs.stream().anyMatch(PackOffer.Pack::required);
   }

   public boolean anyOptional() {
      return this.packs.stream().anyMatch(pack -> !pack.required());
   }

   public record Pack(UUID uuid, String version, long size, boolean required) {
   }
}
