package net.bedrocknative.partner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class PartnerList {
   public static final int VERSION = 1;
   public static final int MAX_SERVERS = 8;
   static final int MAX_ELEMENTS = 64;
   static final int MAX_NAME = 32;
   static final int MAX_MESSAGE = 64;
   static final int MAX_DESCRIPTION = 600;
   static final int MAX_URL = 512;
   static final int DEFAULT_PORT = 19132;
   private static final Set<String> PRIVATE_SUFFIXES = Set.of(
      "localhost", "local", "lan", "internal", "intranet", "home", "corp", "home.arpa", "test", "invalid", "example", "onion", "localdomain"
   );
   private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");
   private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

   private PartnerList() {
   }

   public static PartnerList.Result parse(String json, Instant now, URI listUri, PartnerGuard guard) throws IOException {
      JsonElement root;
      try {
         JsonReader reader = new JsonReader(new StringReader(json));
         reader.setStrictness(Strictness.STRICT);
         root = JsonParser.parseReader(reader);
         if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw new IOException("text after the JSON document");
         }
      } catch (IOException | JsonParseException | IllegalStateException | StackOverflowError e) {
         throw new IOException("not JSON");
      }

      if (!(root instanceof JsonObject object)) {
         throw new IOException("the list is not a JSON object");
      } else {
         Integer version = integer(object.get("version"));
         if (version != null && version == 1) {
            if (!(object.get("servers") instanceof JsonArray array)) {
               throw new IOException("no \"servers\" array");
            } else {
               ArrayList var17 = new ArrayList();
               ArrayList<PartnerList.Indexed> kept = new ArrayList<>();
               String listHost = listUri.getHost() == null ? "" : listUri.getHost().toLowerCase(Locale.ROOT);

               for (int i = 0; i < array.size(); i++) {
                  if (i >= 64) {
                     var17.add("entries after the first 64 ignored");
                     break;
                  }

                  try {
                     PartnerList.Entry entry = entry(array.get(i), now, listHost, guard, var17, i);
                     if (entry != null) {
                        kept.add(new PartnerList.Indexed(i, entry));
                     }
                  } catch (IOException e) {
                     var17.add("entry " + i + " dropped: " + e.getMessage());
                  }
               }

               kept.sort(Comparator.<PartnerList.Indexed>comparingInt(indexedx -> indexedx.entry.order()).thenComparingInt(indexedx -> indexedx.index));
               List<PartnerList.Entry> entries = new ArrayList<>();

               for (PartnerList.Indexed indexed : kept) {
                  if (entries.size() >= 8) {
                     var17.add("entry " + indexed.index + " dropped: more than 8 servers");
                  } else {
                     entries.add(indexed.entry);
                  }
               }

               return new PartnerList.Result(entries, var17);
            }
         } else {
            throw new IOException("unsupported list version " + object.get("version") + " (this client reads 1)");
         }
      }
   }

   private static PartnerList.@Nullable Entry entry(JsonElement element, Instant now, String listHost, PartnerGuard guard, List<String> problems, int index) throws IOException {
      if (!(element instanceof JsonObject entry)) {
         throw new IOException("not an object");
      } else {
         String name = requiredText(entry, "name", 32);
         String address = address(requiredText(entry, "address", 253), guard.devLoopback());
         int port = 19132;
         if (entry.has("port")) {
            Integer value = integer(entry.get("port"));
            if (value == null || value < 1 || value > 65535) {
               throw new IOException("port must be a whole number from 1 to 65535");
            }

            port = value;
         }

         int order = Integer.MAX_VALUE;
         if (entry.has("order")) {
            Integer value = integer(entry.get("order"));
            if (value == null || value == Integer.MAX_VALUE) {
               throw new IOException("order must be a whole number");
            }

            order = value;
         }

         String message = optionalText(entry, "message", 64, false);
         String description = optionalText(entry, "description", 600, true);
         Instant starts = date(entry, "starts", false);
         Instant ends = date(entry, "ends", true);
         if (starts != null && now.isBefore(starts)) {
            problems.add("entry " + index + " (" + name + ") not shown: starts " + starts);
            return null;
         } else if (ends != null && !now.isBefore(ends)) {
            problems.add("entry " + index + " (" + name + ") not shown: ended " + ends);
            return null;
         } else {
            PartnerList.ImageRef icon = image(entry, "icon", PartnerImage.Kind.ICON, listHost, guard, problems, index);
            PartnerList.ImageRef banner = image(entry, "banner", PartnerImage.Kind.BANNER, listHost, guard, problems, index);
            return new PartnerList.Entry(name, address, port, order, message, description, icon, banner, starts, ends);
         }
      }
   }

   private static String requiredText(JsonObject entry, String key, int max) throws IOException {
      String text = optionalText(entry, key, max, false);
      if (text.isEmpty()) {
         throw new IOException(key + " is required");
      } else {
         return text;
      }
   }

   private static String optionalText(JsonObject entry, String key, int max, boolean lines) throws IOException {
      JsonElement value = entry.get(key);
      if (value != null && !value.isJsonNull()) {
         if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            String text = primitive.getAsString();
            if (lines) {
               text = text.replace("\r\n", " ").replace('\n', ' ');
            }

            text = text.strip();
            if (text.codePointCount(0, text.length()) > max) {
               throw new IOException(key + " longer than " + max + " characters");
            }

            for (int i = 0; i < text.length(); i++) {
               char c = text.charAt(i);
               if (Character.isISOControl(c) || c == 167 || c >= 8203 && c <= 8207 || c >= 8232 && c <= 8238 || c >= 8294 && c <= 8297 || c == '\ufeff') {
                  throw new IOException(key + " has a control, formatting or direction character");
               }
            }

            return text;
         } else {
            throw new IOException(key + " must be a string");
         }
      } else {
         return "";
      }
   }

   static @Nullable Integer integer(@Nullable JsonElement value) {
      if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
         try {
            BigDecimal number = primitive.getAsBigDecimal();
            return number.stripTrailingZeros().scale() <= 0 ? number.intValueExact() : null;
         } catch (ArithmeticException | NumberFormatException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static @Nullable Instant date(JsonObject entry, String key, boolean end) throws IOException {
      JsonElement value = entry.get(key);
      if (value != null && !value.isJsonNull()) {
         if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            String text = primitive.getAsString().strip();

            try {
               if (text.length() == 10) {
                  LocalDate day = LocalDate.parse(text);
                  return (end ? day.plusDays(1L) : day).atStartOfDay().toInstant(ZoneOffset.UTC);
               } else {
                  return OffsetDateTime.parse(text).toInstant();
               }
            } catch (DateTimeParseException e) {
               throw new IOException(key + " is not a date (YYYY-MM-DD or 2026-11-30T23:00:00+01:00)");
            }
         } else {
            throw new IOException(key + " must be a date string");
         }
      } else {
         return null;
      }
   }

   private static PartnerList.@Nullable ImageRef image(
      JsonObject entry, String key, PartnerImage.Kind kind, String listHost, PartnerGuard guard, List<String> problems, int index
   ) {
      JsonElement value = entry.get(key);
      if (value != null && !value.isJsonNull()) {
         try {
            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
               String text = primitive.getAsString().strip();
               if (text.isEmpty()) {
                  return null;
               }

               if (PartnerImage.isDataUri(text)) {
                  PartnerImage.decodeDataUri(text, kind);
                  return new PartnerList.ImageRef(text, true);
               }

               if (text.length() > 512) {
                  throw new IOException("URL longer than 512 characters");
               }

               URI uri;
               try {
                  uri = new URI(text);
               } catch (URISyntaxException e) {
                  throw new IOException("not a URL");
               }

               guard.checkForm(uri);
               if (!uri.getHost().toLowerCase(Locale.ROOT).equals(listHost)) {
                  throw new IOException("pictures by URL must be on the list's own host (" + listHost + ")");
               } else {
                  return new PartnerList.ImageRef(uri.toString(), false);
               }
            } else {
               throw new IOException("must be a string");
            }
         } catch (IOException e) {
            problems.add("entry " + index + " " + key + " dropped: " + e.getMessage());
            return null;
         }
      } else {
         return null;
      }
   }

   static String address(String raw) throws IOException {
      return address(raw, false);
   }

   static String address(String raw, boolean devLoopback) throws IOException {
      String address = raw.toLowerCase(Locale.ROOT);
      if (address.endsWith(".")) {
         address = address.substring(0, address.length() - 1);
      }

      if (address.startsWith("[") && address.endsWith("]")) {
         address = address.substring(1, address.length() - 1);
      }

      if (address.isEmpty() || address.length() > 253) {
         throw new IOException("address is empty or too long");
      }

      if (address.indexOf(58) < 0 && !IPV4.matcher(address).matches()) {
         String[] labels = address.split("\\.", -1);
         if (labels.length < 2) {
            throw new IOException("address needs a domain (play.example.net)");
         }

         for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
               throw new IOException("address has an invalid label \"" + label + "\"");
            }
         }

         if (labels[labels.length - 1].chars().allMatch(Character::isDigit)) {
            throw new IOException("address has a numeric top-level domain");
         }

         for (String suffix : PRIVATE_SUFFIXES) {
            if (address.equals(suffix) || address.endsWith("." + suffix)) {
               throw new IOException("address is a local-network name (." + suffix + ")");
            }
         }

         return address;
      } else {
         InetAddress literal;
         try {
            literal = InetAddress.ofLiteral(address);
         } catch (IllegalArgumentException e) {
            throw new IOException("address is not a valid IP literal");
         }

         if (PartnerGuard.isPublic(literal) || devLoopback && literal.isLoopbackAddress()) {
            return literal.getHostAddress();
         } else {
            throw new IOException("address is not a public IP");
         }
      }
   }

   public record Entry(
      String name,
      String address,
      int port,
      int order,
      String message,
      String description,
      PartnerList.@Nullable ImageRef icon,
      PartnerList.@Nullable ImageRef banner,
      @Nullable Instant starts,
      @Nullable Instant ends
   ) {
   }

   public record ImageRef(String value, boolean inline) {
   }

   private record Indexed(int index, PartnerList.Entry entry) {
   }

   public record Result(List<PartnerList.Entry> entries, List<String> problems) {
      public Result {
         entries = List.copyOf(entries);
         problems = List.copyOf(problems);
      }
   }
}
