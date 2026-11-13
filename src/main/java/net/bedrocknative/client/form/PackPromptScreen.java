package net.bedrocknative.client.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.bedrocknative.net.client.PackChoice;
import net.bedrocknative.net.client.PackOffer;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.layouts.LinearLayout;

public final class PackPromptScreen extends FormScreen {
   private static final int BUTTON_HEIGHT = 24;
   private final PackOffer offer;
   private final PackPromptScreen.Answer answer;
   private List<AbstractButton> buttons = List.of();

   public PackPromptScreen(PackOffer offer, Consumer<PackChoice> choice) {
      this(offer, new PackPromptScreen.Answer(choice));
   }

   private PackPromptScreen(PackOffer offer, PackPromptScreen.Answer answer) {
      super(answer, 0, "progressScreen.dialog.title.resourcePack", null);
      this.offer = offer;
      this.answer = answer;
   }

   public List<AbstractButton> buttons() {
      return this.buttons;
   }

   public void choose(PackChoice choice) {
      this.answer.choose(choice);
   }

   private List<PackPromptScreen.Option> options() {
      PackPromptScreen.Option everything = new PackPromptScreen.Option(
         BedrockText.translate("progressScreen.dialog.button.joinAndDownload.everything", List.of(size(this.offer.totalBytes()))), PackChoice.EVERYTHING
      );
      PackPromptScreen.Option required = new PackPromptScreen.Option(
         BedrockText.translate("progressScreen.dialog.button.joinAndDownload.required", List.of(size(this.offer.requiredBytes()))), PackChoice.REQUIRED_ONLY
      );
      PackPromptScreen.Option join = new PackPromptScreen.Option(BedrockText.translate("progressScreen.dialog.button.join", List.of()), PackChoice.NONE);
      PackPromptScreen.Option leave = new PackPromptScreen.Option(BedrockText.translate("progressScreen.dialog.button.leave", List.of()), PackChoice.LEAVE);
      if (this.offer.serverRequired()) {
         return List.of(everything, leave);
      } else if (this.offer.anyRequired()) {
         return this.offer.anyOptional() ? List.of(everything, required, leave) : List.of(required, leave);
      } else {
         return List.of(everything, join);
      }
   }

   private String message() {
      String key = this.offer.serverRequired()
         ? "serverRequired"
         : (this.offer.anyRequired() ? (this.offer.anyOptional() ? "optionalAndRequired" : "required") : "optional");
      return BedrockText.translate("progressScreen.dialog.message.resourcePack." + key, List.of());
   }

   static String size(long bytes) {
      return bytes < 1048576L ? Math.max(1L, (bytes + 1023L) / 1024L) + " KB" : String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
   }

   @Override
   protected int preferredWidth() {
      return 240;
   }

   @Override
   protected int preferredHeight() {
      return 110 + this.options().size() * 26;
   }

   @Override
   protected int footerHeight() {
      return this.options().size() * 26 - 2;
   }

   @Override
   protected boolean leftAlignedTitle() {
      return true;
   }

   @Override
   protected String dialogTexture() {
      return "textures/ui/dialog_background_hollow_1";
   }

   @Override
   protected int fillAlpha() {
      return 255;
   }

   @Override
   protected void buildBody(LinearLayout body, int width) {
      body.addChild(new FormTextWidget(this.font, BedrockText.format(this.message()), width, FormTextWidget.Kind.TEXT));
   }

   @Override
   protected void buildFooter(int x, int y, int width, int height) {
      List<AbstractButton> created = new ArrayList<>();

      for (PackPromptScreen.Option option : this.options()) {
         FormButton button = new FormButton(this.font, width, 24, BedrockText.format(option.text()), null, () -> this.answer.choose(option.choice()));
         button.setPosition(x, y);
         y += 26;
         created.add(this.addRenderableWidget(button));
      }

      this.buttons = List.copyOf(created);
   }

   private static final class Answer implements FormScreen.Host {
      private final Consumer<PackChoice> choice;
      private boolean done;

      Answer(Consumer<PackChoice> choice) {
         this.choice = choice;
      }

      void choose(PackChoice picked) {
         if (!this.done) {
            this.done = true;
            this.choice.accept(picked);
         }
      }

      @Override
      public void submit(FormScreen screen, String response) {
      }

      @Override
      public void cancel(FormScreen screen) {
         this.choose(PackChoice.LEAVE);
      }

      @Override
      public void removed(FormScreen screen) {
         this.choose(PackChoice.LEAVE);
      }
   }

   private record Option(String text, PackChoice choice) {
   }
}
