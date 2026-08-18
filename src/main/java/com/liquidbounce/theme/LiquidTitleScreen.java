package com.liquidbounce.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * LiquidBounce 水影主题标题屏幕 - Fabric 26.2
 * 使用PNG贴图 + 原生OpenGL渲染
 */
public class LiquidTitleScreen extends Screen {

    // ============ 贴图标识符 ============
    private static final Identifier TEX_LOGO = 
        Identifier.of("liquidtheme", "textures/gui/logo.png");
    private static final Identifier TEX_ICON_SINGLEPLAYER = 
        Identifier.of("liquidtheme", "textures/gui/icon_singleplayer.png");
    private static final Identifier TEX_ICON_MULTIPLAYER = 
        Identifier.of("liquidtheme", "textures/gui/icon_multiplayer.png");
    private static final Identifier TEX_ICON_OPTIONS = 
        Identifier.of("liquidtheme", "textures/gui/icon_options.png");
    private static final Identifier TEX_ICON_LIQUIDBOUNCE = 
        Identifier.of("liquidtheme", "textures/gui/icon_liquidbounce.png");
    private static final Identifier TEX_ICON_REALMS = 
        Identifier.of("liquidtheme", "textures/gui/icon_realms.png");
    private static final Identifier TEX_ICON_BACK = 
        Identifier.of("liquidtheme", "textures/gui/icon_back.png");
    private static final Identifier TEX_BUTTON_BG = 
        Identifier.of("liquidtheme", "textures/gui/button_bg.png");
    private static final Identifier TEX_BUTTON_HOVER = 
        Identifier.of("liquidtheme", "textures/gui/button_hover.png");

    // ============ 着色器参数 (来自background.frag) ============
    private static final float AURORA_SPEED_1 = 0.05f;
    private static final float AURORA_SPEED_2 = 0.10f;
    private static final float AURORA_SPEED_3 = 0.15f;
    private static final float AURORA_SPEED_4 = 0.07f;

    private static final int[] AURORA_1 = {0, 255, 77};
    private static final int[] AURORA_2 = {26, 128, 230};
    private static final int[] AURORA_3 = {102, 26, 204};
    private static final int[] AURORA_4 = {204, 26, 153};

    private static final int[] SKY_1 = {51, 0, 102};
    private static final int[] SKY_2 = {38, 51, 89};

    // ============ 粒子系统 ============
    private final List<StarParticle> stars = new ArrayList<>();
    private final List<Meteor> meteors = new ArrayList<>();
    private final Random random = new Random(42);
    private float time = 0f;

    private static final int MOUNTAIN_SEGMENTS = 200;

    // ============ 菜单状态 ============
    private int selectedButton = 0;
    private boolean regularButtonsShown = true;
    private boolean clientButtonsShown = false;

    // ============ 按钮数据 ============
    private final String[][] regularButtons = {
        {"Singleplayer", "singleplayer"},
        {"Multiplayer", "multiplayer"},
        {"LiquidBounce", "liquidbounce"},
        {"Options", "options"}
    };
    private final String[][] clientButtons = {
        {"Proxy Manager", "liquidbounce"},
        {"Click GUI", "liquidbounce"},
        {"Back", "back"}
    };
    private final List<LiquidMainButton> mainButtonWidgets = new ArrayList<>();

    // ============ 颜色常量 ============
    private static final int COLOR_ACCENT = 0x3B82F6;
    private static final int COLOR_TEXT = 0xD0D8F0;
    private static final int COLOR_TEXT_DIM = 0x8AA4C8;

    public LiquidTitleScreen() {
        super(Text.empty());
        for (int i = 0; i < 200; i++) {
            stars.add(new StarParticle(random));
        }
    }

    @Override
    protected void init() {
        super.init();
        mainButtonWidgets.clear();
        this.clearChildren();

        String[][] currentButtons = regularButtonsShown ? regularButtons : clientButtons;

        int btnWidth = 360;
        int btnHeight = 56;
        int startY = this.height / 3 + 10;
        int startX = 24;

        for (int i = 0; i < currentButtons.length; i++) {
            final int idx = i;
            final String[][] btnSet = currentButtons;
            LiquidMainButton btn = new LiquidMainButton(
                startX, startY + i * 64,
                btnWidth, btnHeight,
                Text.literal(currentButtons[i][0]),
                b -> onMainButtonClick(idx, btnSet)
            );
            mainButtonWidgets.add(btn);
            this.addDrawableChild(btn);
        }
    }

    private void onMainButtonClick(int index, String[][] buttons) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = buttons[index][0];

        if (name.equals("LiquidBounce")) {
            toggleButtons();
        } else if (name.equals("Back")) {
            toggleButtons();
        } else if (name.equals("Singleplayer")) {
            mc.setScreen(new SelectWorldScreen(this));
        } else if (name.equals("Multiplayer")) {
            mc.setScreen(new MultiplayerScreen(this));
        } else if (name.equals("Options")) {
            mc.setScreen(new OptionsScreen(this, mc.options));
        }
    }

    private void toggleButtons() {
        if (clientButtonsShown) {
            clientButtonsShown = false;
            regularButtonsShown = true;
        } else {
            regularButtonsShown = false;
            clientButtonsShown = true;
        }
        init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        time += delta * 0.016f;

        // 1. 极光山脉背景
        renderAuroraMountainBackground(context);

        // 2. 星空粒子
        renderStarfield(context);

        // 3. 流星
        renderMeteors(context);

        // 4. LiquidBounce Logo (贴图)
        renderLogo(context);

        // 5. 顶部UI
        renderTopBar(context, mouseX, mouseY);

        // 6. 主菜单按钮
        super.render(context, mouseX, mouseY, delta);
        renderButtonGlowEffects(context, mouseX, mouseY);

        // 7. 底部虚拟按键
        renderVirtualKeys(context);
    }

    // ============ 极光山脉背景 ============
    private void renderAuroraMountainBackground(DrawContext context) {
        int w = this.width;
        int h = this.height;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        for (int y = 0; y < h; y += 2) {
            float uvY = (float) y / h;
            float t1 = 1.0f - smoothstep(0.0f, 0.5f, uvY);
            float t2 = uvY < 1.0f ? 1.0f : 0.0f;

            int r = (int) (SKY_1[0] * t1 + SKY_2[0] * t2 * (1 - t1));
            int g = (int) (SKY_1[1] * t1 + SKY_2[1] * t2 * (1 - t1));
            int b = (int) (SKY_1[2] * t1 + SKY_2[2] * t2 * (1 - t1));

            buffer.vertex(matrix, 0, y, 0).color(r, g, b, 255);
            buffer.vertex(matrix, w, y, 0).color(r, g, b, 255);
            buffer.vertex(matrix, w, y + 2, 0).color(r, g, b, 255);
            buffer.vertex(matrix, 0, y + 2, 0).color(r, g, b, 255);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        renderAuroraLayers(context, w, h);
        renderMountainLayers(context, w, h);

        RenderSystem.disableBlend();
    }

    private void renderAuroraLayers(DrawContext context, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        renderAuroraLayer(buffer, matrix, AURORA_SPEED_1, 0.3f, AURORA_1, w, h);
        renderAuroraLayer(buffer, matrix, AURORA_SPEED_2, 0.4f, AURORA_2, w, h);
        renderAuroraLayer(buffer, matrix, AURORA_SPEED_3, 0.3f, AURORA_3, w, h);
        renderAuroraLayer(buffer, matrix, AURORA_SPEED_4, 0.2f, AURORA_4, w, h);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderAuroraLayer(BufferBuilder buffer, Matrix4f matrix, 
                                    float speed, float intensity, int[] color, int w, int h) {
        float t = time * speed;

        for (int y = 0; y < h * 0.6f; y += 3) {
            for (int x = 0; x < w; x += 6) {
                float uvX = (float) x / w;
                float uvY = (float) y / h;

                float px = uvX * 2.0f + t * 2.0f;
                float py = uvY * 2.0f + t * -2.0f;
                float n = noise(px + noise(color[0]/255f + px + t), py + noise(color[1]/255f + py + t));
                float aurora = n - uvY * 0.6f;

                if (aurora > 0.05f) {
                    int alpha = (int) Math.min(80, aurora * intensity * 200);
                    if (alpha > 2) {
                        buffer.vertex(matrix, x, y, 0).color(color[0], color[1], color[2], alpha);
                        buffer.vertex(matrix, x + 6, y, 0).color(color[0], color[1], color[2], alpha);
                        buffer.vertex(matrix, x + 6, y + 3, 0).color(color[0], color[1], color[2], 0);
                        buffer.vertex(matrix, x, y + 3, 0).color(color[0], color[1], color[2], 0);
                    }
                }
            }
        }
    }

    private void renderMountainLayers(DrawContext context, int w, int h) {
        for (int layer = 0; layer < 5; layer++) {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

            float layerOffset = layer * 4.0f;
            float layerScale = 1.0f + (5 - layer) * 0.05f;
            float colorScale = (5 - layer) / 4.0f;

            int mr = (int) (SKY_2[0] * colorScale);
            int mg = (int) (SKY_2[1] * colorScale);
            int mb = (int) (SKY_2[2] * colorScale);

            for (int i = 0; i < MOUNTAIN_SEGMENTS; i++) {
                float x = (float) i / MOUNTAIN_SEGMENTS;
                float nx = time * 0.03f * (layer + 1) + layerOffset + x * layerScale;

                float mountainHeight = 0.0f;
                float frequency = 2.0f;
                float amplitude = 0.5f;
                for (int oct = 0; oct < 5; oct++) {
                    mountainHeight += noise(nx * frequency, 0) * amplitude;
                    frequency *= 2.0f;
                    amplitude *= 0.5f;
                }

                float height = (5 - layer) * 0.1f * (1.0f - smoothstep(0.0f, 1.0f, mountainHeight));
                float y1 = h - height * h * 0.35f;

                x = (float) (i + 1) / MOUNTAIN_SEGMENTS;
                nx = time * 0.03f * (layer + 1) + layerOffset + x * layerScale;
                mountainHeight = 0.0f;
                frequency = 2.0f;
                amplitude = 0.5f;
                for (int oct = 0; oct < 5; oct++) {
                    mountainHeight += noise(nx * frequency, 0) * amplitude;
                    frequency *= 2.0f;
                    amplitude *= 0.5f;
                }
                height = (5 - layer) * 0.1f * (1.0f - smoothstep(0.0f, 1.0f, mountainHeight));
                float y2 = h - height * h * 0.35f;

                float x1 = (float) i / MOUNTAIN_SEGMENTS * w;
                float x2 = (float) (i + 1) / MOUNTAIN_SEGMENTS * w;

                buffer.vertex(matrix, x1, y1, 0).color(mr, mg, mb, 255);
                buffer.vertex(matrix, x2, y2, 0).color(mr, mg, mb, 255);
                buffer.vertex(matrix, x2, h, 0).color(mr/3, mg/3, mb/3, 255);
                buffer.vertex(matrix, x1, h, 0).color(mr/3, mg/3, mb/3, 255);
            }

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    // ============ 星空渲染 ============
    private void renderStarfield(DrawContext context) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        for (StarParticle star : stars) {
            star.update(time);
            float sx = star.x * this.width;
            float sy = star.y * this.height * 0.7f;
            float alpha = star.getAlpha(time);
            float size = star.size;
            int a = (int) (alpha * 255);

            buffer.vertex(matrix, sx - size, sy - size, 0).color(200, 210, 255, a);
            buffer.vertex(matrix, sx + size, sy - size, 0).color(200, 210, 255, a);
            buffer.vertex(matrix, sx + size, sy + size, 0).color(200, 210, 255, a);
            buffer.vertex(matrix, sx - size, sy + size, 0).color(200, 210, 255, a);

            if (alpha > 0.6f && star.size > 1.0f) {
                float glow = size * 3;
                int glowA = (int) (alpha * 60);
                buffer.vertex(matrix, sx - glow, sy - 0.3f, 0).color(150, 180, 255, glowA);
                buffer.vertex(matrix, sx + glow, sy - 0.3f, 0).color(150, 180, 255, glowA);
                buffer.vertex(matrix, sx + glow, sy + 0.3f, 0).color(150, 180, 255, glowA);
                buffer.vertex(matrix, sx - glow, sy + 0.3f, 0).color(150, 180, 255, glowA);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private void renderMeteors(DrawContext context) {
        if (random.nextFloat() < 0.005f) {
            meteors.add(new Meteor(random, this.width, this.height));
        }
        meteors.removeIf(m -> m.update(this.width, this.height));

        if (meteors.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        for (Meteor meteor : meteors) {
            int alpha = (int) ((meteor.life / 30f) * 200);
            buffer.vertex(matrix, meteor.x - 1.5f, meteor.y - 1.5f, 0).color(200, 220, 255, alpha);
            buffer.vertex(matrix, meteor.x + 1.5f, meteor.y - 1.5f, 0).color(200, 220, 255, alpha);
            buffer.vertex(matrix, meteor.x + 1.5f, meteor.y + 1.5f, 0).color(200, 220, 255, alpha);
            buffer.vertex(matrix, meteor.x - 1.5f, meteor.y + 1.5f, 0).color(200, 220, 255, alpha);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    // ============ LOGO渲染 (使用贴图) ============
    private void renderLogo(DrawContext context) {
        int logoW = 200;
        int logoH = 75;
        int logoX = 24;
        int logoY = 15;

        // 绘制Logo贴图
        context.drawTexture(TEX_LOGO, logoX, logoY, 0, 0, logoW, logoH, logoW, logoH);
    }

    // ============ 顶部栏 ============
    private void renderTopBar(DrawContext context, int mouseX, int mouseY) {
        drawRoundedRect(context, 12, 12, 48, 48, 12, 30, 40, 70, 180);
        context.drawText(this.textRenderer, "ESC", 22, 28, COLOR_ACCENT, false);

        drawCircle(context, 80, 36, 24, 40, 50, 80, 128);
        context.drawText(this.textRenderer, "IMS", 68, 30, COLOR_TEXT_DIM, false);

        drawCircle(context, 140, 36, 24, 40, 50, 80, 128);
        context.drawText(this.textRenderer, "Enter", 126, 30, COLOR_TEXT_DIM, false);

        int centerX = this.width / 2;
        drawRoundedRect(context, centerX - 32, 12, 28, 48, 14, 20, 30, 60, 100);
        context.drawText(this.textRenderer, "\u2191", centerX - 24, 28, COLOR_ACCENT, false);
        drawRoundedRect(context, centerX + 4, 12, 28, 48, 14, 20, 30, 60, 100);
        context.drawText(this.textRenderer, "\u2193", centerX + 12, 28, COLOR_ACCENT, false);

        drawRoundedRect(context, this.width - 76, 12, 64, 36, 8, 50, 60, 90, 154);
        context.drawText(this.textRenderer, "功能库", this.width - 70, 22, 0xC0C8E0, false);

        drawRoundedRect(context, this.width - 76, 54, 64, 48, 8, 50, 60, 90, 154);
        context.drawText(this.textRenderer, "全键", this.width - 62, 70, 0xC0C8E0, false);

        drawCircle(context, this.width - 100, 36, 22, 20, 30, 60, 200);
        context.drawText(this.textRenderer, "\u263A", this.width - 108, 28, COLOR_ACCENT, false);
    }

    // ============ 按钮发光效果 ============
    private void renderButtonGlowEffects(DrawContext context, int mouseX, int mouseY) {
        for (int i = 0; i < mainButtonWidgets.size(); i++) {
            LiquidMainButton btn = mainButtonWidgets.get(i);
            if (btn.isMouseOver(mouseX, mouseY) || i == selectedButton) {
                int bx = btn.getX();
                int by = btn.getY();
                int bw = btn.getWidth();
                int bh = btn.getHeight();

                RenderSystem.enableBlend();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                Tessellator t = Tessellator.getInstance();
                BufferBuilder b = t.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                Matrix4f m = context.getMatrices().peek().getPositionMatrix();

                b.vertex(m, bx, by, 0).color(59, 130, 246, 100);
                b.vertex(m, bx + 6, by, 0).color(59, 130, 246, 20);
                b.vertex(m, bx + 6, by + bh, 0).color(59, 130, 246, 20);
                b.vertex(m, bx, by + bh, 0).color(59, 130, 246, 100);

                b.vertex(m, bx, by, 0).color(59, 130, 246, 15);
                b.vertex(m, bx + bw, by, 0).color(59, 130, 246, 5);
                b.vertex(m, bx + bw, by + bh, 0).color(59, 130, 246, 5);
                b.vertex(m, bx, by + bh, 0).color(59, 130, 246, 15);

                BufferRenderer.drawWithGlobalProgram(b.end());
                RenderSystem.disableBlend();
            }
        }
    }

    // ============ 底部虚拟按键 ============
    private void renderVirtualKeys(DrawContext context) {
        String[] keys = {"Esc", "Tab", "Up", "Right", "SHIFT", "CTRL", "Down", "Left", "->"};
        int startX = 12;
        int startY = this.height - 68;
        int gap = 6;
        int keySize = 48;

        for (int i = 0; i < keys.length; i++) {
            int x = startX + i * (keySize + gap);
            drawRoundedRect(context, x, startY, keySize, keySize, 14, 20, 30, 60, 100);
            int textW = this.textRenderer.getWidth(keys[i]);
            context.drawText(this.textRenderer, keys[i], x + (keySize - textW) / 2, startY + 18, COLOR_TEXT_DIM, false);
        }

        drawRoundedRect(context, this.width - 48, this.height - 48, 36, 36, 8, 30, 40, 70, 100);
        context.drawText(this.textRenderer, "\u2684", this.width - 40, this.height - 40, 0xFFFFFF, false);
    }

    // ============ 辅助方法 ============
    private void drawRoundedRect(DrawContext context, int x, int y, int w, int h,
                                    int radius, int r, int g, int b, int a) {
        context.fill(x + radius, y, x + w - radius, y + h, (a << 24) | (r << 16) | (g << 8) | b);
        context.fill(x, y + radius, x + w, y + h - radius, (a << 24) | (r << 16) | (g << 8) | b);
        context.drawBorder(x, y, w, h, 0x303B82F6);
    }

    private void drawCircle(DrawContext context, int cx, int cy, int radius,
                             int r, int g, int b, int a) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1,
                        (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
    }

    private float noise(float x, float y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        fx = fx * fx * (3.0f - 2.0f * fx);
        fy = fy * fy * (3.0f - 2.0f * fy);
        float a = hash(ix + hash(iy));
        float b = hash(ix + 1 + hash(iy));
        float c = hash(ix + hash(iy + 1));
        float d = hash(ix + 1 + hash(iy + 1));
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
    }

    private float hash(float n) {
        return (float) ((Math.sin(n) * 43758.5453) % 1.0);
    }

    private float hash(int n) {
        return (float) ((Math.sin(n) * 43758.5453) % 1.0);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float smoothstep(float edge0, float edge1, float x) {
        float t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) {
            selectedButton = (selectedButton - 1 + mainButtonWidgets.size()) % mainButtonWidgets.size();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            selectedButton = (selectedButton + 1) % mainButtonWidgets.size();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (selectedButton < mainButtonWidgets.size()) {
                mainButtonWidgets.get(selectedButton).onPress();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ============ 内部类 ============

    private static class StarParticle {
        float x, y, size, twinkleSpeed, phase;

        StarParticle(Random random) {
            this.x = random.nextFloat();
            this.y = random.nextFloat();
            this.size = random.nextFloat() * 1.5f + 0.3f;
            this.twinkleSpeed = random.nextFloat() * 0.02f + 0.005f;
            this.phase = random.nextFloat() * (float) Math.PI * 2;
        }

        void update(float time) {
            x += (float) Math.sin(time * 0.1 + phase) * 0.00005f;
        }

        float getAlpha(float time) {
            float base = 0.3f + (float) Math.sin(time * twinkleSpeed * 100 + phase) * 0.3f;
            return Math.max(0.1f, Math.min(1.0f, base + 0.4f));
        }
    }

    private static class Meteor {
        float x, y, vx, vy;
        int life;

        Meteor(Random random, int w, int h) {
            this.x = random.nextFloat() * w;
            this.y = random.nextFloat() * h * 0.3f;
            this.vx = -random.nextFloat() * 5 - 2;
            this.vy = random.nextFloat() * 2.5f + 1;
            this.life = 25 + random.nextInt(15);
        }

        boolean update(int w, int h) {
            x += vx;
            y += vy;
            life--;
            return life <= 0 || x < -100 || y > h + 100;
        }
    }

    /**
     * 主按钮 - 使用贴图背景 + 图标贴图
     */
    private static class LiquidMainButton extends ButtonWidget {
        private float hoverProgress = 0f;
        private float targetHover = 0f;

        LiquidMainButton(int x, int y, int w, int h, Text text, PressAction onPress) {
            super(x, y, w, h, text, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            targetHover = hovered ? 1f : 0f;
            hoverProgress = MathHelper.lerp(delta * 0.15f, hoverProgress, targetHover);

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            // 使用贴图背景
            Identifier bgTex = hoverProgress > 0.5f ? TEX_BUTTON_HOVER : TEX_BUTTON_BG;
            context.drawTexture(bgTex, x, y, 0, 0, w, h, w, h);

            // 左侧蓝色发光条
            int glowAlpha = (int) (hoverProgress * 60 + 100);
            context.fill(x, y, x + 4, y + h, (glowAlpha << 24) | 0x3B82F6);

            // 图标 (使用贴图)
            int iconSize = h - 12;
            int iconX = x + 12;
            int iconY = y + 6;

            // 根据按钮类型选择图标
            String msg = getMessage().getString();
            Identifier iconTex = TEX_ICON_LIQUIDBOUNCE;
            if (msg.contains("Singleplayer") || msg.contains("单人")) {
                iconTex = TEX_ICON_SINGLEPLAYER;
            } else if (msg.contains("Multiplayer") || msg.contains("多人")) {
                iconTex = TEX_ICON_MULTIPLAYER;
            } else if (msg.contains("Options") || msg.contains("选项")) {
                iconTex = TEX_ICON_OPTIONS;
            } else if (msg.contains("Realms")) {
                iconTex = TEX_ICON_REALMS;
            } else if (msg.contains("Back") || msg.contains("返回")) {
                iconTex = TEX_ICON_BACK;
            }

            // 绘制圆形图标背景
            int iconCX = iconX + iconSize / 2;
            int iconCY = iconY + iconSize / 2;
            int iconR = iconSize / 2;
            for (int dy = -iconR; dy <= iconR; dy++) {
                for (int dx = -iconR; dx <= iconR; dx++) {
                    if (dx * dx + dy * dy <= iconR * iconR) {
                        int ir = (int) MathHelper.lerp(hoverProgress, 59, 100);
                        int ig = (int) MathHelper.lerp(hoverProgress, 60, 150);
                        int ib = (int) MathHelper.lerp(hoverProgress, 100, 255);
                        context.fill(iconCX + dx, iconCY + dy, iconCX + dx + 1, iconCY + dy + 1,
                            (255 << 24) | (ir << 16) | (ig << 8) | ib);
                    }
                }
            }

            // 绘制图标贴图
            context.drawTexture(iconTex, iconX + 4, iconY + 4, 0, 0, iconSize - 8, iconSize - 8, iconSize - 8, iconSize - 8);

            // 标题文字
            int textColor = hovered ? 0xFFFFFF : COLOR_TEXT;
            context.drawText(MinecraftClient.getInstance().textRenderer, getMessage(),
                x + iconSize + 24, y + h / 2 - 4, textColor, false);

            // 悬停边框
            if (hoverProgress > 0.01f) {
                int borderAlpha = (int) (hoverProgress * 40);
                context.drawBorder(x, y, w, h, (borderAlpha << 24) | 0x3B82F6);
            }
        }
    }
}
