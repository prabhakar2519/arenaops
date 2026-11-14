package com.arena.core.profile;
import com.arena.core.entity.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import com.arena.core.training.*;
import com.arena.core.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.core.MethodParameter;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class BusinessProfileTest {
 AcademyAuthorization authorization=mock(AcademyAuthorization.class);CustomerRepository customers=mock(CustomerRepository.class);BusinessLogoRepository logos=mock(BusinessLogoRepository.class);AuditService audit=mock(AuditService.class);
 AppUserRepository users=mock(AppUserRepository.class);AcademyStaffRepository memberships=mock(AcademyStaffRepository.class);
 BusinessProfileService service=new BusinessProfileService(authorization,customers,logos,new LogoImages(),audit,users,new AdminAuthorizationService(),mock(CustomerAccessService.class),memberships);
 Jwt jwt=Jwt.withTokenValue("test").header("alg","none").subject("subject").build();CustomerEntity customer;AcademyAuthorization.Actor owner;
 @BeforeEach void setup(){customer=CustomerEntity.builder().id(10L).version(4L).customerName("Legal Academy").organizationName("Legal Academy").ownerEmail("trusted@example.test").logoRevision("old-revision").build();owner=new AcademyAuthorization.Actor(AppUserEntity.builder().id(1L).customerId(10L).build(),null,Set.of("OWNER"));when(authorization.manage(jwt)).thenReturn(owner);when(authorization.operations(jwt)).thenReturn(owner);when(customers.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));when(customers.saveAndFlush(any())).thenAnswer(i->{customer.setVersion(5L);return i.getArgument(0);});}
 ProfileUpdate update(Long version,String action){return new ProfileUpdate(version," New public name ","SPORTS_CLUB","public@example.test","+91 98765 43210",null,"City",null,null,null,action);}
 @Test void brandingAndLogoExposeOnlyAuthenticatedCurrentTenant(){
  when(users.findByUsernameIgnoreCase("subject")).thenReturn(Optional.of(AppUserEntity.builder().id(2L).customerId(10L).role("STAFF").keycloakSubject("subject").isActive(true).build()));
  customer.setOrganizationType("CLUB");customer.setSports("SNOOKER");
  var stored=new BusinessLogo();stored.setCustomerId(10L);stored.setImage(new byte[]{1,2});when(logos.findById(10L)).thenReturn(Optional.of(stored));
  var branding=service.branding(jwt);assertEquals("Legal Academy",branding.displayName());assertFalse(branding.permissions().viewProfile());assertFalse(branding.permissions().editProfile());assertArrayEquals(new byte[]{1,2},service.image(jwt));verify(logos).findById(10L);verify(logos,never()).findById(99L);
 }
 @Test void ownerSaveOnlyChangesPublicFieldsAndAuditsNames(){var result=service.save(jwt,update(4L,"KEEP"),null);assertEquals("New public name",result.displayName());assertEquals(5L,result.version());assertEquals("Legal Academy",customer.getCustomerName());assertEquals("trusted@example.test",customer.getOwnerEmail());assertEquals("old-revision",customer.getLogoRevision());var metadata=org.mockito.ArgumentCaptor.forClass(String.class);verify(audit).record(eq("BUSINESS_PROFILE_UPDATED"),eq(10L),any(),eq("1"),isNull(),isNull(),isNull(),metadata.capture());assertTrue(metadata.getValue().contains("displayName"));assertFalse(metadata.getValue().contains("public@example.test"));}
 @Test void conflictDoesNotAlterProfileOrOldLogo(){assertEquals(ErrorCode.CONFLICT,assertThrows(ArenaOpsException.class,()->service.save(jwt,update(3L,"REMOVE"),null)).getErrorCode());assertNull(customer.getBusinessDisplayName());assertEquals("old-revision",customer.getLogoRevision());verifyNoInteractions(logos,audit);}
 @Test void invalidLogoLeavesOldProfileAndLogoIntact(){assertThrows(ArenaOpsException.class,()->service.save(jwt,update(4L,"REPLACE"),new MockMultipartFile("logo","../../evil.png","image/png","not an image".getBytes())));assertNull(customer.getBusinessDisplayName());assertEquals("old-revision",customer.getLogoRevision());verifyNoInteractions(logos,audit);}
 @Test void replacementUsesCurrentTenantAndControlledId()throws Exception{var logo=png();service.save(jwt,update(4L,"REPLACE"),logo);var captured=org.mockito.ArgumentCaptor.forClass(BusinessLogo.class);verify(logos).save(captured.capture());assertEquals(10L,captured.getValue().getCustomerId());assertNotEquals("old-revision",customer.getLogoRevision());assertNotNull(ImageIO.read(new ByteArrayInputStream(captured.getValue().getImage())));}
 @Test void removingLogoIsPartOfVersionedSave(){service.save(jwt,update(4L,"REMOVE"),null);assertNull(customer.getLogoRevision());verify(logos).deleteById(10L);}
 @Test void storageFailureDoesNotReturnSuccessOrAudit()throws Exception{doThrow(new org.springframework.dao.DataAccessResourceFailureException("test")).when(logos).save(any());assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->service.save(jwt,update(4L,"REPLACE"),png()));verify(customers,never()).saveAndFlush(any());verifyNoInteractions(audit);}
 @Test void standardManagerCannotReadProfileAndDowngradeBlocksOpenedForm(){var membership=new AcademyStaff();var standard=new AcademyAuthorization.Actor(owner.user(),membership,Set.of("MANAGER"));when(authorization.operations(jwt)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));assertThrows(ArenaOpsException.class,()->service.get(jwt));assertThrows(ArenaOpsException.class,()->service.save(jwt,update(4L,"REMOVE"),null));verifyNoInteractions(logos,audit);}
 @Test void fullManagerCanEditPublicProfile(){var membership=new AcademyStaff();membership.setManagerAccess("FULL_OPERATIONS");var full=new AcademyAuthorization.Actor(owner.user(),membership,Set.of("MANAGER"));when(authorization.operations(jwt)).thenReturn(full);assertTrue(service.save(jwt,update(4L,"KEEP"),null).permissions().editProfile());assertEquals("trusted@example.test",customer.getOwnerEmail());}
 @Test void staffReadDenied(){when(authorization.operations(jwt)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));assertThrows(ArenaOpsException.class,()->service.get(jwt));}
 @Test void academyBrandingRemainsAvailableWithoutAdministrativeProfileAccess(){
  var user=AppUserEntity.builder().id(2L).customerId(10L).role("STAFF").keycloakSubject("subject").isActive(true).build();
  when(users.findByUsernameIgnoreCase("subject")).thenReturn(Optional.of(user));
  var membership=new AcademyStaff();when(memberships.findByAppUserId(2L)).thenReturn(Optional.of(membership));
  for(String role:List.of("STAFF","INSTRUCTOR","MANAGER")){
   when(authorization.actor(jwt)).thenReturn(new AcademyAuthorization.Actor(user,membership,Set.of(role)));
   var branding=service.branding(jwt);assertEquals("Legal Academy",branding.displayName());assertNotNull(branding.logoUrl());assertFalse(branding.permissions().viewProfile());assertFalse(branding.permissions().manageOrdinaryStaff());
  }
  membership.setManagerAccess("FULL_OPERATIONS");var full=new AcademyAuthorization.Actor(user,membership,Set.of("MANAGER"));when(authorization.actor(jwt)).thenReturn(full);when(authorization.operations(jwt)).thenReturn(full);
  assertTrue(service.branding(jwt).permissions().viewProfile());assertTrue(service.get(jwt).permissions().editProfile());
 }
 @Test void protectedUnknownFieldsRejectedByMapperEvenWhenGloballyIgnored()throws Exception{var mapper=new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);String body="{\"version\":4,\"displayName\":\"Name\",\"logoAction\":\"KEEP\",\"ownerEmail\":\"attacker@example.test\"}";assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,()->mapper.readValue(body,ProfileUpdate.class));}
 @Test void trimmedFieldsAndEmailValidation(){try(var factory=Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();assertFalse(validator.validate(new ProfileUpdate(4L,"  x  ",null,"bad",null,null,null,null,null,null,"KEEP")).isEmpty());assertTrue(validator.validate(update(4L,"KEEP")).isEmpty());}}
 @Test void mimeHeadersCannotMakeUnsupportedContentValid(){assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo","fake.png","image/png","<svg/>".getBytes())));}
 MockMvc mvc(){return MockMvcBuilders.standaloneSetup(new BusinessProfileController(service)).setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.getParameterType()==Jwt.class;}public Object resolveArgument(MethodParameter p,ModelAndViewContainer c,NativeWebRequest r,WebDataBinderFactory f){return jwt;}}).build();}
 @Test void multipartValidationReturnsFieldErrorsAndRejectsProtectedFields()throws Exception{
  var bad=new MockMultipartFile("profile","profile.json","application/json","{\"version\":4,\"displayName\":\" x \",\"logoAction\":\"KEEP\"}".getBytes());
  mvc().perform(multipart("/api/business-profile").file(bad).with(r->{r.setMethod("PUT");return r;})).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.displayName").exists());
  var protectedField=new MockMultipartFile("profile","profile.json","application/json","{\"version\":4,\"displayName\":\"Valid name\",\"logoAction\":\"KEEP\",\"customerId\":99}".getBytes());
  mvc().perform(multipart("/api/business-profile").file(protectedField).with(r->{r.setMethod("PUT");return r;})).andExpect(status().isBadRequest());
  verify(customers,never()).saveAndFlush(any());
 }
 @Test void jpegAndWebpAreDecodedAndNormalizedToPng()throws Exception{
  var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpeg",out);
  var bytes=new LogoImages().normalize(new MockMultipartFile("logo",out.toByteArray()));assertEquals((byte)0x89,bytes[0]);
  try(var stream=getClass().getResourceAsStream("/profile/valid-logo.webp")){assertNotNull(stream);bytes=new LogoImages().normalize(new MockMultipartFile("logo",stream.readAllBytes()));assertEquals((byte)0x89,bytes[0]);assertNotNull(ImageIO.read(new ByteArrayInputStream(bytes)));}
 }
 @Test void oversizedAndDecompressionBombImagesRejected()throws Exception{assertEquals(ErrorCode.LOGO_TOO_LARGE,assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo",new byte[LogoImages.MAX_BYTES+1]))).getErrorCode());var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4097,1,BufferedImage.TYPE_INT_RGB),"png",bytes);assertEquals(ErrorCode.INVALID_LOGO,assertThrows(ArenaOpsException.class,()->new LogoImages().normalize(new MockMultipartFile("logo",bytes.toByteArray()))).getErrorCode());}
 MockMultipartFile png()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("logo","../../logo.png","application/octet-stream",out.toByteArray());}
}
