/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fredboat.audio.player;

import fredboat.Config;
import fredboat.FredBoat;
import fredboat.util.DiscordUtil;
import lavalink.client.io.Lavalink;
import lavalink.client.player.IPlayer;
import lavalink.client.player.LavaplayerPlayerWrapper;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Abstracts the underlying audio providers away.
 * Currently there are two audio providers supported:
 * - Playing audio locally with lavaplayer
 * - Using remote lavalink nodes
 */
public class LavalinkManager {

    @Nonnull
    public static LavalinkManager getInstance() {
        return LavalinkManagerHolder.INSTANCE;
    }

    //holder singleton pattern
    private static class LavalinkManagerHolder {
        private static final LavalinkManager INSTANCE = new LavalinkManager();
    }

    public static boolean isRemote() {
        return getInstance().lavalinkClient != null;
    }

    private LavalinkManager() {
        if (Config.CONFIG.getLavalinkHosts().isEmpty()) {
            return; //no need to setup the lavalink client
        }

        String userId = DiscordUtil.getUserId(Config.CONFIG.getBotToken());
        lavalinkClient = new Lavalink(
                userId,
                Config.CONFIG.getNumShards(),
                shardId -> FredBoat.getShard(shardId).getJda()
        );
        List<Config.LavalinkHost> hosts = Config.CONFIG.getLavalinkHosts();
        hosts.forEach(lavalinkHost -> lavalinkClient.addNode(lavalinkHost.getUri(),
                lavalinkHost.getPassword()));
    }

    //this will be null only when using local lavaplayer as the audio provider
    @Nullable
    private Lavalink lavalinkClient;

    @Nonnull
    public IPlayer createPlayer(@Nonnull Guild guild) {
        return lavalinkClient != null
                ? lavalinkClient.getLink(guild).getPlayer()
                : new LavaplayerPlayerWrapper(AbstractPlayer.getPlayerManager().createPlayer());
    }

    public void openConnection(@Nonnull VoiceChannel channel) {
        if (lavalinkClient != null) {
            lavalinkClient.getLink(channel.getGuild()).connect(channel);
        } else {
            channel.getGuild().getAudioManager().openAudioConnection(channel);
        }
    }

    //todo: decide how to handle the following: we are in a situation where we cant get a guild object, but need to close the connection
    public void closeConnection(@Nonnull Guild guild) {
        if (lavalinkClient != null) {
            lavalinkClient.getLink(guild).disconnect();
        } else {
            guild.getAudioManager().closeAudioConnection();
        }
    }

//NOTE: this has been commented out since there is no reason for the lavalinkmanager to provide such a method. FredBoat code
//      should avoid checking the current voicechat _as much as possible_, and instead rely on Voice Join/Leave/Move events
//      the reason for this is, that to join a channel is a lengthy process and it takes a while until the JDA cache is up to date:
//      1. request voicechannel update through JDA
//      2. intercept the answer from discord and send it to a lavalink server
//      3. lavalink server connects to the channel through an audio websocket
//      4. discord updates our voicestate
//      5. JDA receives the discord update and updates the voice state of our selfmember
//
//      There are also several things that can go wrong in this chain of events.
//
//    @Nullable
//    public VoiceChannel getConnectedChannel(@Nonnull Guild guild) {
//        //NOTE: never use the local audio manager, since the audio connection may be remote
//        // there is also no reason to look the channel up remotely from lavalink, if we have access to a real guild
//        // object here, since we can use the voice state of ourselves (and lavalink 1.1 is buggy in keeping up with the
//        // current voice channel if the bot is moved around in the client)
//        return guild.getSelfMember().getVoiceState().getChannel();
//    }

    @Nullable
    public Lavalink getLavalinkClient() {
        return lavalinkClient;
    }
}
