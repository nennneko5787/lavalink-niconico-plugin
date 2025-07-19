package net.nennneko5787.nndplugin;

import java.net.URI;

import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public class NicoNicoAudioTrack extends DelegatedAudioTrack {
    private final NicoNicoSourceManager sourceManager;

    public NicoNicoAudioTrack(AudioTrackInfo trackInfo, NicoNicoSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        var watchId = sourceManager.getVideoInfo().get("data").get("response").get("client").get("watchId").text();
        var actionTrackId = sourceManager.getVideoInfo().get("data").get("response").get("client").get("watchTrackId")
                .text();
        try (HeartbeatingHttpStream stream = new HeartbeatingHttpStream(
                sourceManager.getHttpInterface(),
                new URI(sourceManager.getHslContentUrl()),
                null,
                "https://nvapi.nicovideo.jp/v1/watch/" + watchId +
                        "/access-rights/hls?actionTrackId=" + actionTrackId + "&__retry=0",
                30000,
                sourceManager.getOutputs(), sourceManager.getNicoSid(), sourceManager.getDomandBid())) {
            processDelegate(new AdtsAudioTrack(trackInfo, stream), executor);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NicoNicoAudioTrack(this.trackInfo, this.sourceManager);
    }

    @Override
    public NicoNicoSourceManager getSourceManager() {
        return this.sourceManager;
    }
}
