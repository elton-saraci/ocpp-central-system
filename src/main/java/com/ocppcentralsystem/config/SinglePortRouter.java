package com.ocppcentralsystem.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Routes every incoming connection on the single public port to either the
 * Spring Boot HTTP server or the OCPP WebSocket server, depending on whether
 * the initial HTTP request is a WebSocket upgrade.
 *
 * This is required on platforms like Render that only expose a single port
 * ($PORT) to the internet. Charge points connect to the same public port and
 * their OCPP upgrade request is forwarded to the OCPP server on
 * {@code websocket.port}, while normal REST traffic goes to the HTTP server
 * on {@code server.port}.
 *
 * <p>Enabled only when the "single-port" Spring profile is active
 * (router.enabled=true, see application-single-port.yml). Platforms that can
 * expose multiple ports (local Docker, VMs, etc.) leave it disabled and run
 * the plain two-port setup instead.
 */
@Slf4j
@ConditionalOnProperty(prefix = "router", name = "enabled", havingValue = "true", matchIfMissing = false)
@Component
public class SinglePortRouter implements ApplicationRunner {

    private static final int MAX_HEADER_BYTES = 65536;
    private static final int READ_TIMEOUT_MS = 15000;

    @Value("${public.port:7070}")
    private int publicPort;

    @Value("${server.port:8081}")
    private int httpPort;

    @Value("${websocket.port:8080}")
    private int websocketPort;

    private ServerSocket serverSocket;

    @Override
    public void run(ApplicationArguments args) {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", publicPort));
            log.info("SinglePortRouter listening on 0.0.0.0:{} -> HTTP(127.0.0.1:{}), OCPP/WebSocket(127.0.0.1:{})",
                    publicPort, httpPort, websocketPort);
            while (!serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    Thread thread = new Thread(() -> handle(client));
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException e) {
                    if (serverSocket.isClosed()) break;
                    log.warn("Accept failed: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("SinglePortRouter could not bind 0.0.0.0:{} - {}", publicPort, e.getMessage());
        }
    }

    private void handle(Socket client) {
        Socket backend = null;
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(READ_TIMEOUT_MS);
            InputStream clientIn = client.getInputStream();

            // Read the HTTP request head to decide where to route this connection.
            ByteArrayOutputStream headBuf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            boolean websocket = false;
            while (true) {
                int n = clientIn.read(chunk);
                if (n < 0) break;
                headBuf.write(chunk, 0, n);
                String head = new String(headBuf.toByteArray(), StandardCharsets.ISO_8859_1);
                if (head.contains("\r\n\r\n")) {
                    websocket = head.toLowerCase().contains("upgrade: websocket");
                    break;
                }
                if (headBuf.size() > MAX_HEADER_BYTES) break;
            }

            int targetPort = websocket ? websocketPort : httpPort;
            backend = new Socket("127.0.0.1", targetPort);
            backend.setTcpNoDelay(true);
            OutputStream backendOut = backend.getOutputStream();
            backendOut.write(headBuf.toByteArray());
            backendOut.flush();

            // The read timeout was only for sniffing the request head. Disable it
            // before the relay phase so long-lived (idle) WebSocket connections
            // are not dropped.
            client.setSoTimeout(0);

            Thread upstream = new Thread(() -> copy(clientIn, backendOut));
            upstream.setDaemon(true);
            upstream.start();
            copy(backend.getInputStream(), client.getOutputStream());
            upstream.join(200);
        } catch (IOException e) {
            // client or backend closed the connection
        } catch (Exception e) {
            log.warn("SinglePortRouter error: {}", e.getMessage());
        } finally {
            closeQuietly(backend);
            closeQuietly(client);
        }
    }

    private void copy(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
            // stream closed
        } finally {
            closeQuietly(out);
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        closeQuietly(serverSocket);
    }
}
