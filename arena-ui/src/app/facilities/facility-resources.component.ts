import {MVP_FEATURES} from '../features';
import {FacilityQrGridComponent} from '../checkin/facility-qr-grid.component';
import {UiFeedbackService} from '../ui/ui-feedback.service';
// SPMS manage-tables layout adapted to persisted, independently priced resources.
import {Component,HostListener,OnInit,OnDestroy,inject,ViewChild,ElementRef} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormArray,FormBuilder,FormControl,FormGroup,ReactiveFormsModule,Validators} from '@angular/forms';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {Subscription,of,defer,tap} from 'rxjs';
import {AuthService} from '../services/auth';
import {AcademySport} from '../training/training.service';
import {FacilityApi,Facility,FacilityContext,GenerateOperation} from './facility-api.service';
import {apiError} from '../api-error';
@Component({selector:'ao-facility-resources',standalone:true,imports:[CommonModule,ReactiveFormsModule,RouterLink,FacilityQrGridComponent],templateUrl:'./facility-resources.component.html',styleUrl:'./facility-resources.component.scss'})
export class FacilityResourcesComponent implements OnInit,OnDestroy {
 readonly features=MVP_FEATURES;
 get facilitySportAvailable(){return !this.features.snookerFacilitiesOnly||this.sport?.catalogueCode==='SNOOKER';}
 feedback=inject(UiFeedbackService);
 private fb=inject(FormBuilder);private route=inject(ActivatedRoute);auth=inject(AuthService);api=inject(FacilityApi);private sub=new Subscription();
 context:FacilityContext|null=null;sport:AcademySport|null=null;records:Facility[]=[];latest:Facility[]|null=null;loading=true;failed=false;busy=false;error='';message='';submitted=false;conflict=false;uncertain=false;storageBlocked=false;pending:GenerateOperation|null=null;
 tableTypes=new FormArray<FormGroup>([]);facilities=new FormArray<FormGroup>([]);
 readonly facilityLogos:Readonly<Record<string,string>>={
  ENGLISH:'/images/games/logo_english_snooker.png',
  FRENCH:'/images/games/logo_french_billiards.png',
  POOL:'/images/games/logo_pool.png',
  SEMI_ENGLISH:'/images/games/logo_semi_english.png'
 };
 @ViewChild('generator') generator?:ElementRef<HTMLDetailsElement>;
 activeTab:'RATES'|'QR'='RATES';qrOpened=false;
 selectTab(tab:'RATES'|'QR'){if(this.busy)return;this.activeTab=tab;if(tab==='QR')this.qrOpened=true;}
 tabKey(event:KeyboardEvent){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();const tab=event.key==='Home'?'RATES':event.key==='End'?'QR':this.activeTab==='RATES'?'QR':'RATES';this.selectTab(tab);document.getElementById(tab==='RATES'?'facility-rates-tab':'facility-qr-tab')?.focus();}
 courtCreationOpen=true;bulkRatesOpen=false;
 bulkRate=new FormControl<number|null>(null,[Validators.required,...this.rateValidators()]);
 editingCourts=new Set<number>();
 conflictCodes:string[]=[];
 get isBadminton(){return this.sport?.catalogueCode==='BADMINTON';}
 get activeCourtCount(){return this.records.filter(row=>row.active).length;}
 openCourtGenerator(){if(this.busy)return;this.courtCreationOpen=true;if(this.generator){this.generator.nativeElement.open=true;setTimeout(()=>{const form=this.generator?.nativeElement;if(form){const bounds=form.getBoundingClientRect();if(bounds.top<0||bounds.bottom>window.innerHeight)form.scrollIntoView({behavior:'smooth',block:'nearest'});}});}}
 closeCourtGenerator(event:Event){event.preventDefault();if(!this.busy)this.courtCreationOpen=!this.courtCreationOpen;}
 cancelAdding(){if(this.busy||this.pending)return;const discard=()=>{this.resetQuantities();this.courtCreationOpen=false;};const hasDraft=this.tableTypes.controls.some(group=>group.value.count!==0||group.value.hourlyRate!==null||group.value.frameRate!==null);if(!hasDraft){discard();return;}this.sub.add(this.feedback.confirm({title:`Cancel adding ${this.surfaceLabelPlural.toLowerCase()}?`,message:'Discard the new facility quantities and rates? Existing facility edits will be kept.',confirmText:'Discard New Facilities',variant:'warning'}).subscribe(ok=>{if(ok)discard();}));}
 courtChanged(index:number){return this.changed(this.facilities.at(index),this.records[index]);}
 courtEditing(index:number){return this.editingCourts.has(this.records[index].facilityId)||(this.submitted&&this.facilities.at(index).invalid);}
 toggleCourt(index:number){const id=this.records[index].facilityId;if(this.editingCourts.has(id))this.editingCourts.delete(id);else this.editingCourts.add(id);}
 applyRateToAll(){
  if(!['BADMINTON','SNOOKER'].includes(this.sport?.catalogueCode||'')||!this.canEdit||this.busy||this.pending||this.conflict)return;
  this.bulkRate.markAsTouched();if(this.bulkRate.invalid)return;
  this.facilities.controls.forEach((group,index)=>{if((this.isBadminton?['COURT']:['ENGLISH','FRENCH','POOL','SEMI_ENGLISH']).includes(this.records[index].facilityType||'')){group.get('hourlyRate')!.setValue(this.bulkRate.value);group.get('hourlyRate')!.markAsDirty();}});
  this.message=`Rate applied to ${this.surfaceLabelPlural.toLowerCase()} drafts. Review individual rates, then Save Configuration.`;
 }
 get maxPerType(){return this.context?.limits?.maxPerType||50;}
 get supportedSports(){return this.context?.sports.filter(s=>this.features.snookerFacilitiesOnly||['SNOOKER','BADMINTON'].includes(s.catalogueCode||''))||[];}
 get eligibleSports(){return this.supportedSports.filter(s=>s.active&&s.sessions&&(!this.features.snookerFacilitiesOnly||s.catalogueCode==='SNOOKER'));}
 sportSetupReason(sport:AcademySport){
  if(!sport.active)return `${sport.name} is inactive. Existing ${sport.catalogueCode==='BADMINTON'?'courts':'tables'} are available for viewing only. Reactivate ${sport.name} to manage these facilities.${sport.sessions?'':' Play & Facility Access is also disabled. Enable it after reactivating the sport.'}`;
  return `Play & Facility Access is disabled for ${sport.name}. Enable it in Sports & Activities before adding facilities.`;
 }

 get surfaceLabelPlural(){return this.sport?.catalogueCode==='BADMINTON'?'Courts':'Tables';}
 get contextName(){return this.auth.businessBrandingValue?.displayName||'Your workspace';}
 get canEdit(){return this.facilitySportAvailable&&!!this.sport?.active&&!!this.sport?.sessions&&(this.auth.hasRole('OWNER')||this.auth.hasRole('MANAGER'));}
 get rowDirty(){return this.facilities.controls.some((group,i)=>this.changed(group,this.records[i]));}
 get dirty(){return this.rowDirty||this.tableTypes.controls.some(group=>group.value.count!==0);}
 private uuid(){const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;const hex=Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;}
 private pendingKey(){return `arena.facility-generation.${this.context?.academyId}`;}
 ngOnInit(){this.sub.add(this.route.queryParamMap.subscribe(()=>{if(!this.busy)this.loadContext();}));}
 ngOnDestroy(){this.sub.unsubscribe();}
 loadContext(){this.loading=true;this.failed=false;this.error='';this.sub.add(this.api.context().subscribe({next:context=>{this.context=context;const id=Number(this.route.snapshot.queryParamMap.get('sportId'));this.sport=this.supportedSports.find(s=>s.id===id)||this.eligibleSports[0]||this.supportedSports[0]||null;this.recoverPending();this.resetQuantities();if(this.sport)this.load();else this.loading=false;},error:e=>{this.failed=true;this.loading=false;this.error=apiError(e).description;}}));}
 private recoverPending(){this.storageBlocked=false;try{const text=sessionStorage.getItem(this.pendingKey());const candidate=text?JSON.parse(text):null;this.pending=candidate?.academyId===this.context?.academyId&&typeof candidate.key==='string'&&Array.isArray(candidate.facilities)?candidate:null;this.uncertain=!!this.pending;}catch{this.storageBlocked=true;this.pending=null;}}
 private resetQuantities(){this.tableTypes.clear();const types=this.sport?.catalogueCode==='BADMINTON'?['COURT']:['ENGLISH','FRENCH','POOL','SEMI_ENGLISH'];types.forEach(type=>this.tableTypes.push(this.fb.group({facilityType:[type],count:[0,[Validators.required,Validators.min(0),Validators.max(this.maxPerType),Validators.pattern(/^\d+$/)]],hourlyRate:[null,this.rateValidators()],frameRate:[null,this.rateValidators()]})));}
 load(){if(!this.sport||!this.context)return;if(!this.facilitySportAvailable){this.records=[];this.resetRows();this.loading=false;this.failed=false;return;}const sportId=this.sport.id;this.loading=true;this.failed=false;this.sub.add(this.api.list(this.context.academyId,this.sport.id).subscribe({next:result=>{if(this.sport?.id!==sportId)return;this.records=result.facilities;this.resetRows();this.loading=false;},error:e=>{if(this.sport?.id!==sportId)return;this.loading=false;this.failed=true;this.error=apiError(e).description;}}));}
 private rateValidators(){return [Validators.min(0.01),Validators.max(9999999999.99),Validators.pattern(/^\d+(\.\d{1,2})?$/)];}
 private resetRows(){this.courtCreationOpen=this.records.length===0;this.bulkRatesOpen=false;this.editingCourts.clear();this.bulkRate.reset();this.conflictCodes=[];this.facilities.clear();this.records.forEach(row=>this.facilities.push(this.fb.group({displayName:[row.displayName||'',[Validators.maxLength(160)]],hourlyRate:[row.hourlyRate,this.rateValidators()],frameRate:[row.frameRate,this.rateValidators()],active:[row.active]})));this.submitted=false;this.conflict=false;this.latest=null;}
 private changed(group:FormGroup,row:Facility){return !!row&&((group.value.displayName||null)!==row.displayName||group.value.hourlyRate!==row.hourlyRate||group.value.frameRate!==row.frameRate||group.value.active!==row.active);}
 onSportChange(sport:AcademySport){if(this.busy||this.sport?.id===sport.id)return;this.sub.add(this.leaveDecision().subscribe(ok=>{if(!ok)return;this.sport=sport;this.error='';this.message='';this.resetQuantities();this.load();}));}
 generate(saveConfiguration=false){if(this.busy||!this.canEdit||this.pending||this.storageBlocked||!this.context||!this.sport)return;if(this.rowDirty){this.error='Save or cancel the existing facility edits before generating more facilities.';return;}this.tableTypes.markAllAsTouched();if(this.tableTypes.invalid){this.courtCreationOpen=true;return;}const quantities=this.tableTypes.getRawValue().filter(item=>item['count']>0).map(item=>({facilityType:item['facilityType'],count:item['count']}));if(!quantities.length){this.error='Enter a quantity for at least one type.';return;}if(quantities.reduce((sum,item)=>sum+item.count,0)>(this.context.limits?.maxBulk||200)){this.error='The combined quantity exceeds the bulk limit.';return;}
  try{this.pending={key:this.uuid(),academyId:this.context.academyId,sportId:this.sport.id,facilities:quantities,initialRates:Object.fromEntries(this.tableTypes.getRawValue().filter(item=>item['count']>0).map(item=>[item['facilityType'],{hourlyRate:item['hourlyRate'],frameRate:item['frameRate']}])),saveConfiguration};sessionStorage.setItem(this.pendingKey(),JSON.stringify(this.pending));}catch{this.pending=null;this.error='A secure operation key and browser session storage are required before generating facilities. Use HTTPS or localhost and allow session storage.';return;}this.retryGeneration();
 }
 retryGeneration(){const operation=this.pending;if(!operation||this.busy||!this.facilitySportAvailable||this.features.snookerFacilitiesOnly&&this.context?.sports.find(s=>s.id===operation.sportId)?.catalogueCode!=='SNOOKER')return;this.busy=true;this.error='';this.message='';this.sub.add(this.api.create(operation).subscribe({next:result=>{if(this.sport?.id===operation.sportId){const byId=new Map(this.records.map(row=>[row.facilityId,row]));result.facilities.forEach(row=>{if(!byId.has(row.facilityId))byId.set(row.facilityId,row);});this.records=[...byId.values()].sort((a,b)=>a.facilityId-b.facilityId);this.resetRows();const createdIds=new Set(result.facilities.map(row=>row.facilityId));this.facilities.controls.forEach((group,i)=>{const row=this.records[i];const rates=operation.initialRates?.[row.facilityType||''];if(createdIds.has(row.facilityId)&&rates){if(row.hourlyRate===null&&rates.hourlyRate!==null)group.get('hourlyRate')!.setValue(rates.hourlyRate);if(row.frameRate===null&&rates.frameRate!==null)group.get('frameRate')!.setValue(rates.frameRate);}});this.resetQuantities();}this.busy=false;this.uncertain=false;this.clearPending();this.message=`${result.facilities.length} facilities generated and stored.`;if(operation.saveConfiguration&&this.rowDirty)this.saveAll(undefined,false);else if(operation.saveConfiguration){this.courtCreationOpen=false;this.feedback.success('Facility configuration saved.');}},error:e=>{this.busy=false;this.uncertain=this.uncertain||e.status===0||e.status>=500;if(this.uncertain){this.error='The generation result is unknown. Retry the same operation safely; its operation key is retained across refresh.';}else if(e.error?.errorCode==='FACILITY_IDEMPOTENCY_CONFLICT'){this.error='This operation key has a different stored payload. Contact support to reconcile it; do not submit a new generation.';}else{this.applyFieldErrors(e,true);this.clearPending();this.error=this.describe(e);}}}));}
 private clearPending(){this.pending=null;try{sessionStorage.removeItem(this.pendingKey());}catch{this.storageBlocked=true;}}
 saveAll(afterSaved?:()=>void,confirmPrices=true){if(this.busy||!this.facilitySportAvailable||!this.context||!this.sport?.active||this.pending||this.conflict)return;this.submitted=true;this.error='';this.message='';this.facilities.markAllAsTouched();if(this.facilities.invalid)return;const changes:Record<string,unknown>[]=[];
  this.facilities.controls.forEach((group,i)=>{const row=this.records[i];if(!this.changed(group,row)||!row.facilityType)return;const change:Record<string,unknown>={facilityId:row.facilityId,version:row.version};for(const key of ['displayName','hourlyRate','frameRate','active'] as const){const value=key==='displayName'?(group.value[key]||null):group.value[key];if(value!==row[key])change[key]=value;}changes.push(change);});
  if(!changes.length)return;
  const deactivating=changes.some(change=>change['active']===false),prices=confirmPrices&&changes.some(change=>'hourlyRate' in change||'frameRate' in change);
  const save=()=>defer(()=>{
   this.busy=true;this.error='';this.message='';
   return this.api.patch(this.context!.academyId,changes).pipe(tap({next:result=>{const updated=new Map(result.facilities.map(row=>[row.facilityId,row]));this.records=this.records.map(row=>updated.get(row.facilityId)||row);this.resetRows();this.busy=false;this.message='Facility configuration saved.';afterSaved?.();},error:e=>{this.busy=false;this.conflict=e.status===409;this.conflictCodes=Array.isArray(e.error?.conflicts)?e.error.conflicts.map((item:any)=>this.records.find(row=>row.facilityId===item.facilityId)?.code).filter((code:any):code is string=>!!code):[];this.applyFieldErrors(e);this.error=this.describe(e);if(e.status===0||e.status>=500){this.conflict=true;this.error='The save result is unknown. Review latest changes before retrying. Your draft is preserved.';}}}));
  });
  if(deactivating||prices)this.sub.add(this.feedback.confirm({id:'facility-save',title:prices?`Save ${this.isBadminton?'court':'table'} configuration?`:'Deactivate selected facilities?',message:prices?`Your updated ${this.isBadminton?'court':'table'} rates will apply to future sessions. Existing sessions will retain their original rate snapshots.`:'They remain in history and cannot be used for new sessions.',details:deactivating&&prices?['Selected facilities will be deactivated. Their configuration and history will be preserved.']:undefined,confirmText:'Save Configuration',variant:deactivating?'destructive':'primary',action:save,canRetry:()=>!this.conflict,successMessage:'Facility configuration saved.'}).subscribe());
  else this.sub.add(save().subscribe({next:()=>this.feedback.success('Facility configuration saved.'),error:e=>this.feedback.failure(e)}));
 }
 get totalFacilities(){return this.records.length+this.tableTypes.controls.reduce((sum,group)=>sum+(Number(group.value.count)||0),0);}
 saveConfiguration(){if(this.rowDirty)this.saveAll(()=>{if(this.tableTypes.controls.some(group=>Number(group.value.count)>0))this.generate(true);});else this.generate(true);}
 toggleFacilityActive(index:number){if(this.busy||!this.facilitySportAvailable||this.pending||!this.sport?.active)return;const group=this.facilities.at(index);if(!this.canEdit&&!this.records[index].active)return;group.get('active')!.setValue(!group.value.active);group.get('active')!.markAsDirty();}
 reviewLatest(){if(!this.context||!this.sport||this.busy)return;this.busy=true;this.sub.add(this.api.list(this.context.academyId,this.sport.id).subscribe({next:result=>{this.latest=result.facilities;this.busy=false;},error:e=>{this.busy=false;this.error=this.describe(e);}}));}
 useLatest(){if(!this.latest||this.busy)return;this.sub.add(this.feedback.confirm({title:'Use latest configuration?',message:'Discard your draft and use the latest saved configuration?',confirmText:'Discard Draft',variant:'warning'}).subscribe(ok=>{if(!ok||!this.latest)return;this.records=this.latest;this.resetRows();this.error='';}));}
 cancel(){if(this.busy)return;this.sub.add(this.leaveDecision().subscribe(ok=>{if(!ok)return;this.resetRows();this.resetQuantities();this.error='';}));}
 canLeave(){return !this.busy&&(!this.dirty||this.feedback.confirm({title:'Discard facility changes?',message:'Discard unsaved facility changes? Generated facilities are already stored and will remain.',confirmText:'Discard Changes',variant:'warning'}));}
 private leaveDecision(){const decision=this.canLeave();return typeof decision==='boolean'?of(decision):decision;}
 @HostListener('window:beforeunload',['$event']) beforeUnload(event:BeforeUnloadEvent){if(this.dirty||this.busy){event.preventDefault();event.returnValue='';}}
 invalid(group:FormGroup,field:string){const control=group.get(field);return !!control?.invalid&&(control.touched||this.submitted);}
 private applyFieldErrors(e:any,generation=false){for(const [path,message] of Object.entries(e.error?.fieldErrors||{})){const match=/^facilities\[(\d+)\]\.(displayName|hourlyRate|frameRate|count)$/.exec(path);if(!match)continue;const index=Number(match[1]);const group=generation?this.tableTypes.controls.find(row=>row.value.facilityType===this.pending?.facilities[index]?.facilityType):this.facilities.controls.find((_row,i)=>this.records[i]?.facilityId===index);const control=group?.get(match[2]);control?.setErrors({server:String(message)});control?.markAsTouched();}}
 fieldError(group:FormGroup,field:string,fallback:string){return group.get(field)?.getError('server')||fallback;}
 private describe(e:any){const fields=e.error?.fieldErrors;return [apiError(e).description,...Object.values(fields||{})].join(' ');}
}
