package net.bedrocknative.text;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket.Type;

public final class ChatLines {
   private ChatLines() {
   }

   public static Component line(Type type, String sourceName, String message, List<String> parameters, boolean translate) {
      MutableComponent text = type != Type.JSON && type != Type.WHISPER_JSON && type != Type.ANNOUNCEMENT_JSON
         ? BedrockText.message(message, parameters, translate)
         : BedrockText.rawText(message);
      String source = sourceName == null ? "" : sourceName;

      return switch (type) {
         case CHAT -> source.isEmpty() ? text : Component.translatable("chat.type.text", BedrockText.format(source), text);
         case WHISPER -> Component.translatable("commands.message.display.incoming", BedrockText.format(source), text)
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
         default -> text;
      };
   }
}
