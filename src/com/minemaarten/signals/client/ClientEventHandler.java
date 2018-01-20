package com.minemaarten.signals.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.opengl.GL11;

import com.minemaarten.signals.block.BlockSignalBase;
import com.minemaarten.signals.client.render.signals.BlockSectionRenderer;
import com.minemaarten.signals.init.ModItems;
import com.minemaarten.signals.tileentity.TileEntitySignalBase;
import com.minemaarten.signals.tileentity.TileEntityStationMarker;

public class ClientEventHandler{

    private final BlockSectionRenderer blockSectionRenderer = new BlockSectionRenderer();

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event){
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();

        EntityPlayer player = Minecraft.getMinecraft().player;
        Item item = player.inventory.getCurrentItem().getItem();
        if(item != ModItems.RAIL_CONFIGURATOR && (!(item instanceof ItemBlock) || !(((ItemBlock)item).getBlock() instanceof BlockSignalBase))) return;

        double playerX = player.prevPosX + (player.posX - player.prevPosX) * event.getPartialTicks();
        double playerY = player.prevPosY + (player.posY - player.prevPosY) * event.getPartialTicks();
        double playerZ = player.prevPosZ + (player.posZ - player.prevPosZ) * event.getPartialTicks();
        GL11.glPushMatrix();
        GL11.glTranslated(-playerX, -playerY, -playerZ);

        GL11.glPointSize(10);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        b.setTranslation(0, 0, 0);

        List<TileEntity> tes = player.world.loadedTileEntityList;
        /*if(player.isSneaking())*/blockSectionRenderer.render(b, tes);

        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        List<TileEntityStationMarker> markers = new ArrayList<>();
        for(TileEntity te : tes) {
            if(player.isSneaking() && te instanceof TileEntitySignalBase) {
                TileEntitySignalBase teSignal = (TileEntitySignalBase)te;

                for(TileEntitySignalBase signal : teSignal.getNextSignals()) {
                    drawBetween(b, signal.getPos(), te.getPos(), 0.5, 1, 1, 1, 1);
                }
            } else if(te instanceof TileEntityStationMarker) {
                markers.add((TileEntityStationMarker)te);
            }
        }
        for(int i = 0; i < markers.size(); i++) {
            TileEntityStationMarker marker1 = markers.get(i);
            for(int j = 0; j < i; j++) {
                TileEntityStationMarker marker2 = markers.get(j);
                if(marker1.getStationName().equals(marker2.getStationName())) {
                    drawBetween(b, marker1.getPos(), marker2.getPos(), 1, 0, 1, 0, 1);
                }
            }
        }
        //for(RailWrapper rail : rails) {
        //              wr.pos(rail.getX() + 0.5, rail.getY() + 0.5, rail.getZ() + 0.5).color(1F, 1F, 1F, 1F).endVertex();
        //}

        t.draw();

        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GL11.glPopMatrix();
    }

    /*private void drawBetween(BufferBuilder buffer, BlockPos p1, BlockPos p2, double offset1, double offset2, int colorHash){
        drawBetween(buffer, p1, p2, offset1, offset2, getR(colorHash), getG(colorHash), getB(colorHash), 1);
    }

    private float getR(int colorHash){
        return colorHash % 256 / 256F;
    }

    private float getG(int colorHash){
        return colorHash % 415 / 415F;
    }

    private float getB(int colorHash){
        return colorHash % 351 / 351F;
    }*/

    private void drawBetween(BufferBuilder buffer, BlockPos p1, BlockPos p2, double offset, float r, float g, float b, float a){
        drawBetween(buffer, p1, p2, offset, offset, r, g, b, a);
    }

    private static void drawBetween(BufferBuilder buffer, BlockPos p1, BlockPos p2, double offset1, double offset2, float r, float g, float b, float alpha){
        buffer.pos(p1.getX() + 0.5, p1.getY() + offset1, p1.getZ() + 0.5).color(r, g, b, alpha).endVertex();
        buffer.pos(p2.getX() + 0.5, p2.getY() + offset2, p2.getZ() + 0.5).color(r, g, b, alpha).endVertex();
    }
}
