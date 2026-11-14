import {Component,effect,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ApiNotifications} from '../api-error';
import {UiFeedbackService} from './ui-feedback.service';
import {DialogPanelComponent} from './dialog-panel.component';
@Component({selector:'ao-ui-feedback',standalone:true,imports:[CommonModule,DialogPanelComponent],template:`
 <ao-dialog-panel *ngIf="feedback.current() as request" [options]="request.options" [busy]="request.busy" [error]="request.error" [retryAllowed]="!request.error || !request.options.canRetry || request.options.canRetry()" (proceed)="feedback.proceed()" (dismiss)="feedback.cancel()" />
 <section class="ao-toasts" aria-label="Notifications"><article *ngFor="let toast of feedback.toasts()" class="ao-toast" [attr.data-kind]="toast.kind" [attr.role]="toast.kind==='error'?'alert':'status'" (mouseenter)="feedback.pauseToast(toast.id)" (mouseleave)="feedback.resumeToast(toast.id)" (focusin)="feedback.pauseToast(toast.id)" (focusout)="feedback.resumeToast(toast.id)"><span aria-hidden="true">{{toast.kind==='success'?'✓':toast.kind==='information'?'ⓘ':'!'}}</span><span class="toast-message">{{toast.message}}</span><button *ngIf="toast.action" type="button" (click)="feedback.runToastAction(toast)">{{toast.action.label}}</button><button type="button" (click)="feedback.dismissToast(toast.id)" [attr.aria-label]="'Dismiss '+toast.kind+' notification'">×</button></article></section>`,styleUrl:'./ui-feedback.component.scss'})
export class UiFeedbackComponent {
 readonly feedback=inject(UiFeedbackService);private api=inject(ApiNotifications);
 constructor(){effect(()=>{const failure=this.api.error();if(failure){this.feedback.failure({status:failure.status,error:failure});this.api.dismiss();}});}
}
