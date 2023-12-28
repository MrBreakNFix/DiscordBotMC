package com.mrbreaknfix;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

        JDABuilder jdaBuilder = JDABuilder.createDefault(config.getBotToken())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(new Discordmc());
        jda = jdaBuilder.build();
        try {
            jda.awaitReady();
        } catch (InterruptedException ignored) {

        }

        LOGGER.info("Guilds: " + getGuilds());

        // /discord <guild name> <channel name>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("discord")
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
                                mc.player.sendMessage(Text.literal("Toggled discord chat"));
                                toggleChat();
                                return 1;
                            })
                    ));
        });
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        assert mc.player != null;
        String currentServerNiceName = "test";

        System.out.println("Received a message from " + event.getAuthor().getName() + ": " + event.getMessage().getContentRaw());
    
        if (event.getChannel().getName().equals(config.getCurrentChat()) || event.getChannel().getName().equals(currentServerNiceName)) {
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
    }

    public static void sendDiscordMessage(String message, String guildName, String channelName) {
        try {
            jda.getGuildsByName(guildName, true)
                    .stream()
                    .findFirst().flatMap(guild -> guild.getTextChannelsByName(channelName, true)
                    .stream()
                    .findFirst()).ifPresent(channel -> channel.sendMessage(message).queue());
        } catch (Exception e) {
            assert mc.player != null;
            mc.player.sendMessage(Text.literal("Error sending message, check logs."));
            mc.player.sendMessage(Text.literal("Try switching to a guild & channel that you have sufficient permissions to view, and send messages in."));
            mc.player.sendMessage(Text.literal("Please make sure you also have all gateway intents enabled."));

            LOGGER.error("Error sending message: ", e);
        }
    }

    public static List<String> getGuilds() {
        List<String> guilds = new java.util.ArrayList<>(jda.getGuilds().stream().map(Guild::getName).toList());
        guilds.replaceAll(s -> "\"" + s + "\"");
        return guilds;
    }

    public static List<String> listAllChannelNamesInCurrentGuild() {
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
            String configJson = Files.readString(Path.of("discordmc.json"));
            config = GSON.fromJson(configJson, Config.class);
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