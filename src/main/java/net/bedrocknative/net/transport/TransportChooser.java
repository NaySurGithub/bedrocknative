package net.bedrocknative.net.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;

public final class TransportChooser {
   private static final TransportChooser.Wait WAIT = new TransportChooser.Wait();
   private final TransportChooser.Inputs in;
   private final EnumSet<TransportChooser.Probe> started = EnumSet.noneOf(TransportChooser.Probe.class);
   private final Map<TransportKind, Integer> joins = new EnumMap<>(TransportKind.class);
   private final Map<TransportKind, TransportChooser.JoinFailure> failures = new EnumMap<>(TransportKind.class);
   private final List<String> trail = new ArrayList<>();
   @Nullable
   private PingOutcome ping;
   @Nullable
   private Boolean javaAnswer;
   @Nullable
   private ProbeResult probe;
   private boolean overdue;
   private boolean trustAsked;
   @Nullable
   private Boolean trusted;
   @Nullable
   private TransportChooser.Step terminal;

   public TransportChooser(TransportChooser.Inputs inputs) {
      this.in = inputs;
      if (!inputs.javaAllowed()) {
         this.javaAnswer = false;
      }
   }

   public void pingOverdue() {
      if (this.ping == null && !this.overdue) {
         this.overdue = true;
         this.trail.add("ping overdue");
      }
   }

   public TransportChooser.Inputs inputs() {
      return this.in;
   }

   public TransportChooser.Step next() {
      if (this.terminal != null) {
         return this.terminal;
      } else {
         TransportChooser.Step step = this.decide();
         switch (step) {
            case TransportChooser.Start start:
               this.started.addAll(start.probes());
               this.trail.add("start " + start.probes());
               break;
            case TransportChooser.Join join:
               this.joins.merge(join.kind(), 1, Integer::sum);
               this.trail.add("join " + join.kind().shown());
               if (join.kind() == TransportKind.JAVA) {
                  this.terminal = step;
               }
               break;
            case TransportChooser.Fail fail:
               this.trail.add("fail " + fail.kind().shown());
               this.terminal = step;
               break;
            case TransportChooser.Stop stop:
               this.trail.add("stop");
               this.terminal = step;
               break;
            case TransportChooser.TrustPrompt prompt:
               this.trail.add("ask trust");
               break;
            case TransportChooser.Wait var9:
               break;
            default:
               throw new MatchException(null, null);
         }

         return step;
      }
   }

   public void pinged(PingOutcome outcome) {
      this.check(TransportChooser.Probe.RAKNET_PING, this.ping);
      this.ping = outcome;
      this.trail.add("ping " + outcome);
   }

   public void javaStatus(boolean answered) {
      this.check(TransportChooser.Probe.JAVA_STATUS, this.javaAnswer);
      this.javaAnswer = answered;
      this.trail.add("java " + (answered ? "answered" : "no"));
   }

   public void probed(ProbeResult result) {
      this.check(TransportChooser.Probe.NETHERNET_PROBE, this.probe);
      this.probe = result;
      this.trail.add("nethernet " + (result.supported() ? "ok" : result.reason().name().toLowerCase(Locale.ROOT)));
   }

   public void joinFailed(TransportKind kind, TransportChooser.JoinFailure failure) {
      if (kind.bedrock() && this.joins.getOrDefault(kind, 0) != 0) {
         this.failures.put(kind, failure);
         this.trail.add(kind.shown() + " failed " + failure);
      } else {
         throw new IllegalStateException("No " + kind + " join to fail");
      }
   }

   public void trust(boolean answer) {
      if (this.trustAsked && this.trusted == null) {
         this.trusted = answer;
         this.trail.add(answer ? "trusted" : "not trusted");
      } else {
         throw new IllegalStateException("Trust was not asked");
      }
   }

   private void check(TransportChooser.Probe probe, @Nullable Object outcome) {
      if (!this.started.contains(probe) || outcome != null) {
         throw new IllegalStateException(probe + (outcome != null ? " reported twice" : " was not started"));
      }
   }

   @Nullable
   public PingOutcome ping() {
      return this.ping;
   }

   @Nullable
   public Boolean javaAnswer() {
      return this.javaAnswer;
   }

   @Nullable
   public ProbeResult netherNetProbe() {
      return this.probe;
   }

   public boolean started(TransportChooser.Probe probe) {
      return this.started.contains(probe);
   }

   public String trail() {
      StringJoiner joiner = new StringJoiner(", ");
      this.trail.forEach(joiner::add);
      return joiner.toString();
   }

   private TransportChooser.Step decide() {
      if (this.in.forced() != null) {
         return this.forced(this.in.forced());
      } else {
         TransportKind first = this.in.preferred();
         if (first == TransportKind.JAVA && !this.in.javaAllowed()) {
            first = null;
         }

         boolean known = first != null && first.bedrock();
         if (first == null && !this.in.order().isEmpty() && this.in.order().getFirst() == TransportKind.NETHERNET) {
            first = TransportKind.NETHERNET;
         }

         if (first == TransportKind.NETHERNET && !this.in.netherNet()) {
            first = null;
         }

         if (first == TransportKind.JAVA) {
            Set<TransportChooser.Probe> toStart = EnumSet.noneOf(TransportChooser.Probe.class);
            if (!this.started(TransportChooser.Probe.JAVA_STATUS)) {
               toStart.add(TransportChooser.Probe.JAVA_STATUS);
            }

            if (!this.started(TransportChooser.Probe.RAKNET_PING)) {
               toStart.add(TransportChooser.Probe.RAKNET_PING);
            }

            if (!toStart.isEmpty()) {
               return start(toStart);
            }

            if (this.ping == PingOutcome.PONG) {
               return this.rakNet();
            }

            if (this.javaAnswer == null) {
               return WAIT;
            }

            if (this.javaAnswer) {
               return join(TransportKind.JAVA);
            }
         } else if (first == TransportKind.NETHERNET) {
            TransportChooser.Step step = this.netherNetFirst();
            if (step != null) {
               return step;
            }
         }

         return this.general(known);
      }
   }

   private TransportChooser.Step forced(TransportKind kind) {
      return (TransportChooser.Step)(switch (kind) {
         case JAVA -> join(TransportKind.JAVA);
         case RAKNET -> !this.started(TransportChooser.Probe.RAKNET_PING)
            ? start(TransportChooser.Probe.RAKNET_PING)
            : (this.ping == null ? WAIT : this.rakNet());
         case NETHERNET -> {
            if (!this.in.netherNet()) {
               yield new TransportChooser.Fail(TransportKind.NETHERNET);
            } else if (!this.started(TransportChooser.Probe.NETHERNET_PROBE)) {
               yield start(TransportChooser.Probe.NETHERNET_PROBE);
            } else if (this.probe == null) {
               yield WAIT;
            } else if (!this.probe.supported()) {
               yield new TransportChooser.Fail(TransportKind.NETHERNET);
            } else {
               TransportChooser.Step step = this.netherNet();
               yield step != null ? step : new TransportChooser.Fail(TransportKind.NETHERNET);
            }
         }
      });
   }

   @Nullable
   private TransportChooser.Step netherNetFirst() {
      if (!this.started(TransportChooser.Probe.NETHERNET_PROBE)) {
         return start(TransportChooser.Probe.NETHERNET_PROBE);
      } else if (this.probe == null) {
         return WAIT;
      } else if (!this.probe.supported()) {
         return null;
      } else {
         TransportChooser.Step step = this.netherNet();
         if (step != null) {
            return step;
         } else if (!this.started(TransportChooser.Probe.RAKNET_PING)) {
            return start(TransportChooser.Probe.RAKNET_PING);
         } else if (this.ping == null) {
            return WAIT;
         } else {
            return (TransportChooser.Step)(this.ping == PingOutcome.PONG ? this.rakNet() : new TransportChooser.Fail(TransportKind.NETHERNET));
         }
      }
   }

   @Nullable
   private TransportChooser.Step netherNet() {
      int done = this.joins.getOrDefault(TransportKind.NETHERNET, 0);
      if (done == 0) {
         return join(TransportKind.NETHERNET);
      } else {
         TransportChooser.JoinFailure failure = this.failures.get(TransportKind.NETHERNET);
         if (failure == null) {
            throw new IllegalStateException("The NetherNet join's outcome was not reported");
         } else {
            return (TransportChooser.Step)(switch (failure) {
               case NETHERNET_LINK -> null;
               case NETHERNET_UNTRUSTED -> {
                  if (!this.trustAsked) {
                     this.trustAsked = true;
                     yield new TransportChooser.TrustPrompt();
                  } else {
                     if (this.trusted == null) {
                        throw new IllegalStateException("The trust prompt's answer was not reported");
                     }

                     if (!this.trusted) {
                        yield new TransportChooser.Stop();
                     } else if (done == 1) {
                        this.failures.remove(TransportKind.NETHERNET);
                        yield join(TransportKind.NETHERNET);
                     } else {
                        yield new TransportChooser.Fail(TransportKind.NETHERNET);
                     }
                  }
               }
               default -> new TransportChooser.Fail(TransportKind.NETHERNET);
            });
         }
      }
   }

   private TransportChooser.Step general(boolean known) {
      Set<TransportChooser.Probe> toStart = EnumSet.noneOf(TransportChooser.Probe.class);
      if (!this.started(TransportChooser.Probe.RAKNET_PING)) {
         toStart.add(TransportChooser.Probe.RAKNET_PING);
      }

      if (!known && this.in.javaAllowed() && !this.started(TransportChooser.Probe.JAVA_STATUS)) {
         toStart.add(TransportChooser.Probe.JAVA_STATUS);
      }

      if (!toStart.isEmpty()) {
         return start(toStart);
      } else if (this.ping == null) {
         TransportChooser.Step early = known ? null : this.earlyJava();
         return (TransportChooser.Step)(early != null ? early : WAIT);
      } else {
         return switch (this.ping) {
            case PONG -> this.rakNet();
            case SILENT, NOT_PINGED -> known ? this.knownBedrockSilent() : (this.ping == PingOutcome.SILENT ? this.silent() : this.notPinged());
         };
      }
   }

   @Nullable
   private TransportChooser.Step earlyJava() {
      if (this.overdue && Boolean.TRUE.equals(this.javaAnswer)) {
         if (this.in.netherNet() && !this.in.javaSharesTcpPort()) {
            if (!this.started(TransportChooser.Probe.NETHERNET_PROBE)) {
               return start(TransportChooser.Probe.NETHERNET_PROBE);
            }

            if (this.probe == null) {
               return WAIT;
            }

            if (this.probe.supported()) {
               TransportChooser.Step step = this.netherNet();
               return (TransportChooser.Step)(step != null ? step : new TransportChooser.Fail(TransportKind.NETHERNET));
            }
         }

         return join(TransportKind.JAVA);
      } else {
         return null;
      }
   }

   private TransportChooser.Step knownBedrockSilent() {
      if (this.joins.getOrDefault(TransportKind.RAKNET, 0) == 0) {
         return join(TransportKind.RAKNET);
      } else {
         TransportChooser.JoinFailure failure = this.failures.get(TransportKind.RAKNET);
         if (failure == null) {
            throw new IllegalStateException("The RakNet join's outcome was not reported");
         } else {
            return (TransportChooser.Step)(failure == TransportChooser.JoinFailure.RAKNET_UNANSWERED
               ? this.silent()
               : new TransportChooser.Fail(TransportKind.RAKNET));
         }
      }
   }

   private TransportChooser.Step silent() {
      Set<TransportChooser.Probe> toStart = EnumSet.noneOf(TransportChooser.Probe.class);
      if (this.in.javaAllowed() && !this.started(TransportChooser.Probe.JAVA_STATUS)) {
         toStart.add(TransportChooser.Probe.JAVA_STATUS);
      }

      if (this.in.netherNet() && !this.started(TransportChooser.Probe.NETHERNET_PROBE)) {
         if (this.in.javaSharesTcpPort() && this.javaAnswer == null && !toStart.contains(TransportChooser.Probe.JAVA_STATUS)) {
            return WAIT;
         }

         if (!this.in.javaSharesTcpPort() || !Boolean.TRUE.equals(this.javaAnswer)) {
            toStart.add(TransportChooser.Probe.NETHERNET_PROBE);
         }
      }

      if (!toStart.isEmpty()) {
         return start(toStart);
      } else if (this.probe != null && this.probe.supported()) {
         TransportChooser.Step step = this.netherNet();
         return (TransportChooser.Step)(step != null ? step : new TransportChooser.Fail(TransportKind.NETHERNET));
      } else if (this.started(TransportChooser.Probe.NETHERNET_PROBE) && this.probe == null) {
         return WAIT;
      } else if (this.javaAnswer == null) {
         return WAIT;
      } else {
         return (TransportChooser.Step)(this.javaAnswer ? join(TransportKind.JAVA) : this.rakNet());
      }
   }

   private TransportChooser.Step notPinged() {
      if (this.in.javaAllowed() && !this.started(TransportChooser.Probe.JAVA_STATUS)) {
         return start(TransportChooser.Probe.JAVA_STATUS);
      } else if (this.javaAnswer == null) {
         return WAIT;
      } else if (this.javaAnswer) {
         return join(TransportKind.JAVA);
      } else if (this.joins.getOrDefault(TransportKind.RAKNET, 0) == 0) {
         return join(TransportKind.RAKNET);
      } else if (this.failures.get(TransportKind.RAKNET) != TransportChooser.JoinFailure.RAKNET_UNANSWERED || !this.in.netherNet()) {
         return new TransportChooser.Fail(TransportKind.RAKNET);
      } else if (!this.started(TransportChooser.Probe.NETHERNET_PROBE)) {
         return start(TransportChooser.Probe.NETHERNET_PROBE);
      } else if (this.probe == null) {
         return WAIT;
      } else if (!this.probe.supported()) {
         return new TransportChooser.Fail(TransportKind.RAKNET);
      } else {
         TransportChooser.Step step = this.netherNet();
         return (TransportChooser.Step)(step != null ? step : new TransportChooser.Fail(TransportKind.NETHERNET));
      }
   }

   private TransportChooser.Step rakNet() {
      if (this.joins.getOrDefault(TransportKind.RAKNET, 0) == 0) {
         return join(TransportKind.RAKNET);
      } else if (!this.failures.containsKey(TransportKind.RAKNET)) {
         throw new IllegalStateException("The RakNet join's outcome was not reported");
      } else {
         return new TransportChooser.Fail(TransportKind.RAKNET);
      }
   }

   private static TransportChooser.Start start(TransportChooser.Probe probe) {
      return new TransportChooser.Start(EnumSet.of(probe));
   }

   private static TransportChooser.Start start(Set<TransportChooser.Probe> probes) {
      return new TransportChooser.Start(probes);
   }

   private static TransportChooser.Join join(TransportKind kind) {
      return new TransportChooser.Join(kind);
   }

   public record Fail(TransportKind kind) implements TransportChooser.Step {
   }

   public record Inputs(
      @Nullable TransportKind forced,
      @Nullable TransportKind preferred,
      List<TransportKind> order,
      boolean netherNet,
      boolean javaSharesTcpPort,
      boolean javaAllowed
   ) {
      public Inputs(
         @Nullable TransportKind forced,
         @Nullable TransportKind preferred,
         List<TransportKind> order,
         boolean netherNet,
         boolean javaSharesTcpPort,
         boolean javaAllowed
      ) {
         order = List.copyOf(order);
         this.forced = forced;
         this.preferred = preferred;
         this.order = order;
         this.netherNet = netherNet;
         this.javaSharesTcpPort = javaSharesTcpPort;
         this.javaAllowed = javaAllowed;
      }

      public Inputs(@Nullable TransportKind forced, @Nullable TransportKind preferred, List<TransportKind> order, boolean netherNet, boolean javaSharesTcpPort) {
         this(forced, preferred, order, netherNet, javaSharesTcpPort, true);
      }
   }

   public record Join(TransportKind kind) implements TransportChooser.Step {
   }

   public static enum JoinFailure {
      RAKNET_ANSWERED,
      RAKNET_UNANSWERED,
      NETHERNET_LINK,
      NETHERNET_UNTRUSTED,
      UNAVAILABLE;
   }

   public static enum Probe {
      RAKNET_PING,
      JAVA_STATUS,
      NETHERNET_PROBE;
   }

   public record Start(Set<TransportChooser.Probe> probes) implements TransportChooser.Step {
      public Start(Set<TransportChooser.Probe> probes) {
         probes = probes.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(probes));
         this.probes = probes;
      }
   }

   public sealed interface Step
      permits TransportChooser.Start,
      TransportChooser.Wait,
      TransportChooser.Join,
      TransportChooser.TrustPrompt,
      TransportChooser.Fail,
      TransportChooser.Stop {
   }

   public record Stop() implements TransportChooser.Step {
   }

   public record TrustPrompt() implements TransportChooser.Step {
   }

   public record Wait() implements TransportChooser.Step {
   }
}
