package anticope.rejects.gui.screens;

import anticope.rejects.mixin.EntityAccessor;
import anticope.rejects.modules.InteractionMenu;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.render.PeekScreen;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.meteordev.starscript.compiler.Compiler;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.utils.Error;
import org.meteordev.starscript.utils.StarscriptError;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/*
    Ported from: https://github.com/BleachDrinker420/BleachHack/pull/211
*/
public class InteractionScreen extends Screen {

    public static Entity interactionMenuEntity;

    private final Entity entity;
    private String focusedString = null;
    private int crosshairX, crosshairY, focusedDot = -1;
    private float yaw, pitch;
    private final Map<String, Consumer<Entity>> functions;
    private final Map<String, String> msgs;
    public RenderPipeline renderPipeline;

    private final Identifier GUI_ICONS_TEXTURE = Identifier.of("textures/gui/icons.png");

    private final StaticListener shiftListener = new StaticListener();

    // Style
    private final int selectedDotColor;
    private final int dotColor;
    private final int backgroundColor;
    private final int borderColor;
    private final int textColor;

    public InteractionScreen(Entity e) {
        this(e, Modules.get().get(InteractionMenu.class));
    }

    public InteractionScreen(Entity entity, InteractionMenu module) {
        super(Text.literal("Menu Screen"));
        if (module == null) closeScreen();

        selectedDotColor = module.selectedDotColor.get().getPacked();
        dotColor = module.dotColor.get().getPacked();
        backgroundColor = module.backgroundColor.get().getPacked();
        borderColor = module.borderColor.get().getPacked();
        textColor = module.textColor.get().getPacked();

        this.entity = entity;
        functions = new HashMap<>();
        functions.put("Stats", (Entity e) -> {
            closeScreen();
            client.setScreen(new StatsScreen(e));
        });
        switch (entity) {
            case PlayerEntity playerEntity -> functions.put("Open Inventory", (Entity e) -> {
                closeScreen();
                client.setScreen(new InventoryScreen((PlayerEntity) e));
            });
            case AbstractHorseEntity abstractHorseEntity -> functions.put("Open Inventory", (Entity e) -> {
                closeScreen();
                if (client.player.isRiding()) {
//                    client.player.networkHandler.sendPacket(new PlayerInputC2SPacket(0, 0, false, true));
                    client.player.networkHandler.sendPacket(new PlayerInputC2SPacket(new PlayerInput(false, false, false, false, false, true, false)));

                }
                client.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(entity, true, Hand.MAIN_HAND));
                client.player.setSneaking(false);
            });
            case StorageMinecartEntity storageMinecartEntity -> functions.put("Open Inventory", (Entity e) -> {
                closeScreen();
                client.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(entity, true, Hand.MAIN_HAND));
            });
            case null, default -> functions.put("Open Inventory", (Entity e) -> {
                closeScreen();
                ItemStack container = new ItemStack(Items.CHEST);
                container.set(DataComponentTypes.CUSTOM_NAME, e.getName());
                client.setScreen(new PeekScreen(container, getInventory(e)));
            });
        }

        functions.put("Spectate", (Entity e) -> {
            MinecraftClient.getInstance().setCameraEntity(e);
            client.player.sendMessage(Text.literal("Sneak to un-spectate."), true);
            MeteorClient.EVENT_BUS.subscribe(shiftListener);
            closeScreen();
        });

        if (entity.isGlowing()) {
            functions.put("Remove glow", (Entity e) -> {
                e.setGlowing(false);
                ((EntityAccessor) e).invokeSetFlag(6, false);
                closeScreen();
            });
        } else {
            functions.put("Glow", (Entity e) -> {
                e.setGlowing(true);
                ((EntityAccessor) e).invokeSetFlag(6, true);
                closeScreen();
            });
        }
        if (entity.noClip) {
            functions.put("Disable NoClip", (Entity e) -> {
                entity.noClip = false;
                closeScreen();
            });
        } else {
            functions.put("NoClip", (Entity e) -> {
                entity.noClip = true;
                closeScreen();
            });
        }
        msgs = Modules.get().get(InteractionMenu.class).messages.get();
        msgs.keySet().forEach((key) -> {
            functions.put(key, (Entity e) -> {
                closeScreen();
                interactionMenuEntity = e;
                var result = Parser.parse(msgs.get(key));
                if (result.hasErrors()) {
                    for (Error error : result.errors) MeteorStarscript.printChatError(error);
                    return;
                }
                var script = Compiler.compile(result);
                try {
                    var section = MeteorStarscript.ss.run(script);
                    client.setScreen(new ChatScreen(section.text));
                } catch (StarscriptError err) {
                    MeteorStarscript.printChatError(err);
                }
            });
        });
        functions.put("Cancel", (Entity e) -> {
            closeScreen();
        });
    }

    private ItemStack[] getInventory(Entity e) {
        ItemStack[] stack = new ItemStack[27];
        final int[] index = {0};
        if (e instanceof EndermanEntity) {
            try {
                stack[index[0]] = ((EndermanEntity) e).getCarriedBlock().getBlock().asItem().getDefaultStack();
                index[0]++;
            } catch (NullPointerException ex) {
            }
        }
        if (e instanceof HorseEntity) {
            if (((HorseEntity) e).isWearingBodyArmor()) {
                stack[index[0]] = Items.SADDLE.getDefaultStack();
                index[0]++;
            }
        }

        LivingEntity a = (LivingEntity) e;
        ItemStack itemStack = a.getStackInHand(Hand.MAIN_HAND);
        if (itemStack != null) {
            stack[index[0]] = itemStack;
            index[0]++;
        }

        for (int i = index[0]; i < 27; i++) stack[i] = Items.AIR.getDefaultStack();
        return stack;
    }

    public void init() {
        super.init();
        this.cursorMode(GLFW.GLFW_CURSOR_HIDDEN);
        yaw = client.player.getYaw();
        pitch = client.player.getPitch();
    }

    private void cursorMode(int mode) {
        KeyBinding.unpressAll();
        double x = (double) this.client.getWindow().getWidth() / 2;
        double y = (double) this.client.getWindow().getHeight() / 2;
        InputUtil.setCursorParameters(this.client.getWindow().getHandle(), mode, x, y);
    }

    public void tick() {
        if (Modules.get().get(InteractionMenu.class).keybind.get().isPressed())
            close();
    }

    private void closeScreen() {
        client.setScreen(null);
    }

    public void close() {
        cursorMode(GLFW.GLFW_CURSOR_NORMAL);
        // This makes the magic
        if (focusedString != null) {
            functions.get(focusedString).accept(this.entity);
        } else
            client.setScreen(null);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Matrix3x2fStack matrix = context.getMatrices();
        // Fake crosshair stuff
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableBlend();
        // Common blend mode for UI elements
        GlStateManager._blendFuncSeparate(
                770,  // GL_SRC_ALPHA
                771,  // GL_ONE_MINUS_SRC_ALPHA
                1,    // GL_ONE
                0     // GL_ZERO
        );

    //    context.drawTexturedQuad(GUI_ICONS_TEXTURE, crosshairX - 8, crosshairY - 8, 0, 0, 15, 15, 256, 256);

        drawDots(context, (int) (Math.min(height, width) / 2 * 0.75), mouseX, mouseY);
        matrix.scale(2f, 2f);
        context.drawCenteredTextWithShadow(textRenderer, entity.getName(), width / 4, 6, 0xFFFFFFFF);

        Vector2f mouse = getMouseVecs(mouseX, mouseY);

        this.crosshairX = (int) mouse.x + width / 2;
        this.crosshairY = (int) mouse.y + height / 2;

        client.player.setYaw(yaw + mouse.x / 3);
        client.player.setPitch(MathHelper.clamp(pitch + mouse.y / 3, -90f, 90f));
        super.render(context, mouseX, mouseY, delta);
    }

    private Vector2f getMouseVecs(int mouseX, int mouseY) {
        int scale = client.options.getGuiScale().getValue();
        Vector2f mouse = new Vector2f(mouseX, mouseY);
        Vector2f center = new Vector2f(width / 2, height / 2);
        mouse.sub(center);

        // Add this check to prevent normalizing a zero vector
        if (mouse.x != 0 || mouse.y != 0) {
            mouse.normalize();
        }

        if (scale == 0) scale = 4;

        // Move crossHair based on distance between mouse and center. But with limit
        if (Math.hypot(width / 2 - mouseX, height / 2 - mouseY) < 1f / scale * 200f)
            mouse.mul((float) Math.hypot(width / 2 - mouseX, height / 2 - mouseY));
        else
            mouse.mul(1f / scale * 200f);
        return mouse;
    }


    private void drawDots(DrawContext context, int radius, int mouseX, int mouseY) {
        ArrayList<Point> pointList = new ArrayList<Point>();
        String[] cache = new String[functions.size()];
        double lowestDistance = Double.MAX_VALUE;
        int i = 0;

        for (String string : functions.keySet()) {
            // Just some fancy calculations to get the positions of the dots
            double s = (double) i / functions.size() * 2 * Math.PI;
            int x = (int) Math.round(radius * Math.cos(s) + width / 2);
            int y = (int) Math.round(radius * Math.sin(s) + height / 2);
            drawTextField(context, x, y, string);

            // Calculate lowest distance between mouse and dot
            if (Math.hypot(x - mouseX, y - mouseY) < lowestDistance) {
                lowestDistance = Math.hypot(x - mouseX, y - mouseY);
                focusedDot = i;
            }

            cache[i] = string;
            pointList.add(new Point(x, y));
            i++;
        }

        // Go through all point and if it is focused -> drawing different color, changing closest string value
        for (int j = 0; j < functions.size(); j++) {
            Point point = pointList.get(j);
            if (pointList.get(focusedDot) == point) {
                drawDot(context, point.x - 4, point.y - 4, selectedDotColor);
                this.focusedString = cache[focusedDot];
            } else
                drawDot(context, point.x - 4, point.y - 4, dotColor);
        }
    }

    private void drawRect(DrawContext context, int startX, int startY, int width, int height, int colorInner, int colorOuter) {
        context.drawHorizontalLine(startX, startX + width, startY, colorOuter);
        context.drawHorizontalLine(startX, startX + width, startY + height, colorOuter);
        context.drawVerticalLine(startX, startY, startY + height, colorOuter);
        context.drawVerticalLine(startX + width, startY, startY + height, colorOuter);
        context.fill(startX + 1, startY + 1, startX + width, startY + height, colorInner);
    }

    private void drawTextField(DrawContext context, int x, int y, String key) {
        if (x >= width / 2) {
            drawRect(context, x + 10, y - 8, textRenderer.getWidth(key) + 3, 15, backgroundColor, borderColor);
            context.drawTextWithShadow(textRenderer, key, x + 12, y - 4, textColor);
        } else {
            drawRect(context, x - 14 - textRenderer.getWidth(key), y - 8, textRenderer.getWidth(key) + 3, 15, backgroundColor, borderColor);
            context.drawTextWithShadow(textRenderer, key, x - 12 - textRenderer.getWidth(key), y - 4, textColor);
        }
    }

    // Literally drawing it in code
    private void drawDot(DrawContext context, int startX, int startY, int colorInner) {
        // Draw dot itself
        context.drawHorizontalLine(startX + 2, startX + 5, startY, borderColor);
        context.drawHorizontalLine(startX + 1, startX + 6, startY + 1, borderColor);
        context.drawHorizontalLine(startX + 2, startX + 5, startY + 1, colorInner);
        context.fill(startX, startY + 2, startX + 8, startY + 6, borderColor);
        context.fill(startX + 1, startY + 2, startX + 7, startY + 6, colorInner);
        context.drawHorizontalLine(startX + 1, startX + 6, startY + 6, borderColor);
        context.drawHorizontalLine(startX + 2, startX + 5, startY + 6, colorInner);
        context.drawHorizontalLine(startX + 2, startX + 5, startY + 7, borderColor);

        // Draw light overlay
        context.drawHorizontalLine(startX + 2, startX + 3, startY + 1, 0x80FFFFFF);
        context.drawHorizontalLine(startX + 1, startX + 1, startY + 2, 0x80FFFFFF);
    }

    private class StaticListener {
        @EventHandler
        private void onKey(KeyEvent event) {
            if (client.options.sneakKey.matchesKey(event.key, 0) || client.options.sneakKey.matchesMouse(event.key)) {
                client.setCameraEntity(client.player);
                event.cancel();
                MeteorClient.EVENT_BUS.unsubscribe(this);
            }
        }
    }
}
