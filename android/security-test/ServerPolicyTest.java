package com.rommayo.app;
public class ServerPolicyTest {
    private static void same(String expected,String actual){if(!expected.equals(actual))throw new AssertionError("Unexpected normalized address");}
    private static void rejects(String value)throws Exception{try{ServerPolicy.normalize(value);}catch(Exception expected){return;}throw new AssertionError("Invalid server accepted");}
    public static void main(String[] args)throws Exception{
        same("https://example.test/",ServerPolicy.normalize("EXAMPLE.TEST"));
        same("https://example.test/romm/",ServerPolicy.normalize("https://example.test/romm///"));
        same("https://example.test/romm/",ServerPolicy.normalize("https://example.test/romm"+"/".repeat(1500)));
        same("https://example.test",ServerPolicy.origin("https://example.test:443/romm/"));
        rejects("https://user:secret@example.test");rejects("https://example.test/?token=x");rejects("https://example.test/#fragment");rejects("file:///tmp/library");rejects("https://example.test/"+"a".repeat(2048));
        System.out.println("PASS: server normalization, bounded long paths and credential/scheme rejection");
    }
}
