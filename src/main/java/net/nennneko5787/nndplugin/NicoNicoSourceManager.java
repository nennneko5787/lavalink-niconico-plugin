package net.nennneko5787.nndplugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

public class NicoNicoSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(NicoNicoSourceManager.class);

    public static final Pattern URL_PATTERN = Pattern.compile(
            "(https?:\\/\\/)?(www\\.)?(?:nicovideo\\.jp\\/watch|nico\\.ms)\\/(?<videoId>[a-z]{2}\\d+)");

    private final HttpInterfaceManager httpInterfaceManager;
    private String domandBid;
    private String nicosid;
    private JsonBrowser videoInfo;
    private Map<String, List<String>> outputs;
    private String outputLabel;
    private String hslContentUrl;
    private String cookies;

    public NicoNicoSourceManager() {
        httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "niconico";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        var matcher = URL_PATTERN.matcher(reference.identifier);
        if (!matcher.find()) {
            return null;
        }
        var videoId = matcher.group("videoId");

        JsonBrowser info = fetchVideoInfo(videoId);
        if (info == null || info.isNull()) {
            log.warn("Failed to fetch or parse video info for videoId: {}", videoId);
        }
        this.videoInfo = info;

        outputs = extractAvailableStreams(videoInfo, true);
        outputLabel = outputs.keySet().stream().findFirst().orElse(null);
        hslContentUrl = requestAccess(videoInfo, Map.of(outputLabel, outputs.get(outputLabel)));

        return new NicoNicoAudioTrack(
                new AudioTrackInfo(videoInfo.get("data").get("response").get("video").get("title").safeText(),
                        videoInfo.get("data").get("response").get("owner").get("nickname").safeText(),
                        videoInfo.get("data").get("response").get("video").get("duration").asLong(0),
                        getWatchUrl(), false, getWatchUrl()),
                getHslContentUrl(), getSelectedOutput(),
                getCookies(), getNicoSid(), getHeartbeatUrl());
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        NicoNicoAudioTrack nicoTrack = (NicoNicoAudioTrack) track;
        output.writeUTF(nicoTrack.hlsContentUrl);
        output.writeUTF(nicoTrack.cookies);
        output.writeUTF(nicoTrack.nicoSid);
        output.writeUTF(nicoTrack.heartBeatUrl);

        // 手動で Map<String, List<String>> をシリアライズ
        Map<String, List<String>> outputs = nicoTrack.selectedOutput;
        output.writeInt(outputs.size());
        for (Map.Entry<String, List<String>> entry : outputs.entrySet()) {
            output.writeUTF(entry.getKey());
            List<String> list = entry.getValue();
            output.writeInt(list.size());
            for (String item : list) {
                output.writeUTF(item);
            }
        }
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        String hlsContentUrl = input.readUTF();
        String cookies = input.readUTF();
        String nicoSid = input.readUTF();
        String heartBeatUrl = input.readUTF();

        // 手動で Map<String, List<String>> をデシリアライズ
        int size = input.readInt();
        Map<String, List<String>> outputs = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = input.readUTF();
            int listSize = input.readInt();
            List<String> list = new ArrayList<>();
            for (int j = 0; j < listSize; j++) {
                list.add(input.readUTF());
            }
            outputs.put(key, list);
        }

        return new NicoNicoAudioTrack(trackInfo, hlsContentUrl, outputs, cookies, nicoSid, heartBeatUrl);
    }

    @Override
    public void shutdown() {
        // Nothing to shut down
    }

    public HttpInterfaceManager getHttpInterfaceManager() {
        return httpInterfaceManager;
    }

    /**
     * @return Get an HTTP interface for a playing track.
     */
    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    private HttpUriRequest addClientHeaders(HttpUriRequest request) {
        request.setHeader("User-Agent", "Lavalink-NicoNico-Plugin");
        request.setHeader("X-Frontend-Id", "6");
        request.setHeader("X-Frontend-Version", "0");
        request.setHeader("X-Niconico-Language", "ja-jp");
        request.setHeader("X-Client-Os-Type", "others");
        request.setHeader("X-Request-With", "https://www.nicovideo.jp");
        request.setHeader("Referer", "https://www.nicovideo.jp/");
        return request;
    }

    /**
     * @param url Request URL
     * @return Request with necessary headers attached.
     */
    public HttpUriRequest createGetRequest(String url) {
        return addClientHeaders(new HttpGet(url));
    }

    /**
     * @param url Request URL
     * @return Request with necessary headers attached.
     */
    public HttpUriRequest createGetRequest(URI url) {
        return addClientHeaders(new HttpGet(url));
    }

    /**
     * @param url Request URL
     * @return Request with necessary headers attached.
     */
    public HttpUriRequest fetchVideo(URI url) {
        return addClientHeaders(new HttpGet(url));
    }

    private JsonBrowser fetchVideoInfo(String videoId) {
        try (HttpInterface httpInterface = getHttpInterface()) {
            // helix/streams?user_login=name
            HttpUriRequest request = createGetRequest(
                    "https://www.nicovideo.jp/watch/" + videoId + "?responseType=json");

            return HttpClientTools.fetchResponseAsJson(httpInterface, request);
        } catch (IOException e) {
            throw new FriendlyException("Loading NicoNico video information failed.", SUSPICIOUS, e);
        }
    }

    public static Map<String, List<String>> extractAvailableStreams(JsonBrowser videoInfo, boolean audioOnly) {
        Map<String, List<String>> outputs = new HashMap<>();
        String topAudioId = null;
        int topAudioQuality = -1;

        JsonBrowser audios = videoInfo
                .get("data")
                .get("response")
                .get("media")
                .get("domand")
                .get("audios");

        for (JsonBrowser audio : audios.values()) {
            boolean isAvailable = audio.get("isAvailable").asBoolean(false);
            int qualityLevel = audio.get("qualityLevel").asInt(-1);
            if (isAvailable && qualityLevel > topAudioQuality) {
                topAudioId = audio.get("id").text();
                topAudioQuality = qualityLevel;
            }
        }

        if (topAudioId == null) {
            return outputs;
        }

        JsonBrowser videos = videoInfo
                .get("data")
                .get("response")
                .get("media")
                .get("domand")
                .get("videos");

        for (JsonBrowser video : videos.values()) {
            if (video.get("isAvailable").asBoolean(false)) {
                String label = video.get("label").text();
                List<String> stream = new ArrayList<>();
                if (audioOnly) {
                    stream.add(topAudioId);
                } else {
                    stream.add(video.get("id").text());
                    stream.add(topAudioId);
                }
                outputs.put(label, stream);
            }
        }

        return outputs;
    }

    public String requestAccess(JsonBrowser videoInfo, Map<String, List<String>> outputs) {
        String videoId = videoInfo.get("data").get("response").get("client").get("watchId").text();
        String actionTrackId = videoInfo.get("data").get("response").get("client").get("watchTrackId").text();
        String accessRightKey = videoInfo.get("data").get("response").get("media").get("domand").get("accessRightKey")
                .text();

        try (HttpInterface httpInterface = getHttpInterface()) {
            String url = String.format(
                    "https://nvapi.nicovideo.jp/v1/watch/%s/access-rights/hls?actionTrackId=%s",
                    videoId, actionTrackId);

            String jsonBody = "{\"outputs\":" + buildOutputsJson(outputs) + "}";

            org.apache.http.client.methods.HttpPost request = new org.apache.http.client.methods.HttpPost(url);
            addClientHeaders(request);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("X-Access-Right-Key", accessRightKey);
            request.setEntity(
                    new org.apache.http.entity.StringEntity(jsonBody, java.nio.charset.StandardCharsets.UTF_8));

            try (var response = httpInterface.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                if (status == 201) {
                    var cookieHeader = response.getHeaders("Set-Cookie");
                    for (var cookie : cookieHeader) {
                        if (!cookies.isEmpty()) {
                            cookies += "; ";
                        }
                        cookies += cookie.getValue().split(";", 2)[0];

                        if (cookie.getValue().startsWith("domand_bid=")) {
                            this.domandBid = cookie.getValue().split(";")[0].split("=")[1];
                        }
                        if (cookie.getValue().startsWith("nicosid=")) {
                            this.nicosid = cookie.getValue().split(";")[0].split("=")[1];
                        }
                    }

                    String jsonString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    JsonBrowser json = JsonBrowser.parse(jsonString);
                    if (!json.get("data").isNull()) {
                        return json.get("data").get("contentUrl").text();
                    }
                }
            }
        } catch (IOException e) {
            throw new FriendlyException("Failed to request HLS access rights.", SUSPICIOUS, e);
        }

        return null;
    }

    private String buildOutputsJson(Map<String, List<String>> outputs) {
        return "{" +
                outputs.entrySet().stream()
                        .map(e -> "\"" + e.getKey() + "\":[" +
                                e.getValue().stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(",")) +
                                "]")
                        .collect(Collectors.joining(","))
                +
                "}";
    }

    public String getDomandBid() {
        return this.domandBid;
    }

    public JsonBrowser getVideoInfo() {
        return this.videoInfo;
    }

    public Map<String, List<String>> getOutputs() {
        return this.outputs;
    }

    public String getOutputLabel() {
        return this.outputLabel;
    }

    public Map<String, List<String>> getSelectedOutput() {
        return Map.of(getOutputLabel(), getOutputs().get(getOutputLabel()));
    }

    public String getHslContentUrl() {
        return this.hslContentUrl;
    }

    public String getNicoSid() {
        return this.nicosid;
    }

    public String getWatchUrl() {
        return "https://nico.ms/" + videoInfo.get("data").get("response").get("client").get("watchId").text();
    }

    public String getHeartbeatUrl() {
        String watchId = videoInfo.get("data").get("response").get("client").get("watchId").text();
        String actionTrackId = videoInfo.get("data").get("response").get("client").get("watchTrackId").text();
        return "https://nvapi.nicovideo.jp/v1/watch/" + watchId +
                "/access-rights/hls?actionTrackId=" + actionTrackId + "&__retry=0";
    }

    public String getCookies() {
        return this.cookies;
    }
}
