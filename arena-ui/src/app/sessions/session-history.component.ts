import {DrawerComponent} from '../ui/shared';
import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { apiError } from '../api-error';
import { RunningSession, RunningSessionsService,DiscountRequest } from './running-sessions.service';

@Component({selector:'app-session-history',standalone:true,imports:[CommonModule,FormsModule,RouterLink,DrawerComponent],templateUrl:'./session-history.component.html',styleUrl:'./session-history.component.scss'})
export class SessionHistoryComponent implements OnInit, OnDestroy {
  private readonly service=inject(RunningSessionsService);
  private request?:Subscription;
  exporting=false;
  get fullFinancial():boolean {return this.rows.length?this.rows[0].financialVisible!==false:!!this.service.permissions?.()?.canViewFinancials;}
  billPaid(session:RunningSession):string{return session.paymentStatus==='PAID'?'Paid':['UNPAID','PARTIALLY_PAID'].includes(session.paymentStatus)?'Unpaid':'Not Finalized';}
  downloadExcel():void {
    if(this.exporting||this.loading||!this.from||!this.to||this.from>this.to)return;
    this.exporting=true;this.error='';
    this.service.exportHistory({from:this.from,to:this.to,status:this.status,search:this.search.trim()}).subscribe({next:blob=>{const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`arenaops-session-history-${new Date().toISOString().slice(0,10)}.xlsx`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);this.exporting=false;},error:async e=>{if(e.error instanceof Blob){try{e={...e,error:JSON.parse(await e.error.text())};}catch{ /* use existing safe fallback */ }}this.error=apiError(e).description;this.exporting=false;}});
  }
  audit:DiscountRequest|null=null;
  showDiscount(session:RunningSession):void {if(!session.discountRequestId)return;this.service.discountRequest(session.discountRequestId).subscribe({next:record=>this.audit=record,error:e=>this.error=apiError(e).description});}
  rows:RunningSession[]=[];loading=false;error='';page=0;total=0;pages=0;
  from=new Date(Date.now()-29*86400000).toLocaleDateString('en-CA');
  to=new Date().toLocaleDateString('en-CA');status='';search='';
  ngOnInit():void {this.load();}
  ngOnDestroy():void {this.request?.unsubscribe();}
  apply():void {this.page=0;this.load();}
  load():void {
    if(!this.from||!this.to||this.from>this.to){this.error='Choose a valid date range.';return;}
    this.request?.unsubscribe();this.loading=true;this.error='';
    this.request=this.service.history({from:this.from,to:this.to,status:this.status,search:this.search.trim(),page:this.page,size:25}).subscribe({next:r=>{this.rows=r.content;this.total=r.totalElements;this.pages=r.totalPages;this.loading=false;},error:e=>{this.rows=[];this.total=0;this.pages=0;this.error=apiError(e).description;this.loading=false;}});
  }
  changePage(delta:number):void {this.page+=delta;this.load();}
  duration(session:RunningSession):string {const seconds=Math.max(0,session.durationSeconds||0);return [Math.floor(seconds/3600),Math.floor(seconds%3600/60),seconds%60].map(n=>String(n).padStart(2,'0')).join(':');}
  pausedDuration(session:RunningSession):string {const seconds=Math.max(0,Math.floor(session.totalPausedSeconds||0));return [Math.floor(seconds/3600),Math.floor(seconds%3600/60),seconds%60].map(n=>String(n).padStart(2,'0')).join(':');}
  label(session:RunningSession):string {return session.status==='CANCELLED'?'Cancelled':session.status==='UNCONFIRMED'||session.outstandingAmount>0?'Unsettled':'Completed';}
}
