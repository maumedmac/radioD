package net.toadless.radio.web.guild;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.toadless.radio.modules.WebModule;
import net.toadless.radio.objects.cache.GuildSettingsCache;
import org.jetbrains.annotations.NotNull;

public class UncacheRoute implements Handler
{
    private final WebModule webModule;

    public UncacheRoute(WebModule webModule)
    {
        this.webModule = webModule;
    }

    @Override
    public void handle(@NotNull Context ctx)
    {
        String guildId = ctx.queryParam("guild_id");

        if (guildId == null)
        {
            throw new BadRequestResponse("No 'guild_id' found in request");
        }

        try
        {
            GuildSettingsCache.removeCache(Long.parseLong(guildId));
            webModule.ok(ctx, DataObject.empty());
        } catch (NumberFormatException exception)
        {
            throw new BadRequestResponse("The provided 'guild_id' is of the wrong format");
        }
    }
}