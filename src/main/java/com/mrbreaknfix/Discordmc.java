package com.mrbreaknfix;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class Discordmc extends ListenerAdapter implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("discordmc");
	private static final MinecraftClient mc = MinecraftClient.getInstance();
	public static JDA jda;
	public static String currentChat = "all-servers";
	public static String currentGuild = "Minecraft Chat";

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		String token = "";
		try {
			token = Files.readString(Path.of("discordmc.conf"));

		} catch (Exception ignored) {

		}

		JDABuilder jdaBuilder = JDABuilder.createDefault(token)
				.enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
				.addEventListeners(new Discordmc());
		jda = jdaBuilder.build();

		// get list of guilds
		 jda.getGuilds().forEach(guild -> System.out.println(guild.getName()));

		 // get list of channels
		 jda.getGuildsByName(currentGuild, true)
				 .stream()
				 .findFirst().ifPresent(guild -> guild.getTextChannels()
						 .forEach(channel -> System.out.println(channel.getName())));
	}
	public void onMessageReceived(MessageReceivedEvent event) {
		assert mc.player != null;
		String currentServerNiceName = "test";


		System.out.println("Received a message from " + event.getAuthor().getName() + ": " + event.getMessage().getContentRaw());

		if (event.getChannel().getName().equals(currentChat) || event.getChannel().getName().equals(currentServerNiceName)) {
			MutableText name = Text.literal("<" + event.getAuthor().getName() + ">").formatted(Formatting.GRAY);
			MutableText text = Text.literal(" " + event.getMessage().getContentRaw()).formatted(Formatting.WHITE);

			if (event.getAuthor().getName().equalsIgnoreCase(mc.player.getName().getString())) {
				name = Text.literal("<" + event.getAuthor().getName() + ">").formatted(Formatting.BLUE);
			}
			MutableText finalText = name.append(text);

			mc.player.sendMessage(finalText);

			if (event.getMessage().getContentRaw().equals("ping")) {
				event.getChannel().sendMessage("pong").queue();
			}
		}
	}
	public static void getServerName() {
		assert mc.player != null;
		String serverIp = Objects.requireNonNull(mc.player.getServer()).getServerIp();
		String serverName = serverIp.substring(0, serverIp.indexOf("."));
		String serverNiceName = serverName.substring(0, 1).toUpperCase() + serverName.substring(1);
		System.out.println(serverNiceName);
	}

	public static void sendDiscordMessage(String message, String guildName, String channelName) {
		jda.getGuildsByName(guildName, true)
                .stream()
                .findFirst().flatMap(guild -> guild.getTextChannelsByName(channelName, true)
                        .stream()
                        .findFirst()).ifPresent(channel -> channel.sendMessage(message).queue());
	}
}
