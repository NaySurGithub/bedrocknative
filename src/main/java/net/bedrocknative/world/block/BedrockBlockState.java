package net.bedrocknative.world.block;

import java.util.SortedMap;
import java.util.StringJoiner;

public record BedrockBlockState(String name, SortedMap<String, Object> states, int networkHash) {
   public String toStateString() {
      StringJoiner joiner = new StringJoiner(",", this.name + "[", "]");
      this.states.forEach((key, value) -> joiner.add(key + "=" + value));
      return joiner.toString();
   }
}
