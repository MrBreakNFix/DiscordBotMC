package com.mrbreaknfix.mixin;

import com.mrbreaknfix.Discordmc;
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
        if (Discordmc.config.isDiscordChatEnabled() && !chatText.startsWith("/") && !chatText.startsWith(".") && !chatText.startsWith(",")) {

            Discordmc.sendDiscordMessage(chatText, Discordmc.config.getCurrentGuild(), Discordmc.config.getCurrentChat());

            mc.setScreen(null);
            cir.setReturnValue(false);
        }
    }

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        MutableText channel = Text.literal(Discordmc.config.getCurrentChat() + "   ").formatted(Formatting.WHITE);
        MutableText guild = Text.literal(Discordmc.config.getCurrentGuild()).formatted(Formatting.WHITE);

        MutableText chatDestination = Text.literal("Chatting to: ").formatted(Formatting.GRAY).append(guild).append(Text.literal(", in #").formatted(Formatting.GRAY)).append(channel);

        MutableText text = Text.literal("").formatted(Formatting.GREEN).append(chatDestination);

        if (!Discordmc.config.isDiscordChatEnabled()) {
            text = Text.literal("").formatted(Formatting.DARK_GRAY).append(chatDestination);
        }
        // warn the user if the bot token is set to the default value
        if (Discordmc.config.getBotToken().equals("Your bot token") || Discordmc.jda == null) {
            text = Text.literal("").formatted(Formatting.RED).append(Text.literal("WARNING: ").formatted(Formatting.RED)).append(Text.literal("You do not have a valid token set! You can do this with \"/dc SetBotToken your-bot-token\", you can also do \"/dc help\" for a list of commands!").formatted(Formatting.RED));
        }

        MultilineTextWidget multilineTextWidget = getTextWidget(text, mc);
        addDrawableChild(multilineTextWidget);
    }

    @Unique
    @NotNull
    private MultilineTextWidget getTextWidget(MutableText text, MinecraftClient mc) {
        MultilineTextWidget multilineTextWidget = new MultilineTextWidget(text, this.textRenderer);
        // bottom right version
//        multilineTextWidget.setPosition(mc.getWindow().getScaledWidth() - multilineTextWidget.getWidth() - 2, mc.getWindow().getScaledHeight() - 25); // bottom right version
        // top right version
        multilineTextWidget.setPosition(mc.getWindow().getScaledWidth() - multilineTextWidget.getWidth() - 2, 2); // top right version
        return multilineTextWidget;
    }
}