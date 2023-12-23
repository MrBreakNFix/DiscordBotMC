package com.mrbreaknfix;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Discordmc implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("discordmc");
	private static final MinecraftClient mc = MinecraftClient.getInstance();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");

		JDABuilder jdaBuilder = JDABuilder.createDefault("")
				.enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
				.addEventListeners(new Discordmc());
		JDA jda = jdaBuilder.build();
	}
	public void onMessageReceived(MessageReceivedEvent event) {
		System.out.println("received a message from " + event.getAuthor().getName() + ": " + event.getMessage().getContentRaw());

		if (event.getChannel().getName().equals("testchannel")) {
			if (!(mc.player == null))
				mc.player.sendMessage(Text.of("§9<" + event.getAuthor().getName() + ">§b " + event.getMessage().getContentRaw()));


			if (event.getMessage().getContentRaw().equals("ping")) {
				event.getChannel().sendMessage("pong").queue();
			}
		}
	}
}