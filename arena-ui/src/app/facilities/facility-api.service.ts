import {Injectable,inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {AcademySport} from '../training/training.service';
export interface Facility {facilityId:number;registeredSportId:number;code:string;facilityType:string|null;displayName:string|null;hourlyRate:number|null;frameRate:number|null;active:boolean;configured:boolean;version:number;}
export interface FacilityResult {facilities:Facility[];}
export interface FacilityContext {academyId:number;sports:AcademySport[];limits?:{maxPerType:number;maxBulk:number};}
export interface GenerateOperation {key:string;academyId:number;sportId:number;facilities:{facilityType:string;count:number}[];initialRates?:Record<string,{hourlyRate:number|null;frameRate:number|null}>;saveConfiguration?:boolean;}
@Injectable({providedIn:'root'})
export class FacilityApi {
 private http=inject(HttpClient);private base='/api/v1/academies';
 context(){return this.http.get<FacilityContext>(`${this.base}/current/facilities/context`);}
 list(academy:number,sport:number){return this.http.get<FacilityResult>(`${this.base}/${academy}/sports/${sport}/facilities`);}
 create(operation:GenerateOperation){return this.http.post<FacilityResult>(`${this.base}/${operation.academyId}/sports/${operation.sportId}/facilities/bulk`,{facilities:operation.facilities},{headers:{'Idempotency-Key':operation.key}});}
 patch(academy:number,facilities:Record<string,unknown>[]){return this.http.patch<FacilityResult>(`${this.base}/${academy}/facilities/bulk`,{facilities});}
}
