package net.bedrocknative.client.lobby;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.account.AccountsScreen;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.oreui.OreKeys;
import net.bedrocknative.client.oreui.OreSession;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.client.world.VanillaRegistries;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.oreui.FeaturedServers;
import net.bedrocknative.oreui.OreKey;
import net.bedrocknative.oreui.OrePlayLayout;
import net.bedrocknative.oreui.OrePlayModel;
import net.bedrocknative.oreui.OrePlayUi;
import net.bedrocknative.partner.PartnerServers;
import net.bedrocknative.ui.PlayData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.LevelSummary.SymlinkLevelSummary;
import org.jspecify.annotations.Nullable;

public final class OrePlayScreen extends Screen implements LobbyScreen, OrePlayUi.Actions {
   private static final int PING_EVERY_TICKS = 300;
   private static final int FEATURED_PING_TIMEOUT_MS = 2500;
   private final Screen parent;
   private final OreSession session;
   private final OrePlayModel model = new OrePlayModel();
   private final OrePlayUi ui;
   private final LobbyServers servers;
   private List<LevelSummary> levels = List.of();
   private List<FeaturedServers.Server> sponsored = List.of();
   private List<FeaturedServers.Server> ownFeatured = List.of();
   private final Map<String, PlayData.Status> featuredStatus = new HashMap<>();
   private final Set<String> pickedPartners = new HashSet<>();
   private PartnerServers.Snapshot partnerState = new PartnerServers.Snapshot(PartnerServers.Source.OFF, null, List.of(), null, List.of());
   private boolean consentAsked;
   private boolean hidden;
   private int ticks;
   @Nullable
   private static String lastSelected;
   private final DiscordButton discord = new DiscordButton(true, true);

   private OrePlayScreen(Screen parent, OreSession session, int tab) {
      super(Component.translatable("menu.play"));
      this.parent = parent;
      this.session = session;
      this.servers = new LobbyServers(Minecraft.getInstance());
      this.ui = new OrePlayUi(
         session.text(), session.lang(), session.theme(), session.vars(), this.model, tab, lastSelected, session.defaultWorldPicture(), this
      );
   }

   @Nullable
   public static OrePlayScreen create(Screen parent, int tab) {
      OreSession session = OreUi.session();
      if (session == null) {
         return null;
      } else {
         OrePlayScreen screen = new OrePlayScreen(parent, session, tab);
         screen.loadPartners();
         screen.refreshServers();
         screen.loadWorlds();
         return screen;
      }
   }

   public OrePlayUi ui() {
      return this.ui;
   }

   public OrePlayModel model() {
      return this.model;
   }

   public int serverCount() {
      return this.servers.size();
   }

   public ServerData server(int index) {
      return this.servers.get(index);
   }

   private void refreshServers() {
      this.model.servers(this.servers.rows());
   }

   private void loadWorlds() {
      LobbyWorlds.load(this.minecraft).thenAcceptAsync(loaded -> {
         this.levels = (List<LevelSummary>)loaded;
         this.showWorlds();
      }, this.minecraft);
   }

   private void showWorlds() {
      List<OrePlayModel.World> worlds = new ArrayList<>();
      List<PlayData.World> rows = LobbyWorlds.rows(this.minecraft, this.levels, this::showWorlds);

      for (int i = 0; i < rows.size() && i < this.levels.size(); i++) {
         PlayData.World row = rows.get(i);
         LevelSummary level = this.levels.get(i);
         Path icon = level.getIcon();
         worlds.add(
            new OrePlayModel.World(
               row.id(), row.name(), row.date(), modeTag(level), row.size(), icon != null && Files.isRegularFile(icon) ? icon : null, row.locked()
            )
         );
      }

      this.model.worlds(worlds, true);
   }

   private static String modeTag(@Nullable LevelSummary level) {
      if (level == null) {
         return "hbui.WorldTag.unknown";
      } else if (level.isHardcore()) {
         return "hbui.WorldTag.hardcore";
      } else {
         return switch (level.getGameMode()) {
            case SURVIVAL -> "hbui.WorldTag.survival";
            case CREATIVE -> "hbui.WorldTag.creative";
            case ADVENTURE -> "hbui.WorldTag.adventure";
            default -> "hbui.WorldTag.unknown";
         };
      }
   }

   private void loadPartners() {
      this.ownFeatured = FeaturedServers.load(LobbyPartners.folder());
      PartnerServers service = LobbyPartners.service();
      this.applyPartners(service.state());
      if (this.accepted()) {
         this.refreshPartners();
      }
   }

   private boolean accepted() {
      return this.partnerState.source() == PartnerServers.Source.FETCHED
         || this.partnerState.source() == PartnerServers.Source.CACHED
         || this.partnerState.source() == PartnerServers.Source.UNAVAILABLE;
   }

   private void refreshPartners() {
      LobbyPartners.service().refreshAsync().thenAcceptAsync(this::applyPartners, this.minecraft).exceptionally(e -> {
         BedrockNative.LOGGER.warn("Partner servers: refresh failed", e);
         return null;
      });
   }

   private void applyPartners(PartnerServers.Snapshot snapshot) {
      this.partnerState = snapshot;
      this.sponsored = snapshot.servers();
      this.showPartners();
   }

   private List<FeaturedServers.Server> featuredServers() {
      List<FeaturedServers.Server> all = new ArrayList<>(this.sponsored);
      all.addAll(this.ownFeatured);
      return all;
   }

   private static String key(FeaturedServers.Server server) {
      return server.address() + ":" + server.port();
   }

   private void showPartners() {
      List<OrePlayModel.Featured> featured = new ArrayList<>();

      for (FeaturedServers.Server server : this.featuredServers()) {
         PlayData.Status status = this.featuredStatus.get(key(server));
         if (status == null) {
            status = server.sponsored() && !this.pickedPartners.contains(key(server)) ? PlayData.Status.UNKNOWN : PlayData.Status.PINGING;
         }

         featured.add(new OrePlayModel.Featured(server, status));
      }

      this.model.featured(featured);
   }

   private void pingPartners() {
      for (FeaturedServers.Server server : this.featuredServers()) {
         if (!server.sponsored() || this.pickedPartners.contains(key(server))) {
            this.ping(server);
         }
      }
   }

   private void ping(FeaturedServers.Server server) {
      String key = key(server);
      Thread.ofVirtual().name("Ore UI featured ping").start(() -> {
         PlayData.Status status;
         try {
            status = LobbyStatusProbe.bedrockServer(server.address(), server.port(), 2500);
         } catch (RuntimeException var5) {
            status = PlayData.Status.OFFLINE;
         }

         PlayData.Status result = status;
         this.minecraft.execute(() -> {
            this.featuredStatus.put(key, result);
            this.showPartners();
         });
      });
   }

   private void maybeAskConsent() {
      if (!this.consentAsked
         && this.ui.tab() == 2
         && this.partnerState.source() == PartnerServers.Source.ASK
         && this.partnerState.host() != null
         && !this.ui.modalOpen()) {
         this.consentAsked = true;
         this.ui.askPartnerConsent(this.partnerState.host(), () -> this.answerConsent(true), () -> this.answerConsent(false));
      }
   }

   private void answerConsent(boolean accept) {
      PartnerServers service = LobbyPartners.service();

      try {
         service.answer(accept);
      } catch (IOException var4) {
         BedrockNative.LOGGER.warn("Partner servers: couldn't store the answer ({}); asked again next time", var4.getMessage());
      }

      this.applyPartners(service.state());
      if (accept) {
         this.refreshPartners();
      }
   }

   public PartnerServers.Snapshot partnerState() {
      return this.partnerState;
   }

   @Override
   public void selected(@Nullable String id) {
      if (id != null && id.startsWith("featured:")) {
         List<FeaturedServers.Server> all = this.featuredServers();
         int index = OrePlayLayout.index(id);
         if (index >= 0 && index < all.size() && all.get(index).sponsored() && this.pickedPartners.add(key(all.get(index)))) {
            this.showPartners();
            this.ping(all.get(index));
         }
      }
   }

   protected void init() {
      this.ui.size(OreSession.windowWidth(), OreSession.windowHeight());
      if (this.hidden) {
         this.hidden = false;
         this.refreshServers();
      }

      this.servers.pingAll(this::refreshServers);
      this.pingPartners();
      this.servers.startDiscovery(this.model::lan);
      this.maybeAskConsent();
      preloadRegistries(this.ui.tab());
   }

   public void tick() {
      if (++this.ticks % 300 == 0) {
         this.servers.pingAll(this::refreshServers);
         this.pingPartners();
      }
   }

   public void removed() {
      this.hidden = true;
      this.servers.stopDiscovery();
      lastSelected = this.ui.selected();
      super.removed();
   }

   private void release() {
      this.session.release();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      BedrockPanorama.prepare(this.minecraft);
      this.extractPanorama(graphics, a);
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      this.ui.text(this.session.text());
      this.ui.size(OreSession.windowWidth(), OreSession.windowHeight());

      try (OreSession.Painter painter = this.session.paint(graphics)) {
         this.ui.render(painter.painter(), System.currentTimeMillis());
      } catch (RuntimeException var10) {
         this.fallBack("play screen", var10);
      }

      this.discord.render(graphics, this.width, this.height, mouseX, mouseY);
   }

   void fallBack(String what, RuntimeException e) {
      OreUi.fail(what, e);
      int tab = this.ui.tab();
      this.release();
      this.minecraft
         .execute(() -> this.minecraft.gui.setScreen((Screen)(tab == 0 ? new SelectWorldScreen(this.parent) : new JoinMultiplayerScreen(this.parent))));
   }

   private static double window(double gui) {
      return gui * OreSession.guiScale();
   }

   public void mouseMoved(double x, double y) {
      this.ui.mouseMoved(window(x), window(y));
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() != 1) {
         return false;
      } else if (this.discord.clicked(event.x(), event.y())) {
         return true;
      } else {
         this.ui.mousePressed(window(event.x()), window(event.y()));
         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (event.button() != 1) {
         return false;
      } else {
         this.ui.mouseReleased(window(event.x()), window(event.y()));
         return true;
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      if (event.button() != 1) {
         return false;
      } else {
         this.ui.mouseDragged(window(event.x()), window(event.y()));
         return true;
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.ui.scrolled(window(x), window(y), scrollY);
   }

   public boolean keyPressed(KeyEvent event) {
      OreKey key = OreKeys.of(event);
      return key != null && this.ui.key(key, event.hasShiftDown());
   }

   public boolean charTyped(CharacterEvent event) {
      return false;
   }

   public void onClose() {
      this.back();
   }

   @Override
   public void back() {
      this.release();
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void tabChanged(int tab) {
      BedrockLobby.lastTab(tab);
      this.maybeAskConsent();
      preloadRegistries(tab);
   }

   private static void preloadRegistries(int tab) {
      if (tab != 0) {
         VanillaRegistries.get(Minecraft.getInstance());
      }
   }

   @Override
   public void notice(String what) {
      String key = switch (what) {
         case "social" -> "bedrocknative.ore.notice.social";
         case "import" -> "bedrocknative.ore.notice.import";
         default -> "bedrocknative.ore.notice.realms";
      };
      SystemToast.addOrUpdate(
         this.minecraft.gui.toastManager(), SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Minecraft"), Component.translatable(key)
      );
   }

   @Override
   public void createWorld() {
      VanillaRegistries.settle();
      CreateWorldScreen.openFresh(this.minecraft, () -> {
         this.minecraft.gui.setScreen(this);
         this.loadWorlds();
      });
   }

   @Override
   public void playWorld(int index) {
      if (index >= 0 && index < this.levels.size()) {
         LevelSummary level = this.levels.get(index);
         if (level.primaryActionActive()) {
            BedrockTextures.releaseFiles();
            if (level instanceof SymlinkLevelSummary) {
               this.minecraft.gui.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.gui.setScreen(this)));
            } else {
               this.release();
               VanillaRegistries.settle();
               this.minecraft.createWorldOpenFlows().openWorld(level.getLevelId(), () -> this.minecraft.gui.setScreen(this));
            }
         }
      }
   }

   @Override
   public void editWorld(int index) {
      if (index >= 0 && index < this.levels.size()) {
         String levelId = this.levels.get(index).getLevelId();

         LevelStorageAccess access;
         try {
            access = this.minecraft.getLevelSource().validateAndCreateAccess(levelId);
         } catch (Exception var6) {
            SystemToast.onWorldAccessFailure(this.minecraft, levelId);
            BedrockNative.LOGGER.warn("Ore UI play screen: couldn't open world {} to edit it", levelId, var6);
            return;
         }

         try {
            this.minecraft.gui.setScreen(EditWorldScreen.create(this.minecraft, access, result -> {
               access.safeClose();
               LobbyWorlds.forgetSizes();
               this.session.forget(this.levels.get(index).getIcon());
               this.minecraft.gui.setScreen(this);
               this.loadWorlds();
            }));
         } catch (Exception var5) {
            access.safeClose();
            SystemToast.onWorldAccessFailure(this.minecraft, levelId);
            BedrockNative.LOGGER.warn("Ore UI play screen: couldn't read world {} to edit it", levelId, var5);
         }
      }
   }

   @Override
   public void joinLan(int index) {
      List<PlayData.Lan> games = this.model.lan();
      if (index >= 0 && index < games.size()) {
         PlayData.Lan game = games.get(index);
         this.join(new ServerData(game.name(), game.address(), Type.LAN));
      }
   }

   @Override
   public void addServer() {
      this.minecraft.gui.setScreen(OreServerFormScreen.add(this, this.session));
   }

   @Override
   public void profile() {
      this.minecraft.gui.setScreen(new AccountsScreen(this));
   }

   @Override
   public void editServer(int index) {
      if (index >= 0 && index < this.servers.size()) {
         ServerData server = this.servers.get(index);
         this.minecraft.gui.setScreen(OreServerFormScreen.edit(this, this.session, index, server.name, BedrockAddress.parse(server.ip)));
      }
   }

   @Override
   public void joinServer(int index) {
      if (index >= 0 && index < this.servers.size()) {
         this.join(this.servers.get(index));
      }
   }

   @Override
   public void joinFeatured(int index) {
      List<FeaturedServers.Server> all = this.featuredServers();
      if (index >= 0 && index < all.size()) {
         FeaturedServers.Server partner = all.get(index);
         this.join(new ServerData(partner.name(), new BedrockAddress(partner.address(), partner.port()).stored(), Type.OTHER));
      }
   }

   void join(ServerData server) {
      BedrockTextures.releaseFiles();
      this.release();
      ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(server.ip), server, false, null);
   }

   @Override
   public void click() {
      AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
   }

   void serverAdded(String name, BedrockAddress address) {
      int index = this.servers.add(name, address);
      this.refreshServers();
      this.ui.select("server:" + index);
      this.snackbar("hbui.PlayScreen.serverTab.serverForm.serverAddedNotification");
   }

   void serverEdited(int index, String name, BedrockAddress address) {
      this.servers.edit(index, name, address);
      this.refreshServers();
      this.ui.select("server:" + index);
      this.snackbar("hbui.PlayScreen.serverTab.serverForm.serverEditedNotification");
   }

   void serverRemoved(int index) {
      this.servers.remove(index);
      this.refreshServers();
      this.snackbar("hbui.PlayScreen.serverTab.serverForm.serverDeletedNotification");
   }

   List<BedrockAddress> addresses(int except) {
      List<BedrockAddress> addresses = new ArrayList<>();

      for (int i = 0; i < this.servers.size(); i++) {
         if (i != except) {
            addresses.add(BedrockAddress.parse(this.servers.get(i).ip));
         }
      }

      return addresses;
   }

   private void snackbar(String key) {
      SystemToast.addOrUpdate(this.minecraft.gui.toastManager(), SystemToastId.PERIODIC_NOTIFICATION, Component.literal(this.session.lang().get(key)), null);
   }

   OreSession session() {
      return this.session;
   }

   static String noticeText(String key) {
      return I18n.get(key, new Object[0]);
   }
}
