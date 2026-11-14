import {Component,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {CommonModule} from '@angular/common';import {RouterLink} from '@angular/router';import {HttpClient} from '@angular/common/http';import {Subscription} from 'rxjs';
import {Facility} from '../facilities/facility-api.service';import {AuthService} from '../services/auth';import {apiError} from '../api-error';import {UiFeedbackService} from '../ui/ui-feedback.service';
import {facilityCheckinUrl,facilityQrImage,printFacilityQr} from './facility-qr.utils';
interface QrCard {facility:Facility;loading:boolean;image:string;url:string;error:string;eligible:boolean;}
@Component({selector:'ao-facility-qr-grid',standalone:true,imports:[CommonModule,RouterLink],templateUrl:'./facility-qr-grid.component.html',styleUrl:'./facility-qr-grid.component.scss'})
export class FacilityQrGridComponent implements OnChanges,OnDestroy {
 @Input() facilities:Facility[]=[];@Input() enabled=true;cards:QrCard[]=[];
 private http=inject(HttpClient);readonly auth=inject(AuthService);readonly feedback=inject(UiFeedbackService);private sub=new Subscription();
 ngOnChanges(){const previous=new Map(this.cards.map(c=>[c.facility.facilityId,c]));this.cards=this.facilities.map(f=>{const eligible=this.enabled&&f.active&&f.configured;const existing=previous.get(f.facilityId);if(existing&&existing.eligible===eligible){existing.facility=f;return existing;}return {facility:f,loading:false,image:'',url:'',error:'',eligible};});for(const card of this.cards)if(card.eligible&&!card.image&&!card.error&&!card.loading)this.load(card);}
 load(card:QrCard){if(!card.eligible||card.loading)return;card.loading=true;card.error='';this.sub.add(this.http.post<{token:string;facility:string;url:string}>(`/api/academy/facilities/${card.facility.facilityId}/check-in-qr`,{}).subscribe({next:q=>{try{card.url=facilityCheckinUrl(q.url);card.image=facilityQrImage(card.url);}catch{card.error='QR preview is unavailable. Try again or ask the owner to review this code.';}card.loading=false;},error:e=>{card.loading=false;card.error=apiError(e).description;}}));}
 label(card:QrCard){return card.facility.displayName?`${card.facility.code} · ${card.facility.displayName}`:card.facility.code;}
 unavailable(card:QrCard){return !this.enabled?'This sport is inactive or facility access is disabled. Existing QR identities are preserved.':!card.facility.active?'This facility is inactive. Reactivate it in Tables & Rates to use QR check-in.':'Configure a positive hourly rate in Tables & Rates before using QR check-in.';}
 downloaded(card:QrCard){this.feedback.success(`QR download started for ${card.facility.code}.`);}
 print(card:QrCard){if(!card.image)return;try{void printFacilityQr(card.image,this.label(card)).catch(()=>this.feedback.notify('error','Unable to open the print dialog. You can download the QR instead.'));}catch{this.feedback.notify('error','Unable to open the print dialog. You can download the QR instead.');}}
 ngOnDestroy(){this.sub.unsubscribe();}
}
