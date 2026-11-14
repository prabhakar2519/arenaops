package com.arena.core.profile;

import com.arena.core.exception.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

// Explicit audit only: -Dtest=BusinessProfileValidationAudit. Known gaps are ordinary failing assertions.
// Deliberately omits the default Surefire *Test suffix until product requirements are implemented.
class BusinessProfileValidationAudit {
 private ProfileUpdate profile(String phone,String email,String postal,String country){return new ProfileUpdate(1L,"QA Club",null,email,phone,null,null,null,postal,country,"KEEP");}
 private boolean valid(ProfileUpdate profile){try(var f=Validation.buildDefaultValidatorFactory()){return f.getValidator().validate(profile).isEmpty();}}
 private byte[] png(int w,int h)throws Exception{var out=new ByteArrayOutputStream();assertTrue(ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB),"png",out));return out.toByteArray();}
 @Test void phoneValidFormats(){for(String p:new String[]{"9876543210","+919876543210","+91 (98765) 43210","+442079460123",null,""})assertTrue(valid(profile(p,null,null,null)),String.valueOf(p));}
 @Test void phoneInvalidFormats(){for(String p:new String[]{"987654321","1234567890","letters","+91987654321","++919876543210"})assertFalse(valid(profile(p,null,null,null)),p);}
 @Test void emailValidFormats(){for(String e:new String[]{"owner@example.test","first.last+club@example.test",null,""})assertTrue(valid(profile(null,e,null,null)),String.valueOf(e));}
 @Test void emailInvalidFormats(){for(String e:new String[]{"owner","owner@","owner example@test.example","owner..name@example.test"})assertFalse(valid(profile(null,e,null,null)),e);}
 @Test void postalIndiaRejectsLetters(){assertFalse(valid(profile(null,null,"ABCDEF","India")),"Missing country-specific PIN validation: alphabetic Indian code was accepted");}
 @Test void postalUnitedStatesRejectsLetters(){assertFalse(valid(profile(null,null,"ABCDE","United States")),"Missing country-specific ZIP validation: alphabetic US code was accepted");}
 @Test void postalValidExamples(){assertTrue(valid(profile(null,null,"560001","India")));assertTrue(valid(profile(null,null,"10001-1234","United States")));}
 @Test void logoExactlyTwoMebibytesAccepted()throws Exception{byte[] input=Arrays.copyOf(png(2,2),LogoImages.MAX_BYTES);assertNotNull(new LogoImages().normalize(new MockMultipartFile("logo",input)));}
 @Test void logoOverTwoMebibytesRejected(){var ex=assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo",new byte[LogoImages.MAX_BYTES+1])));assertEquals(ErrorCode.LOGO_TOO_LARGE,ex.getErrorCode());}
 @Test void logoExactlyFourMillionPixelsAccepted()throws Exception{assertNotNull(new LogoImages().normalize(new MockMultipartFile("logo",png(2000,2000))));}
 @Test void logoAboveFourMillionPixelsRejected()throws Exception{byte[] image=png(2001,2000);assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo",image)),"Documented4MP interpreted as4,000,000:4,002,000 pixels were accepted");}
 @Test void logoCurrentPixelBoundaryAccepted()throws Exception{assertNotNull(new LogoImages().normalize(new MockMultipartFile("logo",png(2048,2048))));}
 @Test void logoAboveCurrentPixelBoundaryRejected()throws Exception{byte[] image=png(2049,2048);assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo",image)));}
 @Test void invalidImageBytesRejected(){assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo","logo.png","image/png","not an image".getBytes())));}
}
