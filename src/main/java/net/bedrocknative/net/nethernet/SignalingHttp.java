package net.bedrocknative.net.nethernet;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import net.bedrocknative.net.transport.SignalingScheme;
import org.jspecify.annotations.Nullable;

final class SignalingHttp {
   static final String USER_AGENT = "libhttpclient/1.0.0.0";
   static final int MAX_HEAD = 16384;

   private SignalingHttp() {
   }

   static Socket open(
      SignalingScheme scheme, String host, InetSocketAddress address, @Nullable X509ExtendedTrustManager trust, Duration connectTimeout, Duration readTimeout
   ) throws IOException {
      SignalingHttp.ObservedSocket socket = new SignalingHttp.ObservedSocket();
      SignalingHttp.Deadline deadline = new SignalingHttp.Deadline(socket, connectTimeout);

      Socket e;
      try {
         socket.connect(address, millis(connectTimeout));
         socket.setSoTimeout(millis(readTimeout));
         socket.setTcpNoDelay(true);
         if (scheme != SignalingScheme.HTTP) {
            SignalingHttp.PresentedCertificate presented = new SignalingHttp.PresentedCertificate(trust != null ? trust : defaultTrust());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{presented}, null);
            SSLSocket tls = (SSLSocket)context.getSocketFactory().createSocket(socket, unbracketed(host), address.getPort(), true);
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);

            try {
               tls.startHandshake();
            } catch (SocketTimeoutException var18) {
               if (socket.firstCount() != 0 && !deadline.expired()) {
                  throw new SignalingHttp.TlsFailure(presented.presented, TlsFallbackRule.classify(socket.first(), socket.firstCount()), var18);
               }

               throw var18;
            } catch (IOException var19) {
               if (deadline.expired()) {
                  throw deadline.timeout("no TLS handshake");
               }

               throw new SignalingHttp.TlsFailure(presented.presented, TlsFallbackRule.classify(socket.first(), socket.firstCount()), var19);
            }

            return deadline.opened(tls);
         }

         e = deadline.opened(socket);
      } catch (GeneralSecurityException | RuntimeException | IOException var20) {
         socket.close();
         if (deadline.expired()) {
            throw deadline.timeout("no connection");
         }

         if (var20 instanceof IOException io) {
            throw io;
         }

         throw new IOException(var20);
      } finally {
         deadline.close();
      }

      return e;
   }

   static SignalingHttp.Reply exchange(
      Socket socket, String method, String path, String host, int port, Map<String, String> headers, @Nullable byte[] body, int maxBody, Duration readTimeout
   ) throws IOException {
      SignalingHttp.Deadline deadline = new SignalingHttp.Deadline(socket, readTimeout);

      SignalingHttp.Reply e;
      try {
         e = exchangeNow(socket, method, path, host, port, headers, body, maxBody, readTimeout);
      } catch (IOException var14) {
         if (deadline.expired() && !(var14 instanceof SignalingHttp.TooLargeException)) {
            throw deadline.timeout("no complete answer");
         }

         throw var14;
      } finally {
         deadline.close();
      }

      return e;
   }

   private static SignalingHttp.Reply exchangeNow(
      Socket socket, String method, String path, String host, int port, Map<String, String> headers, @Nullable byte[] body, int maxBody, Duration readTimeout
   ) throws IOException {
      StringBuilder head = new StringBuilder(method).append(' ').append(path).append(" HTTP/1.1\r\n");
      head.append("Host: ").append(hostHeader(host, port)).append("\r\n");
      headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
      if (body != null) {
         head.append("Content-Length: ").append(body.length).append("\r\n");
      }

      head.append("Connection: close\r\n\r\n");
      OutputStream out = socket.getOutputStream();
      out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
      if (body != null) {
         out.write(body);
      }

      out.flush();
      long deadline = System.nanoTime() + readTimeout.toNanos();
      SignalingHttp.Reader in = new SignalingHttp.Reader(socket, socket.getInputStream(), deadline);
      String statusLine = in.line(16384);
      String[] parts = statusLine.split(" ", 3);
      if (parts.length >= 2 && parts[0].startsWith("HTTP/1.")) {
         int status;
         try {
            status = Integer.parseInt(parts[1]);
         } catch (NumberFormatException var25) {
            throw new IOException("not an HTTP status: " + printable(parts[1]));
         }

         Map<String, String> replyHeaders = new LinkedHashMap<>();
         int headSize = statusLine.length();

         for (String line = in.line(16384); !line.isEmpty(); line = in.line(16384)) {
            headSize += line.length();
            if (headSize > 16384) {
               throw new SignalingHttp.TooLargeException("answer head over 16384 bytes");
            }

            int colon = line.indexOf(58);
            if (colon > 0) {
               replyHeaders.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
         }

         String length = replyHeaders.get("content-length");
         byte[] replyBody;
         if ("chunked".equalsIgnoreCase(replyHeaders.getOrDefault("transfer-encoding", ""))) {
            replyBody = in.chunked(maxBody);
         } else if (length != null) {
            long declared;
            try {
               declared = Long.parseLong(length);
            } catch (NumberFormatException var24) {
               throw new IOException("bad Content-Length: " + printable(length));
            }

            if (declared > maxBody) {
               throw new SignalingHttp.TooLargeException("answer of " + declared + " bytes, over " + maxBody);
            }

            replyBody = in.exactly((int)declared);
         } else {
            replyBody = in.untilEnd(maxBody);
         }

         return new SignalingHttp.Reply(status, replyHeaders, replyBody);
      } else {
         throw new IOException("not an HTTP answer");
      }
   }

   static String hostHeader(String host, int port) {
      String plain = unbracketed(host);
      return (plain.indexOf(58) >= 0 ? "[" + plain + "]" : plain) + ":" + port;
   }

   private static String unbracketed(String host) {
      String trimmed = host.trim();
      return trimmed.startsWith("[") && trimmed.endsWith("]") ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
   }

   static String describe(Throwable error) {
      String message = error.getMessage();
      return message == null ? error.getClass().getSimpleName() : error.getClass().getSimpleName() + ": " + message;
   }

   static String printable(String text) {
      StringBuilder out = new StringBuilder();

      for (int i = 0; i < text.length() && out.length() < 200; i++) {
         char c = text.charAt(i);
         out.append(c >= ' ' && c <= '~' ? c : ' ');
      }

      return out.toString().strip();
   }

   private static int millis(Duration duration) {
      return Math.clamp(duration.toMillis(), 1, Integer.MAX_VALUE);
   }

   static X509ExtendedTrustManager defaultTrust() throws GeneralSecurityException {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init((KeyStore)null);

      for (TrustManager manager : factory.getTrustManagers()) {
         if (manager instanceof X509ExtendedTrustManager extended) {
            return extended;
         }
      }

      throw new GeneralSecurityException("no X.509 trust manager");
   }

   static final class Deadline implements AutoCloseable {
      private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
         Thread thread = new Thread(task, "NetherNet signaling deadline");
         thread.setDaemon(true);
         return thread;
      });
      private final long limitMillis;
      private final ScheduledFuture<?> task;
      private volatile boolean expired;

      Deadline(Socket socket, Duration limit) {
         this.limitMillis = limit.toMillis();
         this.task = TIMER.schedule(() -> {
            this.expired = true;

            try {
               socket.close();
            } catch (IOException var3) {
            }
         }, Math.max(1L, this.limitMillis), TimeUnit.MILLISECONDS);
      }

      boolean expired() {
         return this.expired;
      }

      SocketTimeoutException timeout(String what) {
         return new SocketTimeoutException(what + " in " + this.limitMillis + " ms");
      }

      Socket opened(Socket socket) throws IOException {
         this.task.cancel(false);
         if (this.expired) {
            socket.close();
            throw this.timeout("no connection");
         } else {
            return socket;
         }
      }

      @Override
      public void close() {
         this.task.cancel(false);
      }
   }

   static final class ObservedSocket extends Socket {
      private final byte[] first = new byte[16];
      private int firstCount;
      @Nullable
      private InputStream observed;

      @Override
      public synchronized InputStream getInputStream() throws IOException {
         if (this.observed == null) {
            this.observed = new FilterInputStream(super.getInputStream()) {
               {
                  Objects.requireNonNull(ObservedSocket.this);
               }

               @Override
               public int read() throws IOException {
                  int value = super.read();
                  if (value >= 0) {
                     ObservedSocket.this.keep(new byte[]{(byte)value}, 0, 1);
                  }

                  return value;
               }

               @Override
               public int read(byte[] buffer, int offset, int length) throws IOException {
                  int read = super.read(buffer, offset, length);
                  if (read > 0) {
                     ObservedSocket.this.keep(buffer, offset, read);
                  }

                  return read;
               }
            };
         }

         return this.observed;
      }

      private synchronized void keep(byte[] buffer, int offset, int length) {
         int room = Math.min(this.first.length - this.firstCount, length);
         if (room > 0) {
            System.arraycopy(buffer, offset, this.first, this.firstCount, room);
            this.firstCount += room;
         }
      }

      synchronized byte[] first() {
         return (byte[])this.first.clone();
      }

      synchronized int firstCount() {
         return this.firstCount;
      }
   }

   static final class PresentedCertificate extends X509ExtendedTrustManager {
      private final X509ExtendedTrustManager delegate;
      volatile boolean presented;

      PresentedCertificate(X509ExtendedTrustManager delegate) {
         this.delegate = delegate;
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
         this.presented = true;
         this.delegate.checkServerTrusted(chain, authType, socket);
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
         this.presented = true;
         this.delegate.checkServerTrusted(chain, authType, engine);
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
         this.presented = true;
         this.delegate.checkServerTrusted(chain, authType);
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
         throw new CertificateException("client certificates are not checked here");
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
         throw new CertificateException("client certificates are not checked here");
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
         throw new CertificateException("client certificates are not checked here");
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
         return this.delegate.getAcceptedIssuers();
      }
   }

   private static final class Reader {
      private final Socket socket;
      private final InputStream in;
      private final long deadline;

      Reader(Socket socket, InputStream in, long deadline) {
         this.socket = socket;
         this.in = in;
         this.deadline = deadline;
      }

      private int read() throws IOException {
         this.setTimeout();
         return this.in.read();
      }

      private int read(byte[] buffer, int offset, int length) throws IOException {
         this.setTimeout();
         return this.in.read(buffer, offset, length);
      }

      private void setTimeout() throws IOException {
         long left = (this.deadline - System.nanoTime()) / 1000000L;
         if (left <= 0L) {
            throw new SocketTimeoutException("no complete answer in time");
         } else {
            this.socket.setSoTimeout((int)Math.min(left, 2147483647L));
         }
      }

      String line(int max) throws IOException {
         ByteArrayOutputStream line = new ByteArrayOutputStream();

         do {
            int b = this.read();
            if (b < 0) {
               throw new IOException("the answer ended inside its head");
            }

            if (b == 10) {
               return line.toString(StandardCharsets.ISO_8859_1);
            }

            if (b != 13) {
               line.write(b);
            }
         } while (line.size() <= max);

         throw new SignalingHttp.TooLargeException("answer line over " + max + " bytes");
      }

      byte[] exactly(int length) throws IOException {
         byte[] data = new byte[length];
         int done = 0;

         while (done < length) {
            int read = this.read(data, done, length - done);
            if (read < 0) {
               throw new IOException("the answer ended after " + done + " of " + length + " bytes");
            }

            done += read;
         }

         return data;
      }

      byte[] untilEnd(int max) throws IOException {
         ByteArrayOutputStream data = new ByteArrayOutputStream();
         byte[] buffer = new byte[4096];

         for (int read = this.read(buffer, 0, buffer.length); read >= 0; read = this.read(buffer, 0, buffer.length)) {
            data.write(buffer, 0, read);
            if (data.size() > max) {
               throw new SignalingHttp.TooLargeException("answer over " + max + " bytes");
            }
         }

         return data.toByteArray();
      }

      byte[] chunked(int max) throws IOException {
         ByteArrayOutputStream data = new ByteArrayOutputStream();

         while (true) {
            String sizeLine = this.line(16384);
            int semicolon = sizeLine.indexOf(59);

            int size;
            try {
               size = Integer.parseInt((semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
            } catch (NumberFormatException var7) {
               throw new IOException("bad chunk size: " + SignalingHttp.printable(sizeLine));
            }

            if (size < 0 || (long)data.size() + size > max) {
               throw new SignalingHttp.TooLargeException("answer over " + max + " bytes");
            }

            if (size == 0) {
               String trailer = this.line(16384);

               while (!trailer.isEmpty()) {
                  trailer = this.line(16384);
               }

               return data.toByteArray();
            }

            data.write(this.exactly(size));
            this.line(2);
         }
      }
   }

   record Reply(int status, Map<String, String> headers, byte[] body) {
      String text() {
         return new String(this.body, StandardCharsets.UTF_8);
      }
   }

   static final class TlsFailure extends IOException {
      final boolean certificatePresented;
      final TlsFallbackRule.PeerReply reply;

      TlsFailure(boolean certificatePresented, TlsFallbackRule.PeerReply reply, IOException cause) {
         super((certificatePresented ? "certificate refused" : "TLS failed, " + reply) + ": " + SignalingHttp.describe(cause), cause);
         this.certificatePresented = certificatePresented;
         this.reply = reply;
      }
   }

   static final class TooLargeException extends IOException {
      TooLargeException(String message) {
         super(message);
      }
   }
}
