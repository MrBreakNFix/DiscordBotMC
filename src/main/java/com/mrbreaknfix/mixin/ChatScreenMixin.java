package com.mrbreaknfix.mixin;

import com.mojang.datafixers.kinds.IdF;
import com.mrbreaknfix.Discordmc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
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
			Discordmc.sendDiscordMessage(chatText.substring(3), Discordmc.currentGuild, Discordmc.currentChat);

			mc.setScreen(null);
			cir.setReturnValue(false);
		}
	}
	@Inject(at = @At("TAIL"), method = "init")
	public void init(CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();

		int bottomLeftCornerX = 5 - 25;
		int bottomLeftCornerY = mc.getWindow().getScaledHeight() - 14;

		MutableText chatDestination = Text.literal(Discordmc.currentChat).formatted(Formatting.WHITE);

		MutableText text = Text.literal("Chat to: ").formatted(Formatting.GRAY).append(chatDestination);

		MultilineTextWidget multilineTextWidget = new MultilineTextWidget(text, this.textRenderer);
		multilineTextWidget.setPosition(bottomLeftCornerX, bottomLeftCornerY);
		addDrawableChild(multilineTextWidget);
	}
}