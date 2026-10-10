// Executes the generated production JavaScript with deterministic browser/network boundaries.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const script = fs.readFileSync(process.argv[2], 'utf8');
const id = '7685159514829025465';
const api = 'https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id='+id+'&aid=1128&app_name=aweme';
const body = JSON.stringify({status_code:0, aweme_detail:{aweme_id:id, desc:'public video'}});
function fixture(network) {
    let time = 0;
    function Xhr() { this.listeners = {}; }
    Xhr.prototype.open = function(method, url) { this.url = url; };
    Xhr.prototype.addEventListener = function(name, listener) { this.listeners[name] = listener; };
    Xhr.prototype.send = function() {};
    const context = vm.createContext({URL, JSON, Date:{now:()=>time}, AbortController,
        location:{href:'https://www.douyin.com/video/'+id},innerWidth:1080,innerHeight:1920,
        getComputedStyle:node=>node.style||{display:'block',visibility:'visible',opacity:'1'},
        document:{readyState:'loading',body:{innerText:''},querySelectorAll:()=>[]},
        XMLHttpRequest:Xhr, fetch:network});
    context.window = context;
    return {context, advance(ms){time+=ms;}, run(){const r=vm.runInContext(script,context);return r?JSON.parse(r):null;}};
}
function response(text, status=200, url=api) { return {status,url,text:async()=>text,clone(){return response(text,status,url);}}; }
async function settle() { for(let n=0;n<12;n++) await Promise.resolve(); }

test('an unfinished fetch reaches its own deadline instead of remaining pending forever', async()=>{
    const f=fixture(()=>new Promise(()=>{}));
    f.context.document.readyState='complete';
    f.run();f.advance(3000);f.run();f.advance(7000);
    const result=f.run();
    assert.ok(result && result.requestTimeout, 'network request must expose its timeout');
});

test('captures the page own response before document load completes, even while manual fetch cannot finish',async()=>{
    const f=fixture(async()=>response(body));
    f.run(); // Install observation while document is still loading.
    await f.context.fetch(api);await settle();
    const result=f.run();
    assert.equal(result.status,200);
    assert.equal(JSON.parse(result.body).aweme_detail.aweme_id,id);
    assert.equal(result.source,'page-fetch');
});

test('captures official XHR and ignores other recommended work IDs',async()=>{
    const f=fixture(()=>new Promise(()=>{}));f.run();
    const xhr = new f.context.XMLHttpRequest();xhr.open('GET',api);xhr.send();
    xhr.status=200;xhr.responseText=body;xhr.listeners.load();
    assert.equal(f.run().source,'page-xhr');
    const other=fixture(async()=>response(body.replace(id,'7685159514829025466')));
    other.run();await other.context.fetch(api);await settle();
    assert.ok(!other.run().body, 'recommended video must not replace the requested work');
});

test('a late timed out request cannot overwrite the current result',async()=>{
    let firstResolve, calls=0;
    const fresh=body.replace('public video','fresh');
    const stale=body.replace('public video','stale');
    const f=fixture(()=>++calls===1?new Promise(r=>firstResolve=r):Promise.resolve(response(fresh)));
    f.context.document.readyState='complete';f.run();f.advance(3000);f.run();f.advance(7000);f.run();
    f.advance(2000);f.run();await settle();
    firstResolve(response(stale,200));await settle();
    assert.equal(JSON.parse(f.run().body).aweme_detail.desc,'fresh');
});

test('telemetry never returns unrelated response text',async()=>{
    const f=fixture(async()=>response('private-unrelated-response',403));
    f.run();await f.context.fetch('https://example.org/aweme/detail/?secret=token');await settle();
    assert.ok(!JSON.stringify(f.run()).includes('private-unrelated-response'));
});

test('a 403 from the helper request does not claim that user interaction is required',async()=>{
    const f=fixture(async()=>response('{}',403));
    f.context.document.readyState='complete';f.run();f.advance(3000);f.run();await settle();
    assert.equal(f.run().interactionRequired,false);
});

test('exact persistent verification text is reported without returning page text',()=>{
    const f=fixture(()=>new Promise(()=>{}));
    f.context.document.body.innerText='请完成下列验证后继续\n按住左边按钮拖动完成上方拼图';
    const dialog={style:{display:'block',visibility:'visible',opacity:'1'},getAttribute:()=>null,parentElement:null,
        innerText:f.context.document.body.innerText,getBoundingClientRect:()=>({left:20,top:20,right:640,bottom:620,width:620,height:600})};
    f.context.document.querySelectorAll=selector=>selector.includes('role=')?[dialog]:[];
    const result=f.run();
    assert.equal(result.interactionRequired,true);
    assert.ok(!JSON.stringify(result).includes('按住左边按钮'));
});

test('a visible captcha frame is reported as requiring interaction',()=>{
    const f=fixture(()=>new Promise(()=>{}));
    const frame={style:{display:'block',visibility:'visible',opacity:'1'},parentElement:null,
        getAttribute:name=>name==='src'?'https://verify.douyin.com/captcha/puzzle':null,
        getBoundingClientRect:()=>({left:20,top:20,right:340,bottom:260,width:320,height:240})};
    f.context.document.querySelectorAll=selector=>selector==='iframe[src]'?[frame]:[];
    assert.equal(f.run().interactionRequired,true);
});

test('a visible iframe with only a verifyFp query parameter is not verification',()=>{
    const f=fixture(()=>new Promise(()=>{}));
    const frame={style:{display:'block',visibility:'visible',opacity:'1'},parentElement:null,
        getAttribute:name=>name==='src'?'https://www.douyin.com/video/'+id+'?verifyFp=visitor-token':null,
        getBoundingClientRect:()=>({left:20,top:20,right:900,bottom:700,width:880,height:680})};
    f.context.document.querySelectorAll=selector=>selector==='iframe[src]'?[frame]:[];
    assert.equal(f.run().interactionRequired,false);
});

test('ordinary work URL verifyFp parameter is not a verification page',()=>{
    const f=fixture(()=>new Promise(()=>{}));
    f.context.location.href='https://www.douyin.com/video/'+id+'?verifyFp=visitor-token';
    assert.equal(f.run().interactionRequired,false);
});

test('hidden or offscreen captcha nodes do not require interaction',()=>{
    const cases=[
        {style:{display:'none',visibility:'visible',opacity:'1'},rect:{left:0,top:0,right:320,bottom:240,width:320,height:240},hidden:null},
        {style:{display:'block',visibility:'hidden',opacity:'1'},rect:{left:0,top:0,right:320,bottom:240,width:320,height:240},hidden:null},
        {style:{display:'block',visibility:'visible',opacity:'0'},rect:{left:0,top:0,right:320,bottom:240,width:320,height:240},hidden:null},
        {style:{display:'block',visibility:'visible',opacity:'1'},rect:{left:1200,top:0,right:1520,bottom:240,width:320,height:240},hidden:null},
        {style:{display:'block',visibility:'visible',opacity:'1'},rect:{left:0,top:0,right:320,bottom:240,width:320,height:240},hidden:'true'}
    ];
    for(const value of cases){
        const f=fixture(()=>new Promise(()=>{}));
        f.context.document.querySelectorAll=()=>[{style:value.style,
            getAttribute:name=>name==='aria-hidden'?value.hidden:null,getBoundingClientRect:()=>value.rect}];
        assert.equal(f.run().interactionRequired,false);
    }
});

test('verification text inside a parent-hidden captcha is not actionable',()=>{
    const f=fixture(()=>new Promise(()=>{}));
    f.context.document.body.innerText='请完成下列验证后继续';
    const parent={style:{display:'block',visibility:'visible',opacity:'0'},getAttribute:()=>null,parentElement:null};
    const child={style:{display:'block',visibility:'visible',opacity:'1'},getAttribute:()=>null,parentElement:parent,
        innerText:'请完成下列验证后继续',getBoundingClientRect:()=>({left:20,top:20,right:340,bottom:260,width:320,height:240})};
    f.context.document.querySelectorAll=()=>[child];
    assert.equal(f.run().interactionRequired,false);
});

test('exact verification text in a visible dialog is actionable',()=>{
    const f=fixture(()=>new Promise(()=>{}));
    const dialog={style:{display:'block',visibility:'visible',opacity:'1'},getAttribute:()=>null,parentElement:null,
        innerText:'请完成下列验证后继续',getBoundingClientRect:()=>({left:20,top:20,right:640,bottom:620,width:620,height:600})};
    f.context.document.querySelectorAll=selector=>selector.includes('role=')?[dialog]:[];
    assert.equal(f.run().interactionRequired,true);
});
