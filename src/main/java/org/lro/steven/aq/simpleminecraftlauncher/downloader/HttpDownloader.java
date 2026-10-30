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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.downloader.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/** 基于 OkHttp 的下载器，支持基于 {@code .part} 的断点续传。 */
public final class HttpDownloader implements Downloader {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    public String readString(String url) throws IOException {
        try (Response response = execute(url)) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return response.body().string();
        }
    }

    @Override
    public void download(String url, Path target) throws IOException {
        if (url == null || url.isBlank()) {
            return;
        }
        if (isComplete(target)) {
            return;
        }
        downloadFrom(url, target, false);
    }

    private void downloadFrom(String url, Path target, boolean restarted) throws IOException {
        Path part = partFile(target);
        long existing = sizeOf(part);
        try (Response response = requestWithRange(url, existing)) {
            if (response.code() == 416) {
                handleUnsatisfiableRange(url, target, part, response, restarted);
                return;
            }
            downloadIntoPart(response, part, target, existing, url);
        }
    }

    private void handleUnsatisfiableRange(String url, Path target, Path part, Response response, boolean restarted)
            throws IOException {
        if (moveIfComplete(part, target, response)) {
            return;
        }
        Files.deleteIfExists(part);
        if (restarted) {
            throw new IOException("Server rejected range request: " + url);
        }
        downloadFrom(url, target, true);
    }

    private static void downloadIntoPart(Response response, Path part, Path target, long existing, String url)
            throws IOException {
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("HTTP " + response.code() + " for " + url);
        }
        boolean append = response.code() == 206;
        if (!append) {
            Files.deleteIfExists(part);
            existing = 0;
        }
        appendBody(response, part, append);
        verifyComplete(part, response, existing);
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Response requestWithRange(String url, long existing) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (existing > 0) {
            builder.header("Range", "bytes=" + existing + "-");
        }
        return CLIENT.newCall(builder.build()).execute();
    }

    private static void appendBody(Response response, Path part, boolean append) throws IOException {
        Files.createDirectories(part.getParent());
        OpenOption[] options = append
                ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
        try (InputStream input = response.body().byteStream();
             OutputStream output = Files.newOutputStream(part, options)) {
            input.transferTo(output);
        }
    }

    private static void verifyComplete(Path part, Response response, long existing) throws IOException {
        long total = totalSize(response, existing);
        if (total > 0 && Files.size(part) < total) {
            throw new IOException("Incomplete download: " + Files.size(part) + "/" + total);
        }
    }

    private static long totalSize(Response response, long existing) {
        long total = contentRangeTotal(response.header("Content-Range"));
        if (total > 0) {
            return total;
        }
        String contentLength = response.header("Content-Length");
        return contentLength == null ? 0 : existing + parseLong(contentLength);
    }

    private static long contentRangeTotal(String header) {
        if (header == null) {
            return 0;
        }
        int slash = header.lastIndexOf('/');
        return slash < 0 ? 0 : parseLong(header.substring(slash + 1));
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean moveIfComplete(Path part, Path target, Response response) throws IOException {
        long total = contentRangeTotal(response.header("Content-Range"));
        if (total > 0 && Files.isRegularFile(part) && Files.size(part) == total) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        }
        return false;
    }

    private static boolean isComplete(Path target) throws IOException {
        return Files.isRegularFile(target) && Files.size(target) > 0;
    }

    private static Path partFile(Path target) {
        return target.resolveSibling(target.getFileName() + ".part");
    }

    private static long sizeOf(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.size(path) : 0;
    }

    private static Response execute(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        return CLIENT.newCall(request).execute();
    }
}
