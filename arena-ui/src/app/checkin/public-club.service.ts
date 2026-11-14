import {Injectable,inject} from '@angular/core';import {HttpClient} from '@angular/common/http';
export interface ClubSite {name:string;logo:string|null;cover:string|null;sports:{name:string;code:string}[];email:string|null;phone:string|null;address:string;timezone:string;facilities:{id:number;label:string;sport:string}[];bookingsEnabled:boolean;opensAt:string;closesAt:string;durations:number[];}
export function clubHostname():boolean {const h=location.hostname.toLowerCase();return /^[a-z][a-z0-9-]+\.(arenaops\.in|arenaops\.test|localhost)$/.test(h)&&!['www','sit','dev','api','admin','auth','app','staging'].includes(h.split('.')[0]);}
@Injectable({providedIn:'root'})
export class PublicClubApi {
 private http=inject(HttpClient);
 private context(slug?:string){return slug?{headers:{'X-Arena-Preview-Slug':slug}}:{};}
 site(slug?:string,ownerPreview=false){return this.http.get<ClubSite>(ownerPreview?'/api/academy/public-site/preview':'/api/public/club',this.context(slug));}
 logo(url:string,slug?:string){return this.http.get(url,{...this.context(slug),responseType:'blob'});}
 settings(){return this.http.get<any>('/api/academy/public-site');}
 save(body:any){return this.http.put<any>('/api/academy/public-site',body);}
 book(body:any,slug?:string){return this.http.post<{status:string;reference:string}>('/api/public/club/reservations',body,this.context(slug));}
 customers(page=0){return this.http.get<any>('/api/academy/customers',{params:{page}});}
 visits(id:number){return this.http.get<any[]>(`/api/academy/customers/${id}/visits`);}
 forget(id:number){return this.http.delete(`/api/academy/customers/${id}`);}
 pending(){return this.http.get<any[]>('/api/academy/public-reservations');}
 decision(id:number,approve:boolean){return this.http.post(`/api/academy/public-reservations/${id}/decision`,{approve});}
}
