package net.nennneko5787.nndplugin;

import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import dev.arbjerg.lavalink.api.PluginEventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

@Service
public class NicoNicoPlugin extends PluginEventHandler implements AudioPlayerManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NicoNicoPlugin.class);

    public NicoNicoPlugin() {
        log.info("Loading NicoNico plugin...");
    }

    @Override
    public AudioPlayerManager configure(AudioPlayerManager audioPlayerManager) {
        audioPlayerManager.registerSourceManager(new NicoNicoSourceManager());
        return audioPlayerManager;
    }
}
