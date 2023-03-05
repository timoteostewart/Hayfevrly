package Hayfevrly.Model;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.conf.ConnectionUrlParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_OK;

public class NWS {

    /*
        My area of interest: EWX office, gridX 137--177 (41 cells), gridY 78--112 (35 cells)
        total: 1,435 cells
     */

    // TODO: save list of coordinates that error out, and attempt to pick them up after completion of main loop
    // store gridX and gridY coordinates of missing data using a List<Pair<Integer, Integer>>
    // store successful data in matrix, and only create html table after attempting to plug the holes with above List

    static final int MIN_GRID_X = 137;
    static final int MAX_GRID_X = 177;
    static final int MAX_GRID_Y = 112;
    static final int MIN_GRID_Y = 78;

    static final String forecastURLprefix = "https://api.weather.gov/gridpoints/EWX/";
    static final String forecastURLsuffix = "/forecast/hourly";

    static final String headerUserAgent = "Hayfevrly/1.0 website www.hayfevr.ly, email timoteostewart1977@gmail.com";
    static final String headerFrom = "timoteostewart1977@gmail.com";

    static final int PAUSE_AFTER_SUCCESSFUL_REQUEST = 0;
    static final int PAUSE_AFTER_SERVER_ERROR = 15;

    static final int MAX_NUMBER_OF_ERRORS_IN_A_ROW = 20;

    public static void accessNWSApi() {

        System.out.println(Time.getNowInCentralTimeLdt());

        StringBuilder table = new StringBuilder();
        table.append("<table>\n");

        int numberOfErrorsInARow = 0;

        callAPI:
        for (int j = MAX_GRID_Y; j >= MIN_GRID_Y; --j) { // north to south

            table.append("<tr>");

            for (int i = MIN_GRID_X; i <= MAX_GRID_X; ++i) { // west to east

                if (numberOfErrorsInARow > MAX_NUMBER_OF_ERRORS_IN_A_ROW) {
                    System.out.println("Too many errors!");
                    break callAPI;
                }

                String currentEndpoint = forecastURLprefix + i + "," + j + forecastURLsuffix;
                String jsonString = null;
                JsonNode node = null;
                HttpsURLConnection conn = null;

                try {
                    URL url = new URL(currentEndpoint);
                    conn = (HttpsURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/geo+json;version=1");
                    conn.setRequestProperty("Connection", "close");
                    conn.setRequestProperty("User-Agent", headerUserAgent);
                    conn.setRequestProperty("From", headerFrom);

                    int responseCode = conn.getResponseCode();
                    String responseMessage = conn.getResponseMessage();

//                    printHeaderFields(conn);

                    if (responseCode == HTTP_OK) {
                        numberOfErrorsInARow = 0;

                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String brrl = null;
                        StringBuilder sb = new StringBuilder();
                        while ((brrl = br.readLine()) != null) {
                            sb.append(brrl);
                            sb.append("\n");
                        }
                        conn.disconnect();

                        jsonString = sb.toString();
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            node = mapper.readTree(jsonString);
                        } catch (JacksonException e) {
                            // catches JsonMappingException and JsonProcessingException
                            e.printStackTrace();
                        }

                        System.out.format("%d,%d: %s\n", i, j, getWindDirString(node, 1));
                        String windDir = getWindDirString(node, 1);
                        int windSpeed = getWindSpeedMph(node, 1);
                        table.append("<td><div" +
                                " class=\"" + windDir.toLowerCase() + "\"" +
                                " data-gridx=\"" + i + "\"" +
                                " data-gridy=\"" + j + "\"" +
                                " data-direction=\"" + windDir + "\"" +
                                " data-speed=\"" + windSpeed + "\">" +
                                "🠑" +
                                "</div></td>");

                        Time.successfulPause(PAUSE_AFTER_SUCCESSFUL_REQUEST);

                    } else { // responseCode == HTTP_INTERNAL_ERROR or HTTP_UNAVAILABLE or HTTP_FORBIDDEN
                        ++numberOfErrorsInARow;

                        conn.disconnect();

                        System.out.println("Server returned: " + responseMessage + "," +
                                " code " + responseCode + ";" +
                                " errors in a row: " + numberOfErrorsInARow);

                        System.out.format("%d,%d: %s\n", i, j, "error");
                        table.append("<td><div" +
                                " class=\"no-data\"" +
                                " data-gridx=\"" + i + "\"" +
                                " data-gridy=\"" + j + "\"" +
                                " data-direction=\"no-data\"" +
                                " data-speed=\"no-data\">" +
                                "∅" +
                                "</div></td>");

                        Time.successfulPause(PAUSE_AFTER_SERVER_ERROR * numberOfErrorsInARow);
                    }


                } catch (Exception e) {
                    System.out.println(e);
                }


            }

            table.append("</tr>\n");
        }

        table.append("</table>");

        System.out.println(table);

        System.out.println(Time.getNowInCentralTimeLdt());


    }

    static String getWindDirString(JsonNode node, int periodNumber) {
        String windDir = node.get("properties").get("periods").get(periodNumber - 1).get("windDirection").asText();
        return windDir;
    }

    static int getWindSpeedMph(JsonNode node, int periodNumber) {
        String windSpeedString = node.get("properties").get("periods").get(periodNumber - 1).get("windSpeed").asText();
        int magnitudeOnly = Integer.parseInt(windSpeedString.substring(0, windSpeedString.indexOf(" ")));
        return magnitudeOnly;
    }

    static void printHeaderFields(HttpsURLConnection conn) {
        System.out.format("\n\n\n");
        for (Map.Entry<String, List<String>> me : conn.getHeaderFields().entrySet()) {
            System.out.println(me.getKey());
            me.getValue().forEach((x) -> System.out.println("   " + x));
        }
    }


}
