package Hayfevrly.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    public static List<Reading> parseRawReadingToCanonicalReading(String stringReading) {

//        System.out.format("from parser: %s\n", stringReading);

        List<Reading> results = new ArrayList<>(); // what we'll return

        // guard clause
        if (stringReading == null || stringReading.isBlank() || stringReading.isEmpty()) {
            return results; // return empty List
        }

        // initialize a few fields we'll be trying to populate
        String taxon = null;
        Integer scalar = null;
        String unit = null;
        Reading.levelDescriptorCanonical levelDescriptorCanonical = Reading.levelDescriptorCanonical.NOT_YET_SET;

        // normalize case
        stringReading = stringReading.toLowerCase();

        // delete any stopwords
        String[] stopwords = {"concentration", "family", "level"};
        for (String s : stopwords) {
            if (stringReading.contains(s)) {
                stringReading = stringReading.replaceAll(s, " ");
            }
        }

        // close up any commas within numbers
        stringReading = stringReading.replaceAll("(\\d),(\\d)", "$1$2");

        // close up any periods within numbers (we assume they're typos for commas)
        stringReading = stringReading.replaceAll("(\\d)\\.(\\d)", "$1$2");

        // remove any remaining commas and periods
        stringReading = stringReading.replaceAll("[,\\.]", " ");

        // extract the gr/m³ unit
        // TODO: research if there are any other possible units that could be encountered, and write code to extract them
        String[] gramsPerCubicMeterForms = {"gr/m3", "g/m3", "gr/m³", "g/m³", "gr./m3", "g./m3", "gr./m³", "g./m³"};
        for (String s : gramsPerCubicMeterForms) {
            if (stringReading.contains(s)) {
                unit = "gr/m³";
                stringReading = stringReading.replace(s, " ");
            }
        }

        // now that any "gr/m³" are handled, remove any remaining forward slashes
        stringReading = stringReading.replaceAll("[,\\./]", " ");

        // extract any level descriptor (might be in parens)
        if (stringReading.contains("very high")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.VERY_HIGH;
            stringReading = stringReading.replace("very high", "");
        }
        if (stringReading.contains("high")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.HIGH;
            stringReading = stringReading.replace("high", "");
        }
        if (stringReading.contains("moderate")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.MODERATE;
            stringReading = stringReading.replace("moderate", "");
        }
        Pattern p = Pattern.compile("\\bmod\\b");
        Matcher m = p.matcher(stringReading);
        if (m.find()) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.MODERATE;
            stringReading = stringReading.replaceAll("\\bmod\\b", "");
        }
        if (stringReading.contains("medium")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.MODERATE;
            stringReading = stringReading.replace("medium", "");
        }
        p = Pattern.compile("\\bmed\\b");
        m = p.matcher(stringReading);
        if (m.find()) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.MODERATE;
            stringReading = stringReading.replaceAll("\\bmed\\b", "");
        }
        if (stringReading.contains("very low")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.VERY_LOW;
            stringReading = stringReading.replace("very low", "");
        }
        p = Pattern.compile("\\blow\\b");
        m = p.matcher(stringReading);
        if (m.find()) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.LOW;
            stringReading = stringReading.replaceAll("\\blow\\b", "");
        }
        if (stringReading.contains("absent")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.ABSENT;
            stringReading = stringReading.replace("absent", "");
        }
        if (stringReading.contains("not present")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.ABSENT;
            stringReading = stringReading.replace("not present", "");
        }
        if (stringReading.contains("not recorded")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.ABSENT;
            stringReading = stringReading.replace("not recorded", "");
        }
        if (stringReading.contains("none")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.ABSENT;
            stringReading = stringReading.replace("none", "");
        }
        if (stringReading.contains("n/a")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.ABSENT;
            stringReading = stringReading.replace("n/a", "");
        }
        if (stringReading.contains("present")) {
            levelDescriptorCanonical = Reading.levelDescriptorCanonical.PRESENT;
            stringReading = stringReading.replace("present", "");
        }

        // remove any empty parens produced by extracting a level descriptor in parens
        stringReading = stringReading.replaceAll("\\(\\)", " ");

        // extract anything in parens
        String parentheticalMatter = null;
        if (stringReading.matches(".*\\([^\\)]+\\).*")) {
            parentheticalMatter = stringReading.replaceAll(".*\\(([^\\)]+)\\).*", "$1");
            stringReading = stringReading.replace(parentheticalMatter, " ");
        }
        if (parentheticalMatter != null) {
            for (String s : parentheticalMatter.split("\\ +")) {
                if (!s.isEmpty() && !s.isBlank()) {
                    results.addAll(parseRawReadingToCanonicalReading(s));
                }
            }
        }

        // remove any remaining parens
        stringReading = stringReading.replaceAll("[\\(\\)]", " ");

        // extract any number
        if (stringReading.matches("\\D*(\\d+)\\D*")) {
            String number = stringReading.replaceAll("\\D*(\\d+)\\D*", "$1");
            stringReading = stringReading.replace(number, " ");
            scalar = Integer.parseInt(number);
            unit = "gr/m³"; // presumably
        }

        // populate taxon or other identifier

        /*
            TODO: look for and properly handle keywords like: other, remaining, rest
            TODO: need to be able to recognize "other trees" (etc.) that form part but not all of the total trees readings
         */

        Spreadsheet.TaxonQueryResults tqr = Spreadsheet.queryTheTaxaConcordance(stringReading);

        if (tqr.message().equals(Spreadsheet.TaxonQueryResultMessage.SUCCESSFUL_QUERY)) {
            taxon = tqr.result();
        } else {
//            System.out.println(tqr.message() + " for " + stringReading);
            // instead of print, we'll just TODO: log it
        }

        if (taxon != null) {
            Reading r = new Reading(taxon);

            if (scalar != null) {
                r.setMeasurement_scalar(scalar);
                r.setMeasurement_unit(unit);

                // if no level was given, infer one based on scalar
                if (levelDescriptorCanonical == Reading.levelDescriptorCanonical.NOT_YET_SET) {
                    r.setLevel_descriptor_canonical(inferLevelDescriptorCanonical(Spreadsheet.getHabit(taxon), scalar));
                } else {
                    r.setLevel_descriptor_canonical(levelDescriptorCanonical);
                }

            } else { // scalar == null
                // if no level was given, set it to PRESENT
                if (levelDescriptorCanonical == Reading.levelDescriptorCanonical.NOT_YET_SET) {
                    r.setLevel_descriptor_canonical(Reading.levelDescriptorCanonical.PRESENT);
                } else {
                    r.setLevel_descriptor_canonical(levelDescriptorCanonical);
                }
            }

            results.add(r);
        }

        return results;
    }

    static Reading.levelDescriptorCanonical inferLevelDescriptorCanonical(String habit, Integer scalar) {
        Reading.levelDescriptorCanonical level = Reading.levelDescriptorCanonical.NOT_YET_SET;
        switch (habit) {
            case "All grasses":
                if (scalar == 0) {
                    level = Reading.levelDescriptorCanonical.ABSENT;
                } else if (scalar > 0 && scalar < 5) {
                    level = Reading.levelDescriptorCanonical.LOW;
                } else if (scalar >= 5 && scalar < 20) {
                    level = Reading.levelDescriptorCanonical.MODERATE;
                } else if (scalar >= 20 && scalar < 200) {
                    level = Reading.levelDescriptorCanonical.HIGH;
                } else if (scalar >= 200) {
                    level = Reading.levelDescriptorCanonical.VERY_HIGH;
                }
                break;
            case "All trees":
                if (scalar == 0) {
                    level = Reading.levelDescriptorCanonical.ABSENT;
                } else if (scalar > 0 && scalar < 15) {
                    level = Reading.levelDescriptorCanonical.LOW;
                } else if (scalar >= 15 && scalar < 90) {
                    level = Reading.levelDescriptorCanonical.MODERATE;
                } else if (scalar >= 90 && scalar < 1500) {
                    level = Reading.levelDescriptorCanonical.HIGH;
                } else if (scalar >= 1500) {
                    level = Reading.levelDescriptorCanonical.VERY_HIGH;
                }
                break;
            case "All weeds":
                if (scalar == 0) {
                    level = Reading.levelDescriptorCanonical.ABSENT;
                } else if (scalar > 0 && scalar < 10) {
                    level = Reading.levelDescriptorCanonical.LOW;
                } else if (scalar >= 10 && scalar < 50) {
                    level = Reading.levelDescriptorCanonical.MODERATE;
                } else if (scalar >= 50 && scalar < 500) {
                    level = Reading.levelDescriptorCanonical.HIGH;
                } else if (scalar >= 500) {
                    level = Reading.levelDescriptorCanonical.VERY_HIGH;
                }
                break;
            case "All molds":
                if (scalar == 0) {
                    level = Reading.levelDescriptorCanonical.ABSENT;
                } else if (scalar > 0 && scalar < 6500) {
                    level = Reading.levelDescriptorCanonical.LOW;
                } else if (scalar >= 6500 && scalar < 13000) {
                    level = Reading.levelDescriptorCanonical.MODERATE;
                } else if (scalar >= 13000 && scalar < 50000) {
                    level = Reading.levelDescriptorCanonical.HIGH;
                } else if (scalar >= 50000) {
                    level = Reading.levelDescriptorCanonical.VERY_HIGH;
                }
                break;
            default:
                level = Reading.levelDescriptorCanonical.PRESENT;
        }

        return level;
    }


}
