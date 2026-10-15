package com.transiva.app;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
public class CustomerThemeColorsTest {
 @Test public void bodyTextHasReadableContrastAcrossDarkAndSemanticPanels(){
  int[] backgrounds={CustomerThemeColors.PAGE,CustomerThemeColors.CARD,CustomerThemeColors.SOFT,0xff3c2d19,0xff12382f,0xff40232d};
  Random random=new Random(265);
  for(int bg:backgrounds)for(int i=0;i<1000;i++){
   int original=0xff000000|random.nextInt(0xffffff);
   int mapped=CustomerThemeColors.text(original,bg);
   assertTrue(CustomerThemeColors.contrast(CustomerThemeColors.composite(mapped,bg),bg)>=4.5);
  }
 }
 @Test public void semanticBadgeColorsRemainRecognizable(){
  int bg=CustomerThemeColors.surface(0xfffff4d6,5);int fg=CustomerThemeColors.text(0xff8a5a00,bg);
  assertEquals(0xff3c2d19,bg);assertTrue(CustomerThemeColors.g(fg)>CustomerThemeColors.b(fg));
  assertEquals(0xff12382f,CustomerThemeColors.surface(0xffe7fff2,5));
  assertEquals(0xff40232d,CustomerThemeColors.surface(0xfffff1f2,5));
 }
 @Test public void blueBrandSurfacesSupportWhiteLabels(){
  for(int original:new int[]{0xff0b7cff,0xff25a7ff,0xff006bef,0xff2e9bff})
   assertTrue(CustomerThemeColors.contrast(0xffffffff,CustomerThemeColors.brand(original))>=4.5);
 }
 @Test public void transparentLayersAndExistingDarkSurfacesArePreserved(){
  assertEquals(0x20ffffff,CustomerThemeColors.surface(0x20ffffff,3));
  assertEquals(CustomerThemeColors.CARD,CustomerThemeColors.surface(CustomerThemeColors.CARD,4));
  assertEquals(0xff000000,CustomerThemeColors.composite(0x00ffffff,0xff000000));
 }
 @Test public void lightPaletteKeepsWhiteCardsAndSemanticBrandColors(){
  assertEquals(CustomerThemeColors.LIGHT_PAGE,CustomerThemeColors.lightSurface(0xfff5f8fd,1));
  assertEquals(CustomerThemeColors.LIGHT_SOFT,CustomerThemeColors.lightSurface(0xfff2f8ff,5));
  for(int c:new int[]{0xffffffff,0xff0b7cff,0xffeafbf2,0xfffff4d6,0xfffff1f2,0x20ffffff})assertEquals(c,CustomerThemeColors.lightSurface(c,3));
 }
 @Test public void lightTextStrengthensNeutralAndBlueWithoutChangingSemanticColors(){
  for(int bg:new int[]{CustomerThemeColors.LIGHT_PAGE,CustomerThemeColors.LIGHT_SOFT,0xffffffff}){
   for(int fg:new int[]{0xff64748b,0xff718096,0xff0b7cff,0xff0b3a78})
    assertTrue(CustomerThemeColors.contrast(CustomerThemeColors.lightText(fg,bg),bg)>=4.5);
  }
  assertEquals(0xff07864b,CustomerThemeColors.lightText(0xff07864b,0xffffffff));
  assertEquals(0xffb91c1c,CustomerThemeColors.lightText(0xffb91c1c,0xffffffff));
  assertEquals(0x80718096,CustomerThemeColors.lightText(0x80718096,0xffffffff));
 }
}
