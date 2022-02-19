package net.toadless.radio.modules;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.toadless.radio.Radio;
import net.toadless.radio.objects.Emoji;
import net.toadless.radio.objects.cache.GuildSettingsCache;
import net.toadless.radio.objects.command.CommandEvent;
import net.toadless.radio.objects.exception.CommandException;
import net.toadless.radio.objects.exception.CommandResultException;
import net.toadless.radio.objects.module.Module;
import net.toadless.radio.objects.module.Modules;
import net.toadless.radio.objects.music.GuildMusicManager;
import net.toadless.radio.objects.music.SearchEngine;
import net.toadless.radio.objects.music.TrackScheduler;
import net.toadless.radio.util.CommandChecks;
import net.toadless.radio.util.CommandUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.toadless.radio.objects.Emoji.ARROW_RIGHT;

public class MusicModule extends Module
{
    public static final Pattern URL_PATTERN = Pattern.compile("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]?");
    public static final Pattern SPOTIFY_URL_PATTERN = Pattern.compile("^(https?://)?(www\\.)?open\\.spotify\\.com/(user/[a-zA-Z0-9-_]+/)?(?<type>track|album|playlist)/(?<identifier>[a-zA-Z0-9-_]+)?.+");

    private final Map<Long, GuildMusicManager> musicHandlers;
    private final AudioPlayerManager playerManager;

    public MusicModule(Radio radio, Modules modules)
    {
        super(radio, modules);
        this.musicHandlers = new ConcurrentHashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();

        AudioSourceManagers.registerLocalSource(playerManager);
        AudioSourceManagers.registerRemoteSources(playerManager);

        this.modules.addTask(this::cleanupPlayers, TimeUnit.MINUTES, 1);
    }

    public GuildMusicManager getGuildMusicManager(Guild guild)
    {
        GuildMusicManager manager = musicHandlers.get(guild.getIdLong());
        if (musicHandlers.get(guild.getIdLong()) == null)
        {
            manager = new GuildMusicManager(playerManager);
            musicHandlers.put(guild.getIdLong(), manager);
        }

        guild.getAudioManager().setSendingHandler(manager.getSendHandler());
        return manager;
    }

    public AudioPlayerManager getPlayerManager()
    {
        return playerManager;
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event)
    {
        if(event.getUserIdLong() == event.getJDA().getSelfUser().getIdLong())
        {
            return;
        }

        GuildMusicManager manager = this.musicHandlers.get(event.getGuild().getIdLong());
        if(manager == null)
        {
            return;
        }

        TrackScheduler scheduler = manager.getScheduler();
        Member member = event.getMember();

        GuildVoiceState voiceState = member.getVoiceState();
        if(voiceState == null || voiceState.getChannel() == null || voiceState.getChannel().getIdLong() != voiceState.getChannel().getIdLong())
        {
            return;
        }

        long messageId = event.getMessageIdLong();

        if(messageId != manager.getControllerId())
        {
            return;
        }

        if (!isUserDj(member))
        {
            event.getChannel().sendMessage(member.getAsMention() + ", you need to be a DJ to perform this action!").queue();
            return;
        }

        switch(event.getReactionEmote().getAsReactionCode())
        {
            case "\u27A1\uFE0F" -> scheduler.skipOne();
            case "\u23EF" -> manager.togglePause();
            case "\uD83D\uDD00" -> scheduler.shuffle();
            case "\uD83D\uDD09" -> manager.setVolume(manager.getPlayer().getVolume() - 10);
            case "\uD83D\uDD0A" -> manager.setVolume(manager.getPlayer().getVolume() + 10);
            case "\u274C" -> cleanupPlayer(event.getGuild());
        }

        if(event.getGuild().getSelfMember().hasPermission(event.getChannel(), Permission.MESSAGE_MANAGE))
        {
            event.getReaction().removeReaction(event.getUser()).queue();
        }
    }

    public void cleanupPlayers()
    {
        this.radio.getShardManager().getGuilds().forEach(guild ->
        {
            GuildMusicManager manager = musicHandlers.get(guild.getIdLong());
            VoiceChannel vc = guild.getAudioManager().getConnectedChannel();
            if (vc == null)
            {
                return; // if we arent connected theres no point in checking.
            }

            long humansInVC = vc.getMembers().stream().filter(member -> !member.getUser().isBot()).count();
            if (humansInVC == 0)
            {
                manager.getPlayer().destroy();
                manager.leave(guild);
                manager.getScheduler().clear();
                manager.unbind();
            }
        });
    }

    public void cleanupPlayer(Guild guild)
    {
        GuildMusicManager manager = musicHandlers.get(guild.getIdLong());

        if (manager == null) return;

        manager.removeOldController();
        manager.getPlayer().destroy();
        manager.leave(guild);
        manager.getScheduler().clear();
        manager.unbind();
    }

    public boolean isUserDj(CommandEvent event)
    {
        GuildSettingsCache guildSettingsCache = GuildSettingsCache.getCache(event.getGuildIdLong(), radio);
        long djRole = guildSettingsCache.getDjRole();

        if (djRole == -1L) return true; // return true if dj role not setup
        else return event.getMember().getRoles().stream().anyMatch(role -> role.getIdLong() == djRole);
    }

    public boolean isUserDj(Member member)
    {
        GuildSettingsCache guildSettingsCache = GuildSettingsCache.getCache(member.getGuild().getIdLong(), radio);
        long djRole = guildSettingsCache.getDjRole();

        if (djRole == -1L) return true; // return true if dj role not setup
        else return member.getRoles().stream().anyMatch(role -> role.getIdLong() == djRole);
    }

    public void play(GuildMusicManager manager, String query, Consumer<CommandException> failure, CommandEvent event, SearchEngine searchEngine)
    {
        VoiceChannel channel = event.getMember().getVoiceState().getChannel(); //Safe due to CommandChecks

        Matcher matcher = SPOTIFY_URL_PATTERN.matcher(query);

        if (matcher.matches())
        {
            this.modules.get(SpotifyModule.class).load(event, matcher, failure, channel, manager);
            return;
        }

        if (!URL_PATTERN.matcher(query).matches())
        {
            switch(searchEngine)
            {
                case YOUTUBE -> query = "ytsearch:" + query;
                case SOUNDCLOUD -> query = "scsearch:" + query;
            }
        }

        getPlayerManager().loadItemOrdered(manager, query, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                if (manager.isPlaying())
                {
                    event.replySuccess("Added **" + track.getInfo().title + "** to the queue.");
                }
                manager.play(channel, track, event.getAuthor()); //Safe due to CommandChecks
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                try
                {
                    if (playlist.isSearchResult())
                    {
                        AudioTrack track = playlist.getTracks().get(0);
                        if (manager.isPlaying())
                        {
                            event.replySuccess("Added **" + track.getInfo().title + "** to the queue.");
                        }
                        manager.play(channel, track, event.getAuthor()); //Safe due to CommandChecks
                    }
                    else
                    {
                        event.replySuccess("Added " + playlist.getTracks().size() + " tracks to the queue.");
                        manager.playAll(channel, playlist.getTracks(), event.getAuthor()); //Safe due to CommandChecks
                    }
                }
                catch (IndexOutOfBoundsException exception)
                {
                    failure.accept(new CommandResultException("Couldn't find anything matchsing your query."));
                }
            }

            @Override
            public void noMatches()
            {
                failure.accept(new CommandResultException("Couldn't find anything matching your query."));
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
                System.out.println(exception);
                failure.accept(new CommandResultException("An error occurred while loading the song."));
            }
        });
    }

    public int getPlayers()
    {
        return musicHandlers.size();
    }

    public Radio getRadio()
    {
        return this.radio;
    }

    public void playFromSpotify(CommandEvent event, String query, VoiceChannel channel) // separate method to avoid spamming channel
    {
        GuildMusicManager manager = getGuildMusicManager(event.getGuild());
        getPlayerManager().loadItemOrdered(manager, query, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                manager.play(channel, track, event.getAuthor()); //Safe due to CommandChecks
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                if (playlist.isSearchResult())
                {
                    AudioTrack track = playlist.getTracks().get(0);
                    manager.play(channel, track, event.getAuthor()); //Safe due to CommandChecks
                }
                else
                {
                    manager.playAll(channel, playlist.getTracks(), event.getAuthor()); //Safe due to CommandChecks
                }

            }

            @Override
            public void noMatches()
            {
            }

            @Override
            public void loadFailed(FriendlyException exception)
            {
            }
        });
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event)
    {
        long humansInVC = event.getChannelLeft().getMembers().stream().filter(member -> !member.getUser().isBot()).count();
        if (event.getMember().equals(event.getGuild().getSelfMember()) || humansInVC == 0)
        {
           cleanupPlayer(event.getGuild());
        }
    }

    @Override
    public void onGuildVoiceMove(GuildVoiceMoveEvent event)
    {
        long humansInVC = event.getChannelLeft().getMembers().stream().filter(member -> !member.getUser().isBot()).count();
        if (event.getMember().equals(event.getGuild().getSelfMember()) || humansInVC == 0)
        {
            cleanupPlayer(event.getGuild());
        }
    }
}