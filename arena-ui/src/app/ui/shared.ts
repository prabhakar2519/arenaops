import {DialogPanelComponent} from './dialog-panel.component';
import { Component, Input, HostListener, Output, EventEmitter, ElementRef, OnDestroy, AfterViewInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({selector:'ao-page-header',standalone:true,template:`<header class="page-heading"><div><p class="eyebrow">{{ eyebrow }}</p><h1>{{ title }}</h1><p class="page-subtitle">{{ subtitle }}</p></div><div class="page-actions"><ng-content /></div></header>`})
export class PageHeaderComponent { @Input() title=''; @Input() subtitle=''; @Input() eyebrow='WORKSPACE'; }

@Component({selector:'ao-status',standalone:true,template:`<span class="status-pill" [class.positive]="status==='Active' || status==='ACTIVE'" [class.neutral]="status==='DRAFT'">{{ status | titlecase }}</span>`,imports:[CommonModule]})
export class StatusComponent { @Input() status=''; }

@Component({selector:'ao-empty',standalone:true,template:`<div class="empty-state"><span class="empty-symbol" aria-hidden="true">◇</span><h3>{{ title }}</h3><p>{{ description }}</p><ng-content /></div>`})
export class EmptyStateComponent { @Input() title='Nothing here yet'; @Input() description=''; }

@Component({selector:'ao-drawer',standalone:true,template:`<div class="drawer-backdrop" (click)="close.emit()"></div><section class="drawer-panel" role="dialog" aria-modal="true" [attr.aria-label]="title" tabindex="-1"><header class="drawer-heading"><div><p class="eyebrow">ACADEMY WORKSPACE</p><h2>{{ title }}</h2></div><button type="button" class="icon-button" aria-label="Close editor" (click)="close.emit()">×</button></header><div class="drawer-content"><ng-content /></div></section>`})
export class DrawerComponent implements AfterViewInit, OnDestroy {
 @Input() title='Editor'; @Output() close=new EventEmitter<void>();
 private element=inject(ElementRef); private previous=document.activeElement as HTMLElement|null; private overflow=document.body.style.overflow;
 ngAfterViewInit():void {document.body.style.overflow='hidden';this.element.nativeElement.querySelector('.drawer-panel').focus();}
 ngOnDestroy():void {document.body.style.overflow=this.overflow;this.previous?.focus();}
 @HostListener('document:keydown',['$event']) key(event:KeyboardEvent):void {
  if(document.querySelector('dialog[open]'))return;
  if(event.key==='Escape'){event.preventDefault();this.close.emit();}
  if(event.key!=='Tab')return;
  const nodes=[...this.element.nativeElement.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),a[href],textarea:not(:disabled)')] as HTMLElement[];
  const first=nodes[0],last=nodes[nodes.length-1];
  if(event.shiftKey&&(document.activeElement===first||document.activeElement===this.element.nativeElement.querySelector('.drawer-panel'))){event.preventDefault();last?.focus();}
  else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
 }
}

@Component({selector:'ao-confirm',standalone:true,imports:[DialogPanelComponent],template:`<ao-dialog-panel [options]="{title:title,message:description,confirmText:action,variant:'destructive'}" [busy]="busy" (dismiss)="cancel.emit()" (proceed)="confirm.emit()" />`})
export class ConfirmationComponent {
 @Input() busy=false;
 @Input() title='Confirm change'; @Input() description=''; @Input() action='Confirm';
 @Output() confirm=new EventEmitter<void>(); @Output() cancel=new EventEmitter<void>();
}
