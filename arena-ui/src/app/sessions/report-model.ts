import type { RunningSession,SessionCollection } from './running-sessions.service';
export type ReportPeriod='DAILY'|'WEEKLY'|'MONTHLY';
export interface ReportStat {label:string;sport:string;sessionCount:number;hours:number;revenue:number;gross:number;discounts:number;paid:number;pending:number;}
export interface FacilityReport {from:string;to:string;sessionCount:number;hours:number;revenue:number;gross:number;discounts:number;paid:number;pending:number;reservedClosed:number;peakTime:string;mostPlayed:string;facilityStats:ReportStat[];gameTypeStats:ReportStat[];trend:ReportStat[];sessions:RunningSession[];collections:SessionCollection[];}
const day=(d:Date)=>d.toISOString().slice(0,10);
export function reportRange(anchor:string,period:ReportPeriod):{from:string;to:string} {
 const date=new Date(anchor+'T00:00:00Z');if(!/^\d{4}-\d{2}-\d{2}$/.test(anchor)||!Number.isFinite(date.getTime())||day(date)!==anchor)throw new Error('Choose a valid report date.');
 let from=new Date(date),to=new Date(date);
 if(period==='WEEKLY'){from.setUTCDate(from.getUTCDate()-((from.getUTCDay()+6)%7));to=new Date(from);to.setUTCDate(to.getUTCDate()+6);}
 if(period==='MONTHLY'){from=new Date(Date.UTC(date.getUTCFullYear(),date.getUTCMonth(),1));to=new Date(Date.UTC(date.getUTCFullYear(),date.getUTCMonth()+1,0));}
 return {from:day(from),to:day(to)};
}
const money=(rows:RunningSession[],field:'totalAmount'|'paidAmount'|'outstandingAmount')=>rows.reduce((sum,s)=>sum+Math.round(Number(s[field]||0)*100),0)/100;
function stat(label:string,sport:string,rows:RunningSession[]):ReportStat {return {label,sport,sessionCount:rows.length,hours:rows.reduce((sum,s)=>sum+Number(s.durationSeconds||0),0)/3600,revenue:money(rows,'totalAmount'),gross:rows.reduce((n,s)=>n+Math.round(Number(s.originalAmount??s.totalAmount??0)*100),0)/100,discounts:rows.reduce((n,s)=>n+Math.round(Number(s.approvedDiscount||0)*100),0)/100,paid:money(rows,'paidAmount'),pending:money(rows,'outstandingAmount')};}
function grouped(rows:RunningSession[],key:(s:RunningSession)=>string,label:(s:RunningSession)=>string):ReportStat[]{const map=new Map<string,RunningSession[]>();for(const row of rows){const k=key(row);map.set(k,[...(map.get(k)||[]),row]);}return [...map.values()].map(items=>stat(label(items[0]),items[0].sport,items)).sort((a,b)=>b.revenue-a.revenue||a.label.localeCompare(b.label));}
export function buildReport(rows:RunningSession[],from:string,to:string,period:ReportPeriod,sport='',payments?:SessionCollection[]):FacilityReport {
 const collections=(payments||[]).filter(p=>!sport||p.sport===sport);
 const sessions=rows.filter(s=>!sport||s.sport===sport);const closed=sessions.filter(s=>s.status!=='CANCELLED');const total=stat('', '',closed);
 const facilityStats=grouped(closed,s=>String(s.facilityId),s=>s.facilityName||s.facilityCode);
 const gameTypeStats=grouped(closed,s=>s.sport+'|'+s.facilityType,s=>s.facilityType.replaceAll('_',' '));
 const peak=grouped(closed,s=>s.startedAt.slice(11,13),s=>s.startedAt.slice(11,13)+':00').sort((a,b)=>b.sessionCount-a.sessionCount||a.label.localeCompare(b.label))[0];
 const most=[...facilityStats].sort((a,b)=>b.hours-a.hours||b.sessionCount-a.sessionCount)[0];const trend:ReportStat[]=[];
 if(period==='DAILY'){for(let hour=0;hour<24;hour++){const label=String(hour).padStart(2,'0')+':00';trend.push(stat(label,'',closed.filter(s=>s.startedAt.slice(11,13)===label.slice(0,2))));}}
 else {for(let d=new Date(from+'T00:00:00Z');day(d)<=to;d.setUTCDate(d.getUTCDate()+1)){const label=day(d);trend.push(stat(label,'',closed.filter(s=>s.startedAt.slice(0,10)===label)));}}
 if(payments)total.paid=collections.reduce((n,p)=>n+Math.round(p.amount*100),0)/100;
 return {collections,from,to,sessionCount:total.sessionCount,hours:total.hours,revenue:total.revenue,gross:total.gross,discounts:total.discounts,paid:total.paid,pending:total.pending,reservedClosed:closed.filter(s=>!!s.reservedStart).length,peakTime:peak?.label||'—',mostPlayed:most?.label||'—',facilityStats,gameTypeStats,trend,sessions};
}
const escapeXML=(value:unknown)=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]!));
export function reportWorkbook(report:FacilityReport):string {
 const sheet=(name:string,rows:(string|number)[][])=>`<Worksheet ss:Name="${escapeXML(name)}"><Table>${rows.map(row=>`<Row>${row.map(value=>`<Cell><Data ss:Type="${typeof value==='number'?'Number':'String'}">${escapeXML(value)}</Data></Cell>`).join('')}</Row>`).join('')}</Table></Worksheet>`;
 const stats=(items:ReportStat[])=>[['Name','Sport','Sessions','Hours','Gross charges','Approved discounts','Net charges','Cumulative collected','Outstanding'],...items.map(s=>[s.label,s.sport,s.sessionCount,s.hours,s.gross,s.discounts,s.revenue,s.paid,s.pending])] as (string|number)[][];
 return '<?xml version="1.0"?><?mso-application progid="Excel.Sheet"?><Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">'+sheet('Summary',[['From',report.from],['To',report.to],['Closed sessions',report.sessionCount],['Hours run',report.hours],['Gross charges',report.gross],['Approved discounts',report.discounts],['Net charges',report.revenue],['Collected',report.paid],['Pending',report.pending],['Reserved sessions closed',report.reservedClosed],['Peak starting hour',report.peakTime],['Most played facility',report.mostPlayed]])+sheet('Facility performance',stats(report.facilityStats))+sheet('Game types',stats(report.gameTypeStats))+sheet('Trend',stats(report.trend))+sheet('Session history',[['Facility','Sport','Type','Player','Status','Started','Ended','Seconds','Original bill','Approved discount','Net bill','Paid','Pending'],...report.sessions.map(s=>[s.facilityName,s.sport,s.facilityType,s.playerName||'Guest',s.status,s.startedAt,s.endedAt||s.stoppedAt||'',s.durationSeconds,Number(s.originalAmount??s.totalAmount??0),Number(s.approvedDiscount||0),Number(s.totalAmount||0),s.paidAmount,s.outstandingAmount])])+sheet('Dated collections',[['Session','Sport','Payment date','Amount','Recorded at','Recorded by','Reference'],...report.collections.map(p=>[p.sessionId,p.sport,p.paymentDate,p.amount,p.recordedAt,p.recordedBy,p.reference])])+'</Workbook>';
}
