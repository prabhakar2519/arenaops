import {UiFeedbackService} from '../ui/ui-feedback.service';
import {of} from 'rxjs';
import {MVP_FEATURES} from '../features';
import { BusinessLogoComponent } from '../ui/business-logo.component';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BusinessProfileService, BusinessProfile } from './business-profile.service';
import { AuthService } from '../services/auth';
import { apiError } from '../api-error';
const fields=['displayName','businessType','publicContactEmail','publicPhone','addressLine','city','region','postalCode','country'] as const;
type Field=typeof fields[number];
@Component({selector:'ao-business-profile',standalone:true,imports:[CommonModule,FormsModule,BusinessLogoComponent],templateUrl:'./business-profile.component.html',styleUrl:'./business-profile.component.scss'})
export class BusinessProfileComponent implements OnInit,OnDestroy {
 readonly features=MVP_FEATURES;
 feedback=inject(UiFeedbackService);
 api=inject(BusinessProfileService);auth=inject(AuthService);
 profile:BusinessProfile|null=null;draft:Record<Field,string>={displayName:'',businessType:'',publicContactEmail:'',publicPhone:'',addressLine:'',city:'',region:'',postalCode:'',country:''};
 loading=true;editing=false;busy=false;error='';success='';conflict=false;fieldErrors:Record<string,string>={};file:File|null=null;preview:string|null=null;removeLogo=false;
 nonEmpty=(value:string|null)=>!!value;
 types=[{value:'SPORTS_ACADEMY',label:'Sports Academy'},{value:'SPORTS_CLUB',label:'Sports Club'},{value:'SPORTS_RECREATION_CENTRE',label:'Sports / Recreation Centre'},{value:'GYM_FITNESS_CENTRE',label:'Gym / Fitness Centre'},{value:'OTHER',label:'Other'}];
 ngOnInit(){this.load();}
 load(){this.loading=true;this.api.get().subscribe({next:p=>{this.profile=p;this.auth.setBusinessBranding(p);this.loading=false;this.cancel();},error:e=>{this.error=apiError(e).description;this.loading=false;}});}
 edit(){this.error='';this.auth.fetchBusinessBranding().subscribe({next:b=>{if(!b.permissions.editProfile){this.error='Your current permissions do not allow editing this profile.';return;}this.resetDraft();this.editing=true;},error:e=>this.error=apiError(e).description});}
 resetDraft(){for(const key of fields)this.draft[key]=this.profile?.[key]||'';this.clearPreview();this.file=null;this.removeLogo=false;this.fieldErrors={};this.conflict=false;}
 cancel(){this.resetDraft();this.editing=false;}
 get dirty():boolean{return this.editing&&(!!this.file||this.removeLogo&&!!this.profile?.logoUrl||fields.some(k=>this.draft[k]!== (this.profile?.[k]||'')));}
 canLeave(){if(this.busy)return false;return !this.dirty||this.feedback.confirm({title:'Discard profile changes?',message:'Discard unsaved business profile changes?',confirmText:'Discard Changes',variant:'warning'});}
 @HostListener('window:beforeunload',['$event']) beforeUnload(event:BeforeUnloadEvent){if(this.dirty){event.preventDefault();event.returnValue='';}}
 get logoUrl(){return this.preview||(!this.removeLogo?this.profile?.logoUrl:null);}
 get initial(){return (this.editing?this.draft.displayName:this.profile?.displayName||'B').slice(0,1).toUpperCase();}
 get typeLabel(){return this.types.find(t=>t.value===this.profile?.businessType)?.label||'Not provided';}
 chooseLogo(event:Event){const input=event.target as HTMLInputElement;const f=input.files?.[0];input.value='';if(!f)return;
  if(f.size>2*1024*1024){this.fieldErrors['logo']='Choose an image of at most 2 MB.';return;}
  if(!['image/png','image/jpeg','image/webp'].includes(f.type)){this.fieldErrors['logo']='Choose PNG, JPEG or WebP.';return;}
  this.clearPreview();this.file=f;this.preview=URL.createObjectURL(f);this.removeLogo=false;delete this.fieldErrors['logo'];
 }
 remove(){this.clearPreview();this.file=null;this.removeLogo=!!this.profile?.logoUrl;delete this.fieldErrors['logo'];}
 private clearPreview(){if(this.preview)URL.revokeObjectURL(this.preview);this.preview=null;}
 validate(){this.fieldErrors={};const name=this.draft.displayName.trim();if(name.length<2||name.length>120)this.fieldErrors['displayName']='Enter 2–120 characters.';
  const email=this.draft.publicContactEmail.trim();if(email&&(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||email.length>255))this.fieldErrors['publicContactEmail']='Enter a valid email address.';
  const rawPhone=this.draft.publicPhone.trim(),phone=rawPhone.replace(/[\s().-]/g,'');const validPhone=/^[6-9][0-9]{9}$/.test(phone)||(/^\+[1-9][0-9]{7,14}$/.test(phone)&&(!phone.startsWith('+91')||/^\+91[6-9][0-9]{9}$/.test(phone)));if(rawPhone&&(rawPhone.length>50||!validPhone))this.fieldErrors['publicPhone']='Enter a valid 10-digit mobile number starting with 6–9. +91 is optional.';
  for(const [key,max] of Object.entries({addressLine:255,city:100,region:100,postalCode:30,country:100}))if(this.draft[key as Field].trim().length>max)this.fieldErrors[key]=`Use at most ${max} characters.`;
  return !Object.keys(this.fieldErrors).length;
 }
 save(){if(this.busy||!this.profile||!this.validate())return;this.busy=true;this.error='';this.success='';
  const request:Record<string,unknown>={version:this.profile.version,logoAction:this.file?'REPLACE':this.removeLogo?'REMOVE':'KEEP'};for(const key of fields)request[key]=this.draft[key].trim()||null;
  this.api.save(request,this.file).subscribe({next:p=>{this.profile=p;this.auth.setBusinessBranding(p);this.busy=false;this.cancel();this.success='Business profile saved.';this.feedback.success(this.success);},error:e=>{const err=apiError(e);this.busy=false;this.error=e.status===409?'The profile changed while you were editing. Reload the latest profile before saving again.':err.description;this.conflict=e.status===409;this.fieldErrors=err.fieldErrors||{};if(['INVALID_LOGO','LOGO_TOO_LARGE'].includes(err.errorCode||''))this.fieldErrors['logo']=err.description;if(e.status===403)this.auth.fetchBusinessBranding().subscribe({error:()=>undefined});}});
 }
 reload(){const decision=this.canLeave();(typeof decision==='boolean'?of(decision):decision).subscribe(ok=>{if(ok)this.load();});}
 ngOnDestroy(){this.clearPreview();}
}
