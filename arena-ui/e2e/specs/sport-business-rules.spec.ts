import {test,expect} from '../fixtures/dashboard';
const limit='Your club allows only one active sport. Deactivate your current sport before activating another.';
const snooker={id:11,version:0,name:'Snooker',catalogueCode:'SNOOKER',active:true,sessions:true,training:false};
const badminton={...snooker,id:12,name:'Badminton',catalogueCode:'BADMINTON',active:false};
async function signIn(page:any,model:any){await page.addInitScript((user:any)=>localStorage.setItem('currentUser',JSON.stringify(user)),model.user());}

test('single-sport rejection keeps selection and allows explicit inactive registration',async({page,model})=>{
 const sports=[{...snooker}];let payload:any;
 await page.route('**/api/academy/catalogue?*',r=>r.fulfill({json:{content:[{id:1,version:0,code:'BADMINTON',label:'Badminton',facilityKind:'COURT'}],totalPages:1,number:0}}));
 await page.route('**/api/academy/sports?*',r=>r.fulfill({json:{content:sports,totalPages:1,number:0}}));
 await page.route('**/api/academy/sports/bulk',r=>{payload=r.request().postDataJSON();if(payload.activities[0].active!==false)return r.fulfill({status:409,json:{errorCode:'SINGLE_SPORT_LIMIT_EXCEEDED'}});sports.push({...badminton});return r.fulfill({status:201,json:[badminton]});});
 await signIn(page,model);await page.goto('/academy/sports');
 await page.getByRole('button',{name:/Add Activity/}).click();
 await page.locator('.sport-choice').filter({hasText:'Badminton'}).click();
 await page.getByRole('button',{name:'Add Selected',exact:true}).click();
 await expect(page.getByRole('dialog')).toBeVisible();await expect(page.getByRole('alert')).toHaveText(limit);
 await expect(page.locator('.sport-choice').filter({hasText:'Badminton'})).toHaveAttribute('aria-pressed','true');
 await page.getByRole('checkbox',{name:'Register selected activities as inactive',exact:true}).check();
 await page.getByRole('button',{name:'Add Selected',exact:true}).click();
 await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('row').filter({hasText:'Badminton'})).toContainText('Inactive');expect(payload.activities[0].active).toBe(false);
 await expect(page.getByRole('row').filter({hasText:'Snooker'})).toContainText('Active');
});

test('blocked reactivation and unsafe deactivation retain activity status with useful errors',async({page,model})=>{
 await page.route('**/api/academy/catalogue?*',r=>r.fulfill({json:{content:[],totalPages:1,number:0}}));
 await page.route('**/api/academy/sports?*',r=>r.fulfill({json:{content:[snooker,badminton],totalPages:1,number:0}}));
 let blocker='SPORT_SESSION_ACTIVE';
 await page.route('**/api/academy/sports/*',r=>r.fulfill({status:409,json:{errorCode:r.request().url().endsWith('/12')?'SINGLE_SPORT_LIMIT_EXCEEDED':blocker}}));
 await signIn(page,model);await page.goto('/academy/sports');
 const inactive=page.getByRole('row').filter({hasText:'Badminton'});await inactive.getByLabel('More actions for Badminton').click();await inactive.getByRole('button',{name:'Reactivate',exact:true}).click();await expect(page.getByRole('alert')).toHaveText(limit);await expect(inactive).toContainText('Inactive');
 const active=page.getByRole('row').filter({hasText:'Snooker'});await active.getByLabel('More actions for Snooker').click();await active.getByRole('button',{name:'Deactivate',exact:true}).click();
 await page.getByRole('dialog').getByRole('button',{name:'Deactivate',exact:true}).click();await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Running or paused facility sessions');
 blocker='SPORT_RESERVATION_EXISTS';await page.getByRole('dialog').getByRole('button',{name:'Deactivate',exact:true}).click();await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Reserved facility sessions');await expect(active).toContainText('Active');
});

test('inactive sport facilities remain visible read-only and regain controls after reactivation',async({page,model})=>{
 let sport={...snooker,active:false};
 const facilities=[{facilityId:1,registeredSportId:11,code:'E1',facilityType:'ENGLISH',displayName:'Match Table',hourlyRate:220,frameRate:80,active:true,configured:true,version:0},{facilityId:2,registeredSportId:11,code:'E2',facilityType:'ENGLISH',displayName:null,hourlyRate:200,frameRate:70,active:false,configured:true,version:3}];
 await page.route('**/api/v1/academies/current/facilities/context',r=>r.fulfill({json:{academyId:1,sports:[sport]}}));
 await page.route('**/api/v1/academies/1/sports/11/facilities',r=>r.fulfill({json:{facilities}}));
 await page.route('**/api/academy/catalogue?*',r=>r.fulfill({json:{content:[],totalPages:1,number:0}}));
 await page.route('**/api/academy/sports?*',r=>r.fulfill({json:{content:[sport],totalPages:1,number:0}}));
 await page.route('**/api/academy/sports/11',r=>{sport={...sport,active:true,version:1};return r.fulfill({json:sport});});
 await signIn(page,model);await page.goto('/academy/facilities?sportId=11');
 await expect(page.getByRole('heading',{name:'Inactive Sport',exact:true})).toBeVisible();await expect(page.locator('.existing-table-item')).toHaveCount(2);
 await expect(page.locator('.existing-table-item').first()).toContainText('Match Table');await expect(page.locator('.existing-table-item').first()).toContainText('₹220/hr');await expect(page.locator('.existing-table-item').first()).toContainText('₹80/frame');await expect(page.locator('.existing-table-item').nth(1)).toContainText('Inactive');
 await expect(page.getByRole('button',{name:/^(Edit |Deactivate E|Reactivate E|Save Configuration)/})).toHaveCount(0);await expect(page.locator('.facility-generator')).toHaveCount(0);
 await page.getByRole('link',{name:'Configure Sport',exact:true}).click();await page.getByLabel('More actions for Snooker').click();await page.getByRole('button',{name:'Reactivate',exact:true}).click();await expect(page.getByText('Activity reactivated.',{exact:true})).toBeVisible();
 await page.goto('/academy/facilities?sportId=11');await expect(page.locator('.facility-generator')).toBeHidden();await expect(page.getByRole('button',{name:'+ Add Tables',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Edit E1',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Deactivate E1',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Reactivate E2',exact:true})).toBeVisible();await expect(page.locator('.existing-table-item').nth(1)).toContainText('Inactive');
});
