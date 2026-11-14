import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve('dist/arena-ui/browser');
if(!fs.existsSync(path.join(root,'index.html')))throw Error('Run npm run build before E2E.');
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.woff2':'font/woff2','.json':'application/json'};
http.createServer((req,res)=>{let pathname;try{pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);}catch{res.writeHead(400).end();return;}let file=path.resolve(root,'.'+pathname);if(!file.startsWith(root+path.sep)){file=path.join(root,'index.html');}if(!fs.existsSync(file)||!fs.statSync(file).isFile())file=path.join(root,'index.html');res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.setHeader('Cache-Control','no-store');fs.createReadStream(file).pipe(res);}).listen(Number(process.env.E2E_PORT||4317),'127.0.0.1');
