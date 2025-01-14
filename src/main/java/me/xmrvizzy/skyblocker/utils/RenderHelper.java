package me.xmrvizzy.skyblocker.utils;

import me.x150.renderer.render.Renderer3d;
import me.xmrvizzy.skyblocker.mixin.accessor.BeaconBlockEntityRendererInvoker;
import me.xmrvizzy.skyblocker.utils.culling.OcclusionCulling;
import me.xmrvizzy.skyblocker.utils.title.Title;
import me.xmrvizzy.skyblocker.utils.title.TitleContainer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.awt.*;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;

public class RenderHelper {
    private static final Vec3d ONE = new Vec3d(1, 1, 1);
    private static final int MAX_OVERWORLD_BUILD_HEIGHT = 319;

    public static void renderFilledThroughWallsWithBeaconBeam(WorldRenderContext context, BlockPos pos, float[] colorComponents, float alpha) {
        renderFilledThroughWalls(context, pos, colorComponents, alpha);
        renderBeaconBeam(context, pos, colorComponents);
    }

    public static void renderFilledThroughWalls(WorldRenderContext context, BlockPos pos, float[] colorComponents, float alpha) {
        if (FrustumUtils.isVisible(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
            Renderer3d.renderThroughWalls();
    	    renderFilled(context, pos, colorComponents, alpha);
    	    Renderer3d.stopRenderThroughWalls();
        }
    }

    public static void renderFilledIfVisible(WorldRenderContext context, BlockPos pos, float[] colorComponents, float alpha) {
        if (OcclusionCulling.isVisible(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
            renderFilled(context, pos, colorComponents, alpha);
        }
    }

    private static void renderFilled(WorldRenderContext context, BlockPos pos, float[] colorComponents, float alpha) {
        Renderer3d.renderFilled(context.matrixStack(), new Color(colorComponents[0], colorComponents[1], colorComponents[2], alpha), Vec3d.of(pos), ONE);
    }

    private static void renderBeaconBeam(WorldRenderContext context, BlockPos pos, float[] colorComponents) {
        if (FrustumUtils.isVisible(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, MAX_OVERWORLD_BUILD_HEIGHT, pos.getZ() + 1)) {
            MatrixStack matrices = context.matrixStack();
            Vec3d camera = context.camera().getPos();
            
            matrices.push();
            matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            
            Tessellator tessellator = RenderSystem.renderThreadTesselator();
            BufferBuilder buffer = tessellator.getBuffer();
            VertexConsumerProvider.Immediate consumer = VertexConsumerProvider.immediate(buffer);
            
            BeaconBlockEntityRendererInvoker.renderBeam(matrices, consumer, context.tickDelta(), context.world().getTime(), 0, MAX_OVERWORLD_BUILD_HEIGHT, colorComponents);
            
            consumer.draw();
            matrices.pop();
        }
    }
    
	/**
	 * Draws lines from point to point.<br><br>
	 * 
	 * Tip: To draw lines from the center of a block, offset the X, Y and Z each by 0.5
	 * 
	 * @param context The WorldRenderContext which supplies the matrices and tick delta
	 * @param points The points from which to draw lines between
	 * @param colorComponents An array of R, G and B color components
	 * @param alpha The alpha of the lines
	 * @param lineWidth The width of the lines
	 */
	public static void renderLinesFromPoints(WorldRenderContext context, Vec3d[] points, float[] colorComponents, float alpha, float lineWidth) {
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder buffer = tessellator.getBuffer();
		Matrix4f projectionMatrix = matrices.peek().getPositionMatrix();
		Matrix3f normalMatrix = matrices.peek().getNormalMatrix();
		
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
		
		RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.lineWidth(lineWidth);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
				
		buffer.begin(DrawMode.LINE_STRIP, VertexFormats.LINES);

		for (int i = 0; i < points.length; i++) {
			Vec3d point = points[i];
			Vec3d nextPoint = (i + 1 == points.length) ? points[i - 1] : points[i + 1];
			Vector3f normalVec = new Vector3f((float) nextPoint.getX(), (float) nextPoint.getY(), (float) nextPoint.getZ()).sub((float) point.getX(), (float) point.getY(), (float) point.getZ()).normalize();
			
			buffer
			.vertex(projectionMatrix, (float) point.getX(), (float) point.getY(), (float) point.getZ())
			.color(colorComponents[0], colorComponents[1], colorComponents[2], alpha)
			.normal(normalMatrix, normalVec.x, normalVec.y, normalVec.z)
			.next();
		}
		
		tessellator.draw();
		
		matrices.pop();
		GL11.glDisable(GL11.GL_LINE_SMOOTH);
		RenderSystem.lineWidth(1f);
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.disableDepthTest();
	}

    public static void displayTitleAndPlaySound(int stayTicks, int fadeOutTicks, String titleKey, Formatting formatting) {
        MinecraftClient.getInstance().inGameHud.setTitleTicks(0, stayTicks, fadeOutTicks);
        MinecraftClient.getInstance().inGameHud.setTitle(Text.translatable(titleKey).formatted(formatting));
        playNotificationSound();
    }

    /**
     * Adds the title to {@link TitleContainer} and {@link #playNotificationSound() plays the notification sound} if the title is not in the {@link TitleContainer} already.
     * No checking needs to be done on whether the title is in the {@link TitleContainer} already by the caller.
     *
     * @param title the title
     */
    public static void displayInTitleContainerAndPlaySound(Title title) {
        if (TitleContainer.addTitle(title)) {
            playNotificationSound();
        }
    }

    /**
     * Adds the title to {@link TitleContainer} for a set number of ticks and {@link #playNotificationSound() plays the notification sound} if the title is not in the {@link TitleContainer} already.
     * No checking needs to be done on whether the title is in the {@link TitleContainer} already by the caller.
     *
     * @param title the title
     * @param ticks the number of ticks the title will remain
     */
    public static void displayInTitleContainerAndPlaySound(Title title, int ticks) {
        if (TitleContainer.addTitle(title, ticks)) {
            playNotificationSound();
        }
    }

    private static void playNotificationSound() {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.playSound(SoundEvent.of(new Identifier("entity.experience_orb.pickup")), 100f, 0.1f);
        }
    }
}
