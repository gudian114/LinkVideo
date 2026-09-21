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
        document:{readyState:'loading'}, XMLHttpRequest:Xhr, fetch:network});
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
