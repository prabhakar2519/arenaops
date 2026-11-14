// Synchronous confirmation adapter for existing component unit tests; browser behavior is tested separately.
import {defer,of,map,catchError} from 'rxjs';
export const feedbackURL='data:text/javascript;base64,'+Buffer.from('export class UiFeedbackService {}').toString('base64');
export const {UiFeedbackService}=await import(feedbackURL);
export const feedbackStub={
 confirm(options){if(options.action)return defer(options.action).pipe(map(()=>true),catchError(()=>of(false)));return of(globalThis.window?.confirm?.()??true);},
 success(){},failure(){},notify(){}
};
