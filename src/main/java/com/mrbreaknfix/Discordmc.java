package com.mrbreaknfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
                    }))
                .then(ClientCommandManager.literal("listen")
                    .executes(context -> {
                        listListeningChannels();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("enable")
                    .executes(context -> {
                        config.enabled = true;
                        mc.player.sendMessage(Text.of("DiscordMC ENABLED"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("disable")
                    .executes(context -> {
                        config.enabled = false;
                        mc.player.sendMessage(Text.of("DiscordMC DISABLED"));
                        return 1;
                    }))
                .then(ClientCommandManager.literal("toggle")
                    .executes(context -> {
                        toggle();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("select")
                    .executes(context -> {
                        select();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("debug")
                    .executes((context) -> {
                        var r = Text.literal("Sending list:");
                        for (String sendingChannel : config.sendingChannels) {
                            r.append("\n"+sendingChannel);
                        }
                        r.append("\nListening list:");
                        for (String listeningChannel : config.listeningChannels) {
                            r.append("\n"+listeningChannel);
                        }
                        mc.player.sendMessage(r);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("setToken")
                    .then(ClientCommandManager.argument("token", StringArgumentType.string())
                    .executes((context) -> {
                        setBotToken(StringArgumentType.getString(context, "token"));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("dest")
                    .executes(context -> {
                        select();
                        return 1;
                    })
                    .then(ClientCommandManager.argument("channel id", StringArgumentType.string())
                    .executes((context) -> {
                        setSendingChannel(StringArgumentType.getString(context, "channel id"));
                        return 1;
                    })))
            );
        });
    }

    private void setBotToken(String t) {
        if (t.length() != 73) {
            mc.player.sendMessage(Text.literal("Bot token updated, connecting to Discord..."));
            config.botToken = t;
            createJDA(t);
            saveConfig();
        } else {
            mc.player.sendMessage(Text.literal("A discord bot token is exactly 73 characters long, yours is " + t.length() + ". Please provide a valid discord bot token."));
        }
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
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
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
//        if (event.getJDA().getSelfUser().getId().equals(event.getAuthor().getId())) {
//            LOGGER.info("Dropping Discord message from " + event.getAuthor().getName() + " because it is self-message");
//            return;
//        }

        // ... why did you do this? The point is when you send discord messages they don't get sent to the minecraft server...
        // I might be wrong, if so please elaborate on your decision to include this.

        MutableText name = Text.literal("<" + event.getAuthor().getName() + ">").formatted(Formatting.GRAY);
        if (true) {

            String fancyname = event.getChannel().getName().toUpperCase().replace("A", "ᴀ").replace("B", "ʙ").replace("C", "ᴄ").replace("D", "ᴅ").replace("E", "ᴇ").replace("F", "ғ").replace("G", "ɢ").replace("H",
            "ʜ").replace("I", "ɪ").replace("J", "ᴊ").replace("K", "ᴋ").replace("L", "ʟ").replace("M", "ᴍ").replace("N", "ɴ").replace(
                    "O", "ᴏ").replace("P", "ᴘ").replace("Q", "ǫ").replace("R",
            "ʀ").replace("S", "s").replace("T", "ᴛ").replace("U", "ᴜ").replace("V", "ᴠ").replace("W", "ᴡ").replace("X", "").replace("Y", "ʏ").replace("Z", "ᴢ").replace("1", "𝟷").replace("2", "𝟸").replace("3", "𝟹").replace("4", "𝟺").replace("5", "𝟻").replace("6", "𝟼").replace("7", "𝟽").replace("8", "𝟾").replace("9", "𝟿").replace("0", "𝟶");

            name = Text.literal("#" + fancyname).formatted(Formatting.DARK_AQUA).append(Text.literal(" <" + event.getAuthor().getName() + ">").formatted(Formatting.GRAY));
        }
        MutableText text = Text.literal(" " + event.getMessage().getContentRaw()).formatted(Formatting.WHITE);

//        if (event.getAuthor().getName().equalsIgnoreCase(mc.player.getName().getString())) {
//            name = Text.literal("<" + event.getAuthor().getName() + ">").setStyle(Style.EMPTY.withColor(0x5865F2));
//        }
        MutableText finalText = name.append(text);

        mc.player.sendMessage(finalText);
    }

    public static void sendDiscordMessage(String message) {
        if (!config.enabled) {
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
        if (jda == null) {
            mc.player.sendMessage(Text.literal("DiscordMC is not connected to a bot or is invalid, please set/reset your bot token with \"/dc SetBotToken your-bot-token\", or disable DiscordMC with \"/dc toggle\"."));
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
        try {
            chan.sendMessage(message).queue();
        } catch (Exception e) {
            LOGGER.error("Error sending message to Discord: ", e); // temp for insufficient permissions
        }
    }

//    public static void toggleSending() {
//        if (config.sendingChannels.isEmpty()) {
//            mc.player.sendMessage(Text.of("Was not sending to any channel, nothing to toggle."));
//            return;
//        }
//        if (config.sendingChannels.get(0).isEmpty()) {
//            mc.player.sendMessage(Text.of("Discord messages sending ENABLED"));
//            config.sendingChannels.remove(0);
//        } else {
//            mc.player.sendMessage(Text.of("Discord messages sending DISABLED"));
//            config.sendingChannels.add(0, "");
//        }
//    }
//
//    public static void toggleListening() {
//        if (config.listeningChannels.isEmpty()) {
//            mc.player.sendMessage(Text.of("Was not listening to any channel, nothing to toggle."));
//            return;
//        }
//        if (config.listeningChannels.get(0).isEmpty()) {
//            mc.player.sendMessage(Text.of("Discord messages listening ENABLED"));
//            config.listeningChannels.remove(0);
//        } else {
//            mc.player.sendMessage(Text.of("Discord messages listening DISABLED"));
//            config.listeningChannels.add(0, "");
//        }
//    }

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
        saveConfig();
    }

    public static void select() {
        if (mc.player == null) {
            return;
        }
        var ret = Text.empty();
        ret.append(Text.literal("Select destination:"));
        var g = jda.getGuilds();
        int i = 0;
        for (Guild guild : g) {
            var c = guild.getTextChannels();
            for (TextChannel channel : c) {
                if (!(channel).canTalk()) {
                    continue;
                }
                var channelText = Text.literal("\n  " + guild.getName() + " #" + channel.getName()).setStyle(
                        Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dc dest " + channel.getId()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to select channel " + channel.getId())))
                );
                if (i % 2 == 1) {
                    channelText = channelText.formatted(Formatting.GRAY);
                }
                ret.append(channelText);
            }
            i++;
        }
        mc.player.sendMessage(ret);
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
        saveConfig();
    }

    public static String getSendingChannel() {
        if (config.sendingChannels.isEmpty()) {
            return "";
        }
        var ch = config.sendingChannels.get(0);
        return config.channelNamesCache.getOrDefault(ch, ch);
    }

    public static void setSendingChannel(String id) {
        config.sendingChannels.remove("");
        config.sendingChannels.remove(id);
        config.sendingChannels.add(0, id);
        config.listeningChannels.remove("");
        config.listeningChannels.remove(id);
        config.listeningChannels.add(0, id);
        saveConfig();
        if (mc.player == null) {
            return;
        }
        mc.player.sendMessage(Text.literal("Channel changed to " + id));
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("discordmc.json");
    }

    public static void saveConfig() {
        try {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
            String configJson = gson.toJson(config);
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
        public String botToken = "";
        @SerializedName("enabled")
        public boolean enabled = false;
        @SerializedName("sendingChannels")
        public List<String> sendingChannels = new ArrayList<>();
        @SerializedName("listeningChannels")
        public List<String> listeningChannels = new ArrayList<>();
        @SerializedName("channelNamesCache")
        public Map<String, String> channelNamesCache = new TreeMap<>();
        @SerializedName("guildNamesCache")
        public Map<String, String> guildNamesCache = new TreeMap<>();
    }
}