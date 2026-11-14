import {test,expect} from '../fixtures/dashboard';
import type {Page,Route} from '@playwright/test';
const record={facilityId:1,registeredSportId:11,code:'E1',facilityType:'ENGLISH',displayName:'Match Table',hourlyRate:220,frameRate:80,active:true,configured:true,version:0};
async function setup(page:Page,model:any,patch?:(route:Route)=>Promise<unknown>){
 await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),model.user());
 await page.route('**/api/v1/academies/1/sports/11/facilities',r=>r.fulfill({json:{facilities:[record]}}));
 if(patch)await page.route('**/api/v1/academies/1/facilities/bulk',patch);
 await page.goto('/academy/facilities');await expect(page.getByRole('button',{name:'Edit E1',exact:true})).toBeVisible();
}
async function price(page:Page){await page.getByRole('button',{name:'Edit E1',exact:true}).click();await page.getByRole('spinbutton',{name:'Hourly rate for E1',exact:true}).fill('300');await page.getByRole('button',{name:'Save Configuration',exact:true}).click();return page.getByRole('dialog',{name:'Save table configuration?',exact:true});}

test('facility confirmation cancel and Escape preserve draft, focus and responsive modal',async({page,model},testInfo)=>{
 let calls=0,native=0;page.on('dialog',async d=>{native++;await d.dismiss();});await setup(page,model,async r=>{calls++;await r.fulfill({json:{facilities:[]}});});
 const modal=await price(page);await expect(modal).toBeVisible();await expect(modal).toContainText('Your updated table rates will apply to future sessions. Existing sessions will retain their original rate snapshots.');
 await expect(modal.getByRole('button',{name:'Cancel',exact:true})).toBeFocused();
 await page.keyboard.press('Shift+Tab');await expect(modal.getByRole('button',{name:'Save Configuration',exact:true})).toBeFocused();await page.keyboard.press('Tab');await expect(modal.getByRole('button',{name:'Cancel',exact:true})).toBeFocused();
 const bounds=await modal.boundingBox();const viewport=page.viewportSize()!;expect(bounds!.width).toBeLessThanOrEqual(480);expect(bounds!.x).toBeGreaterThanOrEqual(0);expect(bounds!.x+bounds!.width).toBeLessThanOrEqual(viewport.width);
 expect(await modal.evaluate(el=>(el as HTMLDialogElement).matches(':modal'))).toBe(true);
 const visual=await modal.evaluate(el=>{const style=getComputedStyle(el),button=getComputedStyle(el.querySelector('[data-confirm]')!);return {font:style.fontFamily,color:style.color,background:style.backgroundColor,buttonColor:button.color,buttonBackground:button.backgroundColor,inter:document.fonts.check('14px Inter')};});
 expect(visual.font).toContain('Inter');expect(visual.inter).toBe(true);expect(visual.buttonBackground).toBe(testInfo.project.name.includes('dark')?'rgb(102, 214, 181)':'rgb(8, 127, 101)');
 const luminance=(color:string)=>{const channels=color.match(/[\d.]+/g)!.slice(0,3).map(Number).map(n=>{const c=n/255;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);});return channels[0]*.2126+channels[1]*.7152+channels[2]*.0722;};
 const contrast=(a:string,b:string)=>{const values=[luminance(a),luminance(b)].sort((x,y)=>y-x);return (values[0]+.05)/(values[1]+.05);};expect(contrast(visual.color,visual.background)).toBeGreaterThanOrEqual(4.5);expect(contrast(visual.buttonColor,visual.buttonBackground)).toBeGreaterThanOrEqual(4.5);
 await page.screenshot({path:`/tmp/enh008-${testInfo.project.name}.png`});
 await modal.getByRole('button',{name:'Cancel',exact:true}).click();await expect(modal).toHaveCount(0);expect(calls).toBe(0);await expect(page.getByRole('spinbutton',{name:'Hourly rate for E1',exact:true})).toHaveValue('300');await expect(page.getByRole('button',{name:'Save Configuration',exact:true})).toBeFocused();
 await page.getByRole('button',{name:'Save Configuration',exact:true}).click();await page.keyboard.press('Escape');await expect(modal).toHaveCount(0);expect(calls).toBe(0);expect(native).toBe(0);
});

test('facility save blocks duplicate requests and cancellation while saving, then shows one timed success',async({page,model})=>{
 let calls=0;let release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve);await setup(page,model,async r=>{calls++;await gate;expect(r.request().postDataJSON()).toEqual({facilities:[{facilityId:1,version:0,hourlyRate:300}]});await r.fulfill({json:{facilities:[{...record,hourlyRate:300,version:1}]}});});
 const modal=await price(page);await modal.getByRole('button',{name:'Save Configuration',exact:true}).click();await expect(modal.getByRole('button',{name:'Saving…',exact:true})).toBeDisabled();await expect(modal.getByRole('button',{name:'Cancel',exact:true})).toBeDisabled();
 await modal.getByRole('button',{name:'Saving…',exact:true}).evaluate((button:HTMLButtonElement)=>{button.click();button.click();});await page.keyboard.press('Escape');await expect(modal).toBeVisible();expect(calls).toBe(1);
 release();await expect(modal).toHaveCount(0);await expect(page.locator('.ao-toast[data-kind=success]')).toHaveText(/Facility configuration saved/);await expect(page.locator('.ao-toast[data-kind=success]')).toHaveCount(1);await expect(page.locator('.existing-table-item')).toContainText('₹300/hr');expect(calls).toBe(1);
 await page.clock.fastForward(5100);await expect(page.locator('.ao-toast')).toHaveCount(0);
});

test('safe API errors remain in confirmation and explicit retry succeeds without exposing internals',async({page,model})=>{
 let calls=0;await setup(page,model,async r=>{calls++;if(calls===1)await r.fulfill({status:400,json:{errorCode:'FACILITY_INVALID_REQUEST',description:'SQL password=private'}});else await r.fulfill({json:{facilities:[{...record,hourlyRate:300,version:1}]}});});
 const modal=await price(page);await modal.getByRole('button',{name:'Save Configuration',exact:true}).click();await expect(modal.getByRole('alert')).toBeVisible();await expect(modal).not.toContainText('SQL');await expect(modal).not.toContainText('private');expect(calls).toBe(1);await expect(modal.getByRole('button',{name:'Save Configuration',exact:true})).toBeEnabled();await modal.getByRole('button',{name:'Save Configuration',exact:true}).click();await expect(modal).toHaveCount(0);expect(calls).toBe(2);await expect(page.locator('.ao-toast[data-kind=success]')).toHaveCount(1);await expect(page.locator('.ao-toast[data-kind=error]')).toHaveCount(0);
});

test('unknown save result prevents blind retry and preserves reconciliation workflow',async({page,model})=>{
 let calls=0;await setup(page,model,async r=>{calls++;await r.fulfill({status:503,json:{errorCode:'SERVICE_UNAVAILABLE',description:'Internal sensitive detail'}});});const modal=await price(page);await modal.getByRole('button',{name:'Save Configuration',exact:true}).click();await expect(modal.getByRole('alert')).toContainText('temporarily unavailable');await expect(modal.getByRole('button',{name:'Save Configuration',exact:true})).toBeDisabled();await modal.getByRole('button',{name:'Cancel',exact:true}).click();await expect(page.getByRole('button',{name:'Review Latest Changes',exact:true})).toBeVisible();await expect(page.getByRole('spinbutton',{name:'Hourly rate for E1',exact:true})).toHaveValue('300');expect(calls).toBe(1);await expect(page.locator('.ao-toast[data-kind=error]')).toHaveCount(1);
});

test('destructive facility confirmation requires explicit cancel or proceed and preserves payload',async({page,model})=>{
 let calls=0;await setup(page,model,async r=>{calls++;expect(r.request().postDataJSON()).toEqual({facilities:[{facilityId:1,version:0,active:false}]});await r.fulfill({json:{facilities:[{...record,active:false,version:1}]}});});await page.getByRole('button',{name:'Deactivate E1',exact:true}).click();await page.getByRole('button',{name:'Save Configuration',exact:true}).click();const modal=page.getByRole('dialog',{name:'Deactivate selected facilities?',exact:true});await expect(modal).toHaveAttribute('data-variant','destructive');await page.keyboard.press('Escape');await expect(modal).toBeVisible();expect(calls).toBe(0);await modal.getByRole('button',{name:'Cancel',exact:true}).click();expect(calls).toBe(0);await page.getByRole('button',{name:'Save Configuration',exact:true}).click();await modal.getByRole('button',{name:'Save Configuration',exact:true}).click();await expect(modal).toHaveCount(0);expect(calls).toBe(1);await expect(page.getByRole('button',{name:'Reactivate E1',exact:true})).toBeVisible();
});

test('in-app navigation uses shared discard confirmation rather than a browser dialog',async({page,model})=>{
 let native=0;page.on('dialog',async d=>{native++;await d.dismiss();});await setup(page,model);await page.getByRole('button',{name:'Edit E1',exact:true}).click();await page.getByRole('spinbutton',{name:'Hourly rate for E1',exact:true}).fill('300');await page.getByRole('link',{name:'Back to Activities',exact:false}).click();const modal=page.getByRole('dialog',{name:'Discard facility changes?',exact:true});await expect(modal).toBeVisible();await modal.getByRole('button',{name:'Cancel',exact:true}).click();await expect(page).toHaveURL(/\/academy\/facilities$/);await expect(page.getByRole('spinbutton',{name:'Hourly rate for E1',exact:true})).toHaveValue('300');await page.getByRole('link',{name:'Back to Activities',exact:false}).click();await modal.getByRole('button',{name:'Discard Changes',exact:true}).click();await expect(page).toHaveURL(/\/academy\/sports$/);expect(native).toBe(0);
});

test('student status and enrollment confirmations preserve existing requests',async({page,model})=>{
 const student:any={id:31,studentCode:'ST-31',firstName:'QA',lastName:'Member',status:'ACTIVE',sportIds:[],version:0};let updates=0,ends=0;let ended=false;
 await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),model.user());
 await page.route('**/api/academy/sports?*',r=>r.fulfill({json:{content:[]}}));
 await page.route('**/api/academy/students?*',r=>r.fulfill({json:{content:[student],totalElements:1,totalPages:1,number:0,size:20}}));
 await page.route('**/api/academy/students/31',async r=>{updates++;expect(r.request().postDataJSON()).toMatchObject({status:'INACTIVE',version:0,firstName:'QA',lastName:'Member',sportIds:[]});student.status='INACTIVE';student.version=1;await r.fulfill({json:student});});
 await page.route('**/api/academy/batches?*',r=>r.fulfill({json:{content:[]}}));
 await page.route('**/api/academy/students/31/enrollments',r=>r.fulfill({json:[{id:41,studentId:31,studentName:'QA Member',studentCode:'ST-31',batchId:51,batchName:'Evening Batch',startDate:'2026-11-01',endDate:null,status:ended?'ENDED':'ACTIVE'}]}));
 await page.route('**/api/academy/enrollments/41/end',async r=>{ends++;expect(r.request().postDataJSON()).toEqual({});ended=true;await r.fulfill({json:{id:41,status:'ENDED'}});});
 await page.goto('/academy/students');await page.getByRole('button',{name:'Deactivate QA',exact:true}).click();const status=page.getByRole('dialog',{name:'Deactivate student?',exact:true});await status.getByRole('button',{name:'Cancel',exact:true}).click();expect(updates).toBe(0);await page.getByRole('button',{name:'Deactivate QA',exact:true}).click();await status.getByRole('button',{name:'Deactivate',exact:true}).click();await expect(status).toHaveCount(0);expect(updates).toBe(1);await expect(page.locator('.ao-toast')).toContainText('Student inactive.');
 await page.getByRole('button',{name:'QA Member',exact:true}).click();await page.getByRole('button',{name:'End',exact:true}).click();const enrollment=page.getByRole('dialog',{name:'End enrollment?',exact:true});await expect(enrollment).toContainText("End QA Member's enrollment in Evening Batch?");expect(ends).toBe(0);await enrollment.getByRole('button',{name:'End Enrollment',exact:true}).click();await expect(enrollment).toHaveCount(0);expect(ends).toBe(1);await expect(page.locator('.enroll-row')).toContainText('ENDED');
});
