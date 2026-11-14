import {UiFeedbackService} from '../ui/ui-feedback.service';
import {DialogPanelComponent} from '../ui/dialog-panel.component';
import {Component,OnInit,inject,ViewChild,ElementRef,HostListener} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {forkJoin} from 'rxjs';
import {TrainingService,AcademySport,CatalogueItem} from './training.service';
import {activityCategory,activityIcon} from './activity-catalogue';
import {apiError} from '../api-error';
@Component({selector:'ao-sports-activities',standalone:true,imports:[CommonModule,FormsModule,RouterLink,DialogPanelComponent],templateUrl:'./sports-activities.component.html',styleUrl:'./sports-activities.component.scss'})
export class SportsActivitiesComponent implements OnInit {
 @ViewChild('activityDialog') dialog?:ElementRef<HTMLElement>;
 private previousFocus:HTMLElement|null=null;
 private focusDialog(){if(typeof document!=='undefined')this.previousFocus=document.activeElement as HTMLElement;setTimeout(()=>this.dialog?.nativeElement.querySelector<HTMLElement>('input,button')?.focus());}
 @HostListener('document:keydown',['$event']) trapFocus(event:KeyboardEvent){
  if(typeof document!=='undefined'&&document.querySelector('dialog[open]'))return;
  const dialog=this.dialog?.nativeElement;if(!dialog)return;
  if(event.key==='Escape'&&!this.busy){event.preventDefault();if(this.modal)this.close();else {this.renameTarget=null;this.statusTarget=null;this.error='';this.previousFocus?.focus();}return;}
  if(event.key!=='Tab')return;const nodes=Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select:not(:disabled),a[href]')).filter(node=>!node.closest('[hidden]'));const first=nodes[0],last=nodes.at(-1);if(!first||!last)return;
  if(!dialog.contains(document.activeElement)||(event.shiftKey&&document.activeElement===first)||(!event.shiftKey&&document.activeElement===last)){event.preventDefault();(event.shiftKey?last:first).focus();}
 }
 feedback=inject(UiFeedbackService);
 api=inject(TrainingService);sports:AcademySport[]=[];catalogue:CatalogueItem[]=[];loading=true;busy=false;error='';trainingDependency=false;message='';modal=false;
 loadFailed=false;discardPrompt=false;needsReconciliation=false;
 registerInactive=false;
 search='';status='';catalogueSearch='';category='All activities';selected=new Set<string>();customDraft='';customNames:string[]=[];customError='';renameTarget:AcademySport|null=null;renameName='';statusTarget:AcademySport|null=null;
 categories=['All activities','Racket sports','Team sports','Fitness & wellness','Combat sports','Aquatic sports','Precision & indoor','Other activities'];icon=activityIcon;
 ngOnInit(){this.load();}
 load(){this.loading=true;this.loadFailed=false;this.error='';forkJoin({sports:this.api.all<AcademySport>('sports'),catalogue:this.api.all<CatalogueItem>('catalogue')}).subscribe({next:r=>{this.sports=r.sports;this.catalogue=r.catalogue;this.loading=false;if(!this.showFilters){this.search='';this.status='';}},error:e=>{this.error=apiError(e).description;this.loadFailed=true;this.loading=false;}});}
 get selectionDirty(){return !!(this.selectionCount||this.customDraft.trim());}
 catalogueStatus(code:string){const sport=this.sports.find(s=>s.catalogueCode===code);return sport?(sport.active?'Already added':'Inactive'):this.selected.has(code)?'✓ Selected':'Select';}
 reconcile(){if(this.busy)return;this.busy=true;this.api.all<AcademySport>('sports').subscribe({next:sports=>{this.sports=sports;for(const code of this.selected)if(this.registered(code))this.selected.delete(code);this.customNames=this.customNames.filter(name=>!sports.some(s=>s.name.trim().replace(/\s+/g,' ').toLowerCase()===name.toLowerCase()));this.needsReconciliation=false;this.busy=false;this.error='Current activities refreshed. Review the remaining selection before retrying.';},error:()=>{this.busy=false;this.needsReconciliation=true;this.error='Unable to verify whether registration succeeded. Refresh current activities before retrying.';}});}
 get activeCount(){return this.sports.filter(s=>s.active).length;}
 get showFilters(){return this.sports.length>8;}
 get rows(){return this.sports.filter(s=>(!this.status||String(s.active)===this.status)&&s.name.toLowerCase().includes(this.search.trim().toLowerCase()));}
 get choices(){const query=this.catalogueSearch.trim().toLowerCase();return this.catalogue.filter(c=>`${c.label} ${c.code}`.toLowerCase().includes(query)&&(!!query||this.category==='All activities'||activityCategory(c)===this.category));}
 registered(code:string){return this.sports.some(s=>s.catalogueCode===code);}
 toggle(code:string){if(this.busy||this.registered(code))return;if(this.selected.has(code))this.selected.delete(code);else this.selected.add(code);}
 capabilities(s:AcademySport){return s.training&&s.sessions?'Training · Play & Facilities':s.training?'Training':s.sessions?'Play & Facilities':'Not configured';}
 open(){this.error='';this.trainingDependency=false;this.customError='';this.modal=true;this.focusDialog();}
 close(){if(this.busy)return;if(this.selectionDirty){this.discardPrompt=true;return;}this.dismiss();}
 keepSelecting(){this.discardPrompt=false;}
 discard(){this.selected.clear();this.customNames=[];this.customDraft='';this.customError='';this.discardPrompt=false;this.dismiss();}
 private dismiss(){this.registerInactive=false;this.modal=false;this.error='';this.trainingDependency=false;this.previousFocus?.focus();}
 addCustom(){const name=this.customDraft.trim().replace(/\s+/g,' ');if(name.length<2||name.length>160||/[\x00-\x1f\x7f]/.test(name)){this.customError='Enter a name of 2–160 characters.';return;}const key=name.toLowerCase();if([...this.catalogue.map(c=>c.label),...this.sports.map(s=>s.name),...this.customNames].some(v=>v.toLowerCase()===key)){this.customError='This activity already exists. Choose another name.';return;}this.customNames.push(name);this.customDraft='';this.customError='';}
 get selectionCount(){return this.selected.size+this.customNames.length;}
 register(){if(this.busy||this.needsReconciliation||!this.selectionCount)return;this.busy=true;this.error='';this.trainingDependency=false;const activities=[...[...this.selected].map(catalogueCode=>({catalogueCode})),...this.customNames.map(customName=>({catalogueCode:'OTHER',customName}))].map(activity=>this.registerInactive?{...activity,active:false}:activity);this.api.create<AcademySport[]>('sports/bulk',{activities}).subscribe({next:saved=>{this.busy=false;this.modal=false;this.registerInactive=false;this.message=`${saved.length} ${saved.length===1?'activity':'activities'} registered.`;this.feedback.success(this.message);this.selected.clear();this.customNames=[];this.customDraft='';this.load();},error:e=>{this.busy=false;if(e.status===0||e.status>=500){this.needsReconciliation=true;this.reconcile();return;}const failure=apiError(e);this.error=[e.status===405?'Activity registration is unavailable on the running backend. Please contact your administrator.':failure.description,...Object.values(failure.fieldErrors||{})].join(' ');}});}
 changeStatus(s:AcademySport){if(s.active){this.statusTarget=s;this.focusDialog();}else this.persistStatus(s,true);}
 confirmDeactivate(){if(this.statusTarget)this.persistStatus(this.statusTarget,false);}
 private persistStatus(s:AcademySport,active:boolean){if(this.busy)return;this.busy=true;this.error='';this.trainingDependency=false;this.api.update<AcademySport>(`sports/${s.id}`,{version:s.version,training:s.training,sessions:s.sessions,active}).subscribe({next:()=>{this.busy=false;this.statusTarget=null;this.message=active?'Activity reactivated.':'Activity deactivated.';this.feedback.success(this.message);this.load();},error:e=>{this.busy=false;this.trainingDependency=e.status===409&&e.error?.errorCode==='TRAINING_DEPENDENCY_EXISTS';this.error=this.trainingDependency?'Ongoing or future recurring training prevents deactivation. Review Training Batches first.':apiError(e).description;}});}
 rename(s:AcademySport){if(s.catalogueCode)return;this.renameTarget=s;this.renameName=s.name;this.error='';this.trainingDependency=false;this.focusDialog();}
 saveRename(){const s=this.renameTarget;if(!s||this.busy)return;const name=this.renameName.trim().replace(/\s+/g,' ');if(name.length<2||name.length>160){this.error='Enter a name of 2–160 characters.';return;}this.busy=true;this.api.update<AcademySport>(`sports/${s.id}/name`,{version:s.version,name}).subscribe({next:()=>{this.busy=false;this.renameTarget=null;this.message='Activity renamed.';this.feedback.success(this.message);this.load();},error:e=>{this.busy=false;this.error=apiError(e).description;}});}
}
