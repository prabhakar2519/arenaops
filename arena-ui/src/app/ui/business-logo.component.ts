import {Component,Input,OnChanges,OnDestroy,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {HttpClient} from '@angular/common/http';
import {Subscription} from 'rxjs';
@Component({selector:'ao-business-logo',standalone:true,imports:[CommonModule],template:`<img *ngIf="source; else fallback" [src]="source" alt="Business logo" (error)="failed()"><ng-template #fallback><span aria-hidden="true">{{initial}}</span></ng-template>`,styles:[`:host{display:inline-flex;width:var(--logo-size,36px);height:var(--logo-size,36px);flex-shrink:0}img,span{width:100%;height:100%;border-radius:8px}img{object-fit:contain}span{display:grid;place-items:center;background:var(--brand);color:var(--on-primary);font-size:calc(var(--logo-size,36px) * .48);font-weight:600}`]})
export class BusinessLogoComponent implements OnChanges,OnDestroy {
 @Input() url:string|null|undefined=null;@Input() initial='B';source:string|null=null;
 private http=inject(HttpClient);private request?:Subscription;private objectUrl:string|null=null;
 ngOnChanges(){this.request?.unsubscribe();this.release();this.source=null;if(!this.url)return;
  if(this.url.startsWith('blob:')){this.source=this.url;return;}
  if(!this.url.startsWith('/api/business-profile/logo?revision='))return;
  // Use the same authenticated API path as profile saves; image tags do not run HTTP interceptors.
  this.request=this.http.get(this.url,{responseType:'blob',withCredentials:true}).subscribe({next:blob=>{if(blob.size && blob.type.split(';')[0]==='image/png'){this.objectUrl=URL.createObjectURL(blob);this.source=this.objectUrl;}},error:()=>this.failed()});
 }
 failed(){this.release();this.source=null;}
 private release(){if(this.objectUrl)URL.revokeObjectURL(this.objectUrl);this.objectUrl=null;}
 ngOnDestroy(){this.request?.unsubscribe();this.release();}
}
