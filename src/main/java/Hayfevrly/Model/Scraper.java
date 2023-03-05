package Hayfevrly.Model;

import org.openqa.selenium.WebDriver;

import java.time.LocalDateTime;

public class Scraper {

    // fields populated from persistent storage at time of construction
    String dataSourceAbbreviation; // this will also be used to select the correct scrape method
    boolean weekdaysOnly;
    String timesPerHourToCheck = "........................";

    // assigned to this class instance at time of construction
    WebDriver driver;

    // fields not populated at time of construction
    LocalDateTime lastProductiveScrape;
    boolean readingsCollectedForToday;

    // constructor
    public Scraper(String dataSourceAbbreviation, boolean weekdaysOnly, String timesPerHourToCheck, WebDriver driver) {
        this.dataSourceAbbreviation = dataSourceAbbreviation;
        this.weekdaysOnly = weekdaysOnly;
        this.timesPerHourToCheck = timesPerHourToCheck;
        this.driver = driver;
    }

    public void quitDriver() {
        try {
            driver.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getDataSourceAbbreviation() {
        return dataSourceAbbreviation;
    }

    public void setDataSourceAbbreviation(String dataSourceAbbreviation) {
        this.dataSourceAbbreviation = dataSourceAbbreviation;
    }

    public boolean isWeekdaysOnly() {
        return weekdaysOnly;
    }

    public void setWeekdaysOnly(boolean weekdaysOnly) {
        this.weekdaysOnly = weekdaysOnly;
    }

    public String getTimesPerHourToCheck() {
        return timesPerHourToCheck;
    }

    public void setTimesPerHourToCheck(String timesPerHourToCheck) {
        this.timesPerHourToCheck = timesPerHourToCheck;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public LocalDateTime getLastProductiveScrape() {
        return lastProductiveScrape;
    }

    public void setLastProductiveScrape(LocalDateTime lastProductiveScrape) {
        this.lastProductiveScrape = lastProductiveScrape;
    }

    public boolean isReadingsCollectedForToday() {
        return readingsCollectedForToday;
    }

    public void setReadingsCollectedForToday(boolean readingsCollectedForToday) {
        this.readingsCollectedForToday = readingsCollectedForToday;
    }

    // methods
    public Scraping.ScrapeResults performAScrape() {
        Scraping.ScrapeResults sr = Scraping.getReadings(dataSourceAbbreviation, driver);
        if (sr.message().equals(Scraping.ScrapeResultMessage.SUCCESSFUL_SCRAPE)) {
            readingsCollectedForToday = true;
            lastProductiveScrape = LocalDateTime.now();
        }
        return sr;
    }

}
