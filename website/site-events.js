/* Approximate first-party counts. No URL queries, fragments, referrers, cookies or credentials. */
(function(root){
    'use strict';
    const paths=new Set(['/','/research','/changelog','/pilot','/walkthrough']);
    const events=new Set(['page_view','walkthrough_start','walkthrough_complete','pilot_cta_click']);
    const key='capstead-site-day';
    function create(env){
        if(!['capstead.io','www.capstead.io'].includes(env.location.hostname)
            || env.navigator.doNotTrack==='1' || env.navigator.globalPrivacyControl===true)return ()=>false;
        const path=env.location.pathname.replace(/\/+$/,'')||'/';
        if(!paths.has(path))return ()=>false;
        let identity;
        function session(){
            const day=new Date().toISOString().slice(0,10);
            if(identity?.day===day)return identity.token;
            let stored;
            try{stored=JSON.parse(env.localStorage.getItem(key)||'null');}catch(_){}
            if(stored?.day===day && /^[0-9a-f]{32}$/.test(stored.token))identity=stored;
            else{const bytes=new Uint8Array(16);env.crypto.getRandomValues(bytes);identity={day,token:Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('')};
                try{env.localStorage.setItem(key,JSON.stringify(identity));}catch(_){}
            }
            return identity.token;
        }
        return function track(event){
            if(!events.has(event))return false;
            try{const pending=env.fetch('https://app.capstead.io/site-events',{method:'POST',mode:'cors',credentials:'omit',keepalive:true,
                headers:{'Content-Type':'application/json'},body:JSON.stringify({v:1,event,path,session:session()})});
                Promise.resolve(pending).catch(()=>{});return true;
            }catch(_){return false;}
        };
    }
    root.CapsteadSiteEvents=Object.freeze({create});
    if(root.document){const track=create(root);track('page_view');
        root.document.addEventListener('click',event=>{const target=event.target?.closest?.('[data-site-event]');if(target)track(target.getAttribute('data-site-event'));});
    }
})(globalThis);
