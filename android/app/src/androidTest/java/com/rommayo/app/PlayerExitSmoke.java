package com.rommayo.app;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Host integration regression; runs in the isolated diagnostic app without ROMs/accounts. */
final class PlayerExitSmoke {
    private static final String FIXTURE="(()=>{document.body.innerHTML='<div id=\"engine\" tabindex=\"-1\" style=\"position:relative;height:300px\"><div class=\"ejs_virtualGamepad_parent\">Touch D-pad</div><button id=\"toolbar\">Save state</button></div>';const parent=document.getElementById('engine'),pad=parent.firstElementChild,events={};window.EJS_emulator={started:true,elements:{parent},virtualGamepad:pad,toggleVirtualGamepad:show=>pad.style.display=show?'':'none',on:(name,callback)=>(events[name]??=[]).push(callback),callEvent:name=>(events[name]??[]).forEach(callback=>callback())};})()";
    private static Object get(Object object,String name)throws Exception{java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static String js(Instrumentation test,WebView web,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] answer={null};
        test.runOnMainSync(()->web.evaluateJavascript(script,value->{answer[0]=value;done.countDown();}));
        if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("Player script timed out");return answer[0];
    }
    static void run(Instrumentation test,MainActivity activity)throws Exception{
        java.net.ServerSocket socket=new java.net.ServerSocket(0,5,java.net.InetAddress.getByName("127.0.0.1"));
        Thread fixtureServer=new Thread(()->{while(!socket.isClosed())try(java.net.Socket client=socket.accept()){
            java.io.BufferedReader input=new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream()));String line;while((line=input.readLine())!=null&&!line.isEmpty()){}
            byte[] body=("<html><body><script>"+FIXTURE+"</script></body></html>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.io.OutputStream output=client.getOutputStream();output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(body);
        }catch(Exception ignored){}});fixtureServer.setDaemon(true);fixtureServer.start();
        java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        test.runOnMainSync(()->{try{
            set(activity,"server","http://127.0.0.1:"+socket.getLocalPort()+"/"); activity.openWeb("");
        }catch(Throwable failure){error.set(failure);}});
        if(error.get()!=null)throw new AssertionError(error.get());
        WebView web=(WebView)get(activity,"remote");
        boolean ready=false;for(int i=0;i<100;i++){if("true".equals(js(test,web,"!!document.querySelector('.mayo-touch-toggle')"))){ready=true;break;}SystemClock.sleep(50);}
        if(!ready)throw new AssertionError("Touch toggle did not attach in Android WebView: "+js(test,web,"document.body.innerText"));
        if(!"true".equals(js(test,web,"(()=>{const b=document.querySelector('.mayo-touch-toggle');if(b.textContent.startsWith('Show'))b.click();b.click();return getComputedStyle(document.querySelector('.ejs_virtualGamepad_parent')).display==='none'&&getComputedStyle(document.getElementById('toolbar')).display!=='none'&&b.textContent==='Show touch controls'})()")))throw new AssertionError("Touch toggle hid toolbar or failed to hide gamepad");
        js(test,web,"window.rommayoPlayer.postMessage('unknown')");SystemClock.sleep(100);
        if(get(activity,"remote")!=web)throw new AssertionError("Unknown player message closed the host");
        js(test,web,"window.EJS_emulator.callEvent('exit')");
        boolean closed=false;for(int i=0;i<100;i++){test.waitForIdleSync();if(get(activity,"remote")==null){closed=true;break;}SystemClock.sleep(30);}
        if(!closed)throw new AssertionError("Confirmed EmulatorJS exit did not return to native library");
        socket.close();
    }
}
