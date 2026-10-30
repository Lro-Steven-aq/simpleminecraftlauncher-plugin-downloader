/*
 * Copyright (C) ${year} Lro-Steven.aq
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.lro.steven.aq.simpleminecraftlauncher.downloader;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDownloaderTest {

    private static final byte[] CONTENT = "hello downloader".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String url;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            long start = 0;
            if (range != null && range.startsWith("bytes=")) {
                String spec = range.substring("bytes=".length());
                start = Long.parseLong(spec.substring(0, spec.indexOf('-')));
            }
            if (start >= CONTENT.length) {
                exchange.getResponseHeaders().add("Content-Range", "bytes */" + CONTENT.length);
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
            byte[] body = Arrays.copyOfRange(CONTENT, (int) start, CONTENT.length);
            if (start > 0) {
                exchange.getResponseHeaders().add("Content-Range",
                        "bytes " + start + "-" + (CONTENT.length - 1) + "/" + CONTENT.length);
                exchange.sendResponseHeaders(206, body.length);
            } else {
                exchange.sendResponseHeaders(200, body.length);
            }
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void downloadsWholeFile(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("file.bin");
        new HttpDownloader().download(url, target);
        assertArrayEquals(CONTENT, Files.readAllBytes(target));
    }

    @Test
    void resumesFromPartFile(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("file.bin");
        Files.write(directory.resolve("file.bin.part"), Arrays.copyOf(CONTENT, 6));

        new HttpDownloader().download(url, target);

        assertArrayEquals(CONTENT, Files.readAllBytes(target));
        assertFalse(Files.exists(directory.resolve("file.bin.part")));
    }

    @Test
    void readsStringContent() throws IOException {
        assertTrue(new HttpDownloader().readString(url).startsWith("hello"));
    }
}
