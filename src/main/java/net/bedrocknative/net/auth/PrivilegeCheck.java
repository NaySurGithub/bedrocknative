package net.bedrocknative.net.auth;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class PrivilegeCheck {
   public static final long MAX_WAIT_MS = 3000L;
   private final CompletableFuture<XboxPrivileges.Result> result;
   private final long startedAt;
   private final boolean waited;
   private final LongSupplier clock;
   @Nullable
   private final XboxAccountStore.Entry account;

   PrivilegeCheck(CompletableFuture<XboxPrivileges.Result> result, long startedAt, boolean waited, LongSupplier clock, @Nullable XboxAccountStore.Entry account) {
      this.result = result;
      this.startedAt = startedAt;
      this.waited = waited;
      this.clock = clock;
      this.account = account;
   }

   public static PrivilegeCheck none() {
      return new PrivilegeCheck(CompletableFuture.completedFuture(XboxPrivileges.Result.unknown("not checked")), 0L, false, () -> 0L, null);
   }

   public PrivilegeCheck unwaited() {
      return this.waited ? new PrivilegeCheck(this.result, this.startedAt, false, this.clock, this.account) : this;
   }

   public boolean finished() {
      return this.result.isDone();
   }

   @Nullable
   public XboxPrivileges.Result now() {
      return this.result.isDone() ? this.result.join() : null;
   }

   public boolean refuses() {
      XboxPrivileges.Result answer = this.now();
      return answer != null && answer.blocksJoining();
   }

   public boolean waited() {
      return this.waited;
   }

   public boolean holdsJoin() {
      return this.waited && !this.result.isDone() && this.clock.getAsLong() - this.startedAt < 3000L;
   }

   public long ageMillis() {
      return Math.max(0L, this.clock.getAsLong() - this.startedAt);
   }

   public XboxAccountStore.AccountException refusal() {
      XboxPrivileges.Result answer = this.now();
      if (answer != null && answer.blocksJoining()) {
         return this.refusal(answer);
      } else {
         throw new IllegalStateException("The check did not refuse: " + answer);
      }
   }

   private XboxAccountStore.AccountException refusal(XboxPrivileges.Result answer) {
      return new XboxAccountStore.AccountException(
         XboxAccountStore.Problem.FAILED, this.account, "The Xbox account may not play online multiplayer: " + answer, null, answer.diagnosis()
      );
   }

   public void whenRefused(Consumer<XboxAccountStore.AccountException> action) {
      this.result.thenAccept(answer -> {
         if (answer != null && answer.blocksJoining()) {
            action.accept(this.refusal(answer));
         }
      });
   }

   public XboxPrivileges.Result await() {
      return this.result.join();
   }
}
