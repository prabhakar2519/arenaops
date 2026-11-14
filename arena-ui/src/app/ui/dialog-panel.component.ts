import {AfterViewInit,Component,ElementRef,EventEmitter,Input,OnDestroy,Output,ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import type {DialogOptions} from './ui-feedback.service';
let sequence=0;
@Component({selector:'ao-dialog-panel',standalone:true,imports:[CommonModule],templateUrl:'./dialog-panel.component.html',styleUrl:'./dialog-panel.component.scss'})
export class DialogPanelComponent implements AfterViewInit,OnDestroy {
 @Input({required:true}) options!:DialogOptions;@Input() busy=false;@Input() error='';@Input() retryAllowed=true;
 @Output() proceed=new EventEmitter<void>();@Output() dismiss=new EventEmitter<void>();
 @ViewChild('dialog',{static:true}) dialog!:ElementRef<HTMLDialogElement>;
 readonly id=`ao-dialog-${++sequence}`;private previous:HTMLElement|null=null;private overflow='';
 ngAfterViewInit(){this.overflow=document.body.style.overflow;document.body.style.overflow='hidden';this.previous=document.activeElement as HTMLElement|null;this.dialog.nativeElement.showModal();this.dialog.nativeElement.querySelector<HTMLElement>(this.options.acknowledge?'[data-confirm]':'[data-cancel]')?.focus();}
 ngOnDestroy(){this.dialog.nativeElement.close();document.body.style.overflow=this.overflow;const target=this.previous?.isConnected&&!this.previous.matches(':disabled')?this.previous:document.getElementById('main-content');if(target){const tab=target.getAttribute('tabindex');if(tab===null)target.setAttribute('tabindex','-1');target.focus({preventScroll:true});if(tab===null)target.removeAttribute('tabindex');}}
 onCancel(event:Event){event.preventDefault();if(!this.busy&&!this.options.acknowledge&&!['destructive','error'].includes(this.options.variant||''))this.dismiss.emit();}
 key(event:KeyboardEvent){if(event.key!=='Tab')return;const nodes=Array.from(this.dialog.nativeElement.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),[tabindex="0"]'));const first=nodes[0],last=nodes.at(-1);if(!first){event.preventDefault();return;}if(event.shiftKey&&(document.activeElement===first||document.activeElement===this.dialog.nativeElement)){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}}
}
