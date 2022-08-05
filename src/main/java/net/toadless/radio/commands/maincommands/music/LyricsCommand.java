package net.toadless.radio.commands.maincommands.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.toadless.radio.modules.CooldownModule;
import net.toadless.radio.modules.MusicModule;
import net.toadless.radio.objects.command.Command;
import net.toadless.radio.objects.command.CommandEvent;
import net.toadless.radio.objects.exception.CommandCooldownException;
import net.toadless.radio.objects.exception.CommandException;
import net.toadless.radio.objects.exception.CommandResultException;
import net.toadless.radio.objects.music.GuildMusicManager;
import net.toadless.radio.util.CommandChecks;
import net.toadless.radio.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class LyricsCommand extends Command
{
    private static final ExecutorService LYRIC_EXECUTOR = Executors.newCachedThreadPool();

    public LyricsCommand()
    {
        super("Lyrics", "Fetches the lyrics for the provided song.", "[Song]");
        addAliases("lyrics");
        setCooldown(5000L);
    }

    @Override
    public void run(@NotNull List<String> args, @NotNull CommandEvent event, @NotNull Consumer<CommandException> failure)
    {
        String song;

        if (event.getRadio().getModules().get(CooldownModule.class).isOnCooldown(event.getMember(), this))
        {
            failure.accept(new CommandCooldownException(this));
            return;
        }

        if (args.isEmpty())
        {
            MusicModule musicModule = event.getRadio().getModules().get(MusicModule.class);
            GuildMusicManager manager = musicModule.getGuildMusicManager(event.getGuild());

            if (CommandChecks.boundToChannel(manager, event.getChannel(), failure)) return;
            if (CommandChecks.sharesVoice(event, failure)) return;

            AudioTrack currentTrack = manager.getPlayer().getPlayingTrack();

            if (currentTrack == null)
            {
                failure.accept(new CommandResultException("Nothing is playing."));
                return;
            }

            song = currentTrack.getInfo().title;
        } else song = String.join(" ", args);

        LYRIC_EXECUTOR.submit(() ->
        {
            try
            {
                Document doc = Jsoup.connect("https://www.musixmatch.com/search/" +
                        StringUtils.URLSanitize(String.join("%20", song))).get(); //Enable safe search

                String url = Objects.requireNonNull(doc.selectFirst("a.title[href*=/lyrics/]")).attr("abs:href");

                if (url.isEmpty())
                {
                    event.replyError("Unable to find anything matching `" + song + "`!");
                    return;
                }

                doc = Jsoup.connect(url).get();

                event.getRadio().getModules().get(CooldownModule.class).addCooldown(event.getMember(), this);

                String title = Objects.requireNonNull(doc.selectFirst("h1")).ownText();
                String artist = Objects.requireNonNull(doc.selectFirst("h2 span a")).ownText();
                String lyrics = Jsoup.clean(
                        Objects.requireNonNull(doc.selectFirst("div.mxm-lyrics > span")).html(), Safelist.simpleText());

                if (title.isEmpty() || artist.isEmpty() || lyrics.isEmpty())
                {
                    failure.accept(new CommandResultException("No results found."));
                    return;
                }

                if (lyrics.length() > 4096) // max embed description length
                {
                    event.sendMessage(new EmbedBuilder()
                        .setTitle(title, url)
                        .setAuthor(artist)
                        .setDescription("Lyrics too long to send..."));
                } else event.sendMessage(new EmbedBuilder()
                    .setTitle(title, url)
                    .setAuthor(artist)
                    .setDescription(lyrics));
            } catch (Exception exception)
            {
                failure.accept(new CommandResultException("Unable to fetch lyrics!"));
                event.getRadio().getLogger().error("Unable to fetch lyrics... :(");
            }
        });
    }
}