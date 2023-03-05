package Hayfevrly.Model;

import Hayfevrly.Main;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Spreadsheet {

    // TODO: stop using the integer allergenID as a unique identifier and instead use the Latin binomen


    private static final String locationSpeciesSS = Main.hfSettings.getProperty("location.data.concordancecsv");
    protected static Map<String, String> binomenToSpeciesCommonName = new HashMap<>();
    protected static Map<String, String> genusToGenusCommonName = new HashMap<>();
    protected static Map<String, String> familiaToFamiliaCommonName = new HashMap<>();

    private static final List<List<String>> lines = new ArrayList<>();
    //    private static Map<Integer, Double> scores = new HashMap<>(); // K = allergenID, V = score
    private static final Map<String, Set<String>> concordance = new TreeMap<>(); // K = text, V = list of allergenIDs associated with that text
    private static final Map<String, Integer> columnHeadsToColumnNumbers = new HashMap<>(); // note: column numbers start at zero
    private static final Map<Integer, String> allergenIDsToCommonName = new HashMap<>();
    private static final Map<String, String> binominaToWeightedKws = new HashMap<>();
    private static final Map<String, String> binomenToGenus = new HashMap<>();
    private static final Map<String, String> genusToFamilia = new HashMap<>();
    private static final Map<String, String> familiaToHabit = new HashMap<>();

    private static final Set<String> allHabits = new HashSet<>();
    private static final Set<String> allFamiliae = new HashSet<>();
    private static final Set<String> allGenera = new HashSet<>();
    private static final Set<String> allBinomina = new HashSet<>();

    private static final Set<String> taxaProcessedForConcordance = new HashSet<>();

    private static final String[] stopWords = {"a", "and", "concentration", "n/a", "or", "the"}; // TODO: this can be expanded

    public static void loadSS() {
        try (Reader r = new FileReader(locationSpeciesSS, StandardCharsets.UTF_8)) {
            Iterable<CSVRecord> csvr = CSVFormat.DEFAULT
                    .parse(r);
            for (CSVRecord record : csvr) {
                if (!record.get(0).startsWith("Y") && !record.get(0).startsWith("Active Row")) {
                    continue;
                }
                List<String> ls = new ArrayList<>();
                Iterator<String> it = record.iterator();
                it.forEachRemaining((s) -> {
                    ls.add(s);
                });
                lines.add(ls);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void populateConcordance() {

        loadSS();

        int curCol = 0;
        for (String header : lines.get(0)) {
            columnHeadsToColumnNumbers.put(header, curCol++);
        }

        for (int curRow = 1; curRow < lines.size(); ++curRow) {
            binomenToSpeciesCommonName.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Binomen")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name"))
            );
            genusToGenusCommonName.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_Common_Name"))
            );
            familiaToFamiliaCommonName.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia_Common_Name"))
            );

            binomenToGenus.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Binomen")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus"))
            );
            genusToFamilia.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia"))
            );
            familiaToHabit.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Habit"))
            );
            binominaToWeightedKws.put(
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Binomen")),
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Binomen_Weighted_kws"))
            );

            allHabits.add(lines.get(curRow).get(columnHeadsToColumnNumbers.get("Habit")));
            allFamiliae.add(lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia")));
            allGenera.add(lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus")));
            allBinomina.add(lines.get(curRow).get(columnHeadsToColumnNumbers.get("Binomen")));
        }

        String addlKeywordsTypeAll = "all";

        String addlKeywordsTypeOther = "any, elsewhere, etc, many, miscellaneous, multiple, other, remaining, rest, types, unidentified";

        // populate habits
        for (int curRow = 1; curRow < lines.size(); ++curRow) {

            String habitName = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Habit"));
            if (taxaProcessedForConcordance.contains(habitName)) { // don't try to add the same habit more than once!
                continue;
            }

            String habitKeywordAccumulator = habitName +
                    " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Habit_singular")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Habit_addl_kws")) + " ";

            if (habitName.startsWith("All ")) {
                habitKeywordAccumulator = habitKeywordAccumulator + addlKeywordsTypeAll + " ";
            } else if (habitName.startsWith("Other ")) {
                habitKeywordAccumulator = habitKeywordAccumulator + addlKeywordsTypeOther + " ";
            }

            // sanitize case and punctuation
            Set<String> habitSanitizedKeywords =
                    new HashSet<>(Arrays.asList(
                            sanitizer(habitKeywordAccumulator).split("\\ +")));

            // expand hyphenated and apostrophe-ess items
            expander(habitSanitizedKeywords);

            // load taxa and their associated terms into the concordance
            for (String s : habitSanitizedKeywords) {
                if (!concordance.containsKey(s)) {
                    Set<String> ss = new HashSet<>();
                    concordance.put(s, ss);
                }
                concordance.get(s).add(habitName);
            }
            taxaProcessedForConcordance.add(habitName);
        }

        // populate familiae
        for (int curRow = 1; curRow < lines.size(); ++curRow) {
            String familiaName = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia"));
            String familiaCommonName = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Familia_Common_Name"));
            if (taxaProcessedForConcordance.contains(familiaName)) { // don't try to add the same familia more than once!
                continue;
            }

            String familiaKeywordAccumulator = familiaName + " ";

            if (familiaCommonName.startsWith("All ")) {
                familiaKeywordAccumulator = familiaKeywordAccumulator + addlKeywordsTypeAll + " ";
            } else if (familiaCommonName.startsWith("Other ")) {
                familiaKeywordAccumulator = familiaKeywordAccumulator + addlKeywordsTypeOther + " ";
            }

            // sanitize case and punctuation
            Set<String> familiaSanitizedKeywords =
                    new HashSet<>(Arrays.asList(
                            sanitizer(familiaKeywordAccumulator).split("\\ +")));

            // expand hyphenated and apostrophe-ess items
            expander(familiaSanitizedKeywords);

            // load taxa and their associated terms into the concordance
            for (String s : familiaSanitizedKeywords) {
                if (!concordance.containsKey(s)) {
                    Set<String> ss = new HashSet<>();
                    concordance.put(s, ss);
                }
                concordance.get(s).add(familiaName);
            }
            taxaProcessedForConcordance.add(familiaName);
        }

        // populate genera
        for (int curRow = 1; curRow < lines.size(); ++curRow) {
            String genusName = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus"));
            String genusCommonName = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_Common_Name"));

            if (taxaProcessedForConcordance.contains(genusName)) { // don't try to add the same familia more than once!
                continue;
            }

            String genusKeywordAccumulator = genusName + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_Common_Name")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_Common_Name_decomposed")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_Common_Name_singular")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_Common_Name_singular_decomposed")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Genus_addl_kws")) + " ";

            if (genusCommonName.startsWith("All ")) {
                genusKeywordAccumulator = genusKeywordAccumulator + addlKeywordsTypeAll + " ";
            } else if (genusCommonName.startsWith("Other ")) {
                genusKeywordAccumulator = genusKeywordAccumulator + addlKeywordsTypeOther + " ";
            }

            // sanitize case and punctuation
            Set<String> genusSanitizedKeywords =
                    new HashSet<>(Arrays.asList(
                            sanitizer(genusKeywordAccumulator).split("\\ +")));

            // expand hyphenated and apostrophe-ess items
            expander(genusSanitizedKeywords);

            // load taxa and their associated terms into the concordance
            for (String s : genusSanitizedKeywords) {
                if (!concordance.containsKey(s)) {
                    Set<String> ss = new HashSet<>();
                    concordance.put(s, ss);
                }
                concordance.get(s).add(genusName);
            }
            taxaProcessedForConcordance.add(genusName);
        }

        // populate binomina (i.e., species)
        for (int curRow = 1; curRow < lines.size(); ++curRow) {

            String binomen = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Binomen"));
            String commonName = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name"));

            String binomenKeywordAccumulator = binomen + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name_decomposed")) + " " +
                    lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name_addl_kws")) + " ";

            if (!binomen.startsWith("Other ")) {
                binomenKeywordAccumulator = binomenKeywordAccumulator + getLikelyPluralForms(lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name"))) + " ";
            }

            StringBuilder sb = new StringBuilder();

            String commonNamesDecomposedString = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Common_Name_decomposed"));
            List<String> commonNamesDecomposed = new ArrayList<>(Arrays.asList(commonNamesDecomposedString.split(",")));
            for (String s : commonNamesDecomposed) {
                s = s.trim();
                sb.append(s);
                sb.append(" ");
                if (!s.endsWith(" X")) {
                    sb.append(getLikelyPluralForms(s));
                    sb.append(" ");
                }
            }

            String commonNamesWPString = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Species_Common_Names_WP"));
            List<String> commonNamesWP = new ArrayList<>(Arrays.asList(commonNamesWPString.split(",")));
            for (String s : commonNamesWP) {
                s = s.trim();
                sb.append(s);
                sb.append(" ");
                sb.append(getLikelyPluralForms(s));
                sb.append(" ");
            }

            String commonNamesWPDecomposedString = lines.get(curRow).get(columnHeadsToColumnNumbers.get("Species_Common_Names_WP_decomposed"));
            List<String> commonNamesWPDecomposed = new ArrayList<>(Arrays.asList(commonNamesWPDecomposedString.split(",")));
            for (String s : commonNamesWPDecomposed) {
                s = s.trim();
                sb.append(s);
                sb.append(" ");
                if (!s.endsWith(" X")) {
                    sb.append(getLikelyPluralForms(s));
                    sb.append(" ");
                }
            }

            if (commonName.startsWith("Other ")) {
                binomenKeywordAccumulator = binomenKeywordAccumulator + addlKeywordsTypeOther + " ";
            }

            binomenKeywordAccumulator = binomenKeywordAccumulator + sb;

            // sanitize case and punctuation
            Set<String> binomenSanitizedKeywords =
                    new HashSet<>(Arrays.asList(
                            sanitizer(binomenKeywordAccumulator).split("\\ +")));

            // expand hyphenated and apostrophe-ess items
            expander(binomenSanitizedKeywords);

            // load taxa and their associated terms into the concordance
            for (String s : binomenSanitizedKeywords) {
                if (!concordance.containsKey(s)) {
                    Set<String> ss = new HashSet<>();
                    concordance.put(s, ss);
                }
                concordance.get(s).add(binomen);
            }
            taxaProcessedForConcordance.add(binomen);
        }

        // remove the x key that was added as a byproduct of using it to indicate adjectival forms
        concordance.remove("x");

    }

    private static String sanitizer(String s) {
        // sanitize case and punctuation
        s = s.toLowerCase();
        s = s.replaceAll("’", "'");

        // replace non-permitted characters with spaces
        String permittedCharacters = "abcdefghijklmnopqrstuvwxyz-' "; // a-z, hyphen, straight apostrophe, forward slash, and space
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (permittedCharacters.indexOf(c) >= 0) {
                sb.append(c);
            } else {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static void expander(Set<String> ss) {
        Set<String> formsToAdd = new HashSet<>();
        for (String s : ss) {
            formsToAdd.addAll(expanderHelper(s));
        }
        ss.addAll(formsToAdd);
    }

    private static Set<String> expanderHelper(String s) {
        // hyphens
        Set<String> formsToAdd = new HashSet<>();
        formsToAdd.add(s);

        if (s.contains("-")) { // e.g., "rag-weed"
            formsToAdd.add(s.replaceAll("-", "")); // we add the closed-up form, e.g. "ragweed"
            formsToAdd.addAll(Arrays.asList(s.split("-"))); // we add the individual components, e.g. "rag" "weed"
        }
        Set<String> results = new HashSet<>(formsToAdd);

        // apostrophe-esses
        formsToAdd.clear(); // re-use this Set
        for (String t : results) {
            if (t.contains("'s")) {
                formsToAdd.add(t.replaceAll("'s", "")); // with apostrophe-ess deleted
                formsToAdd.add(t.replaceAll("'s", "s")); // with apostrophe-ess collapsed to ess
            }
        }
        results.addAll(formsToAdd);

        // forward slashes
        formsToAdd.clear(); // re-use this Set
        for (String t : results) {
            if (t.contains("/")) {
                formsToAdd.add(t.replaceAll("/", " "));
            }
        }
        results.addAll(formsToAdd);

        return results;
    }

    public static void printConcordance() {
        for (Map.Entry<String, Set<String>> me : concordance.entrySet()) {
            System.out.format("%s: %s\n", me.getKey(), me.getValue());
        }
    }


    public static void queryConcordanceDemo() {

        String q = "mesquite";

        TaxonQueryResults tqr = queryTheTaxaConcordance(q);
        System.out.format("\"%s\" probably refers to %s (%s)\n", q, tqr.result, binomenToSpeciesCommonName.get(tqr.result));
    }

    public static TaxonQueryResults queryTheTaxaConcordance(String q) {

        q = sanitizer(q);
        q = q.trim();

//        System.out.println("received: " + q);

        String[] queryElements = q.split("\\ +");

        Map<String, Double> scores = new HashMap<>(); // K = taxon, V = score

        for (String s : queryElements) {
            if (s.isEmpty() || s.isBlank()) {
                continue;
            }

            if (!concordance.containsKey(s)) {
                Database.logEvent(s + " isn't in concordance");
                continue;
            }

            Set<String> associatedTaxa = concordance.get(s);
            for (String taxon : associatedTaxa) {
                scores.put(taxon, 1.0 + scores.getOrDefault(taxon, 0.0));
            }
        }

        if (scores.size() == 0) {
            return new TaxonQueryResults("", TaxonQueryResultMessage.NO_MATCHES);
        }

        Set<String> candidates = new HashSet<>();
        double currentlyLeadingScore = 0.0;

//        scores.entrySet().stream() // this block prints the scores of all possible matches
//                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
//                .forEach((x) -> {
//                    System.out.format("     %s, score: %f\n", x.getKey(), x.getValue());
//                });

        for (Map.Entry<String, Double> me : scores.entrySet()) {
            if (me.getKey().isEmpty() || me.getKey().isBlank()) { // TODO: find a better way to deal with blanks
                continue;
            }
            if (me.getValue() > currentlyLeadingScore) {
                candidates.clear();
                candidates.add(me.getKey());
                currentlyLeadingScore = me.getValue();
            } else if (me.getValue() == currentlyLeadingScore) {
                candidates.add(me.getKey());
            }
        }

        if (candidates.size() == 1) {
            for (String s : candidates) {
                return new TaxonQueryResults(s, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
            }
        } else if (candidates.size() == 0) {
            return new TaxonQueryResults("", TaxonQueryResultMessage.NO_MATCHES);
        }

        // if we've reached this point, we have two or more identifiers // TODO: this is a good logging opportunity
//        System.out.println("Tie:");
//        for (String s : candidates) {
//            System.out.format("%s (%s)\n", s, getCommonNameOfAnyIdentifier(s));
//        }

        /*
            Logic for selecting the correct allergen identifier among the tied contenders
         */

        // if one of the winners has the query as a weighted keyword, then return it immediately
        for (String s : candidates) {
            if (getTaxonomicRank(s).equals("species")) {
                if (binominaToWeightedKws.containsKey(s) && binominaToWeightedKws.get(s).contains(q)) {
                    return new TaxonQueryResults(s, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
                }
            }
        }

        // Differentiate between the true mulberries and Broussonetia papyrifera
        if (!q.contains("Broussonetia")) {
            candidates.remove("Broussonetia papyrifera"); // to consider this species, we need "Broussonetia" in the query;
            //     otherwise it gets mixed up with genus Morus
        }

        // If it's a tie between All X and Other X and query contains either the word "all" or "other",
        // then go with the identifier that has the same word
        String nounAfterAll = null;
        String nounAfterOther = null;
        for (String s : candidates) {
            if (s.startsWith("Other ")) {
                nounAfterOther = s.substring(s.indexOf(" ") + 1);
            }
            if (s.startsWith("All ")) {
                nounAfterAll = s.substring(s.indexOf(" ") + 1);
            }
        }
        if (nounAfterOther != null && nounAfterAll != null) {
            if (nounAfterOther.equals(nounAfterAll)) {
                if (q.contains("all")) {
                    return new TaxonQueryResults("All " + nounAfterAll, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
                } else if (q.contains("other")) {
                    return new TaxonQueryResults("Other " + nounAfterOther, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
                }
            }
        }

        // if we have one species, return that, even if it's tied with higher-ranked taxa
        int numSpecies = 0;
        String speciesName = "";
        for (String s : candidates) {
            if (isBinomen(s)) {
                ++numSpecies;
                speciesName = s;
            }
        }
        if (numSpecies == 1) {
            return new TaxonQueryResults(speciesName, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // TODO: instead of bubbling up promotions from species to genera, etc.,
        //  if we have two or more species of same habit, then interpret them as
        //  "Other trees (including X, Y, and Z) 111 gr/m3"


        // step 1: promote all species to genera
        List<String> listOfWinners = new ArrayList<>(candidates);
        for (String s : listOfWinners) {
            if (isBinomen(s)) {
                String genus = binomenToGenus.get(s);
                candidates.remove(s);
                candidates.add(genus);
            }
        }

        // step 1½: if at any time we are down to 1 taxon in the set, that's our match!
        if (candidates.size() == 1) {
            String taxon = null;
            for (String s : candidates) {
                taxon = s;
            }
            return new TaxonQueryResults(taxon, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // step 1¾: if "Other" is one of the winners, return that now


        // step 2. if any genus has a plurality, return that genus.
        Map<String, Integer> numberOfEachGenus = new HashMap<>();
        for (String s : candidates) {
            numberOfEachGenus.merge(s, 1, Integer::sum);
        }
        List<String> generaCounts = new ArrayList<>();
        for (Map.Entry<String, Integer> me : numberOfEachGenus.entrySet()) {
            generaCounts.add(String.format("%05d", me.getValue()) + me.getKey());
        }
        generaCounts.sort(Collections.reverseOrder());
        if (!generaCounts.get(0).substring(0, 5).equals(generaCounts.get(1).substring(0, 5))) {
            // we have a plurality!
            return new TaxonQueryResults(generaCounts.get(0).substring(5), TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // step 3: since all genera are tied, promote all genera to familiae
        listOfWinners.clear();
        listOfWinners.addAll(candidates);
        for (String s : listOfWinners) {
            if (isGenus(s)) {
                String familia = genusToFamilia.get(s);
                candidates.remove(s);
                candidates.add(familia);
            }
        }

        // step 3½: if at any time we are down to 1 taxon in the set, that's our match!
        if (candidates.size() == 1) {
            String taxon = null;
            for (String s : candidates) {
                taxon = s;
            }
            return new TaxonQueryResults(taxon, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // step 4. if any familia has a plurality, return that familia.
        Map<String, Integer> numberOfEachFamilia = new HashMap<>();
        for (String s : candidates) {
            numberOfEachFamilia.merge(s, 1, Integer::sum);
        }
        List<String> familiaCounts = new ArrayList<>();
        for (Map.Entry<String, Integer> me : numberOfEachFamilia.entrySet()) {
            familiaCounts.add(String.format("%05d", me.getValue()) + me.getKey());
        }
        familiaCounts.sort(Collections.reverseOrder());
        if (!familiaCounts.get(0).substring(0, 5).equals(familiaCounts.get(1).substring(0, 5))) {
            // we have a plurality!
            return new TaxonQueryResults(familiaCounts.get(0).substring(5), TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // step 5: since all familiae are tied, promote the familiae to habits
        listOfWinners.clear();
        listOfWinners.addAll(candidates);
        for (String s : listOfWinners) {
            if (isFamilia(s)) {
                String habit = familiaToHabit.get(s);
                candidates.remove(s);
                candidates.add(habit);
            }
        }

        // step 5½: if at any time we are down to 1 taxon in the set, that's our match!
        if (candidates.size() == 1) {
            String taxon = null;
            for (String s : candidates) {
                taxon = s;
            }
            return new TaxonQueryResults(taxon, TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // step 6: if any habit has a plurality, return that habit.
        Map<String, Integer> numberOfEachHabit = new HashMap<>();
        for (String s : candidates) {
            numberOfEachHabit.merge(s, 1, Integer::sum);
        }
        List<String> habitCounts = new ArrayList<>();
        for (Map.Entry<String, Integer> me : numberOfEachHabit.entrySet()) {
            habitCounts.add(String.format("%05d", me.getValue()) + me.getKey());
        }
        habitCounts.sort(Collections.reverseOrder());
        if (!habitCounts.get(0).substring(0, 5).equals(habitCounts.get(1).substring(0, 5))) {
            // we have a plurality!
            return new TaxonQueryResults(habitCounts.get(0).substring(5), TaxonQueryResultMessage.SUCCESSFUL_QUERY);
        }

        // step 7: if all habits are tied, return an error!
        return new TaxonQueryResults("", TaxonQueryResultMessage.AMBIGUOUS_MATCH_SET);
    }

    private static boolean isVowel(String s) {
        String vowels = "aeiou";
        return (vowels.contains(s));
    }

    private static String getLikelyPluralForms(String s) { // returned String is space-delimited plural forms
        // guard clauses
        if (s.isBlank()) {
            return ""; // return empty string
        } else if (s.length() < 3) {
            return s + "s " + s + "es";
        }

        Set<String> results = new HashSet<>();
        String penLetter = s.substring(s.length() - 2, s.length() - 1); // pen = penultimate
        String minusLastLetter = s.substring(0, s.length() - 1);
        String minusLastTwoLetters = s.substring(0, s.length() - 2);

        // logic source: https://www.grammarly.com/blog/plural-nouns/

        // Grammarly rule 2
        if (s.endsWith("s") ||
                s.endsWith("ss") ||
                s.endsWith("sh") ||
                s.endsWith("ch") ||
                s.endsWith("x") ||
                s.endsWith("z")) {
            results.add(s + "es");
        }

        // Grammarly rule 3, part 1 of 2
        if (s.endsWith("s") && !penLetter.equals("s")) {
            results.add(s + "ses");
        }

        // Grammarly rule 3, part 2 of 2
        if (s.endsWith("z")) {
            results.add(s + "zes");
        }

        // Grammarly rule 8
        if (s.endsWith("us")) {
            results.add(minusLastTwoLetters + "i");
            results.add(s + "es");
            results.add(s + "ses");
        }

        // Grammarly rule 4, part 1 of 2
        if (s.endsWith("f")) {
            results.add(s + "s");
            results.add(minusLastLetter + "ves");
        }

        // Grammarly rule 4, part 2 of 2
        if (s.endsWith("fe")) {
            results.add(s + "s");
            results.add(minusLastTwoLetters + "ves");
        }

        if (s.endsWith("y")) {
            if (!isVowel(penLetter)) {                // Grammarly rule 5
                results.add(minusLastLetter + "ies");
            } else {                                  // Grammarly rule 6
                results.add(s + "s");
            }
        }

        // Grammarly rule 7
        if (s.endsWith("o")) {
            results.add(s + "es");
            results.add(s + "s");
        }

        // Grammarly rule 9
        if (s.endsWith("is")) {
            results.add(minusLastTwoLetters + "es");
        }

        // Grammarly rule 10
        if (s.endsWith("on")) {
            results.add(minusLastTwoLetters + "a");
            results.add(s + "s");
        }

        // Grammarly rule 1 -- the catchall!
        if (results.isEmpty()) {
            results.add(s + "s");
        }

        StringBuilder sb = new StringBuilder();
        results.forEach((x) -> {
            sb.append(x);
            sb.append(" ");
        });

        return sb.toString();
    }

    public static int getPossibleAllergenIDsForQuery(String q) {

        // q will be transformed into a list of forms, and each form will be checked against concordance

        /*
        query transformations:
        - lowercase text before checking it against concordance
        - curly apostrophes become straight apostrophes
        - text with apostrophes are kept but closed up and minus apostrophe-ess forms are added
         */

        return -1; // TODO: fixme
    }

    public static String getTaxonomicRank(String s) {
        if (isBinomen(s)) {
            return "species";
        } else if (isGenus(s)) {
            return "genus";
        } else if (isFamilia(s)) {
            return "family";
        } else {
            return "habit";
        }
    }

    public static String getHabit(String s) {
        if (isBinomen(s)) {
            return familiaToHabit.get(genusToFamilia.get(binomenToGenus.get(s)));
        } else if (isGenus(s)) {
            return familiaToHabit.get(genusToFamilia.get(s));
        } else if (isFamilia(s)) {
            return familiaToHabit.get(s);
        } else if (isHabit(s)) {
            return s;
        }
        return null;
    }

    public static String getFamilia(String s) {
        if (isBinomen(s)) {
            return genusToFamilia.get(binomenToGenus.get(s));
        } else if (isGenus(s)) {
            return genusToFamilia.get(s);
        } else if (isFamilia(s)) {
            return s;
        } else { // isHabit()
            return null;
        }
    }

    public static String getGenus(String s) {
        if (isBinomen(s)) {
            return binomenToGenus.get(s);
        } else if (isGenus(s)) {
            return s;
        } else { // isFamilia(), isHabit()
            return null;
        }
    }

    public static String getBinomen(String s) {
        if (isBinomen(s)) {
            return s;
        } else { // isGenus(), isFamilia(), isHabit()
            return null;
        }
    }


    public static String getParent(String s) {
        if (isBinomen(s)) {
            return binomenToGenus.get(s);
        } else if (isGenus(s)) {
            return genusToFamilia.get(s);
        } else if (isFamilia(s)) {
            return familiaToHabit.get(s);
        }
        return s; // I might change this behavior later; currently parent of habit is same habit
    }

    private static boolean isBinomen(String s) {
        return allBinomina.contains(s);
    }

    private static boolean isFamilia(String s) {
        return allFamiliae.contains(s);
    }

    private static boolean isGenus(String s) {
        return allGenera.contains(s);
    }

    public static boolean isHabit(String s) {
        return allHabits.contains(s);
    }

    public static String getCommonNameOfAnyIdentifier(String s) {
        String commonName = null;
        switch (getTaxonomicRank(s)) {
            case "species":
                commonName = binomenToSpeciesCommonName.get(s);
                break;
            case "genus":
                commonName = genusToGenusCommonName.get(s);
                break;
            case "family":
                commonName = familiaToFamiliaCommonName.get(s);
                break;
            case "habit":
                commonName = s;
                break;
            default:
                commonName = "n/a";
        }
        return commonName;
    }

    public enum TaxonQueryResultMessage {
        SUCCESSFUL_QUERY,
        NO_MATCHES,
        AMBIGUOUS_MATCH_SET
    }

    public record TaxonQueryResults(String result, TaxonQueryResultMessage message) {
    }

}
