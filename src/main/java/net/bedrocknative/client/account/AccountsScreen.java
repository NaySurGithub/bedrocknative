package net.bedrocknative.client.account;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.net.auth.XboxProblem;
import net.bedrocknative.net.auth.XboxProblemText;
import net.bedrocknative.net.auth.XboxProfile;
import net.bedrocknative.net.auth.XboxSignInFlow;
import net.bedrocknative.net.auth.window.RedirectCapture;
import net.bedrocknative.ui.SignInData;
import net.bedrocknative.ui.UiControl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.ObjectSelectionList.Entry;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public final class AccountsScreen extends Screen {
   private static final int ROW_HEIGHT = 30;
   private static final int MAX_NOTICE_LINES = 6;
   private static final int ERROR = -43691;
   private static final int WARNING = -22016;
   private static final int GOOD = -11141291;
   private static final int DIM = -6250336;
   private static final int WHITE = -1;
   private final Screen parent;
   private Component notice;
   private int noticeColor = -1;
   private boolean signInOnOpen;
   @Nullable
   private String preselect;
   private HeaderAndFooterLayout layout;
   private AccountsScreen.AccountList list;
   @Nullable
   private Button use;
   @Nullable
   private Button signInAgain;
   @Nullable
   private Button remove;
   @Nullable
   private Button openLink;
   @Nullable
   private Button copyCode;
   @Nullable
   private Button codeInstead;
   @Nullable
   private Button problemAction;
   @Nullable
   private XboxSignInFlow flow;
   private AccountsScreen.Panel signInPanel = AccountsScreen.Panel.WINDOW;
   private long copiedAt;
   @Nullable
   private XboxProblem.Diagnosis noticeProblem;
   private boolean noticeFromSignIn;
   private boolean lastActivate;
   private final JsonUiSignIn bedrock = new JsonUiSignIn(new JsonUiSignIn.Host() {
      {
         Objects.requireNonNull(AccountsScreen.this);
      }

      @Override
      public void close(SignInData.View view) {
         switch (view) {
            case CODE:
            case WAITING:
               AccountsScreen.this.cancelSignIn();
               break;
            case PROBLEM:
               AccountsScreen.this.closeProblem();
               break;
            case WELCOME:
               AccountsScreen.this.closeWelcome();
         }
      }

      @Override
      public void primary(SignInData.View view) {
         if (view == SignInData.View.WAITING && AccountsScreen.this.flow != null) {
            AccountsScreen.this.flow.useCodeInstead();
         } else if (view == SignInData.View.PROBLEM && AccountsScreen.this.problemModal != null) {
            XboxProblem.Diagnosis problem = AccountsScreen.this.problemModal;
            if (problem.problem().action() == XboxProblem.Action.LINK) {
               AccountManager.signInActions().openBrowser(problem.problem().link());
            } else {
               AccountsScreen.this.closeProblem();
               AccountsScreen.this.problemAction(problem);
            }
         }
      }

      @Override
      public void play() {
         AccountsScreen.this.closeWelcome();
      }

      @Override
      public void openLink() {
         XboxSignInFlow.Snapshot snapshot = AccountsScreen.this.flow == null ? null : AccountsScreen.this.flow.snapshot();
         if (snapshot != null && snapshot.directUri() != null) {
            AccountManager.signInActions().openBrowser(snapshot.directUri());
         }
      }

      @Override
      public void copyCode() {
         XboxSignInFlow.Snapshot snapshot = AccountsScreen.this.flow == null ? null : AccountsScreen.this.flow.snapshot();
         if (snapshot != null && snapshot.code() != null) {
            AccountsScreen.this.minecraft.keyboardHandler.setClipboard(snapshot.code());
            AccountsScreen.this.copiedAt = System.currentTimeMillis();
         }
      }
   });
   private boolean bedrockShown;
   @Nullable
   private XboxAccountStore.Entry welcome;
   private String welcomeScore = "";
   @Nullable
   private XboxProblem.Diagnosis problemModal;

   public AccountsScreen(Screen parent) {
      this(parent, Component.empty(), -1, false, null);
   }

   public AccountsScreen(Screen parent, Component notice, int noticeColor, boolean signInOnOpen, @Nullable String selectId) {
      super(Component.translatable("bedrocknative.accounts.title"));
      this.parent = parent;
      this.notice = notice;
      this.noticeColor = noticeColor;
      this.signInOnOpen = signInOnOpen;
      this.preselect = selectId;
   }

   public static AccountsScreen forFailure(Screen parent, XboxAccountStore.AccountException failure) {
      boolean none = failure.problem() == XboxAccountStore.Problem.NONE_ACTIVE;
      AccountsScreen screen = new AccountsScreen(
         parent, AccountManager.message(failure), none ? -22016 : -43691, none, failure.account() == null ? null : failure.account().id()
      );
      screen.noticeProblem = failure.diagnosis();
      if (failure.diagnosis() != null && JsonUiSignIn.wanted(SignInData.View.PROBLEM)) {
         screen.problemModal = failure.diagnosis();
      }

      return screen;
   }

   @Nullable
   public SignInData.View bedrockView() {
      return this.bedrock.view();
   }

   @Nullable
   public SignInData bedrockData() {
      return this.bedrock.data();
   }

   @Nullable
   public UiControl bedrockControl(String name) {
      return this.bedrock.control(name);
   }

   @Nullable
   public XboxProblem.Diagnosis noticeProblem() {
      return this.noticeProblem;
   }

   @Nullable
   public Button problemButton() {
      return this.problemAction;
   }

   public List<XboxAccountStore.Entry> listed() {
      List<XboxAccountStore.Entry> entries = new ArrayList<>();
      if (this.list != null) {
         this.list.children().forEach(row -> entries.add(row.entry));
      }

      return entries;
   }

   @Nullable
   public XboxAccountStore.Entry selected() {
      return this.list != null && this.list.getSelected() != null ? ((AccountsScreen.AccountRow)this.list.getSelected()).entry : null;
   }

   public void select(String id) {
      if (this.list != null) {
         this.list.children().stream().filter(row -> row.entry.id().equals(id)).findFirst().ifPresent(this.list::setSelected);
      }
   }

   public boolean signingIn() {
      return this.flow != null;
   }

   @Nullable
   public XboxSignInFlow.Snapshot signInSnapshot() {
      return this.flow == null ? null : this.flow.snapshot();
   }

   @Nullable
   public Button useButton() {
      return this.use;
   }

   @Nullable
   public Button removeButton() {
      return this.remove;
   }

   public Component notice() {
      return this.notice;
   }

   protected void init() {
      if (this.signInOnOpen && this.flow == null) {
         this.beginSignIn(AccountManager.canSwitch(this.minecraft), true);
      }

      this.bedrockShown = this.showBedrock();
      if (this.bedrockShown) {
         this.layout = null;
         this.list = null;
         this.use = this.signInAgain = this.remove = this.openLink = this.copyCode = this.codeInstead = this.problemAction = null;
         this.bedrock.resize(this.width, this.height);
         this.verifyActiveOnOpen();
      } else {
         int noticeLines = this.noticeLines();
         this.layout = new HeaderAndFooterLayout(this, Math.max(33, 26 + noticeLines * 10 + 6), 56);
         this.layout.addToHeader(new StringWidget(this.title, this.font), settings -> settings.alignVerticallyTop().paddingTop(12));
         this.list = null;
         this.use = this.signInAgain = this.remove = this.openLink = this.copyCode = this.codeInstead = this.problemAction = null;
         if (this.flow == null) {
            this.buildList();
         } else {
            this.buildSignInFooter();
         }

         this.layout.visitWidgets(x$0 -> this.addRenderableWidget(x$0));
         this.repositionElements();
         if (this.flow == null) {
            this.updateButtons();
         }

         this.verifyActiveOnOpen();
      }
   }

   private void verifyActiveOnOpen() {
      if (AccountManager.autoVerify() && this.flow == null) {
         XboxAccountStore.Entry active = AccountManager.store().active().orElse(null);
         if (active != null && AccountManager.status(active.id()).check() == AccountManager.Check.UNKNOWN) {
            AccountManager.verifyAsync(active.id());
         }
      }
   }

   private boolean showBedrock() {
      if (this.flow != null) {
         XboxSignInFlow.Snapshot snapshot = this.flow.snapshot();

         boolean shown = switch (snapshot.state()) {
            case STARTING, WAITING_FOR_WINDOW -> this.showWaiting(snapshot);
            case SIGNING_IN -> this.bedrock.view() == SignInData.View.WAITING ? this.showWaiting(snapshot) : this.bedrock.showing();
            case WAITING_FOR_CODE_ENTRY -> this.showCode(snapshot);
            default -> this.bedrock.showing();
         };
         if (!shown) {
            this.bedrock.hide();
         }

         return shown;
      } else {
         boolean shown = this.welcome != null ? this.showWelcome(this.welcome) : this.problemModal != null && this.showProblem(this.problemModal);
         if (!shown) {
            this.bedrock.hide();
         }

         return shown;
      }
   }

   private boolean showWaiting(XboxSignInFlow.Snapshot snapshot) {
      String title = AccountProblems.bedrockOr("xbox.signin", "bedrocknative.accounts.window.title").getString();

      String hint = switch (snapshot.state()) {
         case WAITING_FOR_WINDOW -> snapshot.window() == RedirectCapture.Window.HELPER
            ? "bedrocknative.accounts.window.helper"
            : "bedrocknative.accounts.window.browser";
         case SIGNING_IN -> "bedrocknative.accounts.window.returned";
         default -> null;
      };
      String body = AccountProblems.bedrockOr("xbox.signingin", "bedrocknative.accounts.signing_in").getString()
         + (hint == null ? "" : "\n\n" + Component.translatable(hint).getString());
      String left = Component.translatable("bedrocknative.accounts.code_instead").getString();
      String right = AccountProblems.bedrockOr("gui.cancel", "gui.cancel").getString();
      this.bedrock.hint(null);
      return this.bedrock.show(SignInData.View.WAITING, title + "\n" + body + "\n" + left + "\n" + right, data -> data.modal(title, body, left, right));
   }

   private boolean showCode(XboxSignInFlow.Snapshot snapshot) {
      String link = snapshot.directUri() != null ? snapshot.directUri() : String.valueOf(snapshot.uri());
      String uri = String.valueOf(snapshot.uri());
      String code = String.valueOf(snapshot.code());
      return this.bedrock.show(SignInData.View.CODE, link + "\n" + code, data -> data.code(link, uri, code));
   }

   private boolean showWelcome(XboxAccountStore.Entry entry) {
      String message = AccountProblems.bedrockOr("xbox.signin.welcome", "bedrocknative.accounts.welcome").getString();
      String gamertag = AccountManager.name(entry).getString();
      this.bedrock.hint(null);
      return this.bedrock.show(SignInData.View.WELCOME, entry.id(), data -> data.welcome(message, gamertag, this.welcomeScore));
   }

   private boolean showProblem(XboxProblem.Diagnosis problem) {
      XboxProblemText.Text text = AccountProblems.text(problem, this.selectedOrActive());
      String title = text.title() != null ? text.title() : AccountProblems.bedrockOr("xbox.signin", "bedrocknative.accounts.window.title").getString();
      String body = text.body() + (text.errorLine() != null ? "\n\n" + text.errorLine() : "");
      String left = text.action() != null ? text.action() : AccountProblems.bedrockOr("gui.ok", "gui.ok").getString();
      String right = AccountProblems.bedrockOr("gui.close", "gui.close").getString();
      this.bedrock.hint(null);
      return this.bedrock.show(SignInData.View.PROBLEM, title + "\n" + body + "\n" + left + "\n" + right, data -> data.modal(title, body, left, right));
   }

   @Nullable
   private XboxAccountStore.Entry selectedOrActive() {
      XboxAccountStore.Entry selected = this.selected();
      return selected != null ? selected : AccountManager.store().active().orElse(null);
   }

   private void closeWelcome() {
      this.welcome = null;
      this.rebuildWidgets();
   }

   private void closeProblem() {
      this.problemModal = null;
      this.rebuildWidgets();
   }

   private void loadProfile(XboxAccountStore.Entry entry) {
      if (AccountManager.autoVerify()) {
         XboxAccountStore store = AccountManager.store();
         Thread thread = new Thread(() -> {
            try {
               XboxProfile profile = XboxProfile.fetch(store.load(entry.id()));
               this.minecraft.execute(() -> {
                  if (this.welcome != null && this.welcome.id().equals(entry.id())) {
                     this.welcomeScore = profile.gamerscore() == null ? "" : profile.gamerscore();
                     this.bedrock.picture(profile.pictureUrl());
                     if (this.bedrockShown) {
                        this.showWelcome(entry);
                     }
                  }
               });
            } catch (Exception var4) {
               BedrockNative.LOGGER.info("The welcome screen shows the gamertag only: the Xbox profile did not answer ({})", var4.getClass().getSimpleName());
            }
         }, "Xbox profile");
         thread.setDaemon(true);
         thread.start();
      }
   }

   private void buildList() {
      this.list = (AccountsScreen.AccountList)this.layout
         .addToContents(new AccountsScreen.AccountList(this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight()));
      this.fillList(this.preselect);
      this.preselect = null;
      LinearLayout footer = (LinearLayout)this.layout.addToFooter(LinearLayout.vertical().spacing(4));
      footer.defaultCellSetting().alignHorizontallyCenter();
      LinearLayout top = (LinearLayout)footer.addChild(LinearLayout.horizontal().spacing(4));
      LinearLayout bottom = (LinearLayout)footer.addChild(LinearLayout.horizontal().spacing(4));
      this.use = (Button)top.addChild(Button.builder(Component.translatable("bedrocknative.accounts.use"), button -> this.useSelected()).width(100).build());
      top.addChild(
         Button.builder(Component.translatable("bedrocknative.accounts.add"), button -> this.startSignIn(AccountManager.canSwitch(this.minecraft)))
            .width(100)
            .build()
      );
      this.signInAgain = (Button)top.addChild(Button.builder(Component.translatable("bedrocknative.accounts.signin_again"), button -> {
         XboxAccountStore.Entry entry = this.selected();
         if (entry != null) {
            this.startSignIn(false);
         }
      }).width(100).build());
      this.remove = (Button)bottom.addChild(
         Button.builder(Component.translatable("bedrocknative.accounts.remove"), button -> this.confirmRemove()).width(100).build()
      );
      bottom.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(100).build());
      XboxProblem.Diagnosis problem = this.noticeProblem;
      String label = problem == null ? null : AccountProblems.text(problem, this.selected()).action();
      if (label != null) {
         this.problemAction = (Button)bottom.addChild(
            Button.builder(Component.literal(label), button -> this.problemAction(problem)).width(Math.max(100, this.font.width(label) + 12)).build()
         );
      }
   }

   private void problemAction(XboxProblem.Diagnosis problem) {
      switch (problem.problem().action()) {
         case LINK:
            AccountManager.signInActions().openBrowser(problem.problem().link());
            break;
         case SIGN_IN:
            this.startSignIn(false);
            break;
         case TRY_AGAIN:
            if (this.noticeFromSignIn) {
               this.startSignIn(this.lastActivate);
            } else {
               XboxAccountStore.Entry entry = this.selected() != null ? this.selected() : AccountManager.store().active().orElse(null);
               this.setNotice(Component.empty(), -1);
               if (entry != null) {
                  AccountManager.verifyAsync(entry.id());
               }
            }
         case NONE:
      }
   }

   private int noticeLines() {
      return this.notice.getString().isEmpty() ? 0 : Math.min(6, this.font.split(this.notice, this.width - 40).size());
   }

   private void buildSignInFooter() {
      LinearLayout footer = (LinearLayout)this.layout.addToFooter(LinearLayout.horizontal().spacing(4));
      this.signInPanel = AccountsScreen.Panel.of(this.flow == null ? null : this.flow.snapshot());
      if (this.signInPanel == AccountsScreen.Panel.WINDOW) {
         Component label = Component.translatable("bedrocknative.accounts.code_instead");
         this.codeInstead = (Button)footer.addChild(Button.builder(label, button -> {
            if (this.flow != null && this.flow.useCodeInstead()) {
               button.active = false;
            }
         }).width(Math.max(100, this.font.width(label) + 12)).build());
      } else if (this.signInPanel == AccountsScreen.Panel.CODE) {
         this.openLink = (Button)footer.addChild(Button.builder(Component.translatable("bedrocknative.accounts.open_link"), button -> {
            XboxSignInFlow.Snapshot snapshot = this.flow == null ? null : this.flow.snapshot();
            if (snapshot != null && snapshot.directUri() != null) {
               AccountManager.signInActions().openBrowser(snapshot.directUri());
            }
         }).width(100).build());
         this.copyCode = (Button)footer.addChild(Button.builder(Component.translatable("bedrocknative.accounts.copy_code"), button -> {
            XboxSignInFlow.Snapshot snapshot = this.flow == null ? null : this.flow.snapshot();
            if (snapshot != null && snapshot.code() != null) {
               this.minecraft.keyboardHandler.setClipboard(snapshot.code());
               this.copiedAt = System.currentTimeMillis();
            }
         }).width(100).build());
      }

      footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.cancelSignIn()).width(100).build());
      this.updateSignInButtons();
   }

   private void fillList(@Nullable String selectId) {
      if (this.list != null) {
         XboxAccountStore.Entry keep = selectId != null ? null : this.selected();
         String keepId = selectId != null ? selectId : (keep == null ? null : keep.id());
         List<AccountsScreen.AccountRow> rows = new ArrayList<>();

         for (XboxAccountStore.Entry entry : AccountManager.store().accounts()) {
            rows.add(new AccountsScreen.AccountRow(entry));
         }

         this.list.replaceEntries(rows);
         AccountsScreen.AccountRow chosen = rows.stream()
            .filter(row -> row.entry.id().equals(keepId))
            .findFirst()
            .orElseGet(() -> rows.stream().filter(row -> isActive(row.entry)).findFirst().orElse(rows.isEmpty() ? null : rows.getFirst()));
         this.list.setSelected(chosen);
         if (chosen != null) {
            this.list.centerOn(chosen);
         }
      }
   }

   private static boolean isActive(XboxAccountStore.Entry entry) {
      return AccountManager.store().active().map(active -> active.id().equals(entry.id())).orElse(false);
   }

   private void useSelected() {
      XboxAccountStore.Entry entry = this.selected();
      if (entry != null && AccountManager.canSwitch(this.minecraft) && !isActive(entry)) {
         try {
            AccountManager.store().setActive(entry.id());
         } catch (UncheckedIOException var3) {
            BedrockNative.LOGGER.warn("Couldn't switch the Xbox account: {}", var3.getCause().getMessage());
            this.setNotice(Component.translatable("bedrocknative.accounts.switch_failed", new Object[]{String.valueOf(var3.getCause().getMessage())}), -43691);
            return;
         }

         this.setNotice(Component.translatable("bedrocknative.accounts.switched", new Object[]{AccountManager.name(entry)}), -11141291);
         if (AccountManager.autoVerify()) {
            AccountManager.verifyAsync(entry.id());
         }
      }
   }

   private void confirmRemove() {
      XboxAccountStore.Entry entry = this.selected();
      if (entry != null) {
         this.minecraft
            .gui
            .setScreen(
               new ConfirmScreen(
                  confirmed -> {
                     if (confirmed) {
                        try {
                           AccountManager.store().remove(entry.id());
                           this.setNotice(Component.translatable("bedrocknative.accounts.removed", new Object[]{AccountManager.name(entry)}), -1);
                        } catch (Exception var4) {
                           BedrockNative.LOGGER.warn("Couldn't remove the Xbox account: {}", var4.getMessage());
                           this.setNotice(
                              Component.translatable("bedrocknative.accounts.remove_failed", new Object[]{String.valueOf(var4.getMessage())}), -43691
                           );
                        }

                        this.fillList(null);
                     }

                     this.minecraft.gui.setScreen(this);
                  },
                  Component.translatable("bedrocknative.accounts.remove.title"),
                  Component.translatable("bedrocknative.accounts.remove.message", new Object[]{AccountManager.name(entry)}),
                  Component.translatable("bedrocknative.accounts.remove"),
                  CommonComponents.GUI_CANCEL
               )
            );
      }
   }

   private void startSignIn(boolean activate) {
      this.beginSignIn(activate, false);
      this.rebuildWidgets();
   }

   private void beginSignIn(boolean activate, boolean keepNotice) {
      this.signInOnOpen = false;
      this.lastActivate = activate;
      XboxSignInFlow[] self = new XboxSignInFlow[1];
      self[0] = new XboxSignInFlow(
         AccountManager.store(),
         AccountManager.signer(),
         AccountManager.signInActions(),
         activate,
         snapshot -> this.minecraft.execute(() -> this.onSignIn(self[0], snapshot))
      );
      this.copiedAt = 0L;
      this.flow = self[0];
      if (!keepNotice) {
         this.notice = Component.empty();
         this.noticeProblem = null;
      }

      this.flow.start();
   }

   private void cancelSignIn() {
      if (this.flow != null && this.flow.cancel()) {
         this.flow = null;
         this.setNotice(Component.translatable("bedrocknative.accounts.cancelled"), -1);
         this.rebuildWidgets();
      }
   }

   private void onSignIn(XboxSignInFlow source, XboxSignInFlow.Snapshot snapshot) {
      if (source == this.flow) {
         if (snapshot.finished()) {
            this.flow = null;
            switch (snapshot.state()) {
               case DONE:
                  XboxAccountStore.Entry entry = snapshot.account();
                  AccountManager.record(entry.id(), AccountManager.Status.OK);
                  this.preselect = entry.id();
                  boolean nowActive = isActive(entry);
                  this.setNotice(
                     Component.translatable(
                        nowActive ? "bedrocknative.accounts.added_active" : "bedrocknative.accounts.added", new Object[]{AccountManager.name(entry)}
                     ),
                     -11141291
                  );
                  if (JsonUiSignIn.wanted(SignInData.View.WELCOME)) {
                     this.welcome = entry;
                     this.welcomeScore = "";
                     this.bedrock.picture(null);
                     this.loadProfile(entry);
                  }

                  if (AccountManager.autoVerify()) {
                     AccountManager.verifyAsync(entry.id());
                  }
                  break;
               case FAILED:
                  if (snapshot.problem() != null) {
                     this.setNotice(AccountProblems.message(snapshot.problem(), null), -43691, snapshot.problem(), true);
                     if (JsonUiSignIn.wanted(SignInData.View.PROBLEM)) {
                        this.problemModal = snapshot.problem();
                     }
                  } else {
                     this.setNotice(Component.translatable("bedrocknative.accounts.signin_failed", new Object[]{String.valueOf(snapshot.error())}), -43691);
                  }
                  break;
               default:
                  this.setNotice(Component.translatable("bedrocknative.accounts.cancelled"), -1);
            }

            this.rebuildWidgets();
         } else {
            if (!this.bedrockShown && !JsonUiSignIn.wanted(SignInData.View.WAITING) && AccountsScreen.Panel.of(snapshot) == this.signInPanel) {
               this.updateSignInButtons();
            } else {
               this.rebuildWidgets();
            }
         }
      }
   }

   private void setNotice(Component notice, int color) {
      this.setNotice(notice, color, null, false);
   }

   private void setNotice(Component notice, int color, @Nullable XboxProblem.Diagnosis problem, boolean fromSignIn) {
      boolean resize = this.font.split(this.notice, Math.max(1, this.width - 40)).size() != this.font.split(notice, Math.max(1, this.width - 40)).size()
         || this.notice.getString().isEmpty() != notice.getString().isEmpty()
         || !Objects.equals(this.noticeProblem, problem);
      this.notice = notice;
      this.noticeColor = color;
      this.noticeProblem = problem;
      this.noticeFromSignIn = fromSignIn;
      if (resize && this.flow == null && this.layout != null) {
         this.rebuildWidgets();
      }
   }

   public void tick() {
      super.tick();
      if (this.bedrockShown) {
         if (!this.bedrock.showing()) {
            this.rebuildWidgets();
         }
      } else if (this.bedrockDue() && JsonUi.definitions() != null) {
         this.rebuildWidgets();
      } else {
         if (this.flow == null) {
            this.updateButtons();
         } else {
            this.updateSignInButtons();
         }
      }
   }

   private boolean bedrockDue() {
      if (this.bedrock.failedOnce()) {
         return false;
      } else if (this.flow != null) {
         XboxSignInFlow.State state = this.flow.snapshot().state();
         boolean showable = state == XboxSignInFlow.State.STARTING
            || state == XboxSignInFlow.State.WAITING_FOR_WINDOW
            || state == XboxSignInFlow.State.WAITING_FOR_CODE_ENTRY;
         return showable && JsonUiSignIn.wanted(SignInData.View.WAITING);
      } else {
         return this.welcome != null || this.problemModal != null;
      }
   }

   public void mouseMoved(double x, double y) {
      if (this.bedrockShown) {
         this.bedrock.mouseMoved(x, y);
      } else {
         super.mouseMoved(x, y);
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      return this.bedrockShown ? this.bedrock.mouseClicked(event) : super.mouseClicked(event, doubleClick);
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      return this.bedrockShown ? this.bedrock.mouseReleased(event) : super.mouseReleased(event);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return this.bedrockShown ? this.bedrock.mouseDragged(event) : super.mouseDragged(event, dx, dy);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.bedrockShown ? this.bedrock.mouseScrolled(x, y, scrollY) : super.mouseScrolled(x, y, scrollX, scrollY);
   }

   public boolean keyPressed(KeyEvent event) {
      return this.bedrockShown && this.bedrock.keyPressed(event) ? true : super.keyPressed(event);
   }

   public boolean charTyped(CharacterEvent event) {
      return this.bedrockShown ? this.bedrock.charTyped(event) : super.charTyped(event);
   }

   private void updateButtons() {
      XboxAccountStore.Entry entry = this.selected();
      boolean canSwitch = AccountManager.canSwitch(this.minecraft);
      if (this.use != null) {
         this.use.active = entry != null && canSwitch && !isActive(entry);
      }

      if (this.remove != null) {
         this.remove.active = entry != null && (canSwitch || !isActive(entry));
      }

      if (this.signInAgain != null) {
         this.signInAgain.active = entry != null;
      }
   }

   private void updateSignInButtons() {
      XboxSignInFlow.Snapshot snapshot = this.flow == null ? null : this.flow.snapshot();
      boolean hasCode = snapshot != null && snapshot.code() != null;
      if (this.openLink != null) {
         this.openLink.active = hasCode;
      }

      if (this.copyCode != null) {
         this.copyCode.active = hasCode;
      }

      if (this.codeInstead != null && snapshot != null && !snapshot.windowPart()) {
         this.codeInstead.active = false;
      }
   }

   protected void repositionElements() {
      if (this.layout != null) {
         this.layout.arrangeElements();
         if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
         }
      }
   }

   public void onClose() {
      if (this.flow != null) {
         this.cancelSignIn();
      } else {
         this.minecraft.gui.setScreen(this.parent);
      }
   }

   public void removed() {
      if (this.flow != null) {
         this.flow.cancel();
         this.flow = null;
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      if (this.bedrockShown) {
         this.bedrock.hint(this.codeHint());
         this.bedrock.render(graphics, this.width, this.height);
      } else {
         if (!this.notice.getString().isEmpty()) {
            int y = 26;

            for (FormattedCharSequence line : this.font.split(this.notice, this.width - 40).stream().limit(6L).toList()) {
               graphics.centeredText(this.font, line, this.width / 2, y, this.noticeColor);
               y += 10;
            }
         }

         if (this.flow != null) {
            this.extractSignIn(graphics);
         } else if (this.list != null && this.list.children().isEmpty()) {
            graphics.centeredText(
               this.font, Component.translatable("bedrocknative.accounts.empty"), this.width / 2, this.layout.getHeaderHeight() + 20, -6250336
            );
         }

         if (this.flow == null && !AccountManager.canSwitch(this.minecraft)) {
            graphics.centeredText(
               this.font, Component.translatable("bedrocknative.accounts.locked"), this.width / 2, this.height - this.layout.getFooterHeight() - 12, -22016
            );
         }
      }
   }

   @Nullable
   private Component codeHint() {
      XboxSignInFlow.Snapshot snapshot = this.flow == null ? null : this.flow.snapshot();
      if (this.bedrock.view() == SignInData.View.CODE && snapshot != null) {
         MutableComponent line = Component.translatable(
            snapshot.browserOpened() ? "bedrocknative.accounts.qr.hint" : "bedrocknative.accounts.qr.hint_no_browser"
         );
         if (System.currentTimeMillis() - this.copiedAt < 2000L) {
            line.append(" ").append(Component.translatable("bedrocknative.accounts.code.copied").withStyle(ChatFormatting.GREEN));
         }

         return line;
      } else {
         return null;
      }
   }

   private void extractSignIn(GuiGraphicsExtractor graphics) {
      XboxSignInFlow.Snapshot snapshot = this.flow.snapshot();
      int middle = this.width / 2;
      int y = this.layout.getHeaderHeight() + 14;
      switch (snapshot.state()) {
         case STARTING:
         case WAITING_FOR_WINDOW:
         case SIGNING_IN:
            graphics.centeredText(this.font, AccountProblems.bedrockOr("xbox.signingin", "bedrocknative.accounts.signing_in"), middle, y + 20, -1);

            String hint = switch (snapshot.state()) {
               case WAITING_FOR_WINDOW -> snapshot.window() == RedirectCapture.Window.HELPER
                  ? "bedrocknative.accounts.window.helper"
                  : "bedrocknative.accounts.window.browser";
               case SIGNING_IN -> "bedrocknative.accounts.window.returned";
               default -> null;
            };
            int lineY = y + 40;
            if (hint != null) {
               for (FormattedCharSequence line : this.font.split(Component.translatable(hint), this.width - 40).stream().limit(3L).toList()) {
                  graphics.centeredText(this.font, line, middle, lineY, -6250336);
                  lineY += 10;
               }
            }

            int dots = (int)(System.currentTimeMillis() / 500L % 4L);
            graphics.centeredText(this.font, Component.literal(".".repeat(dots + 1)), middle, lineY + 10, -6250336);
            break;
         case WAITING_FOR_CODE_ENTRY:
            Component instruction = Component.translatable(snapshot.browserOpened() ? "bedrocknative.accounts.code.opened" : "bedrocknative.accounts.code.open");
            int lineY = y;

            for (FormattedCharSequence line : this.font.split(instruction, this.width - 40).stream().limit(2L).toList()) {
               graphics.centeredText(this.font, line, middle, lineY, -1);
               lineY += 10;
            }

            graphics.centeredText(
               this.font, Component.literal(snapshot.uri()).withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.UNDERLINE}), middle, y + 24, -1
            );
            graphics.centeredText(this.font, Component.translatable("bedrocknative.accounts.code.enter"), middle, y + 44, -6250336);
            graphics.pose().pushMatrix();
            graphics.pose().translate(middle, y + 58);
            graphics.pose().scale(2.0F, 2.0F);
            graphics.centeredText(
               this.font, Component.literal(snapshot.code()).withStyle(new ChatFormatting[]{ChatFormatting.YELLOW, ChatFormatting.BOLD}), 0, 0, -1
            );
            graphics.pose().popMatrix();
            if (System.currentTimeMillis() - this.copiedAt < 2000L) {
               graphics.centeredText(this.font, Component.translatable("bedrocknative.accounts.code.copied"), middle, y + 76, -11141291);
            }

            int dots = (int)(System.currentTimeMillis() / 500L % 4L);
            graphics.centeredText(this.font, Component.translatable("bedrocknative.accounts.code.waiting").append(".".repeat(dots)), middle, y + 92, -6250336);
            break;
         case DONE:
         case FAILED:
         default:
            graphics.centeredText(this.font, Component.translatable("bedrocknative.accounts.requesting"), middle, y + 30, -1);
            break;
         case SAVING:
            graphics.centeredText(this.font, Component.translatable("bedrocknative.accounts.saving"), middle, y + 30, -1);
      }
   }

   private final class AccountList extends ObjectSelectionList<AccountsScreen.AccountRow> {
      AccountList(Minecraft minecraft, int width, int height, int y) {
         Objects.requireNonNull(AccountsScreen.this);
         super(minecraft, width, height, y, 30);
      }

      void centerOn(AccountsScreen.AccountRow row) {
         this.centerScrollOn(row);
      }

      public int getRowWidth() {
         return Math.min(this.width - 20, 300);
      }
   }

   private final class AccountRow extends Entry<AccountsScreen.AccountRow> {
      private final XboxAccountStore.Entry entry;

      AccountRow(XboxAccountStore.Entry entry) {
         Objects.requireNonNull(AccountsScreen.this);
         super();
         this.entry = entry;
      }

      public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
         graphics.text(AccountsScreen.this.font, AccountManager.name(this.entry), this.getContentX() + 3, this.getContentY() + 4, -1);
         if (AccountsScreen.isActive(this.entry)) {
            Component tag = Component.translatable("bedrocknative.accounts.active");
            graphics.text(AccountsScreen.this.font, tag, this.getContentRight() - 3 - AccountsScreen.this.font.width(tag), this.getContentY() + 4, -11141291);
         }

         AccountManager.Status status = AccountManager.status(this.entry.id());

         Component line = (Component)(switch (status.check()) {
            case UNKNOWN -> Component.translatable("bedrocknative.accounts.status.unknown");
            case CHECKING -> Component.translatable("bedrocknative.accounts.status.checking");
            case OK -> Component.translatable("bedrocknative.accounts.status.ok");
            case SIGN_IN_REQUIRED -> Component.translatable("bedrocknative.accounts.status.signin");
            case FAILED -> status.diagnosis() != null
               ? AccountProblems.shortLine(status.diagnosis(), this.entry)
               : Component.translatable("bedrocknative.accounts.status.failed", new Object[]{status.detail()});
         });

         int color = switch (status.check()) {
            case OK -> -11141291;
            case SIGN_IN_REQUIRED -> -43691;
            case FAILED -> -22016;
            default -> -6250336;
         };
         String text = AccountsScreen.this.font.plainSubstrByWidth(line.getString(), this.getContentWidth() - 6);
         graphics.text(AccountsScreen.this.font, text, this.getContentX() + 3, this.getContentY() + 16, color);
      }

      public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
         AccountsScreen.this.list.setSelected(this);
         if (doubleClick) {
            AccountsScreen.this.useSelected();
         }

         return super.mouseClicked(event, doubleClick);
      }

      public Component getNarration() {
         return Component.translatable("narrator.select", new Object[]{AccountManager.name(this.entry)});
      }
   }

   private static enum Panel {
      WINDOW,
      CODE,
      PLAIN;

      static AccountsScreen.Panel of(@Nullable XboxSignInFlow.Snapshot snapshot) {
         if (snapshot != null && !snapshot.windowPart()) {
            return snapshot.state() != XboxSignInFlow.State.REQUESTING_CODE && snapshot.state() != XboxSignInFlow.State.WAITING_FOR_CODE_ENTRY ? PLAIN : CODE;
         } else {
            return WINDOW;
         }
      }
   }
}
