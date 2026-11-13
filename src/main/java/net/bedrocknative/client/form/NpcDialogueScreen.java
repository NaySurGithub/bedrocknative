package net.bedrocknative.client.form;

import net.bedrocknative.npc.NpcDialogue;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.layouts.LinearLayout;

public final class NpcDialogueScreen extends FormScreen {
   private static final int BUTTON_HEIGHT = 24;
   private final NpcDialogue dialogue;

   NpcDialogueScreen(FormScreen.Host host, NpcDialogue dialogue) {
      super(host, 0, dialogue.npcName().isEmpty() ? "entity.npc.name" : dialogue.npcName(), null);
      this.dialogue = dialogue;
   }

   public NpcDialogue dialogue() {
      return this.dialogue;
   }

   @Override
   public boolean isServerSettings() {
      return true;
   }

   @Override
   protected int preferredHeight() {
      return 220;
   }

   @Override
   protected void buildBody(LinearLayout body, int width) {
      if (!this.dialogue.text().isEmpty()) {
         body.addChild(new FormTextWidget(this.font, BedrockText.ui(this.dialogue.text()), width, FormTextWidget.Kind.TEXT));
      }

      for (NpcDialogue.Action action : this.dialogue.buttons()) {
         body.addChild(
            new FormButton(this.font, width, 24, BedrockText.ui(action.name()), null, () -> this.host.submit(this, Integer.toString(action.index())))
         );
      }
   }
}
