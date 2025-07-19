package net.nennneko5787.nndplugin;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * An extension of PersistentHttpStream that allows for sending heartbeats to a
 * secondary URL.
 */
public class HeartbeatingHttpStream extends PersistentHttpStream {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatingHttpStream.class);
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private String heartbeatUrl;
    private int heartbeatInterval;
    private Map<String, List<String>> videoOutputs;
    private String nicosid;
    private String domandbid;

    private ScheduledFuture<?> heartbeatFuture;

    /**
     * Creates a new heartbeating http stream.
     *
     * @param httpInterface     The HTTP interface to use for requests.
     * @param contentUrl        The URL to play from.
     * @param contentLength     The length of the content. Null if unknown.
     * @param heartbeatUrl      The URL to send heartbeat requests to.
     * @param heartbeatInterval The interval at which to heartbeat, in milliseconds.
     * @param outputs           Video output.
     * @param nicosid           The NicoNico nicosid.
     * @param domandbid         The NicoNico domandbid.
     */
    public HeartbeatingHttpStream(
            HttpInterface httpInterface,
            URI contentUrl,
            Long contentLength,
            String heartbeatUrl,
            int heartbeatInterval,
            Map<String, List<String>> outputs,
            String nicosid, String domandbid) {
        super(httpInterface, contentUrl, contentLength);

        this.heartbeatUrl = heartbeatUrl;
        this.heartbeatInterval = heartbeatInterval;
        this.videoOutputs = outputs;
        this.nicosid = nicosid;
        this.domandbid = domandbid;

        setupHeartbeat();
    }

    private HttpGet getConnectRequest() {
        HttpGet request = new HttpGet(getConnectUrl());

        if (position > 0 && useHeadersForRange()) {
            request.setHeader(HttpHeaders.RANGE, "bytes=" + position + "-");
        }

        if (nicosid != null && domandbid != null) {
            request.setHeader("Cookie", "nicosid=" + nicosid + "; domand_bid=" + domandbid);
        }

        return request;
    }

    protected void setupHeartbeat() {
        log.debug("Heartbeat every {} milliseconds to URL: {}", heartbeatInterval, heartbeatUrl);

        heartbeatFuture = executor.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Throwable t) {
                log.error("Heartbeat error!", t);
                IOUtils.closeQuietly(this);
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    protected void sendHeartbeat() throws IOException {
        HttpPost request = new HttpPost(heartbeatUrl);
        request.setHeader("User-Agent", "Lavalink-NicoNico-Plugin");
        request.setHeader("X-Frontend-Id", "6");
        request.setHeader("X-Frontend-Version", "0");
        request.setHeader("X-Niconico-Language", "ja-jp");
        request.setHeader("X-Client-Os-Type", "others");
        request.setHeader("X-Request-With", "https://www.nicovideo.jp");
        request.setHeader("Referer", "https://www.nicovideo.jp/");

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
        String eventOccurredAt = now.toString();

        long accessStart = now.toEpochSecond();
        long loadingStart = accessStart + 10000;

        // JSON組み立て
        JsonBrowser body = JsonBrowser.newMap();
        body.put("outputs", videoOutputs);

        JsonBrowser heartbeat = JsonBrowser.newMap();
        heartbeat.put("method", "regular");

        JsonBrowser params = JsonBrowser.newMap();
        params.put("eventType", "start");
        params.put("eventOccurredAt", eventOccurredAt);
        params.put("watchMilliseconds", 0);
        params.put("endCount", 0);

        JsonBrowser additional = JsonBrowser.newMap();
        additional.put("___pc_v", 1);
        additional.put("os", "Windows");
        additional.put("os_version", "15.0.0");
        additional.put("nicosid", nicosid);
        additional.put("referer", "");
        additional.put("query_parameters", JsonBrowser.newMap());
        additional.put("is_ad_block", false);
        additional.put("has_playlist", false);
        additional.put("___abw", null);
        additional.put("abw_show", false);
        additional.put("abw_closed", false);
        additional.put("abw_seen_at", null);
        additional.put("viewing_source", "");
        additional.put("viewing_source_detail", JsonBrowser.newMap());
        additional.put("playback_rate", "");
        additional.put("use_flip", false);
        additional.put("quality", JsonBrowser.newList());
        additional.put("auto_quality", JsonBrowser.newList());
        additional.put("loop_count", 0);
        additional.put("suspend_count", 0);
        additional.put("load_failed", false);
        additional.put("error_description", JsonBrowser.newList());
        additional.put("end_position_milliseconds", null);

        JsonBrowser performance = JsonBrowser.newMap();
        performance.put("watch_access_start", accessStart);
        performance.put("watch_access_finish", null);
        performance.put("video_loading_start", loadingStart);
        performance.put("video_loading_finish", null);
        performance.put("video_play_start", null);

        JsonBrowser endContext = JsonBrowser.newMap();
        endContext.put("ad_playing", false);
        endContext.put("video_playing", false);
        endContext.put("is_suspending", false);
        performance.put("end_context", endContext);

        additional.put("performance", performance);
        params.put("additionalParameters", additional);
        heartbeat.put("params", params);
        body.put("heartbeat", heartbeat);

        request.setEntity(new StringEntity(body.format()));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "heartbeat page");

            var response2 = JsonBrowser.parse(response.getEntity().getContent()).get("data").format();
        }
    }

    @Override
    public void close() throws IOException {
        heartbeatFuture.cancel(false);
        super.close();
    }
}
