#!/usr/bin/env python3
"""Explicit local/review deployment configuration; never invoked automatically by the app.
Mount the built academy provider into /opt/keycloak/providers and the themes into /opt/keycloak/themes; restart Keycloak before running.
Secrets are read only from environment and never printed.
"""
import argparse,json,os,urllib.request,urllib.parse
from keycloak_subject_scope import ensure_subject_scope
from keycloak_setup_redirect import ensure_setup_redirect
p=argparse.ArgumentParser();p.add_argument('--url',required=True);p.add_argument('--realm',required=True);p.add_argument('--public-url',required=True);args=p.parse_args()
def request(path,method='GET',data=None,token=None):
 body=None if data is None else json.dumps(data).encode()
 req=urllib.request.Request(args.url.rstrip('/')+path,data=body,method=method,headers={'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if token else {})})
 with urllib.request.urlopen(req,timeout=15) as r:return json.loads(r.read()) if r.status!=204 else None
client=os.environ['KC_ADMIN_CLIENT_ID'];secret=os.environ['KC_ADMIN_CLIENT_SECRET']
body=urllib.parse.urlencode({'grant_type':'client_credentials','client_id':client,'client_secret':secret}).encode()
req=urllib.request.Request(args.url.rstrip('/')+'/realms/'+args.realm+'/protocol/openid-connect/token',data=body)
try:
 with urllib.request.urlopen(req,timeout=15) as r:token=json.load(r)['access_token']
 base='/admin/realms/'+args.realm
 # Existing realms need this too; imports do not update their client scopes.
 ensure_subject_scope(lambda path, method='GET', data=None: request(path,method,data,token),args.realm,apply=True)
 providers=request(base+'/authentication/unregistered-required-actions',token=token)
 actions=request(base+'/authentication/required-actions',token=token)
 if not any(a.get('alias')=='ARENA_STAFF_SETUP' for a in actions):
  if not any(a.get('providerId')=='ARENA_STAFF_SETUP' for a in providers):
   raise RuntimeError('Install and restart Keycloak with the academy provider first')
  request(base+'/authentication/register-required-action','POST',{'name':'ArenaOps staff account setup','providerId':'ARENA_STAFF_SETUP'},token)
 action=request(base+'/authentication/required-actions/ARENA_STAFF_SETUP',token=token)
 action['enabled']=True;action['defaultAction']=False
 request(base+'/authentication/required-actions/ARENA_STAFF_SETUP','PUT',action,token)
 ensure_setup_redirect(lambda path, method='GET', data=None: request(path,method,data,token),args.realm,args.public_url)
 realm=request(base,token=token)
 realm['emailTheme']='arena-academy';realm['resetPasswordAllowed']=True
 realm['attributes']={**realm.get('attributes',{}),'frontendUrl':args.public_url.rstrip('/')}
 realm['smtpServer']={'host':os.environ.get('BREVO_SMTP_HOST','smtp-relay.brevo.com'),'port':os.environ.get('BREVO_SMTP_PORT','587'),'from':os.environ['ARENAOPS_MAIL_FROM'],'fromDisplayName':'ArenaOps','auth':'true','starttls':'true','user':os.environ['BREVO_SMTP_USERNAME'],'password':os.environ['BREVO_SMTP_PASSWORD']}
 request(base,'PUT',realm,token)
 profile=request(base+'/users/profile',token=token)
 existing={a['name']:a for a in profile.get('attributes',[])}
 for name in ['arenaAcademyId','arenaStaffId','arenaAcademyName']:
  existing[name]={'name':name,'displayName':name,'permissions':{'view':['admin'],'edit':['admin']},'multivalued':False}
 profile['attributes']=list(existing.values());request(base+'/users/profile','PUT',profile,token)
 print('Configured ArenaOps email theme, Brevo SMTP, password recovery, and admin-only academy identity attributes.')
except Exception as exc:
 print('Configuration failed: '+type(exc).__name__);raise SystemExit(1)
