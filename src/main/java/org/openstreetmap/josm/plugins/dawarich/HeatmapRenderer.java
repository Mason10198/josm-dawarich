package org.openstreetmap.josm.plugins.dawarich;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapViewState;

/** Source-local density. Both computation buffers and output image are bounded. */
final class HeatmapRenderer {
 static final int CELL=4,MAX_CELLS=262144;
 private HeatmapRenderer(){}
 static BufferedImage render(ReferenceDataset data,MapViewState state,Color color,int width,int height){return render(data,state,color,width,height,()->false);}
 static BufferedImage render(ReferenceDataset data,MapViewState state,Color color,int width,int height,BooleanSupplier cancelled){
  if(width<=0||height<=0)return new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
  int step=Math.max(CELL,(int)Math.ceil(Math.sqrt((double)width*height/MAX_CELLS)));
  int cols=(int)Math.ceil(width/(double)step),rows=(int)Math.ceil(height/(double)step);
  while((long)cols*rows>MAX_CELLS){step++;cols=(int)Math.ceil(width/(double)step);rows=(int)Math.ceil(height/(double)step);}
  float[] bins=new float[cols*rows];int i=0;
  for(ReferencePoint point:data.points()){
   if((i++&255)==0)check(cancelled);var projected=state.getPointFor(new LatLon(point.latitude(),point.longitude()));double px=projected.getInViewX(),py=projected.getInViewY();
   if(Double.isFinite(px)&&Double.isFinite(py)&&px>=0&&py>=0&&px<width&&py<height)bins[((int)py/step)*cols+(int)px/step]++;
  }
  float[] blurred=blur(bins,cols,rows,Math.max(1,(int)Math.ceil(12.0/step)),cancelled);float max=0;for(float density:blurred)max=Math.max(max,density);
  BufferedImage image=new BufferedImage(cols,rows,BufferedImage.TYPE_INT_ARGB);int rgb=color.getRGB()&0xffffff;double denominator=Math.log1p(max);
  for(int y=0;y<rows;y++){check(cancelled);for(int x=0;x<cols;x++){float density=blurred[y*cols+x];if(density>0){int alpha=Math.min(220,Math.max(1,(int)Math.round(220*Math.log1p(density)/denominator)));image.setRGB(x,y,(alpha<<24)|rgb);}}}
  return image;
 }
 private static void check(BooleanSupplier cancelled){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException();}
 private static float[] blur(float[] input,int width,int height,int radius,BooleanSupplier cancelled){
  float[] temporary=new float[input.length],output=new float[input.length],kernel=new float[2*radius+1];float sigma=Math.max(1f,radius/2f),sum=0;
  for(int k=-radius;k<=radius;k++){float value=(float)Math.exp(-(double)k*k/(2*sigma*sigma));kernel[k+radius]=value;sum+=value;}for(int k=0;k<kernel.length;k++)kernel[k]/=sum;
  for(int y=0;y<height;y++){check(cancelled);for(int x=0;x<width;x++)for(int k=-radius;k<=radius;k++){int xx=x+k;if(xx>=0&&xx<width)temporary[y*width+x]+=input[y*width+xx]*kernel[k+radius];}}
  for(int y=0;y<height;y++){check(cancelled);for(int x=0;x<width;x++)for(int k=-radius;k<=radius;k++){int yy=y+k;if(yy>=0&&yy<height)output[y*width+x]+=temporary[yy*width+x]*kernel[k+radius];}}
  return output;
 }
}
