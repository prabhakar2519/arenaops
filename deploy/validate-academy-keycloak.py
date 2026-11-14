#!/usr/bin/env python3
"""Disposable local Keycloak + captured SMTP integration; never sends external email.
Build keycloak/academy-provider first. Requires Docker, Maven, Node and Playwright.
Do not run concurrently with other arena-core Maven commands (uses clean test).
"""
import socketserver,threading,email,json,subprocess,time,urllib.request,urllib.parse,re,os,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];out=root/'artifacts/academy-mvp';out.mkdir(parents=True,exist_ok=True);messages=[]
private=tempfile.TemporaryDirectory(prefix='arena-account-validation-');link_file=Path(private.name)/'link.json'
class SMTP(socketserver.StreamRequestHandler):
 def handle(self):
  self.wfile.write(b'220 validation SMTP\r\n');data=[];reading=False
  for line in self.rfile:
   if reading:
    if line==b'.\r\n':
     messages.append(email.message_from_bytes(b''.join(data)));self.wfile.write(b'250 accepted\r\n');reading=False
    else:data.append(line)
   elif line.upper().startswith(b'EHLO'):self.wfile.write(b'250-validation\r\n250 8BITMIME\r\n')
   elif line.upper().startswith(b'DATA'):reading=True;data=[];self.wfile.write(b'354 send\r\n')
   elif line.upper().startswith(b'QUIT'):self.wfile.write(b'221 bye\r\n');return
   else:self.wfile.write(b'250 OK\r\n')
socketserver.ThreadingTCPServer.allow_reuse_address=True
server=socketserver.ThreadingTCPServer(('127.0.0.1',0),SMTP);threading.Thread(target=server.serve_forever,daemon=True).start()
container='arena-mvp-keycloak-validation-'+str(os.getpid());url='http://127.0.0.1:29191';token=None

def request(path,method='GET',data=None,form=False):
 payload=(urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode()) if data is not None else None
 headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(url+path,data=payload,method=method,headers=headers)
 with urllib.request.urlopen(req,timeout=15) as r:return json.load(r) if r.status not in [201,204] else None
try:
 subprocess.run(['docker','run','-d','--rm','--name',container,'--network','host','-e','ARENA_ACADEMY_REALM=arena-mvp-validation','-e','ARENA_ACADEMY_EMAIL_THEME=arena-academy','-e','ARENA_ACADEMY_SMTP_HOST=127.0.0.1','-e','ARENA_ACADEMY_SMTP_PORT='+str(server.server_address[1]),'-e','ARENA_ACADEMY_SMTP_USERNAME=validation','-e','ARENA_ACADEMY_SMTP_PASSWORD=validation','-e','ARENA_ACADEMY_SMTP_FROM=noreply@example.test','-e','KC_BOOTSTRAP_ADMIN_USERNAME=validation','-e','KC_BOOTSTRAP_ADMIN_PASSWORD=validation','-v',str(root/'keycloak/themes')+':/opt/keycloak/themes:ro','-v',str(root/'keycloak/academy-provider/target/academy-keycloak-provider-1.0.0.jar')+':/opt/keycloak/providers/academy-keycloak-provider.jar:ro','quay.io/keycloak/keycloak:26.5.3','start-dev','--http-host=127.0.0.1','--http-port=29191'],check=True,stdout=subprocess.DEVNULL)
 for i in range(90):
  try:token=request('/realms/master/protocol/openid-connect/token','POST',{'grant_type':'password','client_id':'admin-cli','username':'validation','password':'validation'},True)['access_token'];break
  except Exception:time.sleep(1)
 else:raise RuntimeError('Keycloak startup timed out')
 request('/admin/realms','POST',{'realm':'arena-mvp-validation','enabled':True,'emailTheme':'arena-academy','resetPasswordAllowed':True,'smtpServer':{'host':'127.0.0.1','port':str(server.server_address[1]),'from':'noreply@example.test','fromDisplayName':'ArenaOps','auth':'false','starttls':'false'},'clients':[{'clientId':'arena-ui','enabled':True,'publicClient':True,'standardFlowEnabled':True,'directAccessGrantsEnabled':False,'redirectUris':['http://127.0.0.1:4202/login/callback']}]})
 base='/admin/realms/arena-mvp-validation'
 action=request(base+'/authentication/required-actions/ARENA_STAFF_SETUP')
 assert action['enabled'] and not action['defaultAction'],'New realm action must register automatically'
 action['enabled']=False;action['defaultAction']=True
 request(base+'/authentication/required-actions/ARENA_STAFF_SETUP','PUT',action)
 client=request(base+'/clients?clientId=arena-ui')[0]
 basic=next(scope for scope in request(base+'/client-scopes') if scope['name']=='basic')
 request(base+'/clients/'+client['id']+'/default-client-scopes/'+basic['id'],'DELETE')
 subprocess.run(['docker','restart',container],check=True,stdout=subprocess.DEVNULL)
 token=None
 for i in range(90):
  try:token=request('/realms/master/protocol/openid-connect/token','POST',{'grant_type':'password','client_id':'admin-cli','username':'validation','password':'validation'},True)['access_token'];break
  except Exception:time.sleep(1)
 else:raise RuntimeError('Keycloak restart timed out')
 assert any(scope['name']=='basic' for scope in request(base+'/clients/'+client['id']+'/default-client-scopes')),'Startup must restore the subject scope'
 actions=request(base+'/authentication/required-actions');registered=[a for a in actions if a['alias']=='ARENA_STAFF_SETUP']
 assert len(registered)==1 and registered[0]['enabled'] and not registered[0]['defaultAction'],'Startup must repair configuration without duplicate actions'
 configured=request(base)
 assert configured['emailTheme']=='arena-academy' and configured['smtpServer']['auth']=='true' and configured['smtpServer']['user']=='validation','Startup must configure the email theme and SMTP from environment'
 configured['smtpServer']['auth']='false';configured['smtpServer']['starttls']='false'
 request(base,'PUT',configured)
 profile=request(base+'/users/profile')
 for name in ['arenaAcademyId','arenaStaffId','arenaAcademyName']:
  attribute=next(a for a in profile['attributes'] if a['name']==name)
  assert attribute['permissions']=={'view':['admin'],'edit':['admin']}
  assert not attribute.get('multivalued',False)
 env={**os.environ,'ARENA_TEST_KEYCLOAK_URL':url}
 result=subprocess.run(['mvn','--batch-mode','-f',str(root/'arena-core/pom.xml'),'clean','test','-Dtest=AcademyKeycloakIntegrationTest'],env=env,stdout=(out/'keycloak-java-test.log').open('w'),stderr=subprocess.STDOUT)
 if result.returncode:raise RuntimeError('Keycloak Java integration failed')
 assert messages,'Account setup email was not delivered';body='\n'.join((part.get_payload(decode=True) or b'').decode(errors='replace') for part in messages[0].walk() if part.get_content_type() in ['text/plain','text/html'])
 assert 'ArenaOps' in body and 'coach.validation' in body and 'Validation Academy' in body
 link=re.search(r'http://127\.0\.0\.1:29191/[^\s"<>]+',body).group(0).replace('&amp;','&')
 user_id=request(base+'/users?username=coach.validation&exact=true')[0]['id']
 request(base+'/users/'+user_id+'/execute-actions-email?lifespan=1','PUT',['VERIFY_EMAIL','UPDATE_PASSWORD','ARENA_STAFF_SETUP'])
 assert len(messages)==2,'Expiry-test email was not captured'
 expired_body='\n'.join((part.get_payload(decode=True) or b'').decode(errors='replace') for part in messages[-1].walk() if part.get_content_type() in ['text/plain','text/html'])
 expired_link=re.search(r'http://127\.0\.0\.1:29191/[^\s"<>]+',expired_body).group(0).replace('&amp;','&')
 time.sleep(2)
 link_file.write_text(json.dumps({'link':link,'expiredLink':expired_link,'url':url}));os.chmod(str(link_file),0o600)
 subprocess.run(['node',str(root/'deploy/validate-academy-keycloak-browser.cjs')],env={**os.environ,'ARENA_ACCOUNT_LINK_FILE':str(link_file)},check=True,stdout=(out/'keycloak-browser-test.log').open('w'),stderr=subprocess.STDOUT)
 matches=request(base+'/users?username=coach.validation&exact=true');user=request(base+'/users/'+matches[0]['id']);assert user['emailVerified'] and not user.get('requiredActions')
 assert request(base+'/users/'+user['id']+'/credentials'),'Password was not created'
 (out/'keycloak-results.json').write_text(json.dumps({'automaticRegistrationOnRealmCreate':True,'automaticRegistrationOnRestart':True,'noDuplicateActions':True,'automaticSmtpConfiguration':True,'automaticAdminOnlyAcademyAttributes':True,'automaticSubjectScopeRepair':True,'emailBranding':True,'verified':True,'requiredActionsCompleted':True,'passwordStoredByKeycloak':True,'singleUseLinkVerified':True,'expiredLinkRejected':True},indent=2))
 print('Isolated Keycloak setup email, identity recovery, email verification, password creation and single-use action link checks passed.')
finally:
 subprocess.run(['docker','rm','-f',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);server.shutdown();link_file.unlink(missing_ok=True);private.cleanup()
