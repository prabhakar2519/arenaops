package com.arena.core.publicclub;
import static com.arena.core.publicclub.PublicClubContracts.*;
import com.arena.core.training.*;import com.arena.core.repository.CustomerRepository;import com.arena.core.entity.CustomerEntity;import com.arena.core.service.CustomerAccessService;import com.arena.core.profile.BusinessLogoRepository;import com.arena.core.exception.*;
import java.time.*;import java.util.*;import java.util.stream.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.security.oauth2.jwt.Jwt;
@Service @Transactional @RequiredArgsConstructor
public class PublicClubService {
 private final ClubPublicSiteRepository sites;private final ClubPublicSlugRepository slugs;private final CustomerRepository customers;private final CustomerAccessService access;private final AcademyAuthorization auth;private final AcademySportRepository sports;private final AcademyPlayAreaRepository areas;private final BusinessLogoRepository logos;private final PublicClubHost hosts;
 @org.springframework.beans.factory.annotation.Value("${ARENA_ENV:unknown}") private String environment;
 @org.springframework.beans.factory.annotation.Value("${PUBLIC_CLUB_PREVIEW_ENABLED:false}") private boolean previewEnabled;
 @org.springframework.beans.factory.annotation.Value("${PUBLIC_CLUB_HOSTING_READY:false}") private boolean hostingReady;
 @org.springframework.beans.factory.annotation.Value("${arena.public.reservation-requests-enabled:false}") private boolean reservationRequestsEnabled;
 @org.springframework.beans.factory.annotation.Value("${arena.public.website-management-enabled:false}") private boolean websiteManagementEnabled;
 @org.springframework.beans.factory.annotation.Value("${arena.public.application-url:}") private String publicApplicationUrl;
 private void requireWebsiteManagement(){if(!websiteManagementEnabled)throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);}
 private boolean pathPreview(){return previewEnabled&&hosts.signingConfigured()&&Set.of("dev","sit").contains(environment);}
 private String previewUrl(ClubPublicSite s){if(!s.isPublished())return "/academy/public-site/preview";if(pathPreview())return "/public/"+s.getSlug();return "prod".equals(environment)&&hostingReady?hosts.origin(s.getSlug()):"/academy/public-site/preview";}
 private static final Set<String> RESERVED=Set.of("www","api","admin","sit","dev","auth","staging","prod","production","test","mail","smtp","imap","pop","ftp","ssh","status","support","docs","cdn","assets","static","app","arenaops","localhost","autodiscover","webmail","ns1","ns2","vpn","internal","monitoring","metrics","billing","login","register");
 public static String normalizeSlug(String raw){String s=raw==null?"":raw.strip().toLowerCase(Locale.ROOT);if(!s.matches("[a-z][a-z0-9-]{1,38}[a-z0-9]")||RESERVED.contains(s)||s.contains("--"))throw new ArenaOpsException(ErrorCode.PUBLIC_SLUG_INVALID);return s;}
 public ClubPublicSite resolve(String slug){var a=slugs.findById(normalizeSlug(slug)).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));var site=sites.findById(a.getAcademyId()).filter(ClubPublicSite::isPublished).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));usable(site);return site;}
 public CustomerEntity usable(ClubPublicSite site){return customers.findById(site.getAcademyId()).filter(access::isCustomerUsable).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));}
 public SiteSettings settings(Jwt jwt){requireWebsiteManagement();var a=auth.owner(jwt);return sites.findById(a.academyId()).map(this::view).orElse(new SiteSettings(-1L,"","",false,null,false,null,null,List.of(),null,pathPreview(),hostingReady));}
 public SiteSettings save(Jwt jwt,Settings r){requireWebsiteManagement();var a=auth.owner(jwt);String slug=normalizeSlug(r.slug());var existing=sites.locked(a.academyId());var site=existing.orElseGet(()->{var s=new ClubPublicSite();s.setAcademyId(a.academyId());return s;});if(existing.isPresent()&&!Objects.equals(site.getVersion(),r.version())||existing.isEmpty()&&r.version()!=-1)throw new ArenaOpsException(ErrorCode.CONFLICT);
  boolean change=existing.isPresent()&&!slug.equals(site.getSlug());if(change&&(!r.acknowledgeSlugChange()||site.getSlugChangedAt()!=null&&site.getSlugChangedAt().plus(Duration.ofDays(7)).isAfter(Instant.now())))throw new ArenaOpsException(ErrorCode.PUBLIC_SLUG_CHANGE_NOT_ALLOWED);
  var alias=slugs.findById(slug);if(alias.isPresent()&&!Objects.equals(alias.get().getAcademyId(),a.academyId()))throw new ArenaOpsException(ErrorCode.PUBLIC_SLUG_UNAVAILABLE);
  if(r.bookingsEnabled()&&(r.opensAt()==null||r.closesAt()==null||r.opensAt().equals(r.closesAt())||r.durations()==null||r.durations().isEmpty()||r.durations().stream().anyMatch(d->d==null||d<15||d>240||d%15!=0)))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  site.setSlug(slug);site.setPublished(r.published());site.setBookingsEnabled(r.bookingsEnabled());site.setOpensAt(r.opensAt());site.setClosesAt(r.closesAt());site.setDurationMinutes(r.durations()==null?"":r.durations().stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")));if(change)site.setSlugChangedAt(Instant.now());sites.saveAndFlush(site);
  if(alias.isEmpty()){var key=new ClubPublicSlug();key.setSlug(slug);key.setAcademyId(a.academyId());slugs.saveAndFlush(key);}return view(site);
 }
 /** QR check-in is independent of website publication and its management feature flag. */
 public CustomerEntity checkinCustomer(Long academy){return customers.findById(academy).filter(access::isCustomerUsable).orElseThrow(()->new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE));}
 public Long checkinAcademy(String slug){var alias=slugs.findById(normalizeSlug(slug)).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));checkinCustomer(alias.getAcademyId());return alias.getAcademyId();}
 public String checkinOrigin(Long academy){checkinCustomer(academy);return checkinApplicationOrigin(publicApplicationUrl,environment);}
 public static String checkinApplicationOrigin(String configured,String environment){
  String value=configured==null?"":configured.strip();
  if(value.isEmpty()){if("sit".equals(environment))value="https://sit.arenaops.in";else if("prod".equals(environment))value="https://arenaops.in";else if("dev".equals(environment))value="http://localhost:4200";else throw new IllegalStateException("Configure ARENA_ENV and public application URL for QR check-in");}
  try{var uri=java.net.URI.create(value);String host=uri.getHost();boolean dev="dev".equals(environment);
   if(host==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!(uri.getPath()==null||uri.getPath().isEmpty()||uri.getPath().equals("/"))||!("https".equals(uri.getScheme())||dev&&"http".equals(uri.getScheme())))throw new IllegalArgumentException();
   if(!dev&&(host.equalsIgnoreCase("localhost")||host.endsWith(".localhost")||!host.contains(".")||host.matches("[0-9.]+")||host.contains(":")))throw new IllegalArgumentException();
   return value.replaceAll("/+$","");
  }catch(IllegalArgumentException e){throw new IllegalStateException("Configure a valid public application origin for QR check-in");}
 }
 public String origin(Long academy){return hosts.origin(sites.findById(academy).filter(ClubPublicSite::isPublished).orElseThrow(()->new ArenaOpsException(ErrorCode.PUBLIC_SITE_NOT_PUBLISHED)).getSlug());}
 public List<Integer> durations(ClubPublicSite s){return s.getDurationMinutes()==null||s.getDurationMinutes().isBlank()?List.of():Arrays.stream(s.getDurationMinutes().split(",")).map(Integer::valueOf).toList();}
 private SiteSettings view(ClubPublicSite s){return new SiteSettings(s.getVersion(),s.getSlug(),hosts.origin(s.getSlug()),s.isPublished(),s.getSlugChangedAt()==null?null:s.getSlugChangedAt().plus(Duration.ofDays(7)),s.isBookingsEnabled(),s.getOpensAt(),s.getClosesAt(),durations(s),previewUrl(s),pathPreview(),hostingReady);}
 public Site site(String slug){return projection(resolve(slug),false);}
 public Site ownerPreview(Jwt jwt){requireWebsiteManagement();var a=auth.owner(jwt);return projection(sites.findById(a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)),true);}
 public byte[] ownerPreviewLogo(Jwt jwt){requireWebsiteManagement();var a=auth.owner(jwt);usable(sites.findById(a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)));return logos.findById(a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)).getImage();}
 private Site projection(ClubPublicSite s,boolean preview){var c=usable(s);var active=sports.findByAcademyIdOrderByName(c.getId()).stream().filter(AcademySport::isActive).toList();var list=areas.findByAcademyIdOrderByName(c.getId()).stream().filter(f->eligible(f,active)).map(f->new Facility(f.getId(),f.getDisplayName()==null?f.getCode():f.getDisplayName(),active.stream().filter(sp->sp.getId().equals(f.getSportId())).findFirst().orElseThrow().getName())).toList();String name=Optional.ofNullable(c.getBusinessDisplayName()).filter(v->!v.isBlank()).orElse(Optional.ofNullable(c.getOrganizationName()).filter(v->!v.isBlank()).orElse(c.getCustomerName()));return new Site(name,c.getLogoRevision()==null?null:preview?"/api/academy/public-site/preview/logo":"/api/public/club/logo",null,active.stream().map(sp->new Sport(sp.getName(),sp.getCatalogueCode())).toList(),c.getPublicContactEmail(),c.getPublicPhone(),Stream.of(c.getAddressLine(),c.getCity(),c.getRegion(),c.getPostalCode(),c.getCountry()).filter(v->v!=null&&!v.isBlank()).collect(Collectors.joining(", ")),c.getTrainingTimezone(),list,reservationRequestsEnabled&&s.isBookingsEnabled(),s.getOpensAt(),s.getClosesAt(),durations(s));}
 public byte[] logo(String slug){var s=resolve(slug);return logos.findById(s.getAcademyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)).getImage();}
 public boolean eligible(AcademyPlayArea f,List<AcademySport> ss){return f.isActive()&&f.getFacilityType()!=null&&f.getHourlyRate()!=null&&f.getHourlyRate().signum()>0&&ss.stream().anyMatch(s->s.getId().equals(f.getSportId())&&s.isActive()&&s.isSessions()&&Set.of("SNOOKER","BADMINTON").contains(s.getCatalogueCode()));}
 public void bookingWindow(ClubPublicSite s,LocalDateTime start,int duration){
  var c=usable(s);
  if(!s.isBookingsEnabled())throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_DISABLED);
  if(!durations(s).contains(duration))throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_DURATION);
  if(start==null)throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_INVALID_TIME);
  var zone=ZoneId.of(c.getTrainingTimezone());var offsets=zone.getRules().getValidOffsets(start);
  if(offsets.size()!=1)throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_INVALID_TIME);
  var now=LocalDateTime.now(zone);
  if(start.isBefore(now.plusMinutes(15))||start.isAfter(now.plusDays(30)))throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_WINDOW);
  if(start.getSecond()!=0||start.getNano()!=0||start.getMinute()%15!=0)throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_TIME_INCREMENT);
  var day=start.toLocalDate();if(s.getOpensAt().isAfter(s.getClosesAt())&&start.toLocalTime().isBefore(s.getClosesAt()))day=day.minusDays(1);
  var open=day.atTime(s.getOpensAt());var close=day.atTime(s.getClosesAt());if(!close.isAfter(open))close=close.plusDays(1);
  if(start.isBefore(open)||start.plusMinutes(duration).isAfter(close))throw new ArenaOpsException(ErrorCode.PUBLIC_BOOKING_OUTSIDE_HOURS);
 }
}
