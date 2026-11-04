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

import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.Plugin;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.downloader.Downloader;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.event.Event;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.event.impl.DownloadEvent;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.event.impl.LogEvent;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.signalbus.EventConsumer;
import org.lro.steven.aq.simpleminecraftlauncher.plugin.api.signalbus.SignalBus;

import java.io.IOException;

/** 下载器插件：监听 {@code download.file}/{@code download.read} 信号。 */
public class DownloaderPlugin implements Plugin {

    private final Downloader downloader = new HttpDownloader();

    @Override
    public void start(SignalBus signalBus) {
        signalBus.connect("download.file", (EventConsumer) event -> handleFile(event, signalBus));
        signalBus.connect("download.read", (EventConsumer) event -> handleRead(event, signalBus));
        signalBus.emit("log.info", LogEvent.of("Downloader plugin started"));
    }

    private void handleFile(Event event, SignalBus signalBus) {
        if (!(event instanceof DownloadEvent download)) {
            return;
        }
        download.handled(true);
        try {
            downloader.download(download.url(), download.target());
        } catch (IOException e) {
            download.fail(e);
            signalBus.emit("log.error", LogEvent.of("Download failed: " + download.url() + ": " + e.getMessage()));
        }
    }

    private void handleRead(Event event, SignalBus signalBus) {
        if (!(event instanceof DownloadEvent download)) {
            return;
        }
        download.handled(true);
        try {
            download.content(downloader.readString(download.url()));
        } catch (IOException e) {
            download.fail(e);
            signalBus.emit("log.error", LogEvent.of("Read failed: " + download.url() + ": " + e.getMessage()));
        }
    }
}
