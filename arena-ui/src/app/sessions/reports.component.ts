import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subscription, expand, map, reduce, forkJoin } from 'rxjs';
import { apiError } from '../api-error';
import { AuthService } from '../services/auth';
import { RunningSession, RunningSessionsService,SessionCollection } from './running-sessions.service';
import { FacilityReport, ReportPeriod, buildReport, reportRange, reportWorkbook } from './report-model';

/** Adapts SPMS period reports, breakdowns, trend and spreadsheet export to ArenaOps history. */
@Component({selector:'app-reports',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./reports.component.html',styleUrl:'./reports.component.scss'})
export class ReportsComponent implements OnInit,OnDestroy {
 private readonly service=inject(RunningSessionsService);private readonly auth=inject(AuthService);private request?:Subscription;
 appliedPeriod:ReportPeriod='DAILY';
 period:ReportPeriod='DAILY';date=new Intl.DateTimeFormat('en-CA',{timeZone:this.auth.academyContextValue?.timezone||'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
 collections:SessionCollection[]=[];
 sport='';sports:string[]=[];rows:RunningSession[]=[];report:FacilityReport|null=null;loading=false;error='';
 ngOnInit():void {this.generate();}ngOnDestroy():void {this.request?.unsubscribe();}
 generate():void {
  let range:{from:string;to:string};try{range=reportRange(this.date,this.period);}catch{this.error='Choose a valid report date.';return;}
  const appliedPeriod=this.period;this.request?.unsubscribe();this.loading=true;this.error='';this.report=null;
  const page=(index:number)=>this.service.history({...range,page:index,size:100}).pipe(map(result=>({...result,index})));
  this.request=forkJoin({collections:this.service.collections(range.from,range.to),rows:page(0).pipe(expand(result=>result.index+1<result.totalPages?page(result.index+1):EMPTY),reduce((rows:RunningSession[],result)=>rows.concat(result.content),[]))}).subscribe({next:({rows,collections})=>{this.rows=rows;this.collections=collections;this.sports=[...new Set([...rows.map(s=>s.sport),...collections.map(p=>p.sport)])].sort();if(this.sport&&!this.sports.includes(this.sport))this.sport='';this.appliedPeriod=appliedPeriod;this.report=buildReport(rows,range.from,range.to,appliedPeriod,this.sport,this.collections);this.loading=false;},error:e=>{this.loading=false;this.rows=[];this.sports=[];this.error=apiError(e).description;}});
 }
 filterSport():void {if(this.report)this.report=buildReport(this.rows,this.report.from,this.report.to,this.appliedPeriod,this.sport,this.collections);}
 downloadExcel():void {if(!this.report)return;const url=URL.createObjectURL(new Blob([reportWorkbook(this.report)],{type:'application/vnd.ms-excel;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`ArenaOps_${this.appliedPeriod}_${this.report.from}.xml`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
}
