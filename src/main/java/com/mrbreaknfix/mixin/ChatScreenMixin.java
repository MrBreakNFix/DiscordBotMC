package com.mrbreaknfix.mixin;

import com.mrbreaknfix.DiscordMC;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(ChatScreen.class)

public class ChatScreenMixin extends Screen {
	protected ChatScreenMixin(Text title) {
		super(title);
	}

	@Inject(at = @At("HEAD"), method = "sendMessage", cancellable = true)
	public void sendMessage(String chatText, boolean addToHistory, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if(chatText.toLowerCase().startsWith(".d") || (chatText.toLowerCase().startsWith(",d"))) {
			DiscordMC.sendDiscordMessage(chatText.substring(3), DiscordMC.currentGuild, DiscordMC.currentChat);

			mc.setScreen(null);
			cir.setReturnValue(false);
		}

		if (DiscordMC.discordChatEnabled && !chatText.startsWith("/") && !chatText.startsWith(".") && !chatText.startsWith(",")) {

			DiscordMC.sendDiscordMessage(chatText, DiscordMC.currentGuild, DiscordMC.currentChat);

			mc.setScreen(null);
			cir.setReturnValue(false);
		}
	}
	@Inject(at = @At("TAIL"), method = "init")
	public void init(CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();

		var channelID = DiscordMC.config.sendingChannelID;
		if (channelID.isEmpty()) {
			return;
		}

		var channelName = DiscordMC.config.cacheChannelNames.get(channelID);

		MutableText chatDestination = Text.literal("Chatting in #").formatted(Formatting.GRAY)
				.append(channelName).formatted(Formatting.GRAY);

		MultilineTextWidget multilineTextWidget = getTextWidget(chatDestination, mc);
		addDrawableChild(multilineTextWidget);
	}

	@Unique
	@NotNull
	private MultilineTextWidget getTextWidget(MutableText text, MinecraftClient mc) {
		MultilineTextWidget multilineTextWidget = new MultilineTextWidget(text, this.textRenderer);
		multilineTextWidget.setPosition(mc.getWindow().getScaledWidth() - multilineTextWidget.getWidth() - 2, mc.getWindow().getScaledHeight() - 25);
		return multilineTextWidget;
	}
}