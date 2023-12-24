package com.mrbreaknfix.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class DMCmainConfigScreen extends Screen {
    public DMCmainConfigScreen() {super(Text.of("Discord MC Config"));}

    @Override
    protected void init() {
        if (this.client == null) {return;}
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.DiscordMC.ConfigureAccounts"), button -> {client.setScreen(new DMCtokenSelectorScreen(this));}).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.client.setScreen(null)).dimensions(this.width / 2 - 155 + 160, this.height - 29, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackgroundTexture(context);
    }
}
