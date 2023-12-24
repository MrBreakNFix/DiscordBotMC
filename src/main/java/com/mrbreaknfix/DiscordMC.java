package com.mrbreaknfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mrbreaknfix.config.DMCconfig;
import com.mrbreaknfix.config.DMCmainConfigScreen;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

import static net.dv8tion.jda.api.JDA.Status.CONNECTED;

@Environment(EnvType.CLIENT)

public class DiscordMC extends ListenerAdapter implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("discordmc");
	private static final MinecraftClient mc = MinecraftClient.getInstance();
	public static Map<String, JDA> jdas;
	public static DMCconfig config;
	private static KeyBinding openConfigBind;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing DiscordMC...");
		openConfigBind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.DiscordMC.OpenConfigGUI",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_COMMA,
				"key.category.DiscordMC.ConfigCategory"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openConfigBind.wasPressed()) {
				mc.setScreen(new DMCmainConfigScreen());
			}
		});

		loadConfig();

//		jda = JDABuilder.createDefault(config.selectedAccountToken)
//				.enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
//				.addEventListeners(new DiscordMC())
//				.setAutoReconnect(true)
//				.build();
//		try {
//			jda.awaitReady();
//		} catch (InterruptedException ignored) {
//
//		}
//		HudRenderCallback.EVENT.register((e, d) -> {
//			var s = jda.getStatus();
//			if (s == CONNECTED) {
//				return;
//			}
//			TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
//			e.drawCenteredTextWithShadow(renderer, Text.literal("Discord: " + s.name()), e.getScaledWindowWidth()/2, 15, Colors.RED);
//		});
	}

	public void onMessageReceived(MessageReceivedEvent event) {
		assert mc.player != null;
		String msgAuthor = event.getAuthor().getName();
		String msgContent = event.getMessage().getContentRaw();
		String msgChannel = event.getChannel().getId();
		if (event.getJDA().getSelfUser().getId().equals(event.getAuthor().getId())) {
			return; // self-message, does not count
		}
		LOGGER.info("Received a message from #" + msgChannel + " @" + msgAuthor + ": " + msgContent);
		if (!config.sendingChannelID.equals(msgChannel) && !config.listeningChannels.contains(msgChannel)) {
			return; // message we are not interested in
		}

		MutableText name = Text.literal("<" + msgAuthor + ">").formatted(Formatting.GRAY);
		MutableText text = Text.literal(" " + msgContent).formatted(Formatting.WHITE);

		if (msgAuthor.equalsIgnoreCase(mc.player.getName().getString())) {
			name = Text.literal("<" + msgAuthor + ">").setStyle(Style.EMPTY.withColor(0x5865F2));
		}
		MutableText finalText = name.append(text);

		mc.player.sendMessage(finalText);
	}

	public static JDA getCurrentJDA() {
		if (config.selectedAccountToken.isEmpty() || !config.accountTokens.containsKey(config.selectedAccountToken)) {
			return null;
		}
		return jdas.get(config.selectedAccountToken);
	}

	public static void sendDiscordMessage(String message) {
		var channelID = config.sendingChannelID;
		if (channelID.isEmpty()) {
			return;
		}
		var jda = getCurrentJDA();
		assert mc.player != null;
		if (jda == null) {
			mc.player.sendMessage(Text.of("Discord not initialized, message dropped"));
			return;
		}
		if (jda.getStatus() != CONNECTED) {
			mc.player.sendMessage(Text.of(String.format("Discord disconnected (%s), message dropped", jda.getStatus())));
			return;
		}
		sendDiscordMessage(jda, channelID, message);
	}

	public static void sendDiscordMessage(JDA jda, String channelID, String message) {
		TextChannel ch = jda.getTextChannelById(channelID);
		assert mc.player != null;
		if (ch == null) {
			// TODO: translate
            mc.player.sendMessage(Text.literal("Error getting text channel by ID "+channelID));
			return;
		}
		try {
			// TODO: handle more errors
			MessageCreateAction result = ch.sendMessage(message);
		} catch (InsufficientPermissionException e) {
			// TODO: translate
			mc.player.sendMessage(Text.literal("Error sending message: no permission"));
		}
	}

	public static void toggleChat() {
		if (config.sendingChannelID.isEmpty()) {
			config.sendingChannelID = config.previousChannelID;
		} else {
			config.previousChannelID = config.sendingChannelID;
			config.sendingChannelID = "";
		}
		try {
			saveConfig();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void saveConfig() throws IOException {
		Writer writer = new FileWriter(FabricLoader.getInstance().getConfigDir().resolve("DiscordMC.json").toString());
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		gson.toJson(config, writer);
	}
	public static void loadConfig() {
		Reader reader;
		try {
			reader = new FileReader(FabricLoader.getInstance().getConfigDir().resolve("splits.json").toString());
		} catch (FileNotFoundException e) {
			return;
		}
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		config = gson.fromJson(reader, DMCconfig.class);
	}
}