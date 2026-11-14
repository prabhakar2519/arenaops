import {BarcodeFormat,QRCodeWriter} from '@zxing/library';

/** Preserve the permanent public identifier and server-configured application destination. */
export function facilityCheckinUrl(raw:string,origin=window.location.origin,env=window.ARENAOPS_CONFIG?.arenaEnv):string {
 const base=new URL(origin),issued=new URL(raw,base);
 if(!['http:','https:'].includes(issued.protocol)||issued.username||issued.password||issued.pathname!=='/check-in'||issued.search||!/^#[A-Za-z0-9_-]{43}$/.test(issued.hash))throw Error('Invalid check-in URL');
 if(raw.startsWith('/')){if(!raw.startsWith('/check-in#'))throw Error('Invalid check-in URL');return issued.href;}
 // Absolute destinations are server-configured, independent of the viewing browser host.
 return issued.href;
}
export function facilityQrImage(url:string,size=320):string {
 const matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,size,size,new Map());const canvas=document.createElement('canvas');canvas.width=matrix.getWidth();canvas.height=matrix.getHeight();const ctx=canvas.getContext('2d');if(!ctx)throw Error('QR display unavailable');const data=ctx.createImageData(canvas.width,canvas.height);
 for(let y=0;y<canvas.height;y++)for(let x=0;x<canvas.width;x++){const i=(y*canvas.width+x)*4,v=matrix.get(x,y)?0:255;data.data[i]=data.data[i+1]=data.data[i+2]=v;data.data[i+3]=255;}ctx.putImageData(data,0,0);return canvas.toDataURL('image/png');
}
/** Print a single facility, without navigating away from unsaved rate drafts or opening a popup. */
export function printFacilityQr(image:string,label:string):Promise<void> {
 const frame=document.createElement('iframe');frame.title=`Print QR for ${label}`;frame.dataset['facilityQrPrint']='true';frame.style.cssText='position:fixed;left:-10000px;width:1px;height:1px;border:0';document.body.appendChild(frame);
 const doc=frame.contentDocument!,view=frame.contentWindow!;const heading=doc.createElement('h1');heading.textContent=label;const img=doc.createElement('img');img.src=image;img.alt=`Check-in QR for ${label}`;img.width=320;img.height=320;const note=doc.createElement('p');note.textContent='Scan to check in. Staff will start your session.';doc.body.style.cssText='font:14px Inter,system-ui,sans-serif;color:#000;background:#fff;text-align:center';doc.body.append(heading,img,note);const style=doc.createElement('style');style.textContent=`@font-face{font-family:Inter;src:url('${window.location.origin}/fonts/Inter-variable.ttf') format('truetype');font-weight:100 900}body{font-family:Inter,system-ui,sans-serif}`;doc.head.appendChild(style);
 const cleanup=()=>frame.remove();view.addEventListener('afterprint',cleanup,{once:true});setTimeout(cleanup,60000);
 return Promise.all([img.decode(),doc.fonts.load('14px Inter')]).then(()=>{view.focus();view.print();}).catch(error=>{cleanup();throw error;});
}
