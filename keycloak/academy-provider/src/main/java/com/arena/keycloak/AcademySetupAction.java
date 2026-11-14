package com.arena.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/** Marker action: Keycloak consumes execute-actions tokens containing a one-time factory.
 * It never collects credentials, grants roles, or marks the application membership active.
 */
public final class AcademySetupAction implements RequiredActionProvider, RequiredActionFactory {
 public static final String ID="ARENA_STAFF_SETUP";
 @Override public boolean isOneTimeAction(){return true;}
 @Override public void evaluateTriggers(RequiredActionContext context){}
 @Override public void requiredActionChallenge(RequiredActionContext context){context.success();}
 @Override public void processAction(RequiredActionContext context){context.success();}
 @Override public RequiredActionProvider create(KeycloakSession session){return this;}
 @Override public String getId(){return ID;}
 @Override public String getDisplayText(){return "ArenaOps staff account setup";}
 @Override public void init(Config.Scope config){}
 @Override public void postInit(KeycloakSessionFactory factory){
  String target=System.getenv("ARENA_ACADEMY_REALM");
  if(target==null||target.isBlank())return;
  factory.register(event->{
   if(event instanceof org.keycloak.models.utils.PostMigrationEvent){
    org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction(factory,session->{
     var realm=session.realms().getRealmByName(target);if(realm!=null)register(session,realm);
    });
   }else if(event instanceof org.keycloak.models.RealmModel.RealmPostCreateEvent created && target.equals(created.getCreatedRealm().getName())){
    register(created.getKeycloakSession(),created.getCreatedRealm());
   }
  });
 }
 private void register(KeycloakSession session,org.keycloak.models.RealmModel realm){
  session.getContext().setRealm(realm);
  var profiles=session.getProvider(org.keycloak.userprofile.UserProfileProvider.class);
  var profile=profiles.getConfiguration();
  for(String name:java.util.List.of("arenaAcademyId","arenaStaffId","arenaAcademyName")){
   var attribute=new org.keycloak.representations.userprofile.config.UPAttribute(name);
   attribute.setPermissions(new org.keycloak.representations.userprofile.config.UPAttributePermissions(java.util.Set.of("admin"),java.util.Set.of("admin")));
   attribute.setMultivalued(false);profile.addOrReplaceAttribute(attribute);
  }
  profiles.setConfiguration(profile);
  var action=realm.getRequiredActionProviderByAlias(ID);
  if(action==null){
   action=new org.keycloak.models.RequiredActionProviderModel();
   action.setId(java.util.UUID.randomUUID().toString());action.setAlias(ID);action.setProviderId(ID);
   action.setName("ArenaOps staff account setup");action.setPriority(100);action.setEnabled(true);action.setDefaultAction(false);
   realm.addRequiredActionProvider(action);
  }else{
   if(!ID.equals(action.getProviderId()))throw new IllegalStateException("ArenaOps required action provider mismatch");
   if(!action.isEnabled()||action.isDefaultAction()){
    action.setEnabled(true);action.setDefaultAction(false);realm.updateRequiredActionProvider(action);
   }
  }
  // Keycloak 26 emits the access-token subject through the built-in basic scope.
  var basic=realm.getClientScopesStream().filter(scope->"basic".equals(scope.getName())).findFirst().orElse(null);
  if(basic!=null){
   for(String clientId:java.util.List.of("arena-ui","arena-bff")){
    var client=realm.getClientByClientId(clientId);
    if(client!=null&&!client.getClientScopes(true).containsKey("basic"))client.addClientScope(basic,true);
   }
  }
  String theme=System.getenv("ARENA_ACADEMY_EMAIL_THEME");
  if(theme!=null&&!theme.isBlank())realm.setEmailTheme(theme);
  String user=System.getenv("ARENA_ACADEMY_SMTP_USERNAME"),password=System.getenv("ARENA_ACADEMY_SMTP_PASSWORD"),from=System.getenv("ARENA_ACADEMY_SMTP_FROM");
  if(user!=null&&!user.isBlank()&&password!=null&&!password.isBlank()&&from!=null&&!from.isBlank()){
   var smtp=new java.util.HashMap<String,String>();
   smtp.put("host",environment("ARENA_ACADEMY_SMTP_HOST","smtp-relay.brevo.com"));smtp.put("port",environment("ARENA_ACADEMY_SMTP_PORT","587"));
   smtp.put("from",from);smtp.put("fromDisplayName","ArenaOps");smtp.put("auth","true");smtp.put("starttls","true");smtp.put("user",user);smtp.put("password",password);
   realm.setSmtpConfig(smtp);realm.setResetPasswordAllowed(true);
  }
  System.getLogger(AcademySetupAction.class.getName()).log(System.Logger.Level.INFO,"ArenaOps staff setup action registered and enabled for realm "+realm.getName());
 }
 private static String environment(String name,String fallback){String value=System.getenv(name);return value==null||value.isBlank()?fallback:value;}
 @Override public void close(){}
}
