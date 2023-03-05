package Hayfevrly.Model;

import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Proxies {

    /*
        Here is a proxy provider that allows for whitelist IP address authentication for $30/month:
        https://free-proxy-list.net/rotating-proxy.html
        https://free-proxy-list.net/blog/rotating-proxy-selenium
        Plan: use my home IP address (and no proxy) until a problem develops)
     */

    // https://api.proxyscrape.com/v2/?request=getproxies&protocol=socks4&timeout=10000&country=US&simplified=true

    static String hidemynameCanadianProxyList = null;
    static String hidemynameURL = "https://hidemy.name/en/proxy-list/?country=CA&type=4#list";

    static String testingTargetURL = "https://en.wikipedia.org/wiki/Main_Page";

    public static void testProxies() {

        System.setProperty("webdriver.chrome.driver", "L:/java_libs/chromedriver/chromedriver.exe");

        PrintWriter log = null;
        try {
            log = new PrintWriter(new BufferedWriter(new FileWriter("tp-log.txt", StandardCharsets.UTF_8, false)));
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("log file couldn't be created\nExiting...");
            System.exit(1);
        }

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--start-maximized");
        chromeOptions.addArguments("--log-level=3");
        chromeOptions.addArguments("--ignore-certificate-errors");
//            chromeOptions.addArguments("--headless");

        Proxy proxy = new Proxy();
        proxy.setSocksProxy("proxy.torguard.org:1080");
        proxy.setSocksUsername("2o5ceejp2kcad6o");
        proxy.setSocksPassword("mlICJ4wa9cLx");
        proxy.setSocksVersion(5);
        chromeOptions.setCapability("proxy", proxy);

        ChromeDriverService service = new ChromeDriverService.Builder().build(); // .withSilent(true).withVerbose(false).withLogFile(new File("chromedriverlog.txt"))

        WebDriver driver = new ChromeDriver(service, chromeOptions);

        List<ProxyResult> workingProxies = new ArrayList<>();
        boolean connected = false;
        Clock clock = Clock.systemDefaultZone();
        log.format("Start: %s", LocalDateTime.now());
        System.out.format("Start: %s\n", LocalDateTime.now());

//        for (int curProxy = 0; curProxy < proxies.size(); ++curProxy) {

        boolean proxyFailed = false;

        long start = 0;
        long finish = 0;

        try {
            start = clock.millis();
            driver.get(testingTargetURL);
            Thread.sleep(5000);
        } catch (Exception e) {
            finish = clock.millis();
            proxyFailed = true;
            System.out.println("failure.");
        } finally {
            if (!proxyFailed) {
                finish = clock.millis();
                System.out.println("success!");
            }
        }


//        }

        driver.close();
        driver.quit();

        try (PrintWriter results = new PrintWriter(new BufferedWriter(new FileWriter("tp-results.txt", StandardCharsets.UTF_8, false)))) {
            results.println("Working proxies:");
            System.out.println("Working proxies:");
            for (ProxyResult pr : workingProxies) {
                results.format("%s, %d\n", pr.proxyName, (int) pr.elapsedTime);
                System.out.format("%s, %d\n", pr.proxyName, (int) pr.elapsedTime);
            }
            results.flush();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("IOException, so printing to screen:");
            System.out.println("Working proxies:");
            for (ProxyResult pr : workingProxies) {
                System.out.format("%s, %d\n", pr.proxyName, (int) pr.elapsedTime);
            }
        }

        log.format("End: %s", LocalDateTime.now());
        System.out.format("End: %s", LocalDateTime.now());
        log.flush();
        log.close();
    }

    static List<String> readTextFileIntoString(String file) {
        List<String> lines = null;
        try {
            lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lines;
    }

    public record ProxyResult(String proxyName, long elapsedTime) {
    }

}
