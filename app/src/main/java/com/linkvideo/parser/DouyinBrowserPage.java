package com.linkvideo.parser;

import java.net.URI;
import org.json.JSONObject;

final class DouyinBrowserPage {
    final String pageUrl;
    final String requestUrl;
    final String itemId;

    DouyinBrowserPage(String pageUrl, String requestUrl) throws Exception {
        URI page = new URI(pageUrl);
        URI request = new URI(requestUrl);
        if (!allowed(page) || !allowed(request) || !page.getPath().matches("/(video|note)/[0-9]{15,20}")
                || !"/aweme/v1/web/aweme/detail/".equals(request.getPath())) {
            throw new IllegalArgumentException("网页解析地址不受支持");
        }
        itemId = page.getPath().substring(page.getPath().lastIndexOf('/') + 1);
        if (!("aweme_id=" + itemId + "&aid=1128&app_name=aweme").equals(request.getRawQuery())) {
            throw new IllegalArgumentException("网页解析作品编号不匹配");
        }
        this.pageUrl = pageUrl;
        this.requestUrl = requestUrl;
    }

    static boolean allowed(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && "www.douyin.com".equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    boolean hasItem(String body) {
        try {
            JSONObject data = new JSONObject(body);
            JSONObject item = data.optJSONObject("aweme_detail");
            return data.optInt("status_code", -1) == 0 && item != null && itemId.equals(item.optString("aweme_id", ""));
        } catch (Exception ignored) { return false; }
    }

    String script() {
        // Observe the official page's requests; return only the requested work, never arbitrary page data.
        return String.join("\n",
                "(function(){var id=" + JSONObject.quote(itemId) + ",api=" + JSONObject.quote(requestUrl) + ";",
                "var k='__linkvideo_reader_'+id,s=window[k],now=Date.now();",
                "if(!s){s=window[k]={started:now,requests:0,active:null,result:null,status:0,next:0,requestTimeout:false};",
                "function target(url){try{var u=new URL(url,api);return u.origin==='https://www.douyin.com'&&u.pathname==='/aweme/v1/web/aweme/detail/'&&u.searchParams.get('aweme_id')===id;}catch(e){return false;}}",
                "function accept(body,source){if(typeof body!=='string'||body.length>2097152)return;try{var d=JSON.parse(body);",
                "if(d.status_code===0&&d.aweme_detail&&d.aweme_detail.aweme_id===id)s.result={status:200,body:body,source:source};}catch(e){}}",
                "s.visible=function(n){try{for(var p=n;p&&p!==document;p=p.parentElement){var a=p.getAttribute&&p.getAttribute('aria-hidden');if(a==='true')return false;",
                "var c=typeof getComputedStyle==='function'?getComputedStyle(p):null;if(c&&(c.display==='none'||c.visibility==='hidden'||c.visibility==='collapse'||parseFloat(c.opacity||'1')<=0.05))return false;}",
                "var r=n.getBoundingClientRect(),w=typeof innerWidth==='number'?innerWidth:2147483647,h=typeof innerHeight==='number'?innerHeight:2147483647;",
                "return r.width>=120&&r.height>=80&&r.right>0&&r.bottom>0&&r.left<w&&r.top<h;}catch(e){return false;}};",
                "s.hasPrompt=function(n){try{var text=n&&(n.innerText||n.textContent)||'';return text.indexOf('请完成下列验证后继续')>=0||text.indexOf('拖动完成上方拼图')>=0||text.indexOf('拖动滑块完成拼图')>=0;}catch(e){return false;}};",
                "s.verificationUrl=function(value){try{var u=new URL(String(value||''),api),path=u.pathname.toLowerCase(),host=u.hostname.toLowerCase();",
                "return /(^|\\/)(captcha|verify|challenge)(\\/|$)/.test(path)||/(^|\\.)(captcha|verify)\\./.test(host);}catch(e){return false;}};",
                "s.interaction=function(){try{var path=typeof location==='undefined'?'':new URL(String(location.href||''),api).pathname.toLowerCase();",
                "if(/(^|\\/)(captcha|verify|challenge)(\\/|$)/.test(path))return true;if(typeof document.querySelectorAll!=='function')return false;",
                "var nodes=document.querySelectorAll('#captcha_container,.captcha_verify_container,.secsdk-captcha-wrapper,[id*=\"captcha\"],[class*=\"captcha\"]');",
                "for(var i=0;i<nodes.length;i++)if(s.visible(nodes[i]))return true;",
                "var frames=document.querySelectorAll('iframe[src]');for(var f=0;f<frames.length;f++){var src=frames[f].getAttribute&&frames[f].getAttribute('src');if(s.visible(frames[f])&&s.verificationUrl(src))return true;}",
                "var dialogs=document.querySelectorAll('[role=\"dialog\"],[class*=\"modal\"],[class*=\"verify\"]');",
                "for(var j=0;j<dialogs.length;j++)if(s.visible(dialogs[j])&&s.hasPrompt(dialogs[j]))return true;}catch(e){}return false;}",
                "s.accept=accept;",
                "if(typeof window.fetch==='function'){var original=window.fetch;s.fetch=original;window.fetch=function(){var args=arguments,p=original.apply(this,args);",
                "var url=args[0]&&args[0].url||args[0];if(target(url))p.then(function(res){if(res.status===200)return res.clone().text().then(function(b){accept(b,'page-fetch');});}).catch(function(){});return p;};}",
                "if(window.XMLHttpRequest){var proto=window.XMLHttpRequest.prototype,open=proto.open,send=proto.send;",
                "proto.open=function(method,url){this[k]=target(url);return open.apply(this,arguments);};",
                "proto.send=function(){var xhr=this;if(xhr[k])xhr.addEventListener('load',function(){try{if(xhr.status===200)accept(xhr.responseType==='json'?JSON.stringify(xhr.response):xhr.responseText,'page-xhr');}catch(e){}});return send.apply(this,arguments);};}",
                "}",
                "if(s.result)return JSON.stringify(s.result);",
                "if(s.active&&now-s.active.at>=6000){var expired=s.active;s.active=null;s.requestTimeout=true;s.next=now+1000;if(expired.controller)expired.controller.abort();}",
                "if(!s.active&&s.requests<2&&now>=s.next&&now-s.started>=2500&&document.readyState!=='loading'){",
                "var request={at:now,controller:typeof AbortController==='function'?new AbortController():null};s.active=request;s.requests++;",
                "var options={credentials:'include'};if(request.controller)options.signal=request.controller.signal;",
                "try{s.fetch.call(window,api,options).then(function(res){return res.text().then(function(body){if(s.active!==request)return;s.active=null;s.status=res.status;s.next=Date.now()+1000;s.accept(body,'direct');});})",
                ".catch(function(){if(s.active!==request)return;s.active=null;s.status=0;s.next=Date.now()+1000;});}catch(e){s.active=null;s.status=0;s.scriptError=true;}",
                "}",
                "return JSON.stringify({pending:true,ready:document.readyState,requests:s.requests,status:s.status,requestTimeout:s.requestTimeout,scriptError:!!s.scriptError,interactionRequired:s.interaction()});})()");
    }
}
