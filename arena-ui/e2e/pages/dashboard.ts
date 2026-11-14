import {Page,expect} from '@playwright/test';
export class DashboardPage {
 constructor(readonly page:Page){}
 tab(label:string){return this.page.getByRole('navigation',{name:'Dashboard views'}).getByRole('button',{name:new RegExp('^'+label)});}
 async select(label:string){await this.tab(label).click();}
 metric(label:string){return this.page.locator('.snapshot-grid article').filter({has:this.page.getByText(label,{exact:true})}).locator('strong');}
 facility(name='English One'){return this.page.locator('.facility-row').filter({has:this.page.getByRole('heading',{name:new RegExp(name)})});}
 async manual(name='',phone=''){await this.facility().getByRole('button',{name:'Start session',exact:true}).click();const form=this.page.locator('form.dialog').filter({has:this.page.getByRole('heading',{name:'Start English One',exact:true})});await expect(form).toBeVisible();await form.locator('[name=playerName]').fill(name);if(phone)await form.locator('[name=playerMobile]').fill(phone);return form;}
 async stop(){await this.select('Active floor');await this.page.getByRole('button',{name:'Stop session',exact:true}).first().click();const dialog=this.page.getByRole('dialog');await expect(dialog.locator('.final-amount')).toBeVisible();return dialog;}
}
