package Hayfevrly.Model;

import Hayfevrly.Main;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.ghost4j.util.StreamGobbler;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import javax.imageio.ImageIO;
import javax.mail.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scraping {

    private static final int pageLoadDelay = 5; // in seconds

    public static ScrapeResults getReadings(String dataSourceID, WebDriver driver) {
        return switch (dataSourceID) {
            case "aacg" -> getAacgReadings(driver);
            case "aacg_via_kxan" -> getKxanReadings(driver);
            case "aacg_via_nabweb" -> getAacgViaNabWebReadings(driver);
            case "aacg_via_spectrum" -> getAacgViaSpectrumReadings(); // no need to pass driver since method uses wget
            case "aoa_via_spectrum" -> getAoaViaSpectrumReadings(); // no need to pass driver since method uses wget
            case "kvue" -> getKvueReadings(driver);
//            case "aacg_via_nabemail" -> getAacgViaNabEmailReadings(); // not yet done; not likely to do
            default -> new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.ERROR_BAD_OR_UNKNOWN_DATA_SOURCE_ID);
        };
    }

    public static ScrapeResults getAacgReadings(WebDriver driver) { // TODO:change back to private later
        List<Reading> results = new ArrayList<>();
        String url = "https://www.georgetownallergy.com";

        try {
            driver.get(url);
        } catch (org.openqa.selenium.WebDriverException e) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD);
        }

        if (!Time.successfulPause(pageLoadDelay)) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD_WITHIN_CONFIGURED_PAGE_DELAY);
        }

        // check for availability of today's readings
        String dateShownString = driver.findElements(By.xpath("//div[@class='pollenCounts']//p")).get(1).getText();
        Database.logEvent("getAacgReadings(): dateShownString: " + dateShownString);
        int yearShown = Integer.parseInt(dateShownString.substring(dateShownString.length() - 4));
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(dateShownString.substring(0, dateShownString.length() - 4));
        String dayShown = "";
        while (matcher.find()) {
            dayShown = matcher.group();
        }
        String monthShown = dateShownString.substring(0, dateShownString.indexOf(" "));
        String shownDateFormatted = monthShown + " " + dayShown + " " + yearShown;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d yyyy");
        LocalDate dateShown = LocalDate.parse(shownDateFormatted, formatter);
        LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));
        if (!dateShown.isEqual(today)) {
            return new ScrapeResults(results, ScrapeResultMessage.NO_DATA_FOR_TODAY_RIGHT_NOW);
        }

        LocalDateTime acquisitionLDT = Time.getNowInCentralTimeLdt();

        String[] pollenCardIds = {"Trees", "Weeds", "Grass", "Mold"};

        for (String s : pollenCardIds) {
            // get habit level
            WebElement aPollenCard = driver.findElement(By.id(s.toLowerCase()));
            String categoryLevel = aPollenCard.findElements(By.tagName("text")).get(0).getText();
            results.addAll(Parser.parseRawReadingToCanonicalReading("All " + s.toLowerCase() + ", " + categoryLevel));

            // look for any noted individual species
            List<WebElement> individualSpecies = aPollenCard.findElements(By.tagName("li"));

            if (!individualSpecies.isEmpty()) {
                for (WebElement we : individualSpecies) {
                    String is = we.getText();
                    // see if individual species ends with numeric measurement
                    if (Character.isDigit(is.charAt(is.length() - 1))) { // if so, friendly format the raw reading before passing to be parsed
                        int indexOfLastSpace = is.lastIndexOf(" ");
                        int measurementPart = Integer.parseInt(is.substring(indexOfLastSpace + 1).replaceAll(",", ""));
                        String speciesPart = is.substring(0, indexOfLastSpace);
                        results.addAll(Parser.parseRawReadingToCanonicalReading(speciesPart + ", " + measurementPart + " gr/m³"));
                    } else { // last char is not a digit, so just pass to be parsed
                        results.addAll(Parser.parseRawReadingToCanonicalReading(is));
                    }
                }
            }
        }

        for (Reading r : results) {
            r.setWhen_acquired_ldt(acquisitionLDT);
            r.setImmediate_source("aacg");
            r.setOriginating_entity("aacg");
        }

        return new ScrapeResults(results, ScrapeResultMessage.SUCCESSFUL_SCRAPE);
    }

    public static ScrapeResults getKvueReadings(WebDriver driver) { // TODO:change back to private later
        List<Reading> results = new ArrayList<>();
        String url = "https://www.keepandshare.com/calendar/show_month.php?i=1940971";

        try {
            driver.get(url);
        } catch (org.openqa.selenium.WebDriverException e) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD);
        }

        if (!Time.successfulPause(pageLoadDelay)) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD_WITHIN_CONFIGURED_PAGE_DELAY);
        }

        try {
            // check for availability of today's readings
            int todayDayValue = LocalDate.now(ZoneId.of("America/Chicago")).getDayOfMonth();
            int todayRow = 0, todayCol = 0;
            int topLeftDayValue = Integer.parseInt(driver.findElement(By.id("daynum_0")).getText());
            if (topLeftDayValue != 1) {
                int numDaysInPreviousMonth = LocalDate.now(ZoneId.of("America/Chicago")).minusMonths(1).lengthOfMonth();
                int daysAhead = numDaysInPreviousMonth + todayDayValue - topLeftDayValue;
                while (daysAhead >= 7) {
                    ++todayRow;
                    daysAhead -= 7;
                }
                todayCol += daysAhead;
            } else { // topLeftDayValue == 1
                int daysAhead = todayDayValue - 1;
                while (daysAhead >= 7) {
                    ++todayRow;
                    daysAhead -= 7;
                }
                todayCol += daysAhead;
            }

            String todayId = "calcontent_" + todayRow + "_" + todayCol;
            String aReading = driver.findElement(By.id(todayId)).getText();

            if (aReading.equals(" ")) {
                return new ScrapeResults(results, ScrapeResultMessage.NO_DATA_FOR_TODAY_RIGHT_NOW);
            }
            LocalDateTime retrievalDateTime = Time.getNowInCentralTimeLdt();
            aReading = convertCommasAndSlashesWithinParensToSpaces(aReading);
            String[] rawReadings = aReading.split(", ");

            for (String aRawReading : rawReadings) {
                results.addAll(Parser.parseRawReadingToCanonicalReading(aRawReading));
            }

            if (results.size() == 0) {
                return new ScrapeResults(results, ScrapeResultMessage.NO_DATA_FOR_TODAY_RIGHT_NOW);
            } else {
                for (Reading r : results) {
                    r.setWhen_acquired_ldt(retrievalDateTime);
                    r.setImmediate_source("kvue");
                    r.setOriginating_entity("kvue");
                }
                return new ScrapeResults(results, ScrapeResultMessage.SUCCESSFUL_SCRAPE);
            }

        } catch (Exception e) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.ERROR_UNSPECIFIED);
        }
    }

    public static ScrapeResults getKxanReadings(WebDriver driver) {
        List<Reading> results = new ArrayList<>(); // what we'll return

        List<StringBuilder> lsb = new ArrayList<>(); // where we'll build the Strings that we submit to be parsed

        String url = "https://www.kxan.com/weather/allergy-forecast-austin-texas/";

        try {
            driver.get(url);
        } catch (org.openqa.selenium.WebDriverException e) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD);
        }

        if (!Time.successfulPause(pageLoadDelay)) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD_WITHIN_CONFIGURED_PAGE_DELAY);
        }

        // check for availability of today's readings
        String todayMonthEnum = LocalDate.now(ZoneId.of("America/Chicago")).getMonth().toString();
        String todayMonthName = todayMonthEnum.charAt(0) + todayMonthEnum.substring(1).toLowerCase();
        int todayDayValue = LocalDate.now(ZoneId.of("America/Chicago")).getDayOfMonth();
        int todayYearValue = LocalDate.now(ZoneId.of("America/Chicago")).getYear();
        String dateString = todayMonthName + " " + todayDayValue + ", " + todayYearValue;

        List<WebElement> lwe = driver.findElements(By.xpath("//h3[@class='allergy_content'][.='" + dateString + "']"));
        if (lwe.size() == 0) {
            return new ScrapeResults(results, ScrapeResultMessage.NO_DATA_FOR_TODAY_RIGHT_NOW);
        }

        LocalDateTime acquisitionLDT = Time.getNowInCentralTimeLdt();

        // get list of allergens
        lwe.clear(); // re-use this list
        lwe = driver.findElements(By.xpath("//div[@class='allergen_value']"));
        if (lwe.size() == 0) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.LIST_OF_WEB_ELEMENTS_IS_EMPTY);
        } else {
            for (WebElement we : lwe) {
                String allergen = we.getText().substring(0, we.getText().indexOf(" - "));
                String descriptor = we.getText().substring(we.getText().indexOf(" - ") + 3);
                lsb.add(new StringBuilder(allergen + ", " + descriptor));
//                results.add(new Reading(allergen, "", descriptor));
            }

            // click the X for each allergen block
            for (StringBuilder sb : lsb) {
                String allergenIdentifier = sb.substring(0, sb.indexOf(", "));
                WebElement ecks = driver.findElement(By.xpath("//img[@id='" + allergenIdentifier
                        .replaceAll(" ", "_")
                        .replaceAll("/", "___")
                        + "']"));
                try {
                    ecks.click();
                } catch (ElementClickInterceptedException e) { // an ad blocked our click; delete ads and try again
                    Database.logEvent(e.toString());
                }
                if (!Time.successfulPause(2)) {
                    return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.ERROR_UNSPECIFIED);
                }
            }

            // get the readings
            List<WebElement> measurements = driver.findElements(By.xpath("//div[@class='morris-hover-point']"));
            List<String> measurementStrings = new ArrayList<>();
            for (WebElement we : measurements) {
                String curMeasurementString = we.getAttribute("innerHTML");
                curMeasurementString = curMeasurementString.replaceAll("\\R+", " ").replaceAll("\\ +", " ");
                curMeasurementString = curMeasurementString.substring(curMeasurementString.indexOf(": ") + 2).trim();
                measurementStrings.add(curMeasurementString);
            }
            for (int i = 0; i < measurementStrings.size(); ++i) {
                lsb.get(i).append(", ").append(measurementStrings.get(i));
//                results.get(i).setMeasurement_string(measurementStrings.get(i));
            }
        }

        // parse the raw readings
        for (StringBuilder sb : lsb) {
            String s = sb.toString();
            String allergenIdentifier = s.substring(0, s.indexOf(", "));

            if (allergenIdentifier.equals("Mold")) {
                s = s.replace("Mold, ", "All molds, ");
            } else if (allergenIdentifier.equals("Grass")) {
                s = s.replace("Grass, ", "All grasses, ");
            }

            results.addAll(Parser.parseRawReadingToCanonicalReading(s));
        }

        for (Reading r : results) {
            r.setWhen_acquired_ldt(acquisitionLDT);
            r.setOriginating_entity("aacg");
            r.setImmediate_source("kxan");
        }

        return new ScrapeResults(results, ScrapeResultMessage.SUCCESSFUL_SCRAPE);
    }

    public static ScrapeResults getAacgViaNabWebReadings(WebDriver driver) {
        List<Reading> results = new ArrayList<>(); // what we'll return
        String url = "https://pollen.aaaai.org/#/station/a5373fe8-6339-4b24-ae91-a983904e6f45";

        try {
            driver.get(url);
        } catch (org.openqa.selenium.WebDriverException e) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD);
        }

        if (!Time.successfulPause(pageLoadDelay)) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.URL_FAILED_TO_LOAD_WITHIN_CONFIGURED_PAGE_DELAY);
        }

        // check for availability of today's readings
        String todayMonthEnum = LocalDate.now(ZoneId.of("America/Chicago")).getMonth().toString();
        String todayMonthName = todayMonthEnum.charAt(0) + todayMonthEnum.substring(1).toLowerCase();
        int todayDayValue = LocalDate.now(ZoneId.of("America/Chicago")).getDayOfMonth();
        int todayYearValue = LocalDate.now(ZoneId.of("America/Chicago")).getYear();
        String dateString = todayMonthName + " " + todayDayValue + ", " + todayYearValue;
        List<WebElement> aitchTwoElements = driver.findElements(By.tagName("h2"));
        boolean todaysDataPosted = false;
        for (WebElement we : aitchTwoElements) {
            if (we.getText().endsWith(Integer.toString(todayYearValue))) {
                if (we.getText().equalsIgnoreCase(dateString)) {
                    todaysDataPosted = true;
                }
            }
        }
        if (!todaysDataPosted) {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.NO_DATA_FOR_TODAY_RIGHT_NOW);
        }

        // get category readings
        List<WebElement> categoryReadings = driver.findElements(By.xpath("//div[@class='gauge-widget-sub-value']"));
        List<WebElement> levelDescriptors = driver.findElements(By.xpath("//div[@class='mt-3']"));
        String[] categoryNames = {"All trees", "All weeds", "All grasses", "All molds"};
        for (int i = 0; i < 4; ++i) {
            String t = categoryNames[i] + ", " + categoryReadings.get(i).getText() + ", " + levelDescriptors.get(i).getText();
            results.addAll(Parser.parseRawReadingToCanonicalReading(t));
        }

        // get top species of each category, if any
        String[] imgAltText = {"Trees", "Weeds", "Grass", "Mold"};
        for (int i = 0; i < 4; ++i) {
            results.addAll(getSpeciesAacgAaaaiWeb(driver.findElements(By.xpath("//img[@alt='" + imgAltText[i] + "']/..")).get(0), levelDescriptors.get(i).getText()));
        }

        LocalDateTime retrievalDateTime = Time.getNowInCentralTimeLdt();
        for (Reading r : results) {
            r.setWhen_acquired_ldt(retrievalDateTime);
            r.setOriginating_entity("aacg");
            r.setImmediate_source("nabweb");
        }

        return new ScrapeResults(results, ScrapeResultMessage.SUCCESSFUL_SCRAPE);
    }

    private static List<Reading> getSpeciesAacgAaaaiWeb(WebElement card, String level) {
        List<Reading> results = new ArrayList<>(); // what we'll return
        List<WebElement> divs1 = card.findElements(By.className("mt-1"));
        for (WebElement we1 : divs1) {

            // find web element with class="font-weight-bold" and parse and add to results
            List<WebElement> els = we1.findElements(By.className("font-weight-bold"));
            for (WebElement we2 : els) {
//                System.out.format("- %s\n", we2.getText());
                results.addAll(Parser.parseRawReadingToCanonicalReading(String.format("%s, %s", we2.getText(), level)));
            }


//            String s = we1.getText().replaceAll("\\R+", ", ");
//            results.addAll(Parser.parseRawReadingToCanonicalReading(s + ", Present"));
//            results.add(new Reading(s, "", "Present"));
        }
        return results;
    }

    private static String getMynabGeorgetownEmailReadings() {

        // TODO: move some of this code to the Model.Email class

        String username = Main.hfSecrets.getProperty("secrets.email.username");
        String password = Main.hfSecrets.getProperty("secrets.email.password");
        String pop3host = Main.hfSecrets.getProperty("secrets.email.pop3host");

        int port = 995;

        try {
            // create properties field
            Properties properties = new Properties();
            properties.put("mail.store.protocol", "pop3");
            properties.put("mail.pop3.host", pop3host);
            properties.put("mail.pop3.port", Integer.toString(port));
            properties.put("mail.pop3.starttls.enable", "true");
            Session emailSession = Session.getDefaultInstance(properties);
            // emailSession.setDebug(true);

            // create the POP3 store object and connect with the pop server
            Store store = emailSession.getStore("pop3s");

            store.connect(pop3host, username, password);

            // create the folder object and open it
            Folder emailFolder = store.getFolder("INBOX");
            emailFolder.open(Folder.READ_ONLY);

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    System.in));

            // retrieve the messages from the folder
            Message[] messages = emailFolder.getMessages();

            if (messages.length == 0) {
                return "HF: no data for today right now";
            }

            // TODO: create a LocalDate for today so we can check if a possible My NAB email is for today

            for (Message m : messages) { // now we look for today's My NAB email, if it's there

                if (m.getSubject() == null || !m.getSubject().contains("My NAB Pollen and Mold Levels")) {
                    continue;
                }

                // we've got a My NAB email.

                LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));

                LocalDateTime acquisitionTime = m.getSentDate()
                        .toInstant()
                        .atZone(ZoneId.of("America/Chicago"))
                        .toLocalDateTime();

                StringBuilder sb = new StringBuilder();
                Email.getMessageBody(m, sb, "text/plain");
                String messageBody = sb.toString();

                String[] lines = messageBody.split("\\R");

                String curSection = "email header";

                List<Reading> lr = new ArrayList<>(); // TODO: to use for storing readings parsed from the email

                for (int i = 0; i < lines.length; ++i) {

                    String curLine = lines[i];
                    String prevLine = (i > 0 ? lines[i - 1] : "");

                    // update section
                    if (curLine.startsWith("Station:")) {
                        curSection = "station metadata";
                    } else if (curLine.startsWith("Pollen and Mold Summary")) {
                        curSection = "summary";
                    } else if (curSection.equals("summary") && curLine.endsWith("Tree")) {
                        curSection = "trees";
                    } else if (curSection.equals("trees") && curLine.endsWith("Weed")) {
                        curSection = "weeds";
                    } else if (curSection.equals("weeds") && curLine.endsWith("Grass")) {
                        curSection = "grasses";
                    } else if (curSection.equals("grasses") && curLine.endsWith("Mold")) {
                        curSection = "molds";
                    } else if (curSection.equals("molds") && curLine.endsWith("Unidentified")) {
                        curSection = "unidentified";
                    } else if (curLine.startsWith("---")) {
                        curSection = "footer";
                    }

                    if (curSection.equals("station metadata")) {
                    } else if (curSection.equals("summary")) {
                    } else if (curSection.equals("trees")) {
                    } else if (curSection.equals("weeds")) {
                    } else if (curSection.equals("grasses")) {
                    } else if (curSection.equals("molds")) {
                    } else if (curSection.equals("unidentified")) {
                    } else if (curSection.equals("footer")) {
                        // nothing here
                    }
                }


            }

            // close the store and folder objects
            emailFolder.close(false);
            store.close();

        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "mynabEmail";
    }


    public static ScrapeResults getAacgViaSpectrumReadings() {
        return getReadingsFromImage("aacg_via_spectrum");
    }

    public static ScrapeResults getAoaViaSpectrumReadings() {
        return getReadingsFromImage("aoa_via_spectrum");
    }

    public static ScrapeResults getReadingsFromImage(String dataSourceID) {
        String url = null;
        switch (dataSourceID) {
            case "aacg_via_spectrum":
                url = "http://media.raven.news/tx/weather/ALLERGY_RR-large.jpg";
                break;
            case "aoa_via_spectrum":
                url = "http://media.raven.news/tx/weather/ALLERGY_AUS-large.jpg";
                break;
            default:
                // no default
        }

        String filenameOfDownloadedImage = "orig.jpg";

        // obtain a temporary working directory
        File baseWorkingDir = new File(Main.hfSettings.getProperty("location.directory.working"));
        File workingDir = new File(baseWorkingDir + "/Scraping");
        if (!workingDir.exists()) {
            workingDir.mkdir();
        }
        // delete any existing files with the filenames we're going to use
        String[] filenamesToDelete = {filenameOfDownloadedImage, "allergens.jpg", "date.jpg", "levels.jpg"};
        for (String s : filenamesToDelete) {
            File curFile = new File(workingDir, s);
            if (curFile.exists()) {
                curFile.delete();
            }
        }

        // download the image
        ProcessBuilder downloaderBuilder = new ProcessBuilder();
        downloaderBuilder.directory(workingDir);
        downloaderBuilder.command(
                Main.hfSettings.getProperty("location.binary.wget"),  // these options are wget specific
                "-U", "\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36\"",
                "--secure-protocol=auto",
                "--no-check-certificate",
//                "--timestamping",
                "-S", url,
                "-O", filenameOfDownloadedImage);

//                Main.hfSettings.getProperty("location.binary.curl"), // these options are curl specific
//                "--ssl",
//                "--fail",
//                "--silent",
//                "--referer", "https://www.google.com/",
//                "--user-agent", "\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36\"",
//                "--url", url,
//                "--output", filenameOfDownloadedImage);

        Process downloaderProcess = null;
        try {
            downloaderProcess = downloaderBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        StreamGobbler downloaderStreamGobbler = null;
        try {
            downloaderStreamGobbler = new StreamGobbler(downloaderProcess.getInputStream(), System.out);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        Executors.newSingleThreadExecutor().submit(downloaderStreamGobbler);

        Integer downloaderExitCode = null;
        try {
            downloaderExitCode = downloaderProcess.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ZonedDateTime origjpgLastModifiedZDT = null;
        try {
            File origjpg = new File(workingDir, filenameOfDownloadedImage);
            BasicFileAttributes bfa = Files.readAttributes(origjpg.toPath(), BasicFileAttributes.class);
            Instant i = bfa.lastModifiedTime().toInstant();
            origjpgLastModifiedZDT = i.atZone(ZoneId.of("America/Chicago"));
        } catch (IOException e) {
            Database.logEvent(e.toString());
        }

        // verify the downloaded image exists before we proceed
        File origImage = new File(workingDir, filenameOfDownloadedImage);
        if (!origImage.exists()) {
            Database.logEvent(origImage + " doesn't exist!");
        }

        // crop the sections of interest into individual jpgs
        String magickConvertInvocation = "";
        if (Main.hfSettings.getProperty("location.binary.imagemagick").endsWith("magick.exe")) {
            magickConvertInvocation = "\"" + Main.hfSettings.getProperty("location.binary.imagemagick") + "\"" + " convert ";
        } else if (Main.hfSettings.getProperty("location.binary.imagemagick").endsWith("convert")) {
            magickConvertInvocation = Main.hfSettings.getProperty("location.binary.imagemagick") + " ";
        } else {
            return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.FAILED_TO_COMPOSE_MAGICK_INVOCATION);
        }

        String[] imagemagickCommands = {
                magickConvertInvocation + origImage + " -crop 240x227+127+145 -colorspace Gray -contrast-stretch 5%x80% " + workingDir + "/allergens.jpg",
                magickConvertInvocation + origImage + " -crop 175x28+548+382 -colorspace Gray -contrast-stretch 5%x80% " + workingDir + "/date.jpg",
                magickConvertInvocation + origImage + " -crop 314x227+376+145 -colorspace Gray -contrast-stretch 6%x80% " + workingDir + "/levels.jpg"
        };


        ProcessBuilder imagemagickBuilder = new ProcessBuilder();
        imagemagickBuilder.directory(workingDir);
        for (String eachImagemagickCommand : imagemagickCommands) {

//            Database.logEvent(eachImagemagickCommand);

            if (Main.hfSettings.getProperty("host.platform.type").equals("Linux")) {
                imagemagickBuilder.command("bash", "-c", eachImagemagickCommand);
            } else { // equals "Windows"
                imagemagickBuilder.command("cmd.exe", "/c", eachImagemagickCommand);
            }

            Process imagemagickProcess = null;
            try {
                imagemagickProcess = imagemagickBuilder.start();
            } catch (IOException e) {
                e.printStackTrace();
            }

            StreamGobbler imagemagickStreamGobbler = null;
            try {
                imagemagickStreamGobbler = new StreamGobbler(imagemagickProcess.getInputStream(), System.out);
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            Executors.newSingleThreadExecutor().submit(imagemagickStreamGobbler);

            Integer exitCode = null;
            try {
                exitCode = imagemagickProcess.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        String[] fileNames = {
                "date.jpg", // process date first to see if we have today's data
                "allergens.jpg",
                "levels.jpg"
        };

//        for (String eachFile : fileNames) {
//            File f = new File(workingDir, eachFile);
//            if (!f.exists()) {
//                Database.logEvent(f + " doesn't exist!");
//            } else {
//                Database.logEvent(f + " does exist.");
//            }
//        }

        List<String> allergens = new ArrayList();
        List<String> levels = new ArrayList();

        for (String eachFile : fileNames) {
            // Tess4J
            File tmpFolder = null;

            if (Main.hfSettings.getProperty("host.platform.type").equals("Linux")) {
                tmpFolder = LoadLibs.extractTessResources("x86_64-linux-gnu");
            } else { // equals "Windows"
                tmpFolder = LoadLibs.extractTessResources("win32-x86-64");
            }

            System.setProperty("java.library.path", tmpFolder.getPath());
            Tesseract t = new Tesseract();
            t.setLanguage("eng");
            t.setOcrEngineMode(1);
            t.setDatapath(Main.hfSettings.getProperty("location.data.tessdata"));

            BufferedImage image = null;
            try {
                image = ImageIO.read(new File(workingDir, eachFile)); // date.jpg, allergens.jpg, levels.jpg
            } catch (IOException e) {
                System.out.println("loading image");
                e.printStackTrace();
            }

            String result = null;
            try {
                result = t.doOCR(image).trim();
            } catch (TesseractException e) {
                System.out.println("doOCR()");
                e.printStackTrace();
                return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.TESSERACT_EXCEPTION);
            }

            // check if we have today's data
            if (eachFile.equals("date.jpg")) {
                String todayDayOfTheWeek = ZonedDateTime.now().getDayOfWeek().name();
                String[] words = result.split(" ");
                String dayShownInImage = words[words.length - 1];
                if (!todayDayOfTheWeek.equalsIgnoreCase(dayShownInImage)) {
                    // day in image is not today!
                    return new ScrapeResults(new ArrayList<>(), ScrapeResultMessage.NO_DATA_FOR_TODAY_RIGHT_NOW);
                }
            }

            List<String> whichList = null;
            switch (eachFile) {
                case "date.jpg":
                    continue;
                case "allergens.jpg":
                    whichList = allergens;
                    break;
                case "levels.jpg":
                    whichList = levels;
                    break;
                default:
                    // no default
            }

            String[] lines = result.split("\\R+");
            for (String eachLine : lines) {
                if (!eachLine.isBlank()) {
                    whichList.add(eachLine);
                }
            }
        }

        List<Reading> results = new ArrayList<>();
        for (int i = 0; i < allergens.size(); ++i) {

            String allergen = allergens.get(i);

            if (allergen.toLowerCase().contains("mold")) {
                allergen = "All molds";
            }

            // since the levels are on colored backgrounds, sometimes they won't all come through;
            // if we're missing any levels, assign "Present" to all
            String curLevel = null;
            if (levels.size() < allergens.size()) {
                curLevel = "Present";
            } else {
                curLevel = levels.get(i);
            }

            results.addAll(Parser.parseRawReadingToCanonicalReading(String.format("%s, %s", allergen, curLevel)));
        }
        for (Reading r : results) {
            r.setWhen_acquired_ldt(Time.getNowInCentralTimeLdt());
            r.setOriginating_entity(dataSourceID.substring(0, dataSourceID.indexOf("_via_")));
            r.setImmediate_source("spectrum");
        }

        return new ScrapeResults(results, ScrapeResultMessage.SUCCESSFUL_SCRAPE);
    }


    public static void ScrapeCronJob(WebDriver driver, boolean forceGetReadings) {
        Database.startConnection();
        String[] dataSources = {
                "aacg",
                "aacg_via_kxan",
                "aacg_via_nabweb",
                "aacg_via_spectrum",
                "aoa_via_spectrum",
                "kvue"
        };
        Database.logEvent("Starting ScrapeCronJob() v2");
        Database.logEvent("Time now: " + ZonedDateTime.now());

        boolean anySuccessfulScrapes = false;

        for (String aDataSource : dataSources) {

            if (Database.existReadingsForToday(aDataSource)) {
                if (forceGetReadings) {
                    Database.logEvent("readings already exist for " + aDataSource + "; but forceReadings is true");
                } else {  // !forceGetReadings
                    Database.logEvent("readings already exist for " + aDataSource + "; skipping");
                    continue;
                }
            }

            Database.logEvent("about to scrape " + aDataSource);

            ScrapeResults res = getReadings(aDataSource, driver);

            Database.logEvent("result of scraping " + aDataSource + ": " + res.message());

            if (res.message().equals(ScrapeResultMessage.SUCCESSFUL_SCRAPE)) {

                anySuccessfulScrapes = true;

                /*
                    Any logic to perform before storing the scraped readings
                 */
                // create a utility map to help pull up readings as needed
                Map<String, Reading> allergens = new HashMap<>();
                for (Reading r : res.results()) {
                    allergens.put(r.getAllergen_identifier(), r);
                }

                if (allergens.containsKey("Unspecified mold")) {
                    if (!allergens.containsKey("All molds")) {
                        // change in allergens
                        allergens.get("Unspecified mold").setAllergen_identifier("All molds");
                        allergens.put("All molds", allergens.get("All molds"));
                        allergens.remove("Unspecified mold");
                        // change in res.results()
                        for (Reading r : res.results()) {
                            if (r.getAllergen_identifier().equals("Unspecified mold")) {
                                r.setAllergen_identifier("All molds");
                            }
                        }
                    }
                }

                for (Reading r : res.results()) {
                    Database.storeReadingV2(r);
                }
                String originatingEntity = "";
                if (aDataSource.contains("_via_")) {
                    originatingEntity = aDataSource.substring(0, aDataSource.indexOf("_via_"));
                } else {
                    originatingEntity = aDataSource;
                }
                Database.reconcileTodaysReadings(originatingEntity);
            }
        }

        if (anySuccessfulScrapes) {
            Report.generateReport();
            Database.logEvent("Report updated.");
        } else {
            Database.logEvent("Report unchanged.");
        }

        Database.logEvent("successful exit");
        Database.closeConnection();
//        System.exit(0);
    }

    static String convertCommasAndSlashesWithinParensToSpaces(String s) {
        StringBuilder sb = new StringBuilder();
        boolean inParens = false;
        for (char c : s.toCharArray()) {
            if (c == '(') {
                inParens = true;
            } else if (c == ')') {
                inParens = false;
            }
            if (inParens && (c == ',' || c == '/')) {
                sb.append(" ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public enum ScrapeResultMessage {
        SUCCESSFUL_SCRAPE,
        NO_DATA_FOR_TODAY_RIGHT_NOW,
        ERROR_UNSPECIFIED,
        ERROR_BAD_OR_UNKNOWN_DATA_SOURCE_ID,
        READING_ALREADY_EXISTS_FOR_TODAY,
        URL_FAILED_TO_LOAD,
        URL_FAILED_TO_LOAD_WITHIN_CONFIGURED_PAGE_DELAY,
        LIST_OF_WEB_ELEMENTS_IS_EMPTY,
        FAILED_TO_COMPOSE_MAGICK_INVOCATION,
        TESSERACT_EXCEPTION
    }

    public record ScrapeResults(List<Reading> results, ScrapeResultMessage message) {
    }


}
