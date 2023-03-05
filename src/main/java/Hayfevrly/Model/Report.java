package Hayfevrly.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Report {

    private static final String[] originatingEntitiesIDs = new String[]{
            "aacg",
            "aoa",
            "kvue"
    };
    private static final String[] allHabits = new String[]{
            "All trees",
            "All weeds",
            "All grasses",
            "All molds",
            "All fungi"
    };
    private static final Map<String, String> stationName = new HashMap<>();
    private static final Map<String, String> acquisitionTime = new HashMap<>();

    public static void generateReport() {

        // setup
        StringBuilder reportToUpload = new StringBuilder();
//        reportToUpload.append("<div>");

        stationName.put("aacg", "Allergy & Asthma Center of Georgetown");
        stationName.put("aoa", "Allergy Partners of Austin");
        stationName.put("kvue", "KVUE (ABC) News and Weather");

        LocalDateTime when_generated = Time.getNowInCentralTimeLdt();
        String reportTitle = "Pollen Counts";
        reportToUpload.append(encloseWith(reportTitle, "<div class=\"report-title\">"));

        // collect readings from each originating entity and organize them by habit
        StringBuilder reportsBlock = new StringBuilder();
        for (String eachOriginatingEntity : originatingEntitiesIDs) {
            // setup
            Map<String, String> habitSlugs = new HashMap<>();
            Map<String, List<String>> lowerTaxaSlugs = new HashMap<>();

            List<Reading> allReadings = Database.getReconciledReadingsForOriginatingEntity(eachOriginatingEntity);
            for (Reading r : allReadings) {

                if (!acquisitionTime.containsKey(r.originating_entity)) {
                    acquisitionTime.put(r.originating_entity, Time.ldtToDisplay(r.when_acquired_ldt));
                }

                String currentAllergen = r.getAllergen_identifier();
                String currentAllergensHabit = Spreadsheet.getHabit(r.getAllergen_identifier());

                if (Spreadsheet.isHabit(currentAllergen)) {
                    String slug = String.format("%s: %s",
                            encloseWith(currentAllergen, "<span class=\"pollen-report-allergen\">"),
                            encloseWith(r.getLevel_descriptor_canonical().toString(), "<span class=\"pollen-report-level " + r.getLevel_descriptor_canonical().toString() + "\">"));
                    habitSlugs.put(currentAllergen, encloseWith(slug, "<div class=\"pollen-report-habit-row\">"));
                } else { // not a habit
                    String commonName = Spreadsheet.getCommonNameOfAnyIdentifier(currentAllergen);
                    String slug = null;
                    if (commonName.equals(currentAllergen)) {
                        slug = String.format(" - %s: %s",
                            encloseWith(currentAllergen, "<span class=\"pollen-report-allergen\">"),
                            encloseWith(r.getLevel_descriptor_canonical().toString(), "<span class=\"pollen-report-level " + r.getLevel_descriptor_canonical().toString() + "\">"));
                    } else {
                        slug = String.format(" - %s (%s): %s",
                            encloseWith(currentAllergen, "<span class=\"pollen-report-allergen\">"),
                            commonName,
                            encloseWith(r.getLevel_descriptor_canonical().toString(), "<span class=\"pollen-report-level " + r.getLevel_descriptor_canonical().toString() + "\">"));
                    }

                    if (!lowerTaxaSlugs.containsKey(currentAllergensHabit)) {
                        lowerTaxaSlugs.put(currentAllergensHabit, new ArrayList<>());
                    }
                    lowerTaxaSlugs.get(currentAllergensHabit).add(encloseWith(slug, "<div>"));
                    if (!habitSlugs.containsKey(currentAllergensHabit)) {
                        habitSlugs.put(currentAllergensHabit,
                                String.format("%s", encloseWith(currentAllergensHabit, "<div class=\"pollen-report-habit-row\">")));
                    }
                }
            }

            // display readings
            if (habitSlugs.size() == 0) {
                continue;
            }
            String whichStation = String.format("Station: %s<br/>(last updated %s)", stationName.get(eachOriginatingEntity), acquisitionTime.get(eachOriginatingEntity));
            reportsBlock.append(encloseWith(whichStation, "<div class=\"pollen-report-station-header-row\">"));
//            System.out.println(whichStation);

            for (String habit : allHabits) {
                if (habitSlugs.containsKey(habit)) {
                    reportsBlock.append(habitSlugs.get(habit));
//                    System.out.println(habitSlugs.get(habit));
                    if (lowerTaxaSlugs.containsKey(habit)) {
                        for (String lr : lowerTaxaSlugs.get(habit)) {
                            reportsBlock.append(lr);
//                            System.out.println(lr);
                        }
                    }
                }
            }
//            reportsBlock.append("\n\n"); // spacing between stations
//            System.out.format("\n");
        }
        reportToUpload.append(reportsBlock);

        if (reportToUpload.length() < 50) {
            return;
//            LocalDate ld = LocalDate.now(ZoneId.systemDefault());
//            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, MMMM d");
//            reportToUpload.append(String.format("<div>No pollen counts available yet for today (%s).</div>", ld.format(dtf)));
        }

        // store report in DB
//        reportToUpload.append("</div>");
        String reportToUploadString = reportToUpload.toString();
//        System.out.println(reportToUploadString);
        Database.storeReport(when_generated, reportToUploadString);

        uploadFullReportToS3(Time.ldtToSqlDatetime(when_generated), reportToUploadString);

        // display a courtesy copy of the report
//        System.out.println(reportToUploadString);

    }

    public static void uploadFullReportToS3(String when_generated, String reportToUpload) {

        // produce full report

        String indexHtmlTemplate = """
<html>
<head>
    <meta charset="utf-8">
    <title>Hayfevr.ly</title>
    <meta name="description" content="Hayfevr.ly is seasonal allergy intelligence for the Austin metropolitan area.">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta property="og:title" content="Hayfevr.ly">
    <meta property="og:description" content="Hayfevr.ly is seasonal allergy intelligence for the Austin metropolitan area.">
    <meta property="og:type" content="website">
    <meta property="og:url" content="https://www.hayfevr.ly">
    <meta property="og:image" content="images/hf.webp">
    <link rel="stylesheet" href="css/normalize.css">
    <link rel="stylesheet" href="css/default.css">
</head>
<body>
    <main>
        <div class="masthead">
            <div class="logotype"><img alt="hayfevr.ly is seasonal allergy intelligence for the Austin metropolitan area." src="images/hf.webp" width=100%></div>
            <div>Hayfevr.ly is seasonal allergy intelligence for the Austin metropolitan area.</div>
            <br /><br />
        </div>
        <div id="pollen-reports" class="reports">
            <!-- pollen report goes here -->
        </div>

<p><a href="https://www.hayfevr.ly">hayfevr.ly</a></p>
<br />
<div class="html-generation-time">This page was generated at <!-- html generation statement goes here --></div>
</main>
</body>
</html>

                """;
        indexHtmlTemplate = indexHtmlTemplate.replace("<!-- pollen report goes here -->", reportToUpload);
        indexHtmlTemplate = indexHtmlTemplate.replace("<!-- html generation statement goes here -->", when_generated);

        // upload to S3
//        AWS.uploadFileToBucket("", "", "hayfevr.ly", "index.html", "");
        AWS.uploadStringToBucket("hayfevr.ly", "index.html", indexHtmlTemplate);


    }

    public static String encloseWith(String s, String tag) {
        StringBuilder sb = new StringBuilder();
        sb.append(tag);
        sb.append(s);

        String closingTag = null;
        if (tag.indexOf(" ") >= 0) {
            closingTag = "</" + tag.substring(1, tag.indexOf(" ")) + ">";
        } else {
            closingTag = "</" + tag.substring(1);
        }
        sb.append(closingTag);

        if (closingTag.equals("</div>")) {
            sb.append("\n");
        }

        return sb.toString();
    }
}
