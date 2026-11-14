package com.arena.core.profile;
import com.arena.core.exception.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
@Component
public class LogoImages {
 public static final int MAX_BYTES=2*1024*1024;
 public byte[] normalize(MultipartFile file){
  if(file==null||file.isEmpty())throw new ArenaOpsException(ErrorCode.INVALID_LOGO);
  if(file.getSize()>MAX_BYTES)throw new ArenaOpsException(ErrorCode.LOGO_TOO_LARGE);
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())throw new ArenaOpsException(ErrorCode.INVALID_LOGO);
   ImageReader reader=readers.next();
   try {
    if(!java.util.Set.of("png","jpeg","jpg","webp").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)))throw new ArenaOpsException(ErrorCode.INVALID_LOGO);
    reader.setInput(input,true,true);
    int w=reader.getWidth(0),h=reader.getHeight(0);
    if(w<1||h<1||w>4096||h>4096||(long)w*h>4_194_304)throw new ArenaOpsException(ErrorCode.INVALID_LOGO);
    BufferedImage image=reader.read(0);if(image==null)throw new ArenaOpsException(ErrorCode.INVALID_LOGO);
    // Re-encode pixels only, excluding uploaded metadata and trailing content.
    BufferedImage clean=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<h;y++)clean.setRGB(0,y,w,1,image.getRGB(0,y,w,1,null,0,w),0,w);
    var output=new ByteArrayOutputStream();if(!ImageIO.write(clean,"png",output))throw new ArenaOpsException(ErrorCode.INVALID_LOGO);
    if(output.size()>MAX_BYTES)throw new ArenaOpsException(ErrorCode.LOGO_TOO_LARGE);
    return output.toByteArray();
   }finally{reader.dispose();}
  }catch(IOException|RuntimeException ex){if(ex instanceof ArenaOpsException ae)throw ae;throw new ArenaOpsException(ErrorCode.INVALID_LOGO);}
 }
}
