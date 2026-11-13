package net.bedrocknative.molang;

public final class MolangStruct {
   final VariableStore members;

   public MolangStruct() {
      this(new VariableStore(4));
   }

   private MolangStruct(VariableStore members) {
      this.members = members;
   }

   public MolangStruct copy() {
      return new MolangStruct(this.members.copy());
   }

   public VariableStore members() {
      return this.members;
   }

   public static MolangStruct xyz(float x, float y, float z) {
      MolangStruct struct = new MolangStruct();
      struct.members.set(MolangNames.id("x"), x, null);
      struct.members.set(MolangNames.id("y"), y, null);
      struct.members.set(MolangNames.id("z"), z, null);
      return struct;
   }

   @Override
   public String toString() {
      StringBuilder text = new StringBuilder("{");
      this.members
         .forEachId(
            id -> text.append(text.length() > 1 ? ", " : "")
               .append(MolangNames.name(id))
               .append('=')
               .append(this.members.object(id) != null ? this.members.object(id) : this.members.get(id))
         );
      return text.append('}').toString();
   }
}
