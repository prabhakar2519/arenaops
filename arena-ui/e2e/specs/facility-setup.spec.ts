import {test,expect} from '../fixtures/dashboard';

const snooker={id:11,version:0,name:'Snooker',catalogueCode:'SNOOKER',active:true,sessions:true,training:false};
const badminton={...snooker,id:12,name:'Badminton',catalogueCode:'BADMINTON'};
const states=[
 {name:'no registered sports',sports:[],title:'No sports registered yet',description:'Register a sport or activity before adding your first facility.',action:'Add Sport'},
 {name:'inactive sport',sports:[{...snooker,active:false}],title:'Inactive Sport',description:'Snooker is inactive. Existing tables are available for viewing only. Reactivate Snooker to manage these facilities.',action:'Configure Sport'},
 {name:'disabled facility access',sports:[{...snooker,sessions:false}],title:'Sport configuration required',description:'Play & Facility Access is disabled for Snooker. Enable it in Sports & Activities before adding facilities.',action:'Configure Sport'},
 {name:'inactive and disabled sport',sports:[{...snooker,active:false,sessions:false}],title:'Inactive Sport',description:'Snooker is inactive. Existing tables are available for viewing only. Reactivate Snooker to manage these facilities. Play & Facility Access is also disabled. Enable it after reactivating the sport.',action:'Configure Sport'},
 {name:'unsupported sport',sports:[{...snooker,catalogueCode:'CRICKET',name:'Cricket'}],title:'Coming soon',description:'Facilities for Cricket will be available in a future release. Snooker facility management is available now.',action:null},
 {name:'eligible sport',sports:[snooker],title:null,description:null,action:null}
];
for(const state of states)for(const entry of ['dashboard','direct'] as const)test(`ENH-007 ${state.name} via ${entry}`,async({page,model})=>{
 model.facilities=[];
 await page.route('**/api/v1/academies/current/facilities/context',r=>r.fulfill({json:{academyId:1,sports:state.sports}}));
 await page.route('**/api/academy/sports?*',r=>r.fulfill({json:{content:state.sports,totalPages:1,number:0}}));
 await page.route('**/api/academy/catalogue?*',r=>r.fulfill({json:{content:[],totalPages:1,number:0}}));
 await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),model.user());
 await page.goto(entry==='direct'?'/academy/facilities':'/academy/dashboard');
 if(entry==='dashboard')await page.getByRole('link',{name:'Add Facility',exact:true}).click();
 await expect(page).toHaveURL(/\/academy\/facilities$/);
 await expect(page.getByText('Loading facilities…',{exact:true})).toHaveCount(0);
 if(state.title){
  await expect(page.getByRole('heading',{name:state.title,exact:true})).toBeVisible();
  await expect(page.getByText(state.description!,{exact:true})).toBeVisible();
  await expect(page.locator('.facility-generator')).toHaveCount(0);
  await expect(page.getByText('Set a count above and save your configuration.',{exact:false})).toHaveCount(0);
  if(!state.action){await expect(page.getByRole('tab',{name:'Tables & Rates',exact:true})).toHaveCount(0);return;}
  await page.getByRole('link',{name:state.action!,exact:true}).click();
  await expect(page).toHaveURL(/\/academy\/sports$/);
  await expect(page.getByRole('heading',{name:'Sports & Activities',exact:true})).toBeVisible();
 }else{
  await expect(page.getByRole('heading',{name:'Manage Tables & Rates',exact:true})).toBeVisible();
  await expect(page.locator('.facility-generator')).toBeVisible();
  await expect(page.locator('#quantity-0')).toBeVisible();
  await expect(page.getByRole('button',{name:'Save Configuration',exact:true})).toBeVisible();
  await expect(page.locator('.empty-state')).toHaveCount(0);
 }
 expect(model.calls.filter(c=>c.method!=='GET')).toHaveLength(0);
});

test('ENH-007 default eligible sport and explicit inactive sport preserve historical facilities',async({page,model})=>{
 const historical={facilityId:1,registeredSportId:11,code:'E1',facilityType:'ENGLISH',displayName:null,hourlyRate:220,frameRate:null,active:true,configured:true,version:0};
 await page.route('**/api/v1/academies/current/facilities/context',r=>r.fulfill({json:{academyId:1,sports:[{...snooker,active:false},badminton]}}));
 await page.route('**/api/v1/academies/1/sports/*/facilities',r=>r.fulfill({json:{facilities:r.request().url().includes('/sports/11/')?[historical]:[]}}));
 await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),model.user());
 await page.goto('/academy/facilities');
 await expect(page.getByRole('heading',{name:'Manage Tables & Rates',exact:true})).toBeVisible();
 await expect(page.locator('.existing-table-item')).toContainText('E1');
 await page.goto('/academy/facilities?sportId=11');
 await expect(page.getByText('Snooker is inactive. Existing tables are available for viewing only. Reactivate Snooker to manage these facilities.',{exact:true})).toBeVisible();
 await expect(page.locator('.existing-table-item')).toContainText('E1');
 await expect(page.getByRole('button',{name:'Deactivate E1',exact:true})).toHaveCount(0);
 await expect(page.locator('.facility-generator')).toHaveCount(0);
 await page.getByRole('button',{name:'Badminton',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Coming soon',exact:true})).toBeVisible();
 await expect(page.locator('.existing-table-item')).toHaveCount(0);
 await expect(page.locator('.facility-generator')).toHaveCount(0);
});

for(const code of ['BADMINTON','CRICKET'])test(`MVP ${code} facilities stay locked via direct link and sport selection`,async({page,model})=>{const future={...badminton,catalogueCode:code,name:code==='BADMINTON'?'Badminton':'Cricket'};const calls:string[]=[];await page.route('**/api/v1/academies/current/facilities/context',r=>r.fulfill({json:{academyId:1,sports:[snooker,future]}}));await page.route('**/api/v1/academies/1/sports/*/facilities',r=>{calls.push(r.request().url());return r.fulfill({json:{facilities:[]}});});await page.addInitScript(user=>localStorage.setItem('currentUser',JSON.stringify(user)),model.user());await page.goto('/academy/facilities?sportId=12');await expect(page.getByRole('heading',{name:'Coming soon',exact:true})).toBeVisible();await expect(page.getByRole('tab',{name:'Tables & Rates',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'Save Configuration',exact:true})).toHaveCount(0);expect(calls).toHaveLength(0);await page.getByRole('button',{name:'Snooker',exact:true}).click();await expect(page.locator('#quantity-0')).toBeVisible();await page.getByRole('button',{name:future.name,exact:true}).click();await expect(page.getByRole('heading',{name:'Coming soon',exact:true})).toBeVisible();expect(calls.every(url=>url.includes('/sports/11/'))).toBe(true);await page.reload();await expect(page.getByRole('heading',{name:'Coming soon',exact:true})).toBeVisible();});
