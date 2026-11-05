package dev.unyielding.quests.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Small drawing helpers: quest node shapes and text colour codes. */
public final class Gfx {
    private Gfx() {
    }

    /** Converts FTB-style "&6" colour codes into Minecraft section codes. */
    public static String codes(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
                sb.append('§');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Like {@link #codes} but turns black / dark grey text light grey so titles stay readable on dark windows. */
    public static String readable(String s) {
        return codes(s).replace("§0", "§7").replace("§8", "§7");
    }

    /** Removes colour/format codes. */
    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "").replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static float[] outline(String shape, int sides) {
        // returns unit-radius polygon points [x0,y0,x1,y1,...]
        switch (shape) {
            case "gear": {
                int teeth = 8;
                float[] p = new float[teeth * 4 * 2];
                for (int i = 0; i < teeth * 4; i++) {
                    double a = Math.PI * 2 * i / (teeth * 4) - Math.PI / 2;
                    int seg = i % 4;
                    float r = (seg == 0 || seg == 1) ? 1.0f : 0.78f;
                    p[i * 2] = (float) (Math.cos(a) * r);
                    p[i * 2 + 1] = (float) (Math.sin(a) * r);
                }
                return p;
            }
            case "star": {
                float[] p = new float[20];
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 - Math.PI / 2;
                    float r = (i % 2 == 0) ? 1.0f : 0.45f;
                    p[i * 2] = (float) (Math.cos(a) * r);
                    p[i * 2 + 1] = (float) (Math.sin(a) * r);
                }
                return p;
            }
            case "heart": {
                int n = 36;
                float[] p = new float[n * 2];
                for (int i = 0; i < n; i++) {
                    double t = Math.PI * 2 * i / n;
                    double x = 16 * Math.pow(Math.sin(t), 3);
                    double y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
                    p[i * 2] = (float) (x / 17.0);
                    p[i * 2 + 1] = (float) ((y + 2) / 17.0);
                }
                return p;
            }
            default: {
                double rot = switch (shape) {
                    case "square" -> Math.PI / 4;
                    case "pentagon" -> -Math.PI / 2;
                    case "octagon" -> Math.PI / 8;
                    case "hexagon" -> 0;
                    default -> 0;
                };
                float scale = shape.equals("square") ? (float) Math.sqrt(2) : 1f;
                float[] p = new float[sides * 2];
                for (int i = 0; i < sides; i++) {
                    double a = Math.PI * 2 * i / sides + rot;
                    p[i * 2] = (float) (Math.cos(a) * scale);
                    p[i * 2 + 1] = (float) (Math.sin(a) * scale);
                }
                return p;
            }
        }
    }

    private static int sidesOf(String shape) {
        return switch (shape) {
            case "diamond", "square" -> 4;
            case "pentagon" -> 5;
            case "hexagon" -> 6;
            case "octagon" -> 8;
            default -> 32;
        };
    }

    /** Draws a filled node shape of the given radius (screen pixels, may be fractional). */
    public static void shape(GuiGraphics g, String shape, float cx, float cy, float r, int argb) {
        if (shape.equals("rsquare")) {
            g.flush();
            int x0 = Math.round(cx - r), y0 = Math.round(cy - r), x1 = Math.round(cx + r), y1 = Math.round(cy + r);
            g.fill(x0 + 1, y0, x1 - 1, y1, argb);
            g.fill(x0, y0 + 1, x1, y1 - 1, argb);
            return;
        }
        float[] pts = outline(shape, sidesOf(shape));
        fan(g, pts, cx, cy, r, argb);
    }

    private static void fan(GuiGraphics g, float[] pts, float cx, float cy, float r, int argb) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        int a = argb >>> 24, rr = argb >> 16 & 255, gg = argb >> 8 & 255, bb = argb & 255;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int k = i, l = (i + 1) % n;
            buf.addVertex(m, cx, cy, 0).setColor(rr, gg, bb, a);
            buf.addVertex(m, cx + pts[k * 2] * r, cy + pts[k * 2 + 1] * r, 0).setColor(rr, gg, bb, a);
            buf.addVertex(m, cx + pts[l * 2] * r, cy + pts[l * 2 + 1] * r, 0).setColor(rr, gg, bb, a);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Filled shape whose colour fades from {@code center} in the middle to {@code rim} at the edge. */
    public static void shapeGradient(GuiGraphics g, String shape, float cx, float cy, float r, int center, int rim) {
        float[] pts = outline(shape.equals("rsquare") ? "square" : shape, sidesOf(shape.equals("rsquare") ? "square" : shape));
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int k = i, l = (i + 1) % n;
            col(buf.addVertex(m, cx, cy - r * 0.25f, 0), center);
            col(buf.addVertex(m, cx + pts[k * 2] * r, cy + pts[k * 2 + 1] * r, 0), rim);
            col(buf.addVertex(m, cx + pts[l * 2] * r, cy + pts[l * 2 + 1] * r, 0), rim);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Soft round glow: solid-ish at radius r0, fading to nothing at r1. */
    public static void glow(GuiGraphics g, float cx, float cy, float r0, float r1, int argb) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = 40;
        int clear = argb & 0x00FFFFFF;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            col(buf.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0), argb);
            col(buf.addVertex(m, cx + c0 * r1, cy + s0 * r1, 0), clear);
            col(buf.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0), clear);
            col(buf.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0), argb);
            col(buf.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0), clear);
            col(buf.addVertex(m, cx + c1 * r0, cy + s1 * r0, 0), argb);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Ring segment from the top, clockwise, covering {@code frac} of a full circle (progress ring). */
    public static void arc(GuiGraphics g, float cx, float cy, float rIn, float rOut, float frac, int argb) {
        if (frac <= 0) return;
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int seg = Math.max(2, (int) (48 * Math.min(1f, frac)));
        double total = Math.PI * 2 * Math.min(1f, frac);
        for (int i = 0; i < seg; i++) {
            double a0 = -Math.PI / 2 + total * i / seg, a1 = -Math.PI / 2 + total * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            col(buf.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0), argb);
            col(buf.addVertex(m, cx + c0 * rOut, cy + s0 * rOut, 0), argb);
            col(buf.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0), argb);
            col(buf.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0), argb);
            col(buf.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0), argb);
            col(buf.addVertex(m, cx + c1 * rIn, cy + s1 * rIn, 0), argb);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void col(com.mojang.blaze3d.vertex.VertexConsumer v, int argb) {
        v.setColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
    }

    /** Thick line between two points. */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int argb) {
        g.flush();
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        Matrix4f m = g.pose().last().pose();
        int a = argb >>> 24, rr = argb >> 16 & 255, gg = argb >> 8 & 255, bb = argb & 255;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.addVertex(m, x0 + nx, y0 + ny, 0).setColor(rr, gg, bb, a);
        buf.addVertex(m, x1 + nx, y1 + ny, 0).setColor(rr, gg, bb, a);
        buf.addVertex(m, x1 - nx, y1 - ny, 0).setColor(rr, gg, bb, a);
        buf.addVertex(m, x0 - nx, y0 - ny, 0).setColor(rr, gg, bb, a);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
