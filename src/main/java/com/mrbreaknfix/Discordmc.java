package com.mrbreaknfix;

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
	public static String currentChat = "all-servers";
	public static String currentGuild = "Minecraft Chat";
	public static String botToken = "YOUR_TOKEN"; // this is for a config file, please do not hardcode your token here
	public static boolean discordChatEnabled = true;

	@Override
	public void onInitialize() {
		LOGGER.info("Hello Fabric world!");
		try {
			loadConfig();
		} catch (Exception ignored) {
		}


		JDABuilder jdaBuilder = JDABuilder.createDefault(botToken)
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
												Discordmc.currentGuild = StringArgumentType.getString(context, "guild");
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
											Discordmc.currentChat = StringArgumentType.getString(context, "channel");
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

		if (event.getChannel().getName().equals(currentChat) || event.getChannel().getName().equals(currentServerNiceName)) {
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
		// instead of replacing adding quotes, replace spaces with dashes
//		guilds.replaceAll(s -> s.replace(" ", "-"));
		return guilds;
	}

	public static List<String> listAllChannelNamesInCurrentGuild() {
		List<String> list = jda.getGuildsByName(currentGuild, true)
				.stream()
				.findFirst()
				.map(guild -> guild.getTextChannels().stream()
						.map(Channel::getName)
						.collect(Collectors.toList()))
				.orElse(Collections.emptyList());
		// remove ALL channels that the user doesn't have permission to view, or send messages in
		list.removeIf(channelName -> !jda.getGuildsByName(currentGuild, true)
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
		discordChatEnabled = !discordChatEnabled;
		saveConfig();
	}
	// save config
	public static void saveConfig() {
		// example config: lines are separated by \n
		// token=abc123\n
		// currentGuild=My Guild\n
		// currentChat=general\n
		// discordChatEnabled=true
		String config = "token=" + jda.getToken().substring(4) + "\n" +
				"currentGuild=" + currentGuild + "\n" +
				"currentChat=" + currentChat + "\n" +
				"discordChatEnabled=" + discordChatEnabled;
		try {
			Files.writeString(Path.of("discordmc.conf"), config);
		}
		catch (Exception e) {
			LOGGER.error("Error saving config: ", e);
		}
	}
	// load config
	public static void loadConfig() {
		// example config:
		// token=abc123
		// currentGuild=My Guild
		// currentChat=general
		// discordChatEnabled=true

		String config = "";
		try {
			config = Files.readString(Path.of("discordmc.conf"));
		}
		catch (Exception e) {
			LOGGER.error("Error loading config: ", e);
		}

		String[] lines = config.split("\n");
		for (String line : lines) {
			String[] parts = line.split("=");
			switch (parts[0]) {
				case "token" -> botToken = parts[1];
				case "currentGuild" -> currentGuild = parts[1];
				case "currentChat" -> currentChat = parts[1];
				case "discordChatEnabled" -> discordChatEnabled = Boolean.parseBoolean(parts[1]);
			}
			botToken = botToken.strip();
			currentGuild = currentGuild.strip();
			currentChat = currentChat.strip();
		}
	}
}