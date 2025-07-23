package net.nennneko5787.nndplugin;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public class NicoNicoAudioTrack extends DelegatedAudioTrack {
    public final String heartBeatUrl;
    public final String hlsContentUrl;
    public final Map<String, List<String>> selectedOutput;
    public final String cookies;
    public final String nicoSid;
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();

    public NicoNicoAudioTrack(AudioTrackInfo trackInfo, String hlsContentUrl, Map<String, List<String>> selectedOutput,
            String cookies, String nicoSid, String heartBeatUrl) {
        super(trackInfo);
        this.hlsContentUrl = hlsContentUrl;
        this.selectedOutput = selectedOutput;
        this.cookies = cookies;
        this.nicoSid = nicoSid;
        this.heartBeatUrl = heartBeatUrl;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        sendHeartbeat();

        processDelegate(
                new NicoNicoHlsStreamTrack(trackInfo, hlsContentUrl, httpInterfaceManager,
                        cookies, false),
                executor);
    }

    void sendHeartbeat() throws IOException {
        HttpPost request = new HttpPost(heartBeatUrl);
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
        body.put("outputs", selectedOutput);

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
        additional.put("nicosid", nicoSid);
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

        request.setHeader("Cookie", cookies);

        try (CloseableHttpResponse response = httpInterfaceManager.getInterface().execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "heartbeat page");

            JsonBrowser.parse(response.getEntity().getContent()).get("data").format();
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NicoNicoAudioTrack(this.trackInfo, hlsContentUrl, selectedOutput, cookies, nicoSid, heartBeatUrl);
    }
}
