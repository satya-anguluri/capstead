import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const context=vm.createContext({Date,Uint8Array,Promise});
vm.runInContext(readFileSync(new URL('../website/site-events.js',import.meta.url),'utf8'),context);
const create=context.CapsteadSiteEvents.create;
function setup(){const store=new Map(),calls=[];return {calls,store,location:{hostname:'capstead.io',pathname:'/research/',search:'?email=private',hash:'#private'},navigator:{},crypto:{getRandomValues(bytes){bytes.fill(17);}},localStorage:{getItem(k){return store.get(k);},setItem(k,v){store.set(k,v);}},fetch(url,options){calls.push({url,options});return Promise.resolve({status:204});}};}
const env=setup(),track=create(env);assert.equal(track('page_view'),true);assert.equal(track('arbitrary'),false);
const call=env.calls[0],body=JSON.parse(call.options.body);assert.deepEqual(Object.keys(body),['v','event','path','session']);assert.equal(body.path,'/research');assert.equal(body.session.length,32);
assert.equal(call.options.credentials,'omit');assert.doesNotMatch(call.options.body,/private|email|referrer/);assert.equal(call.url,'https://app.capstead.io/site-events');
create(env)('page_view');assert.equal(JSON.parse(env.calls[1].options.body).session,body.session);
for(const privacy of [{doNotTrack:'1'},{globalPrivacyControl:true}]){const e=setup();e.navigator=privacy;assert.equal(create(e)('page_view'),false);assert.equal(e.calls.length,0);assert.equal(e.store.size,0);}
for(const change of [{hostname:'localhost',pathname:'/'},{hostname:'app.capstead.io',pathname:'/'},{hostname:'capstead.io',pathname:'/private/person'}]){const e=setup();e.location=change;assert.equal(create(e)('page_view'),false);assert.equal(e.calls.length,0);}
const blocked=setup();blocked.localStorage={getItem(){throw Error('blocked');},setItem(){throw Error('blocked');}};assert.equal(create(blocked)('page_view'),true);
const old=setup();old.store.set('capstead-site-day',JSON.stringify({day:'2000-01-01',token:'a'.repeat(32)}));create(old)('page_view');assert.notEqual(JSON.parse(old.calls[0].options.body).session,'a'.repeat(32));
const failed=setup();failed.fetch=()=>Promise.reject(Error('offline'));assert.equal(create(failed)('page_view'),true);await new Promise(resolve=>setTimeout(resolve,0));
console.log('Website event collection: privacy, daily identity, path/event bounds and failure isolation passed');
