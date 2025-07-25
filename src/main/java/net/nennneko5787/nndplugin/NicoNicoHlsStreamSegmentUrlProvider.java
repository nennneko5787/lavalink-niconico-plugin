package net.nennneko5787.nndplugin;

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser;
import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import java.io.IOException;
import java.util.List;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines;

public class NicoNicoHlsStreamSegmentUrlProvider extends M3uStreamSegmentUrlProvider {
    private static final Logger log = LoggerFactory.getLogger(NicoNicoHlsStreamSegmentUrlProvider.class);

    private final String streamListUrl;
    private volatile String segmentPlaylistUrl;
    private final String cookies;

    public NicoNicoHlsStreamSegmentUrlProvider(String streamListUrl, String segmentPlaylistUrl, String cookies) {
        this.streamListUrl = streamListUrl;
        this.segmentPlaylistUrl = segmentPlaylistUrl;
        this.cookies = cookies;
    }

    @Override
    protected String getQualityFromM3uDirective(ExtendedM3uParser.Line directiveLine) {
        return "default";
    }

    @Override
    protected String fetchSegmentPlaylistUrl(HttpInterface httpInterface) throws IOException {
        if (segmentPlaylistUrl != null) {
            return segmentPlaylistUrl;
        }

        HttpUriRequest request = new HttpGet(streamListUrl);
        request.setHeader("Cookie", cookies);
        List<ChannelStreamInfo> streams = loadChannelStreamsList(fetchResponseLines(httpInterface, request,
                "HLS stream list"));

        if (streams.isEmpty()) {
            throw new IllegalStateException("No streams listed in HLS stream list.");
        }

        ChannelStreamInfo stream = streams.get(0);

        log.debug("Chose stream with url {}", stream.quality, stream.url);
        segmentPlaylistUrl = stream.url;
        return segmentPlaylistUrl;
    }

    @Override
    protected HttpUriRequest createSegmentGetRequest(String url) {
        var request = new HttpGet(url);
        request.setHeader("Cookie", cookies);
        return request;
    }

    public String findHlsEntryUrl(String[] lines) {
        List<ChannelStreamInfo> streams = new NicoNicoHlsStreamSegmentUrlProvider(null, null, cookies)
                .loadChannelStreamsList(lines);

        return streams.isEmpty() ? null : streams.get(0).url;
    }
}
