package Hayfevrly.Model;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class TroubleShooting {

    public static void printUserAgentAsReportedByWhatIsMyBrowserCom(WebDriver driver) {
        try {
            driver.get("https://www.whatismybrowser.com/detect/what-is-my-user-agent/");
        } catch (org.openqa.selenium.WebDriverException e) {
            //
        }
        String userAgent = driver.findElements(By.xpath("/html/body/div[1]/section[2]/div/div[1]/div/div/a")).get(0).getText();
        System.out.println(userAgent);
    }

}
