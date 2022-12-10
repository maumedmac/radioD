package net.toadless.radio.modules;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.plugin.bundled.CorsPluginConfig;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.toadless.radio.Radio;
import net.toadless.radio.objects.bot.ConfigOption;
import net.toadless.radio.objects.module.Module;
import net.toadless.radio.objects.module.Modules;
import net.toadless.radio.web.guild.UncacheRoute;
import net.toadless.radio.web.info.InfoRoute;
import net.toadless.radio.web.invite.InviteBotRoute;
import net.toadless.radio.web.invite.InviteDiscordRoute;
import net.toadless.radio.web.shards.ShardsRoute;

import static io.javalin.apibuilder.ApiBuilder.*;

public class WebModule extends Module
{
    private final Javalin javalin;

    public WebModule(Radio radio, Modules modules)
    {
        super(radio, modules);
        this.javalin = Javalin
                .create(this::setJavalinConfig)
                .routes(() ->
                {
                    path("/shards", () -> get(new ShardsRoute(this)));
                    path("/info", () -> get(new InfoRoute(this)));

                    path("/invite", () ->
                    {
                        get(new InviteDiscordRoute());
                        path("/bot", () -> get(new InviteBotRoute(this)));
                    });

                    path("/guild", () ->
                    {
                        path("/uncache", () -> get(new UncacheRoute(this)));
                    });

                    path("/health", () -> get(ctx -> ctx.result("Healthy")));
                }).start(radio.getConfiguration().getInt(ConfigOption.PORT));
    }

    public Radio getRadio()
    {
        return radio;
    }

    public Javalin getJavalin()
    {
        return javalin;
    }

    public void ok(Context context, DataObject payload)
    {
        result(context, 200, payload);
    }

    public void result(Context ctx, int code, DataObject data)
    {
        ctx.header("Content-Type", "application/json");
        ctx.status(code);
        ctx.result(data.toString());
    }

    public void setJavalinConfig(JavalinConfig config)
    {
        config.showJavalinBanner = false;
        config.plugins.enableCors(cors -> cors.add(CorsPluginConfig::anyHost));
    }
}
