package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class SignInData implements UiDataSource {
   public static final String QR_RENDERER = "qr_code_renderer";
   public static final String PICTURE_RENDERER = "profile_image_renderer";
   public static final QrCode.Level QR_LEVEL = QrCode.Level.M;
   static final String EXIT = "button.menu_exit";
   static final String PLAY = "button.play";
   static final String CONFIRM = "button.confirm";
   static final String CANCEL = "button.cancel";
   private final SignInData.View view;
   @Nullable
   private SignInData.Actions actions;
   private String link = "";
   private String url = "";
   private String code = "";
   @Nullable
   private QrCode qr;
   private String welcome = "";
   private String gamertag = "";
   private String gamerscore = "";
   private String title = "";
   private String body = "";
   private String left = "";
   private String right = "";
   private long version = 1L;

   public SignInData(SignInData.View view) {
      this.view = view;
   }

   public SignInData.View view() {
      return this.view;
   }

   public void actions(@Nullable SignInData.Actions target) {
      this.actions = target;
   }

   public void code(String link, String url, String code) {
      if (!link.equals(this.link)) {
         this.link = link;

         QrCode encoded;
         try {
            encoded = link.isEmpty() ? null : QrCode.encode(link, QR_LEVEL);
         } catch (IllegalArgumentException var6) {
            encoded = null;
         }

         this.qr = encoded;
      }

      this.url = url;
      this.code = code;
      this.version++;
   }

   @Nullable
   public QrCode qr() {
      return this.qr;
   }

   public void welcome(String message, String gamertag, String gamerscore) {
      this.welcome = message;
      this.gamertag = gamertag;
      this.gamerscore = gamerscore;
      this.version++;
   }

   public void modal(String title, String body, String left, String right) {
      this.title = title;
      this.body = body;
      this.left = left;
      this.right = right;
      this.version++;
   }

   public String gamertag() {
      return this.gamertag;
   }

   public String body() {
      return this.body;
   }

   @Override
   public UiValue global(String name) {
      return switch (name) {
         case "#qr_code_content" -> UiValue.of(this.link);
         case "#url" -> UiValue.of(this.url);
         case "#code" -> UiValue.of(this.code);
         case "#welcome_message" -> UiValue.of(this.welcome);
         case "#gamertag" -> UiValue.of(this.gamertag);
         case "#gamer_score" -> UiValue.of(this.gamerscore);
         case "#profile_image_options" -> UiValue.EMPTY;
         case "#title_text" -> UiValue.of(this.title);
         case "#body_text" -> UiValue.of(this.body);
         case "#left_button_text" -> UiValue.of(this.left);
         case "#right_button_text" -> UiValue.of(this.right);
         case "#ps4_warning_message_visible", "#is_new_account" -> UiValue.FALSE;
         case "#toggle_enabled", "#slider_enabled", "#text_edit_box_visible" -> UiValue.NULL;
         default -> !name.endsWith("_visible") && !name.endsWith("_visibility") && !name.startsWith("#is_") ? UiValue.NULL : UiValue.FALSE;
      };
   }

   @Override
   public int length(String collection) {
      return 0;
   }

   @Override
   public UiValue item(String collection, int index, String name) {
      return UiValue.NULL;
   }

   @Nullable
   @Override
   public String kind(String collection, int index) {
      return null;
   }

   @Override
   public long version() {
      return this.version;
   }

   @Override
   public void event(String buttonId, @Nullable String collection, int index) {
      SignInData.Actions target = this.actions;
      if (target != null) {
         switch (buttonId) {
            case "button.menu_exit":
            case "button.cancel":
               if (this.view == SignInData.View.WELCOME) {
                  target.play();
               } else {
                  target.close(this.view);
               }
               break;
            case "button.confirm":
               target.primary(this.view);
               break;
            case "button.play":
               target.play();
         }
      }
   }

   public static Map<String, UiValue> variables() {
      Map<String, UiValue> engine = new HashMap<>();

      for (String flag : List.of("$nx_os", "$is_nx_link_unlink_enabled", "$is_ps4", "$console_edition", "$touch")) {
         engine.put(flag, UiValue.FALSE);
      }

      JsonArray frame = new JsonArray();
      frame.add("100% - 16px");
      frame.add("100%c - 6px");
      engine.put("$common_background_size", UiValue.of(frame));
      return UiPlatform.desktop(engine);
   }

   public static int moduleSize(QrCode qr, int size) {
      return Math.max(1, size / (qr.size() + 4));
   }

   public interface Actions {
      void close(SignInData.View var1);

      void primary(SignInData.View var1);

      void play();
   }

   public static enum View {
      CODE("xbl_console_qr_signin.xbl_console_qr_signin", "xbox_signin_qr"),
      WELCOME("xbl_console_signin_succeeded.xbl_console_signin_succeeded", "xbox_signin_welcome"),
      PROBLEM("authentication_modals.ad_modal_dialog", "xbox_signin_problem"),
      WAITING("authentication_modals.ad_modal_dialog", "xbox_signin_waiting");

      public final String screen;
      public final String gateKey;

      private View(String screen, String gateKey) {
         this.screen = screen;
         this.gateKey = gateKey;
      }

      public static List<String> gateKeys() {
         return List.of(CODE.gateKey, WELCOME.gateKey, PROBLEM.gateKey, WAITING.gateKey);
      }
   }
}
