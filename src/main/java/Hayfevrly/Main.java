package Hayfevrly;

import Hayfevrly.Model.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
//import org.openqa.selenium.chrome.ChromeDriverLogLevel;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class Main {

    public static Properties hfSettings = new Properties();
    public static Properties hfSecrets = new Properties();

    public static void main(String[] args) {

        /*
        Process command line arguments
         */
        String configFileString = null;
        String specificTask = null;
//        String forceReadingsString = null;
        boolean forceReadingsSwitch = false; // default to false

        for (int curArg = 0; curArg < args.length; ++curArg) {
            switch (args[curArg]) {
                case "--config":
                    configFileString = args[++curArg];
                    break;
                case "--task":
                    specificTask = args[++curArg];
                    break;
                case "--force-readings":
//                    forceReadingsString = args[curArg];
                    forceReadingsSwitch = true;
                default:
                    // no default
            }
        }

        if (configFileString == null) {
            Database.logEvent("ERROR: A config file must be specified!");
            System.exit(1);
        }

        File configFile = new File(configFileString);

        if (!configFile.exists()) {
            Database.logEvent("ERROR: Specified config file does not exist!");
            System.exit(1);
        }
        try (InputStream input = new FileInputStream(configFile)) {
            try {
                hfSettings.load(input);
            } catch (NullPointerException e) {
                Database.logEvent(e.toString());
                Database.logEvent("ERROR: Could not load specified config file!");
                System.exit(1);
            }
        } catch (IOException e) {
            Database.logEvent(e.toString());
            Database.logEvent("ERROR: Could not read specified config file!");
            System.exit(1);
        }

//        if (forceReadingsString != null) {
//            forceReadingsSwitch = Boolean.parseBoolean(forceReadingsString);
//        }

        // ensure a working directory exists
        if (Main.hfSettings.getProperty("location.directory.working").isBlank()) {
            String systemTempDir = System.getProperty("java.io.tmpdir");
            // ^^^ on Windows, this will most likely be: C:\Users\%USERNAME%\AppData\Local\Temp\
            File workingDir = new File(systemTempDir + "/Hayfevrly");
            if (!workingDir.exists()) {
                workingDir.mkdir();
            }
            Main.hfSettings.setProperty("location.directory.working", workingDir.toString());
        } else {
            File workingDir = new File(Main.hfSettings.getProperty("location.directory.working"));
            if (!workingDir.exists()) {
                workingDir.mkdirs();
            }
        }

        // parse secrets
        if (Main.hfSettings.getProperty("location.config.secrets") != null && !Main.hfSettings.getProperty("location.config.secrets").isBlank()) {

            String secretsFileString = Main.hfSettings.getProperty("location.config.secrets");
            File secretsFile = new File(secretsFileString);

            if (!secretsFile.exists()) {
                Database.logEvent("ERROR: Specified secrets file " + secretsFileString + " does not exist!");
                System.exit(1);
            }
            try (InputStream input = new FileInputStream(secretsFile)) {
                try {
                    hfSecrets.load(input);
                } catch (NullPointerException e) {
                    Database.logEvent(e.toString());
                    Database.logEvent("ERROR: Could not load specified secrets file " + secretsFileString + "!");
                    System.exit(1);
                }
            } catch (IOException e) {
                Database.logEvent(e.toString());
                Database.logEvent("ERROR: Could not read specified secrets file " + secretsFileString + "!");
                System.exit(1);
            }
        }


        System.setProperty("webdriver.chrome.driver", hfSettings.getProperty("location.binary.chromedriver"));

        Spreadsheet.populateConcordance();

//        specificTask = "ScrapeCronJob";

        if (specificTask != null) {
            switch (specificTask) {
                case "ScrapeCronJob":
//                    System.setProperty("webdriver.chrome.driver", "/opt/selenium/chromedriver-110.0.5481.77");

                    ChromeOptions chromeOptions = new ChromeOptions();


                    List<String> arguments = new ArrayList<>();
                    arguments.add("--log-level=3");
                    arguments.add("--headless");
                    arguments.add("--window-size=1600,1600");
                    arguments.add("--start-maximized");
                    arguments.add("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");

                    chromeOptions.addArguments(arguments);

                    ChromeDriverService chromeDriverService = new ChromeDriverService.Builder()
                            .withSilent(true)
                            .withVerbose(false)
                            .withLogFile(new File("chromedriverlog.txt")).build();
                    ChromeDriver driver = new ChromeDriver(chromeDriverService, chromeOptions);

//                    TroubleShooting.printUserAgentAsReportedByWhatIsMyBrowserCom(driver);

                    Scraping.ScrapeCronJob(driver, forceReadingsSwitch);





//                    driver.close();
//                    driver.quit();
                    System.exit(0);
                case "TestProxies":
                    Proxies.testProxies();
                    System.exit(0);
                default:
                    // no default

            }
        }


        // test scraping, reporting, etc.
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--log-level=3");
        chromeOptions.addArguments("--headless"); // chromedriver doesn't work as well for my purposes when it's headless
        chromeOptions.addArguments("--window-size=1000,1000");
        chromeOptions.addArguments("--start-maximized");
        ChromeDriverService chromeDriverService = new ChromeDriverService.Builder()
                .withSilent(true)
                .withVerbose(false)
                .withLogFile(new File("chromedriverlog.txt")).build();
        WebDriver driver = new ChromeDriver(chromeDriverService, chromeOptions);

        Spreadsheet.populateConcordance(); // <--- don't forget this!

//        Scraping.ScrapeCronJob(driver, forceReadingsSwitch);
        Scraping.ScrapeResults res = Scraping.getKxanReadings(driver);

        for (Reading r : res.results()) {
            System.out.println(r);

        }

        driver.close();
        driver.quit();
        System.exit(0);









        /*

            TODO: set up weather api checking using:
            https://home.openweathermap.org/api_keys

            API key:
            - Your API key is XXX
            - Within the next couple of hours, it will be activated and ready to use
            - You can later create more API keys on your account page
            - Please, always use your API key in each API call

            Endpoint:
            - Please, use the endpoint api.openweathermap.org for your API calls
            - Example of API call:
            api.openweathermap.org/data/2.5/weather?q=London,uk&APPID=XXX

            Useful links:
            - API documentation https://openweathermap.org/api
            - Details of your plan https://openweathermap.org/price
            - Please, note that 16-days daily forecast and History API are not available for Free subscribers

        */

        // TODO: after a successful scrape of an immediate source of a certain originating entity:
        //      - delete from the reconciled_readings table any readings of same day from same originating entity
        //      - run the below reading reconciliation code for that originating entity (which will load the latest
        //              reconciled readings into the table reconciled_readings
        //      - after loading the fresh reconciled readings, trigger the writeReport() method to generate
        //              a fresh report, which will take into account all available readings as well as current weather
        //      - note that writeReport() could also be triggered on the hour or something to take into account the day's weather/wind
        //              as well as the latest weather forecasts for next day and following days


//        Database.startConnection();
//        Report.generateReport();
//        Database.closeConnection();


//        NWS.accessNWSApi();
//
//        System.exit(0);


//        Spreadsheet.printConcordance();


                /*
                    Logic for readings from one immediate source:
                    ---------------------------------------------

                    If we have an Other X and no other X at all, then promote it to All X

                    If we have multiple readings for same identifier, try to merge them. For example, All X with a numeric reading
                    and All X with PRESENT can easily be merged. Of course log such mergers

                    If there is another tree reading, then this reading is "Other trees" and these trees are Present.
                    If there isn't another tree reading, then this reading is "All trees" and these trees are Present.



                    If an AMBIGUOUS_MATCH_SET is low, maybe discard? but definitely log so I can investigate later



                    If we have multiple identical readings, delete the duplicates.

                    if we have an All habit and we also have numeric readings for familiae, genera, or species within,
                    then compute the Other X for that habit via subtraction. We won't remove the All X reading, since
                    in the PWA some people just want the topline Trees, Weeds, Grasses, Molds reading, so All X is
                    always good to have. But in the expanded list, we want to show an "Other X (undifferentiated)"


                 */

                /*
                    Logic for readings from same originating entity:
                    ------------------------------------------------

                    If we have two or more PRESENT readings for different genera in same familia, then merge genera readings into single familia reading

                    If we have an Other Trees with a scalar reading, and we have
                    multiple tree species with just PRESENT, then create a composite `report item` object
                    that looks like this: "Other trees (including Ash, Hackberry, Mulberry) 133 gr/m³"

                 */

        // TODO: create a DailyReport class to which we can add display versions of some of
        // these readings, such as the aforementioned "Other trees (including X, Y, and Z) 133 gr/m³"
        // Flow of information: raw station report --> instances of Readings --> report for display


        System.exit(0);
    }


}
