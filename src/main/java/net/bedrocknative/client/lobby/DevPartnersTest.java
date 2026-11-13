package net.bedrocknative.client.lobby;

import com.mojang.blaze3d.platform.Window;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.form.PackPromptScreen;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.oreui.FeaturedServers;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OrePlayLayout;
import net.bedrocknative.oreui.OrePlayModel;
import net.bedrocknative.partner.PartnerConsent;
import net.bedrocknative.partner.PartnerServers;
import net.bedrocknative.ui.PlayData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;
import org.jspecify.annotations.Nullable;

public final class DevPartnersTest {
   private static final int WIDTH = 1920;
   private static final int HEIGHT = 1080;
   private static final String BDS_NAME = "BN partner BDS";
   private final Minecraft minecraft;
   private final Path folder = LobbyPartners.folder();
   private final List<String> requests = new CopyOnWriteArrayList<>();
   private volatile String mode = "ok";
   @Nullable
   private HttpServer http;
   private int passed;
   private int failed;

   private DevPartnersTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start() {
      Thread.ofVirtual().name("Dev partners test").start(() -> new DevPartnersTest(DevOrePlayTest.awaitInstance()).main());
   }

   private DevOrePlayTest.Reporter reporter() {
      return new DevOrePlayTest.Reporter() {
         {
            Objects.requireNonNull(DevPartnersTest.this);
         }

         @Override
         public void check(String what, boolean ok) {
            DevPartnersTest.this.check(what, ok);
         }

         @Override
         public void screenshot(String name) {
            DevOrePlayTest.screenshot(DevPartnersTest.this.minecraft, name);
         }

         @Override
         public void log(String message, Object... args) {
            BedrockNative.LOGGER.info("Dev partners test: " + message, args);
         }
      };
   }

   private void main() {
      Path serversDat = this.minecraft.gameDirectory.toPath().resolve("servers.dat");
      Path serversOld = this.minecraft.gameDirectory.toPath().resolve("servers.dat_old");
      byte[] datBefore = DevOrePlayTest.read(serversDat);
      byte[] oldBefore = DevOrePlayTest.read(serversOld);
      Map<Path, byte[]> saved = this.backup();
      Window window = this.minecraft.getWindow();
      int guiScale = DevOrePlayTest.onClient(this.minecraft, () -> (Integer)this.minecraft.options.guiScale().get());
      int width = DevOrePlayTest.<Integer>onClient(this.minecraft, window::getScreenWidth);
      int height = DevOrePlayTest.<Integer>onClient(this.minecraft, window::getScreenHeight);

      try {
         this.run(serversDat, serversOld, datBefore, oldBefore);
      } catch (Throwable var15) {
         BedrockNative.LOGGER.error("Dev partners test: FAIL crashed", var15);
         this.failed++;
      } finally {
         if (this.http != null) {
            this.http.stop(0);
         }

         this.restore(saved);
         LobbyPartners.reset();
         boolean same = Arrays.equals(datBefore, DevOrePlayTest.read(serversDat)) && Arrays.equals(oldBefore, DevOrePlayTest.read(serversOld));
         this.check("servers.dat and servers.dat_old were never written (byte for byte as before, nothing put back)", same);
         if (!same) {
            DevOrePlayTest.restore(serversDat, datBefore);
            DevOrePlayTest.restore(serversOld, oldBefore);
         }

         this.check(
            "the partner files of config/bedrocknative are back as they were",
            this.backup().entrySet().stream().allMatch(entry -> Arrays.equals(entry.getValue(), saved.get(entry.getKey())))
               && this.backup().keySet().equals(saved.keySet())
         );
         DevOrePlayTest.onClient(this.minecraft, () -> {
            this.minecraft.options.guiScale().set(guiScale);
            window.setWindowed(width, height);
            return null;
         });
         BedrockNative.LOGGER.info("Dev partners test: SUMMARY {} passed, {} failed", this.passed, this.failed);
         BedrockNative.LOGGER.info("Dev partners test: done");
         DevOrePlayTest.sleep(500L);
         DevOrePlayTest.onClient(this.minecraft, () -> {
            this.minecraft.stop();
            return null;
         });
      }
   }

   private void run(Path serversDat, Path serversOld, @Nullable byte[] datBefore, @Nullable byte[] oldBefore) throws IOException {
      DevOrePlayTest.Reporter reporter = this.reporter();
      DevOrePlayTest.waitFor(
         this.minecraft,
         "the title screen",
         () -> DevOrePlayTest.onClient(
            this.minecraft,
            () -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() instanceof TitleScreen
         ),
         180000L,
         reporter
      );
      this.check("the install has Ore UI (" + OreInstall.defaultFolder() + ")", OreUi.available());
      if (OreUi.available()) {
         DevOrePlayTest.onClient(this.minecraft, () -> {
            this.minecraft.getWindow().setWindowed(1920, 1080);
            this.minecraft.options.guiScale().set(2);
            return null;
         });
         DevOrePlayTest.sleep(1500L);
         this.serve();
         String url = "http://127.0.0.1:" + this.http.getAddress().getPort() + "/list.json";
         reporter.log("site {}", url);
         this.clear();
         this.check("the built-in list URL is empty ()", "".isEmpty());
         OrePlayScreen play = this.open(reporter);
         if (play != null) {
            this.check("default: partner list off", this.state(play) == PartnerServers.Source.OFF);
            this.check("default: no consent popup", !DevOrePlayTest.onClient(this.minecraft, () -> play.ui().modalOpen()));
            this.check("default: no sponsored entry", this.sponsored(play) == 0);
            this.check("default: nothing fetched (" + this.requests + ")", this.requests.isEmpty());
            reporter.screenshot("partners-01-off");
            this.close(play);
            Files.writeString(this.folder.resolve("partner-servers.json"), "{\"url\": \"" + url + "\"}");
            LobbyPartners.reset();
            OrePlayScreen ask = this.open(reporter);
            if (ask != null) {
               DevOrePlayTest.waitFor(
                  this.minecraft, "the consent popup", () -> DevOrePlayTest.onClient(this.minecraft, () -> ask.ui().modalOpen()), 5000L, reporter
               );
               String title = DevOrePlayTest.onClient(this.minecraft, () -> ask.ui().modal() == null ? "" : ask.ui().modal().title());
               this.check(
                  "a URL and no answer: the consent popup shows (" + title + ")",
                  DevOrePlayTest.onClient(this.minecraft, () -> ask.ui().modalOpen()) && this.state(ask) == PartnerServers.Source.ASK
               );
               this.check(
                  "the popup names the host 127.0.0.1",
                  DevOrePlayTest.onClient(
                     this.minecraft,
                     () -> ask.ui().modalLayout() != null
                        && ask.ui().modalLayout().paragraphs().stream().flatMap(Collection::stream).anyMatch(line -> line.contains("127.0.0.1"))
                  )
               );
               this.check("nothing fetched before the answer", this.requests.isEmpty());
               reporter.screenshot("partners-02-consent");
               DevOrePlayTest.onClient(this.minecraft, () -> ask.keyPressed(new KeyEvent(41, 0, 0)));
               DevOrePlayTest.sleep(400L);
               this.check(
                  "Esc closes the popup without an answer",
                  !DevOrePlayTest.onClient(this.minecraft, () -> ask.ui().modalOpen())
                     && !Files.exists(this.folder.resolve("partner-servers-consent.json"))
                     && this.requests.isEmpty()
               );
               this.check("Esc on the popup stays on the Play screen", DevOrePlayTest.screen(this.minecraft) == ask);
               this.close(ask);
               OrePlayScreen refuse = this.open(reporter);
               if (refuse != null) {
                  DevOrePlayTest.waitFor(
                     this.minecraft, "the consent popup again", () -> DevOrePlayTest.onClient(this.minecraft, () -> refuse.ui().modalOpen()), 5000L, reporter
                  );
                  DevOrePlayTest.pressModalPlay(this.minecraft, refuse, 1, reporter);
                  this.check("Refuser stores a refusal", PartnerConsent.read(this.folder, "127.0.0.1") == PartnerConsent.Answer.REFUSED);
                  this.check(
                     "after Refuser: nothing fetched, nothing shown",
                     this.requests.isEmpty() && this.sponsored(refuse) == 0 && this.state(refuse) == PartnerServers.Source.REFUSED
                  );
                  reporter.screenshot("partners-03-refused");
                  this.close(refuse);
                  OrePlayScreen again = this.open(reporter);
                  if (again != null) {
                     this.check(
                        "refused: no popup on the next Play screen",
                        !DevOrePlayTest.onClient(this.minecraft, () -> again.ui().modalOpen()) && this.requests.isEmpty()
                     );
                     this.close(again);
                     Files.delete(this.folder.resolve("partner-servers-consent.json"));
                     LobbyPartners.reset();
                     OrePlayScreen accept = this.open(reporter);
                     if (accept != null) {
                        DevOrePlayTest.waitFor(
                           this.minecraft,
                           "the consent popup after deleting the answer",
                           () -> DevOrePlayTest.onClient(this.minecraft, () -> accept.ui().modalOpen()),
                           5000L,
                           reporter
                        );
                        DevOrePlayTest.pressModalPlay(this.minecraft, accept, 0, reporter);
                        DevOrePlayTest.waitFor(this.minecraft, "the fetched list", () -> this.state(accept) == PartnerServers.Source.FETCHED, 10000L, reporter);
                        DevOrePlayTest.sleep(600L);
                        this.check("Accepter stores the answer for 127.0.0.1", PartnerConsent.read(this.folder, "127.0.0.1") == PartnerConsent.Answer.ACCEPTED);
                        List<String> names = this.names(accept);
                        this.check(
                           "the list shows its two valid current entries in order, the expired and the malformed ones hidden (" + names + ")",
                           names.equals(List.of("Partner Alpha", "BN partner BDS"))
                        );
                        this.check(
                           "only plain GETs without query or cookie (" + this.requests + ")",
                           !this.requests.isEmpty()
                              && this.requests
                                 .stream()
                                 .allMatch(request -> request.startsWith("GET /") && !request.contains("?") && !request.contains("cookie"))
                        );
                        this.checkTags(accept, "fetched");
                        this.check(
                           "the first partner shows without a ping (not picked)",
                           DevOrePlayTest.onClient(
                              this.minecraft,
                              () -> accept.ui().frame() != null
                                 && accept.ui().frame().featured() != null
                                 && accept.ui().frame().featured().pingText().isEmpty()
                           )
                        );
                        this.check(
                           "servers.dat untouched by the list",
                           Arrays.equals(datBefore, DevOrePlayTest.read(serversDat)) && Arrays.equals(oldBefore, DevOrePlayTest.read(serversOld))
                        );
                        reporter.screenshot("partners-04-list");
                        this.close(accept);
                        this.mode = "down";
                        LobbyPartners.reset();
                        int before = this.requests.size();
                        OrePlayScreen down = this.open(reporter);
                        if (down != null) {
                           DevOrePlayTest.waitFor(this.minecraft, "the refresh against the site down", () -> this.requests.size() > before, 10000L, reporter);
                           DevOrePlayTest.sleep(1000L);
                           this.check(
                              "site down: the cached copy shows (" + this.state(down) + ", " + this.names(down) + ")",
                              this.state(down) == PartnerServers.Source.CACHED && this.names(down).equals(List.of("Partner Alpha", "BN partner BDS"))
                           );
                           this.checkTags(down, "cached");
                           reporter.screenshot("partners-05-cached");
                           this.close(down);
                           byte[] cache = Files.readAllBytes(this.folder.resolve("partner-servers-cache.json"));
                           this.mode = "malformed";
                           LobbyPartners.reset();
                           int before2 = this.requests.size();
                           OrePlayScreen malformed = this.open(reporter);
                           if (malformed != null) {
                              DevOrePlayTest.waitFor(
                                 this.minecraft, "the refresh against malformed JSON", () -> this.requests.size() > before2, 10000L, reporter
                              );
                              DevOrePlayTest.sleep(1000L);
                              this.check(
                                 "malformed JSON: ignored, the cached copy shows (" + this.state(malformed) + ")",
                                 this.state(malformed) == PartnerServers.Source.CACHED && this.names(malformed).size() == 2
                              );
                              this.check(
                                 "malformed JSON: the cache file is unchanged",
                                 Arrays.equals(cache, Files.readAllBytes(this.folder.resolve("partner-servers-cache.json")))
                              );
                              this.close(malformed);
                              Files.delete(this.folder.resolve("partner-servers.json"));
                              LobbyPartners.reset();
                              int before3 = this.requests.size();
                              OrePlayScreen off = this.open(reporter);
                              if (off != null) {
                                 DevOrePlayTest.sleep(1500L);
                                 this.check(
                                    "no URL again: off, no sponsored entry, nothing fetched",
                                    this.state(off) == PartnerServers.Source.OFF && this.sponsored(off) == 0 && this.requests.size() == before3
                                 );
                                 reporter.screenshot("partners-06-off-again");
                                 this.close(off);
                                 this.mode = "ok";
                                 Files.writeString(this.folder.resolve("partner-servers.json"), "{\"url\": \"" + url + "\"}");
                                 LobbyPartners.reset();
                                 OrePlayScreen join = this.open(reporter);
                                 if (join != null) {
                                    DevOrePlayTest.waitFor(
                                       this.minecraft,
                                       "the fetched list before joining",
                                       () -> this.state(join) == PartnerServers.Source.FETCHED,
                                       10000L,
                                       reporter
                                    );
                                    DevOrePlayTest.sleep(600L);
                                    int bds = this.names(join).indexOf("BN partner BDS");
                                    if (bds < 0) {
                                       this.check("the BDS card is listed", false);
                                    } else {
                                       DevOrePlayTest.press(this.minecraft, join, () -> join.ui().frame().widgets(), "featured:" + bds, reporter);
                                       Supplier<PlayData.State> pinged = () -> DevOrePlayTest.onClient(
                                          this.minecraft,
                                          () -> bds < join.model().featured().size()
                                             ? join.model().featured().get(bds).status().state()
                                             : PlayData.State.UNKNOWN
                                       );
                                       DevOrePlayTest.waitFor(
                                          this.minecraft,
                                          "the BDS card's ping answer after the pick",
                                          () -> pinged.get() == PlayData.State.ONLINE,
                                          8000L,
                                          reporter
                                       );
                                       this.check(
                                          "picking the card pings it, the BDS answers ("
                                             + pinged.get()
                                             + ", details \""
                                             + DevOrePlayTest.onClient(
                                                this.minecraft,
                                                () -> join.ui().frame() != null && join.ui().frame().featured() != null
                                                   ? join.ui().frame().featured().pingText()
                                                   : ""
                                             )
                                             + "\")",
                                          pinged.get() == PlayData.State.ONLINE
                                       );
                                       reporter.screenshot("partners-07-picked");
                                       DevOrePlayTest.press(this.minecraft, join, () -> join.ui().frame().widgets(), "play", reporter);
                                       long started = System.currentTimeMillis();
                                       boolean prompted = false;

                                       while (System.currentTimeMillis() - started < 120000L) {
                                          if (!prompted && DevOrePlayTest.screen(this.minecraft) instanceof PackPromptScreen prompt) {
                                             prompted = true;
                                             AbstractButton first = DevOrePlayTest.onClient(this.minecraft, () -> prompt.buttons().getFirst());
                                             DevOrePlayTest.onClient(this.minecraft, () -> first.keyPressed(new KeyEvent(40, 0, 0)));
                                          }

                                          if (DevOrePlayTest.onClient(this.minecraft, () -> this.minecraft.level != null && this.minecraft.player != null)) {
                                             break;
                                          }

                                          DevOrePlayTest.sleep(200L);
                                       }

                                       boolean joined = DevOrePlayTest.onClient(
                                          this.minecraft, () -> this.minecraft.level != null && this.minecraft.player != null
                                       );
                                       this.check("JOUER on the sponsored BDS card joins 127.0.0.1 (pack prompt " + prompted + ")", joined);
                                       DevOrePlayTest.sleep(4000L);
                                       reporter.screenshot("partners-08-joined");
                                       this.check(
                                          "servers.dat untouched by the sponsored join",
                                          Arrays.equals(datBefore, DevOrePlayTest.read(serversDat))
                                             && Arrays.equals(oldBefore, DevOrePlayTest.read(serversOld))
                                       );
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void serve() throws IOException {
      byte[] banner = png(600, 180, -13999076);
      byte[] icon = png(64, 64, -7556097);
      String today = LocalDate.now(ZoneOffset.UTC).toString();
      String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1L).toString();
      String list = "{\"version\": 1, \"servers\": [\n  {\"name\": \"%s\", \"address\": \"127.0.0.1\", \"port\": 19132, \"order\": 2, \"message\": \"Local test server\",\n   \"description\": \"The local BDS, listed as a sponsored partner by the dev test only.\"},\n  {\"name\": \"Partner Alpha\", \"address\": \"partner-alpha.example.com\", \"order\": 1, \"message\": \"Mini-jeux\",\n   \"description\": \"A made-up partner server (never pinged or joined by the test).\", \"icon\": \"data:image/png;base64,%s\",\n   \"banner\": \"http://127.0.0.1:%d/banner.png\", \"ends\": \"%s\"},\n  {\"name\": \"Expired\", \"address\": \"expired.example.com\", \"order\": 0, \"ends\": \"%s\"},\n  {\"name\": \"Malformed\", \"address\": \"not a host\", \"order\": 0},\n  {\"futureField\": true, \"name\": \"Bad port\", \"address\": \"badport.example.com\", \"port\": 0}\n]}";
      this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      String listText = list.formatted("BN partner BDS", Base64.getEncoder().encodeToString(icon), this.http.getAddress().getPort(), today, yesterday);
      this.http.createContext("/", exchange -> {
         String cookie = exchange.getRequestHeaders().containsKey("Cookie") ? " cookie" : "";
         this.requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + cookie);
         String path = exchange.getRequestURI().getPath();
         int status = 200;
         byte[] body;
         if (this.mode.equals("down")) {
            body = "down".getBytes(StandardCharsets.UTF_8);
            status = 503;
         } else if (path.equals("/list.json")) {
            body = (this.mode.equals("malformed") ? "{\"version\": 1, \"servers\": [" : listText).getBytes(StandardCharsets.UTF_8);
         } else if (path.equals("/banner.png")) {
            body = banner;
         } else {
            body = new byte[0];
            status = 404;
         }

         exchange.sendResponseHeaders(status, body.length == 0 ? -1L : body.length);

         try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
         }
      });
      this.http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      this.http.start();
   }

   private static byte[] png(int width, int height, int argb) throws IOException {
      BufferedImage image = new BufferedImage(width, height, 2);

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            image.setRGB(x, y, (x / 16 + y / 16) % 2 == 0 ? argb : -14803425);
         }
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
   }

   @Nullable
   private OrePlayScreen open(DevOrePlayTest.Reporter reporter) {
      OrePlayScreen play = DevOrePlayTest.open(this.minecraft, 2, reporter);
      if (play != null && DevOrePlayTest.onClient(this.minecraft, () -> play.ui().tab()) != 2) {
         DevOrePlayTest.onClient(this.minecraft, () -> {
            play.ui().openTab(2);
            return null;
         });
      }

      return play;
   }

   private void close(OrePlayScreen play) {
      DevOrePlayTest.onClient(this.minecraft, () -> {
         if (play.ui().modalOpen()) {
            play.ui().closeModal();
         }

         play.back();
         if (!(this.minecraft.gui.screen() instanceof TitleScreen)) {
            this.minecraft.gui.setScreen(new TitleScreen());
         }

         return null;
      });
      DevOrePlayTest.sleep(600L);
   }

   private PartnerServers.Source state(OrePlayScreen play) {
      return DevOrePlayTest.onClient(this.minecraft, () -> play.partnerState().source());
   }

   private int sponsored(OrePlayScreen play) {
      return DevOrePlayTest.onClient(this.minecraft, () -> (int)play.model().featured().stream().filter(featured -> featured.server().sponsored()).count());
   }

   private List<String> names(OrePlayScreen play) {
      return DevOrePlayTest.onClient(
         this.minecraft,
         () -> play.model()
            .featured()
            .stream()
            .map(OrePlayModel.Featured::server)
            .filter(FeaturedServers.Server::sponsored)
            .map(FeaturedServers.Server::name)
            .toList()
      );
   }

   private void checkTags(OrePlayScreen play, String when) {
      String expected = Language.getInstance().getOrDefault("bedrocknative.partner.sponsored");
      boolean ok = DevOrePlayTest.onClient(this.minecraft, () -> {
         OrePlayLayout.Frame frame = play.ui().frame();
         if (frame != null && !frame.sections().isEmpty()) {
            int tagged = 0;

            for (OrePlayLayout.ListItem item : frame.sections().getFirst().items()) {
               int index = OrePlayLayout.index(item.id());
               boolean sponsored = index >= 0 && index < play.model().featured().size() && play.model().featured().get(index).server().sponsored();
               if (sponsored != (item.tag() != null) || sponsored && !item.tag().text().equals(expected)) {
                  return false;
               }

               tagged += sponsored ? 1 : 0;
            }

            return tagged == this.names(play).size() && frame.featured() != null && !frame.featured().sponsorTags().isEmpty();
         } else {
            return false;
         }
      });
      this.check(when + ": every sponsored card carries \"" + expected + "\" (list and details)", ok);
   }

   private List<Path> partnerFiles() {
      List<Path> files = new ArrayList<>(
         List.of(
            this.folder.resolve("partner-servers.json"), this.folder.resolve("partner-servers-consent.json"), this.folder.resolve("partner-servers-cache.json")
         )
      );
      Path pictures = this.folder.resolve("partner-servers-pictures");
      if (Files.isDirectory(pictures)) {
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(pictures)) {
            stream.forEach(files::add);
         } catch (IOException var8) {
         }
      }

      return files;
   }

   private Map<Path, byte[]> backup() {
      Map<Path, byte[]> saved = new LinkedHashMap<>();

      for (Path file : this.partnerFiles()) {
         byte[] bytes = DevOrePlayTest.read(file);
         if (bytes != null) {
            saved.put(file, bytes);
         }
      }

      return saved;
   }

   private void clear() throws IOException {
      for (Path file : this.partnerFiles()) {
         Files.deleteIfExists(file);
      }
   }

   private void restore(Map<Path, byte[]> saved) {
      try {
         this.clear();

         for (Entry<Path, byte[]> entry : saved.entrySet()) {
            Files.createDirectories(entry.getKey().getParent());
            Files.write(entry.getKey(), entry.getValue());
         }
      } catch (IOException var4) {
         BedrockNative.LOGGER.error("Dev partners test: FAIL couldn't put the partner files back", var4);
         this.failed++;
      }
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev partners test: {} {}", ok ? "PASS" : "FAIL", what);
   }
}
