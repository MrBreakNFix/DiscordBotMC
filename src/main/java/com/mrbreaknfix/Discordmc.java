package com.mrbreaknfix;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sun.source.tree.LiteralTree;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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
        LOGGER.info("Hello Fabric world!");
        try {
            loadConfig();
        } catch (Exception ignored) {

        }

        if (!config.getBotToken().equals("Your bot token")) {
            createJDA(config.getBotToken());
        }

        // /d <message>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("d")
                    .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                            .executes((context) -> {
                                sendDiscordMessage(StringArgumentType.getString(context, "message"), config.getCurrentGuild(), config.getCurrentChat());
                                return 1;
                            })));
        });

        // /discord <guild name> <channel name>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("dc")
                    .then(ClientCommandManager.literal("guild")
                            .then(ClientCommandManager.argument("guild", StringArgumentType.string())
                                    .suggests((context, builder) -> {
                                        return CommandSource.suggestMatching(getGuilds(), builder);
                                    })
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
                                mc.player.sendMessage(Text.literal("Discord chat is now " + (config.isDiscordChatEnabled() ? "off." : "on.")));
                                toggleChat();
                                return 1;
                            })
                    )
                    .then(ClientCommandManager.literal("display")
                            .then(ClientCommandManager.literal("AllMessages")
                                    .executes((context) -> {
                                        mc.player.sendMessage(Text.literal("Discord chat will now show all messages."));
                                        config.displayMessages("all");
                                        saveConfig();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("CurrentChannel")
                                    .executes((context) -> {
                                        mc.player.sendMessage(Text.literal("Discord chat will now only show messages from the current channel."));
                                        config.displayMessages("current");
                                        saveConfig();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("never")
                                    .executes((context) -> {
                                        mc.player.sendMessage(Text.literal("Discord chat will now not show any messages."));
                                        config.displayMessages("none");
                                        saveConfig();
                                        return 1;
                                    }))
                            .then(ClientCommandManager.literal("onlyWhenEnabled")
                                    .executes((context) -> {
                                        mc.player.sendMessage(Text.literal("Discord chat will now only show messages when enabled."));
                                        config.displayMessages("onlyWhenEnabled");
                                        saveConfig();
                                        return 1;
                                    }))
                    )
                    .then(ClientCommandManager.literal("SetBotToken")
                            .then(ClientCommandManager.argument("token", StringArgumentType.string())
                                    .executes((context) -> {
                                        if (StringArgumentType.getString(context, "token").length() != 73) {
                                            mc.player.sendMessage(Text.literal("Bot token updated!"));
                                            config.setBotToken(StringArgumentType.getString(context, "token"));
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
        try {

            // remove previous JDA instance
            if (jda != null) {
                jda.shutdown();
            }

            JDABuilder jdaBuilder = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(new Discordmc());

            if (!token.equals("Your bot token")) {
                try {
                    jda = jdaBuilder.build();
                    try {
                        jda.awaitReady();
                    } catch (InterruptedException ignored) {

                    }
                } catch (Exception e) {
                    LOGGER.error("Hey there! looks like there was an error while building the JDA instance, \n " +
                            "But first, try these to try to resolve the problem first.\n" +
                            "1. Is the token in the correct format? it should be in this format \"/dc SetBotToken xxxxxxxxxxxxxxxxxxxxxxxxxx.xxxxxx.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"\n" +
                            "2. Are you online? The bot cant connect to discord if you are offline.\n", e);

                    mc.player.sendMessage(Text.literal("Uh oh! There was an error building the JDA instance, check the logs for more info."));
                }
            }
        } catch (Exception ignored) {

        }
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        assert mc.player != null;

        System.out.println("Received a message from " + event.getAuthor().getName() + ": " + event.getMessage().getContentRaw());



//todo: add a config option to enable/disable this feature

//        if (config.isDiscordChatEnabled() && event.getChannel().getName().equals(config.getCurrentChat()) && event.getGuild().getName().equals(config.getCurrentGuild()) || config.seeAllMessages()) {
//            MutableText name = Text.literal("<" + event.getAuthor().getName() + ">").formatted(Formatting.GRAY);
//            MutableText text = Text.literal(" " + event.getMessage().getContentRaw()).formatted(Formatting.WHITE);
//
//            if (event.getAuthor().getName().equalsIgnoreCase(mc.player.getName().getString())) {
//                name = Text.literal("<" + event.getAuthor().getName() + ">").setStyle(Style.EMPTY.withColor(0x5865F2));
//            }
//            MutableText finalText = name.append(text);
//
//            mc.player.sendMessage(finalText);
//
//            if (event.getMessage().getContentRaw().equals("ping")) {
//                event.getChannel().sendMessage("pong").queue();
//            }
//        }

        // display all messages
        if (config.displayMessages.equals("all")) {
            if (!config.onlyDisplayWhenEnabled()) {
                displayDiscordMessage(event);
            }
        } else if (config.displayMessages.equals("current")) {
            if (event.getChannel().getName().equals(config.getCurrentChat()) && event.getGuild().getName().equals(config.getCurrentGuild())) {
                if (!config.onlyDisplayWhenEnabled()) {
                    displayDiscordMessage(event);
                }
                // ignores event if disabled and onlyDisplayWhenEnabled is true
            }
        }
    }

    private void displayDiscordMessage(MessageReceivedEvent event) {
        if (mc.player == null) {
            return;
        }

        MutableText name = Text.literal("<" + event.getAuthor().getName() + ">").formatted(Formatting.GRAY);
        MutableText text = Text.literal(" " + event.getMessage().getContentRaw()).formatted(Formatting.WHITE);

        if (event.getAuthor().getName().equalsIgnoreCase(mc.player.getName().getString())) {
            name = Text.literal("<" + event.getAuthor().getName() + ">").setStyle(Style.EMPTY.withColor(0x5865F2));
        }
        MutableText finalText = name.append(text);

        mc.player.sendMessage(finalText);

        if (event.getMessage().getContentRaw().equals("ping")) {
            event.getChannel().sendMessage("pong").queue();
        }
    }

    public static void sendDiscordMessage(String message, String guildName, String channelName) {
        if (jda == null) {
            mc.player.sendMessage(Text.literal("DiscordMC is not connected to a bot or is invalid, please set/reset your bot token with \"/dc SetBotToken your-bot-token\", or disable DiscordMC with \"/dc toggle\"."));
            return;
        }
        try {
            jda.getGuildsByName(guildName, true)
                    .stream()
                    .findFirst().flatMap(guild -> guild.getTextChannelsByName(channelName, true)
                    .stream()
                    .findFirst()).ifPresent(channel -> channel.sendMessage(message).queue());
        } catch (Exception e) {
            assert mc.player != null;
//            mc.player.sendMessage(Text.literal("Error sending message, check logs."));
//            mc.player.sendMessage(Text.literal("Try switching to a guild & channel that you have sufficient permissions to view, and send messages in."));
//            mc.player.sendMessage(Text.literal("Please make sure you also have all gateway intents enabled."));

            LOGGER.error("Error sending message: ", e);
        }
    }

    public static List<String> getGuilds() {
        if (jda == null) {
            return Collections.singletonList("No guilds found");
        }
        List<String> guilds = new java.util.ArrayList<>(jda.getGuilds().stream().map(Guild::getName).toList());
        guilds.replaceAll(s -> "\"" + s + "\"");
        return guilds;
    }

    public static List<String> listAllChannelNamesInCurrentGuild() {
        if (jda == null) {
            return Collections.singletonList("No channels found");
        }
        List<String> list = jda.getGuildsByName(config.getCurrentGuild(), true)
                .stream()
                .findFirst()
                .map(guild -> guild.getTextChannels().stream()
                        .map(Channel::getName)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
        list.removeIf(channelName -> !jda.getGuildsByName(config.getCurrentGuild(), true)
                .stream()
                .findFirst()
                .map(guild -> guild.getTextChannelsByName(channelName, true)
                        .stream()
                        .findFirst()
                        .map(GuildMessageChannel::canTalk)
                        .orElse(false))
                .orElse(false));

        return list;
    }

    public static void toggleChat() {
        assert mc.player != null;
        config.setDiscordChatEnabled(!config.isDiscordChatEnabled());
        saveConfig();
    }

    public static void saveConfig() {
        try {
            String configJson = GSON.toJson(config);
            Files.writeString(Path.of("discordmc.json"), configJson);
        } catch (Exception e) {
            LOGGER.error("Error saving config: ", e);
        }
    }

    public static void loadConfig() {
        try {
            Path path = Path.of("discordmc.json");
            if (!Files.exists(path)) {
                // generate config
                config = new Config();
                config.setBotToken("Your bot token");
                config.setCurrentGuild("Your server name");
                config.setCurrentChat("Your channel name");
                config.setDiscordChatEnabled(true);
                config.displayMessages("current");
                config.onlyDisplayWhenEnabled = false;
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
        private String botToken;

        @SerializedName("currentGuild")
        private String currentGuild;

        @SerializedName("currentChat")
        private String currentChat;

        @SerializedName("discordChatEnabled")
        private boolean discordChatEnabled;

        @SerializedName("displayMessages")
        private String displayMessages;

        @SerializedName("onlyDisplayWhenEnabled")
        private boolean onlyDisplayWhenEnabled;

        public boolean onlyDisplayWhenEnabled() {
            return onlyDisplayWhenEnabled;
        }

        public void displayMessages(String displayMessages) {
            this.displayMessages = displayMessages;
        }

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getCurrentGuild() {
            return currentGuild;
        }

        public void setCurrentGuild(String currentGuild) {
            this.currentGuild = currentGuild;
        }

        public String getCurrentChat() {
            return currentChat;
        }

        public void setCurrentChat(String currentChat) {
            this.currentChat = currentChat;
        }

        public boolean isDiscordChatEnabled() {
            return discordChatEnabled;
        }

        public void setDiscordChatEnabled(boolean discordChatEnabled) {
            this.discordChatEnabled = discordChatEnabled;
        }
    }
}