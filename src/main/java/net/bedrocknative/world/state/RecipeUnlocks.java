package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.packet.UnlockedRecipesPacket;

public final class RecipeUnlocks {
   public static final int MAX_RECIPES = 50000;
   private final Set<String> unlocked = new LinkedHashSet<>();
   private boolean known;
   private int version;

   public List<String> apply(UnlockedRecipesPacket packet) {
      List<String> added = new ArrayList<>();
      if (packet.getAction() == null) {
         return added;
      } else {
         switch (packet.getAction()) {
            case INITIALLY_UNLOCKED:
               this.unlocked.clear();
               this.addAll(packet, added);
               this.known = true;
               break;
            case NEWLY_UNLOCKED:
               this.addAll(packet, added);
               break;
            case REMOVE_UNLOCKED:
               packet.getUnlockedRecipes().forEach(this.unlocked::remove);
               break;
            case REMOVE_ALL:
               this.unlocked.clear();
               this.known = true;
               break;
            case EMPTY:
               return added;
         }

         this.version++;
         return added;
      }
   }

   private void addAll(UnlockedRecipesPacket packet, List<String> added) {
      for (String recipe : packet.getUnlockedRecipes()) {
         if (recipe != null && this.unlocked.size() < 50000 && this.unlocked.add(recipe)) {
            added.add(recipe);
         }
      }
   }

   public boolean isUnlocked(String recipeId) {
      return this.unlocked.contains(recipeId);
   }

   public boolean known() {
      return this.known;
   }

   public int size() {
      return this.unlocked.size();
   }

   public Set<String> snapshot() {
      return Set.copyOf(this.unlocked);
   }

   public int version() {
      return this.version;
   }

   public void clear() {
      this.unlocked.clear();
      this.known = false;
      this.version++;
   }
}
