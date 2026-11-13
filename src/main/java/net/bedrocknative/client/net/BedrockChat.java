package net.bedrocknative.client.net;

import java.util.List;
import java.util.UUID;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.text.ChatLines;
import net.bedrocknative.ui.HudData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOriginType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOutputMessage;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type;

public final class BedrockChat {
   private final Minecraft minecraft;
   private final ClientIdentity identity;

   public BedrockChat(Minecraft minecraft, ClientIdentity identity) {
      this.minecraft = minecraft;
      this.identity = identity;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof TextPacket || packet instanceof CommandOutputPacket || packet instanceof SetTitlePacket;
   }

   public void apply(BedrockPacket packet) {
      if (this.minecraft.player != null) {
         switch (packet) {
            case TextPacket text:
               this.onText(text);
               break;
            case SetTitlePacket title:
               this.onTitle(title);
               break;
            case CommandOutputPacket output:
               for (CommandOutputMessage message : output.getMessages()) {
                  MutableComponent line = BedrockText.message(message.getMessageId(), List.of(message.getParameters()), true);
                  this.system(output.getSuccessCount() > 0 ? line : line.withStyle(ChatFormatting.RED));
               }
               break;
            default:
         }
      }
   }

   private void onText(TextPacket text) {
      String message = text.getMessage();
      List<String> parameters = text.getParameters();
      boolean translate = text.isNeedsTranslation();
      Component line = ChatLines.line(text.getType(), text.getSourceName(), message, parameters, translate);
      switch (text.getType()) {
         case TIP:
         case POPUP:
            this.minecraft.gui.chatListener().handleOverlay(line);
            JsonUiHud.data().tip(translate ? BedrockText.translate(message, parameters) : message);
            break;
         case JUKEBOX_POPUP:
            this.minecraft.gui.hud.setOverlayMessage(line, true);
            JsonUiHud.data().jukebox(translate ? BedrockText.translate(message, parameters) : message);
            break;
         default:
            this.system(line);
      }
   }

   private void onTitle(SetTitlePacket title) {
      String text = title.getText() == null ? "" : title.getText().toString();
      BedrockNative.LOGGER.debug("Title {} '{}'", title.getType(), text);
      Hud hud = this.minecraft.gui.hud;
      switch (title.getType()) {
         case CLEAR:
            hud.clearTitles();
            break;
         case RESET:
            hud.clearTitles();
            hud.resetTitleTimes();
            break;
         case TITLE:
            hud.setTitle(BedrockText.format(text));
            break;
         case SUBTITLE:
            hud.setSubtitle(BedrockText.format(text));
            break;
         case ACTIONBAR:
            hud.setOverlayMessage(BedrockText.format(text), false);
            break;
         case TIMES:
            hud.setTimes(title.getFadeInTime(), title.getStayTime(), title.getFadeOutTime());
            break;
         case TITLE_JSON:
            hud.setTitle(BedrockText.rawText(text));
            break;
         case SUBTITLE_JSON:
            hud.setSubtitle(BedrockText.rawText(text));
            break;
         case ACTIONBAR_JSON:
            hud.setOverlayMessage(BedrockText.rawText(text), false);
      }

      jsonUiTitle(title, text);
   }

   private static void jsonUiTitle(SetTitlePacket title, String text) {
      HudData data = JsonUiHud.data();
      switch (title.getType()) {
         case CLEAR:
            data.clearTitles();
            break;
         case RESET:
            data.resetTitles();
            break;
         case TITLE:
            data.title(text);
            break;
         case SUBTITLE:
            data.subtitle(text);
            break;
         case ACTIONBAR:
            data.actionbar(text);
            break;
         case TIMES:
            data.times(title.getFadeInTime(), title.getStayTime(), title.getFadeOutTime());
            break;
         case TITLE_JSON:
            data.title(BedrockText.rawTextString(text));
            break;
         case SUBTITLE_JSON:
            data.subtitle(BedrockText.rawTextString(text));
            break;
         case ACTIONBAR_JSON:
            data.actionbar(BedrockText.rawTextString(text));
      }
   }

   private void system(Component message) {
      this.minecraft.gui.chatListener().handleSystemMessage(message, true);
   }

   public TextPacket chat(String message) {
      TextPacket packet = new TextPacket();
      packet.setType(Type.CHAT);
      packet.setSourceName(this.identity.username());
      packet.setMessage(message);
      packet.setNeedsTranslation(false);
      packet.setXuid(this.identity.xuid());
      packet.setPlatformChatId("");
      packet.setFilteredMessage("");
      return packet;
   }

   public CommandRequestPacket command(String command) {
      CommandRequestPacket packet = new CommandRequestPacket();
      packet.setCommand("/" + command);
      packet.setCommandOriginData(new CommandOriginData(CommandOriginType.PLAYER, UUID.randomUUID(), "", 0L));
      packet.setInternal(false);
      return packet;
   }
}
