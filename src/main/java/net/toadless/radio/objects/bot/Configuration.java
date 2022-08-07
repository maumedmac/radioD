package net.toadless.radio.objects.bot;

import java.io.File;
import java.util.List;

import net.toadless.radio.Radio;
import org.jetbrains.annotations.NotNull;
import org.simpleyaml.configuration.file.FileConfiguration;
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration
{
    public static final File CONFIG_FOLDER = new File("config");
    public static final File CONFIG_FILE = new File(CONFIG_FOLDER, "config.yml");

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private final Radio radio;
    private final FileConfiguration configuration;

    public Configuration(@NotNull Radio radio)
    {
        this.radio = radio;
        this.configuration = initializeConfigFile();
    }

    private FileConfiguration initializeConfigFile()
    {
        if (!CONFIG_FOLDER.exists() || !CONFIG_FILE.exists())
        {
            LOGGER.error("Unable to load config, please create a config.yml file, it should be located in " +
                    "a folder called \"config\". (Example file here: INSERT_FILE_LINK_HERE)");

            System.exit(-1);
        }

        FileConfiguration configuration = new YamlConfiguration();

        try
        {
            configuration.load(CONFIG_FILE);
        }
        catch (Exception exception)
        {
            radio.getLogger().error("Unable to load yml configuration file... ", exception);
            System.exit(-1);
        }

        return configuration;
    }

    public String getString(ConfigOption configOption)
    {
        return configuration.getString(configOption.getKey());
    }

    public int getInt(ConfigOption configOption)
    {
        return configuration.getInt(configOption.getKey());
    }

    public List<String> getList(ConfigOption configOption)
    {
        return configuration.getStringList(configOption.getKey());
    }
}