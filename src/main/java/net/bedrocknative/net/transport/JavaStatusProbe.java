package net.bedrocknative.net.transport;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

public final class JavaStatusProbe {
   static final int MAX_FRAME = 131072;
   private static final int STATUS_INTENT = 1;
   public static final Duration TOTAL_DEADLINE = Duration.ofSeconds(4L);
   public static final JavaStatusProbe.Resolver PLAIN = (host, port) -> {
      InetSocketAddress address = new InetSocketAddress(host, port);
      return address.isUnresolved() ? null : address;
   };

   private JavaStatusProbe() {
   }

   public static CompletableFuture<JavaStatus> probeAsync(
      String host, int port, JavaStatusProbe.Resolver resolver, int protocol, Duration connectTimeout, Duration readTimeout
   ) {
      return probeAsync(host, port, resolver, protocol, connectTimeout, readTimeout, JavaStatusProbe.Options.LIST);
   }

   public static CompletableFuture<JavaStatus> probeAsync(
      String host, int port, JavaStatusProbe.Resolver resolver, int protocol, Duration connectTimeout, Duration readTimeout, JavaStatusProbe.Options options
   ) {
      CompletableFuture<JavaStatus> result = new CompletableFuture<>();
      AtomicReference<Socket> open = new AtomicReference<>();
      result.whenComplete((status, error) -> {
         if (result.isCancelled()) {
            closeQuietly(open.get());
         }
      });
      Thread.ofVirtual()
         .name("Java status probe")
         .start(() -> result.complete(probe(host, port, resolver, protocol, connectTimeout, readTimeout, options, open, result::isCancelled)));
      return result;
   }

   private static void closeQuietly(@Nullable Socket socket) {
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException var2) {
         }
      }
   }

   public static JavaStatus probe(String host, int port, JavaStatusProbe.Resolver resolver, int protocol, Duration connectTimeout, Duration readTimeout) {
      return probe(host, port, resolver, protocol, connectTimeout, readTimeout, JavaStatusProbe.Options.LIST);
   }

   public static JavaStatus probe(
      String host, int port, JavaStatusProbe.Resolver resolver, int protocol, Duration connectTimeout, Duration readTimeout, JavaStatusProbe.Options options
   ) {
      return probe(host, port, resolver, protocol, connectTimeout, readTimeout, options, null, () -> false);
   }

   private static JavaStatus probe(
      String host,
      int port,
      JavaStatusProbe.Resolver resolver,
      int protocol,
      Duration connectTimeout,
      Duration readTimeout,
      JavaStatusProbe.Options options,
      @Nullable AtomicReference<Socket> open,
      BooleanSupplier cancelled
   ) {
      InetSocketAddress address;
      try {
         address = resolver.resolve(host, port);
      } catch (IOException | RuntimeException e) {
         return JavaStatus.none(null, "unknown host (" + e.getClass().getSimpleName() + ")");
      }

      if (address != null && !address.isUnresolved()) {
         long start = System.nanoTime();
         long deadline = start + options.total().toNanos();

         try (Socket socket = new Socket()) {
            if (open != null) {
               open.set(socket);
            }

            if (cancelled.getAsBoolean()) {
               return JavaStatus.none(address, "cancelled");
            }

            socket.setTcpNoDelay(true);
            socket.connect(address, (int)Math.max(1L, Math.min(connectTimeout.toMillis(), remainingMillis(deadline))));
            OutputStream out = socket.getOutputStream();
            InputStream in = new JavaStatusProbe.DeadlineInput(socket, readTimeout.toMillis(), deadline);
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            request.write(frame(handshake(protocol, host, port)));
            request.write(frame(new byte[]{0}));
            out.write(request.toByteArray());
            out.flush();
            long sent = System.nanoTime();
            ByteBuffer response = ByteBuffer.wrap(readFrame(in));
            if (readVarInt(response) != 0) {
               return JavaStatus.none(address, "not a Java status (another packet)");
            }

            int length = readVarInt(response);
            if (length < 0 || length > response.remaining()) {
               return JavaStatus.none(address, "not a Java status (bad string)");
            }

            String json = new String(response.array(), response.position(), length, StandardCharsets.UTF_8);
            long latency = millisSince(sent);
            JavaStatus status = JavaStatus.parse(json, address, latency);
            if (!status.answered() || !options.ping()) {
               return status;
            }

            long payload = System.currentTimeMillis();

            try {
               ByteBuffer ping = ByteBuffer.allocate(9).put((byte)1).putLong(payload);
               out.write(frame(ping.array()));
               out.flush();
               long pingSent = System.nanoTime();
               ByteBuffer pong = ByteBuffer.wrap(readFrame(in));
               if (readVarInt(pong) == 1 && pong.remaining() >= 8 && pong.getLong() == payload) {
                  latency = millisSince(pingSent);
               }
            } catch (IOException var33) {
            }

            return new JavaStatus(true, address, status.motd(), status.online(), status.max(), status.version(), status.protocol(), latency, "");
         } catch (SocketTimeoutException e) {
            return JavaStatus.none(address, "no answer in " + millisSince(start) + " ms");
         } catch (ConnectException e) {
            return JavaStatus.none(address, "refused");
         } catch (EOFException e) {
            return JavaStatus.none(address, "closed without a Java status");
         } catch (IOException | RuntimeException e) {
            return JavaStatus.none(
               address, cancelled.getAsBoolean() ? "cancelled" : e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage())
            );
         }
      } else {
         return JavaStatus.none(null, "unknown host");
      }
   }

   private static long remainingMillis(long deadlineNanos) {
      return (deadlineNanos - System.nanoTime()) / 1000000L;
   }

   private static long millisSince(long nanos) {
      return (System.nanoTime() - nanos) / 1000000L;
   }

   static byte[] handshake(int protocol, String host, int port) throws IOException {
      ByteArrayOutputStream packet = new ByteArrayOutputStream();
      writeVarInt(packet, 0);
      writeVarInt(packet, protocol);
      byte[] name = host.getBytes(StandardCharsets.UTF_8);
      writeVarInt(packet, name.length);
      packet.write(name);
      packet.write(port >> 8 & 0xFF);
      packet.write(port & 0xFF);
      writeVarInt(packet, 1);
      return packet.toByteArray();
   }

   static byte[] frame(byte[] packet) throws IOException {
      ByteArrayOutputStream framed = new ByteArrayOutputStream(packet.length + 5);
      writeVarInt(framed, packet.length);
      framed.write(packet);
      return framed.toByteArray();
   }

   private static byte[] readFrame(InputStream in) throws IOException {
      int length = readVarInt(in);
      if (length > 0 && length <= 131072) {
         byte[] frame = in.readNBytes(length);
         if (frame.length < length) {
            throw new EOFException();
         } else {
            return frame;
         }
      } else {
         throw new IOException("not a Java frame (length " + length + ")");
      }
   }

   static void writeVarInt(OutputStream out, int value) throws IOException {
      int rest;
      for (rest = value; (rest & -128) != 0; rest >>>= 7) {
         out.write(rest & 127 | 128);
      }

      out.write(rest);
   }

   static int readVarInt(InputStream in) throws IOException {
      int value = 0;

      for (int i = 0; i < 5; i++) {
         int read = in.read();
         if (read < 0) {
            throw new EOFException();
         }

         value |= (read & 127) << 7 * i;
         if ((read & 128) == 0) {
            return value;
         }
      }

      throw new IOException("VarInt too long");
   }

   static int readVarInt(ByteBuffer in) throws IOException {
      int value = 0;

      for (int i = 0; i < 5; i++) {
         if (!in.hasRemaining()) {
            throw new EOFException();
         }

         int read = in.get() & 255;
         value |= (read & 127) << 7 * i;
         if ((read & 128) == 0) {
            return value;
         }
      }

      throw new IOException("VarInt too long");
   }

   private static final class DeadlineInput extends InputStream {
      private final Socket socket;
      private final InputStream in;
      private final long readMillis;
      private final long deadline;

      DeadlineInput(Socket socket, long readMillis, long deadline) throws IOException {
         this.socket = socket;
         this.in = socket.getInputStream();
         this.readMillis = Math.max(1L, readMillis);
         this.deadline = deadline;
      }

      private void arm() throws IOException {
         long left = JavaStatusProbe.remainingMillis(this.deadline);
         if (left <= 0L) {
            throw new SocketTimeoutException("total deadline");
         }

         this.socket.setSoTimeout((int)Math.max(1L, Math.min(this.readMillis, left)));
      }

      @Override
      public int read() throws IOException {
         this.arm();
         return this.in.read();
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
         this.arm();
         return this.in.read(buffer, offset, length);
      }
   }

   public record Options(boolean ping, Duration total) {
      public static final JavaStatusProbe.Options LIST = new JavaStatusProbe.Options(true, JavaStatusProbe.TOTAL_DEADLINE);
      public static final JavaStatusProbe.Options JOIN = new JavaStatusProbe.Options(false, JavaStatusProbe.TOTAL_DEADLINE);
   }

   @FunctionalInterface
   public interface Resolver {
      @Nullable InetSocketAddress resolve(String var1, int var2) throws IOException;
   }
}
