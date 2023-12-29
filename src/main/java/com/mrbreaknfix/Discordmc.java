package com.mrbreaknfix;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sun.source.tree.LiteralTree;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class Discordmc extends ListenerAdapter implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("discordmc");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static JDA jda;
    private static final Gson GSON = new Gson();
    public static Config config;

    @Override
    public void onInitialize() {
        LOGGER.info("DiscordMC initializing...");
        loadConfig();

        if (!config.botToken.isEmpty()) {
            createJDA(config.botToken);
        }

        updateNameCaches();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("dc")
                .then(ClientCommandManager.literal("refresh")
                    .executes(context -> {
                        updateNameCaches();
                        return 1;
                    })
                .then(ClientCommandManager.literal("listen")
                    .executes(context -> {
                        listListeningChannels();
                        return 1;
                    })
                .then(ClientCommandManager.literal("toggle")
                    .executes(context -> {
                        listListeningChannels();
                        return 1;
                    })
                .then(ClientCommandManager.literal("enable")
                    .executes(context -> {
                        config.enabled = true;
                        mc.player.sendMessage(Text.of("DiscordMC ENABLED"));
                        return 1;
                    })
                .then(ClientCommandManager.literal("enable")
                    .executes(context -> {
                        config.enabled = false;
                        mc.player.sendMessage(Text.of("DiscordMC DISABLED"));
                        return 1;
                    })
                .then(ClientCommandManager.literal("cache")
                    .executes(context -> {
                        listCache();
                        return 1;
                    })
                .then(ClientCommandManager.argument("dest", StringArgumentType.string())
                    .suggests((context, builder) -> CommandSource.suggestMatching(getGuilds(), builder))
                    .executes((context) -> {
                        mc.player.sendMessage(Text.literal("Switched to guild: " + StringArgumentType.getString(context, "guild")));
                        config.setCurrentGuild(StringArgumentType.getString(context, "guild"));
                        saveConfig();
                        return 1;
                    })))
                // choose channel
                .then(ClientCommandManager.literal("channel").then(ClientCommandManager.argument("channel", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            return CommandSource.suggestMatching(listAllChannelNamesInCurrentGuild(), builder);
                        })
                        .executes((context) -> {
                            mc.player.sendMessage(Text.literal("Switched to channel: " + StringArgumentType.getString(context, "channel")));
                            config.setCurrentChat(StringArgumentType.getString(context, "channel"));
                            saveConfig();
                            return 1;
                        })))
                .then(ClientCommandManager.literal("toggle")
                        .executes((context) -> {
                            mc.player.sendMessage(Text.literal("Discord chat is now " + (config. ? "off." : "on.")));
                            toggleChat();
                            return 1;
                        })
                )
                .then(ClientCommandManager.literal("display"))
                .then(ClientCommandManager.literal("setToken")
                        .then(ClientCommandManager.argument("token", StringArgumentType.string())
                                .executes((context) -> {
                                    if (StringArgumentType.getString(context, "token").length() != 73) {
                                        mc.player.sendMessage(Text.literal("Bot token updated, connecting to Discord..."));
                                        config.botToken = (StringArgumentType.getString(context, "token"));
                                        createJDA(StringArgumentType.getString(context, "token"));
                                        saveConfig();
                                    } else {
                                        mc.player.sendMessage(Text.literal("A discord bot token is exactly 73 characters long, yours is " + StringArgumentType.getString(context, "token").length() + ". Please provide a valid discord bot token."));
                                    }
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("help")
                        .executes((context) -> {
                            mc.player.sendMessage(Text.literal("Welcome to ").formatted(Formatting.GRAY).append(Text.literal("DiscordMC")).setStyle(Style.EMPTY.withColor(MathHelper.packRgb(64, 58, 0)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("https://github.com/MrBreakNFix/DiscordMC")))).append(Text.literal(" by ")).formatted(Formatting.GRAY).append(Text.literal("MrBreakNFix!").formatted(Formatting.BLUE)));
                            mc.player.sendMessage(Text.literal("Here is a list of commands, click on each for more:"));
                            MutableText guildHelp = Text.literal("/dc guild <guild name>").formatted(Formatting.GRAY);
                            guildHelp.setStyle(Style.EMPTY.withColor(Formatting.BLUE).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "test")));
                            return 1;
                        }))

            );
        });
    }

    private void createJDA(String token) {
        if (jda != null) {
            jda.shutdown();
        }
        if (token.isEmpty()) {
            LOGGER.info("Empty Discord token, not initializing JDA");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(new Discordmc())
                    .build();
            jda.awaitReady();
        } catch (Exception e) {
            LOGGER.error("Hey there! looks like there was an error while building the JDA instance, \n " +
                    "But first, try these to try to resolve the problem first.\n" +
                    "1. Is the token in the correct format? it should be in this format \"/dc SetBotToken xxxxxxxxxxxxxxxxxxxxxxxxxx.xxxxxx.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"\n" +
                    "2. Are you online? The bot cant connect to discord if you are offline.\n", e);
        }
    }
    public void onMessageReceived(MessageReceivedEvent event) {
        if (mc.player == null) {
            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because player is null");
            return;
        }
        if (!config.enabled) {
            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because discordmc is disabled");
            return;
        }
        if (config.listeningChannels.isEmpty()) {
            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because listening channels is empty");
            return;
        }
        if (config.listeningChannels.get(0).isEmpty()) {
            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because first channel is empty");
            return;
        }
        if (!config.listeningChannels.contains(event.getChannel().getId())) {
            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because not listening for that channel");
            return;
        }
        if (event.getJDA().getSelfUser().getId().equals(event.getAuthor().getId())) {
            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because it is self-message");
            return;
        }

        MutableText name = Text.literal("<" + event.getAuthor().getName() + ">").formatted(Formatting.GRAY);
        MutableText text = Text.literal(" " + event.getMessage().getContentRaw()).formatted(Formatting.WHITE);

//        if (event.getAuthor().getName().equalsIgnoreCase(mc.player.getName().getString())) {
//            name = Text.literal("<" + event.getAuthor().getName() + ">").setStyle(Style.EMPTY.withColor(0x5865F2));
//        }
        MutableText finalText = name.append(text);

        mc.player.sendMessage(finalText);
    }

    public static void sendDiscordMessage(String message) {
        if (jda == null) {
            mc.player.sendMessage(Text.literal("DiscordMC is not connected to a bot or is invalid, please set/reset your bot token with \"/dc SetBotToken your-bot-token\", or disable DiscordMC with \"/dc toggle\"."));
            return;
        }
        if (config.sendingChannels.isEmpty()) {
            LOGGER.info("Not sending message to discord because no destination selected");
            return;
        }
        if (config.sendingChannels.get(0).isEmpty()) {
            LOGGER.info("Not sending message to discord because sending blocked");
            return;
        }
        var chan = jda.getTextChannelById(config.sendingChannels.get(0));
        if (chan == null) {
            if (config.sendingChannels.size() == 1) {
                mc.player.sendMessage(Text.literal("Channel went missing?! Removing from sending list (there are no recent destinations left)"));
            } else {
                var chanName = config.channelNamesCache.get(config.sendingChannels.get(1));
                if (chanName == null) {
                    chanName = config.sendingChannels.get(1);
                }
                mc.player.sendMessage(Text.literal("Channel went missing?! Falling back to next channel: " + chanName));
            }
            config.sendingChannels.remove(0);
            return;
        }
        chan.sendMessage(message);
    }

    public static List<String> getGuilds() {
        if (jda == null) {
            return Collections.singletonList("No guilds found");
        }
        List<String> guilds = new java.util.ArrayList<>(jda.getGuilds().stream().map(Guild::getName).toList());
        guilds.replaceAll(s -> "\"" + s + "\"");
        return guilds;
    }

    public static void toggleSending() {
        if (config.sendingChannels.isEmpty()) {
            mc.player.sendMessage(Text.of("Was not sending to any channel, nothing to toggle."));
            return;
        }
        if (config.sendingChannels.get(0).isEmpty()) {
            mc.player.sendMessage(Text.of("Discord messages sending ENABLED"));
            config.sendingChannels.remove(0);
        } else {
            mc.player.sendMessage(Text.of("Discord messages sending DISABLED"));
            config.sendingChannels.add(0, "");
        }
    }

    public static void toggleListening() {
        if (config.listeningChannels.isEmpty()) {
            mc.player.sendMessage(Text.of("Was not listening to any channel, nothing to toggle."));
            return;
        }
        if (config.listeningChannels.get(0).isEmpty()) {
            mc.player.sendMessage(Text.of("Discord messages listening ENABLED"));
            config.listeningChannels.remove(0);
        } else {
            mc.player.sendMessage(Text.of("Discord messages listening DISABLED"));
            config.listeningChannels.add(0, "");
        }
    }

    public static void toggle() {
        if (mc.player == null) {
            return;
        }
        if (config.enabled) {
            config.enabled = false;
            mc.player.sendMessage(Text.of("DiscordMC DISABLED"));
        } else {
            config.enabled = true;
            mc.player.sendMessage(Text.of("DiscordMC ENABLED"));
        }
    }

    public static void listCache() {
        if (mc.player == null) {
            return;
        }
        StringBuilder r = new StringBuilder();
        r.append("Guilds names: ").append(config.guildNamesCache.size());
        config.guildNamesCache.forEach((s, s2) -> {
            r.append("\n").append(s).append(" ").append(s2);
        });
        r.append("\nChannels names: ").append(config.channelNamesCache.size());
        config.channelNamesCache.forEach((s, s2) -> {
            r.append("\n").append(s).append(" ").append(s2);
        });
        mc.player.sendMessage(Text.of(r.toString()));
    }

    public static void listListeningChannels() {
        if (mc.player == null) {
            return;
        }
        if (config.listeningChannels.isEmpty()) {
            mc.player.sendMessage(Text.of("You are not listening to any channel right now."));
            return;
        }
        if (config.listeningChannels.get(0).isEmpty()) {
            StringBuilder channels = new StringBuilder();
            for (int i = 1; i < config.listeningChannels.size(); i++) {
                String cid = config.listeningChannels.get(i);
                String cname = config.channelNamesCache.get(config.listeningChannels.get(i));
                if (cname == null) {
                    cname = cid;
                }
                channels.append("\n").append(cname);
            }
            mc.player.sendMessage(Text.of("Message listening DISABLED with following channels:" + channels));
            return;
        }
        StringBuilder channels = new StringBuilder();
        for (int i = 0; i < config.listeningChannels.size(); i++) {
            String cid = config.listeningChannels.get(i);
            String cname = config.channelNamesCache.get(config.listeningChannels.get(i));
            if (cname == null) {
                cname = cid;
            }
            channels.append("\n").append(cname);
        }
        mc.player.sendMessage(Text.of("Listening for messages in following channels:" + channels));
    }

    public static void updateNameCaches() {
        if (jda == null) {
            return;
        }
        var channels = jda.getTextChannels();
        for (TextChannel channel : channels) {
            config.channelNamesCache.put(channel.getId(), channel.getName());
        }
        var guilds = jda.getGuilds();
        for (Guild guild : guilds) {
            config.guildNamesCache.put(guild.getId(), guild.getName());
        }
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("discordmc.json");
    }

    public static void saveConfig() {
        try {
            String configJson = GSON.toJson(config);
            Files.writeString(getConfigPath(), configJson);
        } catch (Exception e) {
            LOGGER.error("Error saving config: ", e);
        }
    }

    public static void loadConfig() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                config = new Config();
                saveConfig();
            } else {
                String configJson = Files.readString(path);
                config = GSON.fromJson(configJson, Config.class);
            }
        } catch (Exception e) {
            LOGGER.error("Error loading config: ", e);
        }
    }

    public static class Config {
        @SerializedName("token")
        public String botToken;
        @SerializedName("enabled")
        public boolean enabled;
        @SerializedName("sendingChannels")
        public List<String> sendingChannels;
        @SerializedName("listeningChannels")
        public List<String> listeningChannels;
        @SerializedName("channelNamesCache")
        public Map<String, String> channelNamesCache;
        @SerializedName("guildNamesCache")
        public Map<String, String> guildNamesCache;
    }
}