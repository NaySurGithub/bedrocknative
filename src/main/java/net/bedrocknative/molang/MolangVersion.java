package net.bedrocknative.molang;

import org.jspecify.annotations.Nullable;

public enum MolangVersion {
   BEFORE_VERSIONING,
   INITIAL,
   FIXED_ITEM_REMAINING_USE_DURATION_QUERY,
   EXPRESSION_ERROR_MESSAGES,
   UNEXPECTED_OPERATOR_ERRORS,
   CONDITIONAL_OPERATOR_ASSOCIATIVITY,
   COMPARISON_AND_LOGICAL_OPERATOR_PRECEDENCE,
   DIVIDE_BY_NEGATIVE_VALUE,
   FIXED_CAPE_FLAP_AMOUNT_QUERY,
   QUERY_BLOCK_PROPERTY_RENAMED_TO_STATE,
   DEPRECATE_OLD_BLOCK_QUERY_NAMES,
   DEPRECATED_SNIFFER_AND_CAMEL_QUERIES,
   LEAF_SUPPORTING_IN_FIRST_SOLID_BLOCK_BELOW,
   LATEST;

   private static final MolangVersion[] VALUES = values();

   public int protocolId() {
      return this.ordinal();
   }

   public boolean atLeast(MolangVersion version) {
      return this.ordinal() >= version.ordinal();
   }

   public static MolangVersion ofProtocol(int id) {
      return id >= 0 && id < VALUES.length ? VALUES[id] : LATEST;
   }

   public static MolangVersion ofEngineVersion(int engineVersion) {
      if (engineVersion <= 0) {
         return BEFORE_VERSIONING;
      } else if (engineVersion < version(1, 17, 30)) {
         return INITIAL;
      } else if (engineVersion < version(1, 17, 40)) {
         return FIXED_ITEM_REMAINING_USE_DURATION_QUERY;
      } else if (engineVersion < version(1, 18, 10)) {
         return UNEXPECTED_OPERATOR_ERRORS;
      } else if (engineVersion < version(1, 18, 20)) {
         return CONDITIONAL_OPERATOR_ASSOCIATIVITY;
      } else if (engineVersion < version(1, 19, 60)) {
         return COMPARISON_AND_LOGICAL_OPERATOR_PRECEDENCE;
      } else if (engineVersion < version(1, 20, 0)) {
         return DIVIDE_BY_NEGATIVE_VALUE;
      } else if (engineVersion < version(1, 20, 40)) {
         return FIXED_CAPE_FLAP_AMOUNT_QUERY;
      } else if (engineVersion < version(1, 20, 50)) {
         return QUERY_BLOCK_PROPERTY_RENAMED_TO_STATE;
      } else {
         return engineVersion < version(1, 20, 70) ? DEPRECATED_SNIFFER_AND_CAMEL_QUERIES : LATEST;
      }
   }

   public static MolangVersion ofEngineVersion(@Nullable String engineVersion) {
      return ofEngineVersion(parse(engineVersion));
   }

   public static int version(int major, int minor, int patch) {
      return major * 1000000 + minor * 1000 + patch;
   }

   public static int parse(@Nullable String text) {
      if (text != null && !text.isBlank()) {
         String[] parts = text.trim().split("\\.");
         int[] numbers = new int[3];

         for (int i = 0; i < Math.min(3, parts.length); i++) {
            try {
               numbers[i] = Math.min(999, Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException e) {
               return i == 0 ? 0 : version(numbers[0], numbers[1], numbers[2]);
            }
         }

         return version(numbers[0], numbers[1], numbers[2]);
      } else {
         return 0;
      }
   }
}
