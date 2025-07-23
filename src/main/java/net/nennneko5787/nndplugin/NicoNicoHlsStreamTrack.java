package net.nennneko5787.nndplugin;

import com.sedmelluq.discord.lavaplayer.source.stream.M3uStreamSegmentUrlProvider;
import com.sedmelluq.discord.lavaplayer.source.stream.MpegTsM3uStreamAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public class NicoNicoHlsStreamTrack extends MpegTsM3uStreamAudioTrack {
    private final NicoNicoHlsStreamSegmentUrlProvider segmentUrlProvider;
    private final HttpInterfaceManager httpInterfaceManager;

    /**
     * @param trackInfo            Track info
     * @param httpInterfaceManager
     */
    public NicoNicoHlsStreamTrack(AudioTrackInfo trackInfo, String streamUrl, HttpInterfaceManager httpInterfaceManager,
            String cookies,
            boolean isInnerUrl) {

        super(trackInfo);

        segmentUrlProvider = isInnerUrl ? new NicoNicoHlsStreamSegmentUrlProvider(null, streamUrl, cookies)
                : new NicoNicoHlsStreamSegmentUrlProvider(streamUrl, null, cookies);

        this.httpInterfaceManager = httpInterfaceManager;
    }

    @Override
    protected M3uStreamSegmentUrlProvider getSegmentUrlProvider() {
        return segmentUrlProvider;
    }

    @Override
    protected HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }
}
