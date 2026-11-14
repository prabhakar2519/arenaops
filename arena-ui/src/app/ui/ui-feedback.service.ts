import {Injectable, OnDestroy, signal} from '@angular/core';
import {Observable, ReplaySubject, Subscription, defer, of} from 'rxjs';
import {apiError} from '../api-error';

export type FeedbackVariant='primary'|'warning'|'destructive'|'information'|'error';
export interface DialogOptions {
 id?:string; title:string; message:string; icon?:string; details?:string[];
 confirmText?:string; cancelText?:string; variant?:FeedbackVariant; acknowledge?:boolean;
 action?:()=>Observable<unknown>; successMessage?:string; canRetry?:()=>boolean;
}
export interface DialogState {options:DialogOptions;busy:boolean;error:string;result:ReplaySubject<boolean>;settled:boolean;errorToast?:number;}
export type ToastKind='success'|'error'|'warning'|'information';
export interface Toast {id:number;kind:ToastKind;message:string;duration:number;action?:{label:string;run:()=>void};}

/** One application-wide feedback queue. Business operations remain in their existing services. */
@Injectable({providedIn:'root'})
export class UiFeedbackService implements OnDestroy {
 readonly current=signal<DialogState|null>(null);
 readonly toasts=signal<Toast[]>([]);
 private queue:DialogState[]=[];private operation?:Subscription;private timers=new Map<number,ReturnType<typeof setTimeout>>();private nextId=0;
 confirm(options:DialogOptions):Observable<boolean>{
  return new Observable<boolean>(subscriber=>{
   const key=options.id||`${options.title}\n${options.message}`;
   if(this.queue.some(r=>(r.options.id||`${r.options.title}\n${r.options.message}`)===key)){subscriber.next(false);subscriber.complete();return;}
   const request:DialogState={options:{...options},busy:false,error:'',result:new ReplaySubject<boolean>(1),settled:false};
   this.queue.push(request);const subscription=request.result.subscribe(subscriber);if(!this.current())this.current.set(this.queue[0]);
   return ()=>{subscription.unsubscribe();if(!request.settled&&!request.busy)this.finish(request,false);};
  });
 }
 acknowledge(options:Omit<DialogOptions,'acknowledge'|'action'>){return this.confirm({...options,acknowledge:true,confirmText:options.confirmText||'OK'});}
 proceed(){
  const request=this.active();if(!request||request.busy||(request.error&&request.options.canRetry&&!request.options.canRetry()))return;
  if(!request.options.action){this.finish(request,true);return;}
  this.current.set({...request,busy:true,error:''});request.busy=true;request.error='';
  this.operation=defer(request.options.action).subscribe({
   error:failure=>{request.busy=false;request.error=apiError(failure).description;this.current.set({...request});request.errorToast=this.notify('error',request.error);},
   complete:()=>{if(request.errorToast)this.dismissToast(request.errorToast);if(request.options.successMessage)this.success(request.options.successMessage);this.finish(request,true);}
  });
 }
 private active(){return this.queue.find(r=>r.result===this.current()?.result)||null;}
 cancel(){const request=this.active();if(request&&!request.busy)this.finish(request,false);}
 private finish(request:DialogState,result:boolean){
  if(request.settled)return;request.settled=true;this.queue=this.queue.filter(r=>r.result!==request.result);
  // Clear state before delivering results: callbacks may synchronously open another dialog.
  if(this.current()?.result===request.result)this.current.set(this.queue[0]||null);
  request.result.next(result);request.result.complete();
 }
 success(message:string){return this.notify('success',message);}
 failure(failure:unknown){return this.notify('error',apiError(failure).description);}
 notify(kind:ToastKind,message:string,options:{duration?:number;action?:Toast['action']}={}){
  const existing=this.toasts().find(t=>t.kind===kind&&t.message===message);if(existing){this.resumeToast(existing.id);return existing.id;}
  const toast:Toast={id:++this.nextId,kind,message,duration:options.duration??(kind==='success'?5000:kind==='information'?7000:0),action:options.action};
  if(this.toasts().length>=3)this.dismissToast(this.toasts()[0].id);
  this.toasts.update(items=>[...items,toast]);this.resumeToast(toast.id);return toast.id;
 }
 dismissToast(id:number){this.pauseToast(id);this.toasts.update(items=>items.filter(t=>t.id!==id));}
 pauseToast(id:number){const timer=this.timers.get(id);if(timer)clearTimeout(timer);this.timers.delete(id);}
 resumeToast(id:number){this.pauseToast(id);const toast=this.toasts().find(t=>t.id===id);if(toast?.duration)this.timers.set(id,setTimeout(()=>this.dismissToast(id),toast.duration));}
 runToastAction(toast:Toast){this.dismissToast(toast.id);toast.action?.run();}
 ngOnDestroy(){this.operation?.unsubscribe();for(const timer of this.timers.values())clearTimeout(timer);for(const request of [...this.queue])this.finish(request,false);this.timers.clear();}
}
