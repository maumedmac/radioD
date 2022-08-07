package net.toadless.radio.objects.bot;

public enum ConfigOption
{
    PORT("port"),

    TOKEN("token"),
    PRIVILEGEDUSERS("privileged-users"),

    SPOTIFYID("spotify.id"),
    SPOTIFYSECRET("spotify.secret"),

    DBUSERNAME("database.username"),
    DBPASSWORD("database.password"),
    DBURL("database.url"),
    DBDRIVER("database.driver"); // jdbc:type://host:port/database

    private final String key;

    ConfigOption(String key)
    {
        this.key = key;
    }

    public String getKey()
    {
        return key;
    }
}