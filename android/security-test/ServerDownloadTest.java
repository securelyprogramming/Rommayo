package com.rommayo.app;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
public class ServerDownloadTest {
    public static void main(String[] args) throws Exception {
        HttpServer source=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0), other=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger leaked=new AtomicInteger(); AtomicReference<String> cookie=new AtomicReference<>();
        byte[] bytes="authorized download fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        other.createContext("/capture",e->{leaked.incrementAndGet();e.sendResponseHeaders(200,0);e.close();});other.start();
        source.createContext("/file",e->{cookie.set(e.getRequestHeaders().getFirst("Cookie"));e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});
        source.createContext("/redirect",e->{e.getResponseHeaders().add("Location","http://127.0.0.1:"+other.getAddress().getPort()+"/capture");e.sendResponseHeaders(302,-1);e.close();});source.start();
        var target=Files.createTempFile("mayo-security-",".download").toFile();String base="http://127.0.0.1:"+source.getAddress().getPort();
        try {
            ServerDownload.fetch(base+"/file",base,"session=dummy-fixture","Test",target,()->false);
            if(!java.util.Arrays.equals(Files.readAllBytes(target.toPath()),bytes)||!"session=dummy-fixture".equals(cookie.get()))throw new AssertionError("Authenticated direct download failed");
            try{ServerDownload.fetch(base+"/redirect",base,"session=dummy-fixture","Test",target,()->false);throw new AssertionError("Redirect accepted");}catch(IOException expected){}
            try{ServerDownload.fetch("http://127.0.0.1:"+other.getAddress().getPort()+"/capture",base,"session=dummy-fixture","Test",target,()->false);throw new AssertionError("Different origin accepted");}catch(IOException expected){}
            if(leaked.get()!=0)throw new AssertionError("Cookie destination was contacted");
            try{ServerDownload.fetch(base+"/file",base,"dummy","Test",target,()->true);throw new AssertionError("Cancellation ignored");}catch(IOException expected){}
            System.out.println("PASS: direct download, redirect refusal, origin isolation, cancellation");
        }finally{source.stop(0);other.stop(0);target.delete();}
    }
}
