import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
export interface ProfilePermissions {viewProfile:boolean;editProfile:boolean;manageOrdinaryStaff:boolean;delegateManagerAccess:boolean;}
export interface BusinessBranding {displayName:string;logoUrl:string|null;permissions:ProfilePermissions;}
export interface BusinessProfile extends BusinessBranding {version:number;businessType:string|null;publicContactEmail:string|null;publicPhone:string|null;addressLine:string|null;city:string|null;region:string|null;postalCode:string|null;country:string|null;}
@Injectable({providedIn:'root'})
export class BusinessProfileService {
 private http=inject(HttpClient);
 get(){return this.http.get<BusinessProfile>('/api/business-profile');}
 save(profile:Record<string,unknown>,file:File|null){const body=new FormData();body.append('profile',new Blob([JSON.stringify(profile)],{type:'application/json'}));if(file)body.append('logo',file,'logo');return this.http.put<BusinessProfile>('/api/business-profile',body);}
}
