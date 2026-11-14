import '@angular/compiler';
import assert from 'node:assert/strict';
import {test} from 'node:test';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import ts from 'typescript';
import {createEnvironmentInjector,runInInjectionContext} from '@angular/core';
import {of} from 'rxjs';
const require=createRequire(import.meta.url);
async function load(path,aliases={}) {let code=ts.transpileModule(fs.readFileSync(new URL(path,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;code=code.replace(/from ['"]([^'"]+)['"]/g,(_m,name)=>`from '${aliases[name]||pathToFileURL(require.resolve(name)).href}'`);const url='data:text/javascript;base64,'+Buffer.from(code).toString('base64');return {url,module:await import(url)};}
const serviceURL='data:text/javascript;base64,'+Buffer.from('export class RunningSessionsService {}').toString('base64');
const {RunningSessionsService}=await import(serviceURL);
const errors=await load('../src/app/api-error.ts');
const panel=await load('../src/app/ui/dialog-panel.component.ts');
const shared=await load('../src/app/ui/shared.ts',{'./dialog-panel.component':panel.url});
const {module:{SessionHistoryComponent}}=await load('../src/app/sessions/session-history.component.ts',{'./running-sessions.service':serviceURL,'../api-error':errors.url,'../ui/shared':shared.url});
function history(rows=[]) {const injector=createEnvironmentInjector([{provide:RunningSessionsService,useValue:{history:()=>of({content:rows,totalElements:rows.length,totalPages:1})}}]);const component=runInInjectionContext(injector,()=>new SessionHistoryComponent());return {component,close(){component.ngOnDestroy();injector.destroy();}};}
for(const [name,paused,billable,expected] of [['never paused',0,933,'00:00:00'],['one pause',120,242,'00:02:00'],['multiple pauses',119,381,'00:01:59'],['stopped while paused',210,390,'00:03:30']])test(`history ${name}: formats backend totals without replacing billable duration`,()=>{const {component,close}=history();const row=Object.freeze({durationSeconds:billable,totalPausedSeconds:paused,startedAt:'2026-10-30T15:25:00.123456',endedAt:'2026-10-30T15:31:02.623456',totalAmount:57,paidAmount:57});assert.equal(component.pausedDuration(row),expected);assert.equal(component.duration(row),new Date(billable*1000).toISOString().slice(11,19));assert.equal(row.totalAmount,57);assert.equal(row.paidAmount,57);close();});
test('cancelled and legacy records retain status and safe zero fallback; hours do not wrap at 24',()=>{const {component,close}=history();assert.equal(component.pausedDuration({totalPausedSeconds:45}),'00:00:45');assert.equal(component.label({status:'CANCELLED'}),'Cancelled');for(const value of [undefined,null,0])assert.equal(component.pausedDuration({totalPausedSeconds:value}),'00:00:00');assert.equal(component.pausedDuration({totalPausedSeconds:90061}),'25:01:01');close();});
test('history API records preserve historical financial amounts and accumulated pause totals on reload',()=>{const row=Object.freeze({id:1,status:'STOPPED',durationSeconds:390,totalPausedSeconds:210,totalAmount:333.37,paidAmount:123.45,outstandingAmount:209.92});const {component,close}=history([row]);component.ngOnInit();component.load();assert.equal(component.rows[0],row);assert.equal(component.pausedDuration(component.rows[0]),'00:03:30');assert.equal(component.rows[0].totalAmount,333.37);assert.equal(component.rows[0].paidAmount,123.45);close();});
