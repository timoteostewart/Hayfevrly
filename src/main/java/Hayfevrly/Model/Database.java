package Hayfevrly.Model;

import Hayfevrly.Main;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Database {

    private static final String jdbcDriver = "com.mysql.cj.jdbc.Driver";

    private static final String[] scrapingSourceIds = new String[]{
            "aacg",
            "aacg_via_kxan",
            "aacg_via_nabweb",
            "aacg_via_spectrum",
            "aoa_via_spectrum",
            "kvue"
    };

    private static Connection connection;

    public static void startConnection() {

        String databaseUsername = Main.hfSecrets.getProperty("secrets.database.username");
        String databasePassword = Main.hfSecrets.getProperty("secrets.database.password");

        String databaseHost = Main.hfSecrets.getProperty("secrets.database.host");
        String databasePort = Main.hfSecrets.getProperty("secrets.database.port");
        String databaseDbName = Main.hfSecrets.getProperty("secrets.database.db_name");

        // final String url = "jdbc:mysql://timstewart.io:3306/HF_a2h";
        final String url = "jdbc:mysql://" + databaseHost + ":" + databasePort + "/" + databaseDbName;

        // TODO: add better exception handling around the connecting to the database, e.g., if the DB is unreachable due to no internet or unreachable due to DB down (figure out both cases), and then note this in the log

        try {
            Class.forName(jdbcDriver);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            // TODO: if we can't load the class, then we shouldn't continue on below to opening a connection...
        }

        try {
            connection = DriverManager.getConnection(url, databaseUsername, databasePassword);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static ResultSet getRS(String query) {
        ResultSet rs = null;
        try {
            rs = connection.createStatement().executeQuery(query);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rs;
    }

    public static boolean existReadingsForToday(String dataSourceID) {

        // Main.hfSecrets.getProperty("secrets.database.readings_table_name")

        String originatingEntity = null;
        String immediateSource = null;
        if (dataSourceID.contains("_via_")) {
            int indexOfVia = dataSourceID.indexOf("_via_");
            originatingEntity = dataSourceID.substring(0, indexOfVia);
            immediateSource = dataSourceID.substring(indexOfVia + 5);
        } else {
            originatingEntity = dataSourceID;
            immediateSource = dataSourceID;
        }

        try (PreparedStatement checkForTodaysReadings = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + Main.hfSecrets.getProperty("secrets.database.readings_table_name") +
                        " WHERE originating_entity = ?" +
                        " AND immediate_source = ?" +
                        " AND when_acquired >= ?;")) {

            checkForTodaysReadings.setString(1, originatingEntity);
            checkForTodaysReadings.setString(2, immediateSource);
            checkForTodaysReadings.setString(3, Time.getFirstMinuteOfTodaySQL());
            ResultSet rs = checkForTodaysReadings.executeQuery();
            Integer numRows = null;
            if (rs != null) {
                while (rs.next()) {
                    numRows = rs.getInt(1);
                }
                if (numRows == null) {
                    return false;
                } else if (numRows == 0) {
                    return false;
                } else {
//                    System.out.println(numRows + " readings for today already for " + originatingEntity + "_via_" + immediateSource);
                    return true;
                }
            } else {
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean logEvent(String eventName) {

        try {
            if (!connection.isValid(15)) {
                startConnection();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        String hostnameFromSettings = Main.hfSettings.getProperty("host.name");
        String hostnameAndIPAddressFromSystemCall;
        String hostnameForLog;

        try {
            hostnameAndIPAddressFromSystemCall = String.valueOf(InetAddress.getLocalHost());
            hostnameForLog = hostnameFromSettings + "; " + hostnameAndIPAddressFromSystemCall;
        } catch (UnknownHostException e) {
            hostnameForLog = hostnameFromSettings + "; " + "failed to get host name or IP address via system call";
        }

        String timeNow = Time.ldtToSqlDatetime(Time.getNowInCentralTimeLdt());

        try (PreparedStatement storeEventLogline = connection.prepareStatement(
                "INSERT INTO event_log" +
                        " (access_time, host, event)" +
                        " VALUES(?, ?, ?);")) {
            storeEventLogline.setString(1, timeNow);
            storeEventLogline.setString(2, hostnameForLog);
            storeEventLogline.setString(3, eventName);

            int rowsChanged = storeEventLogline.executeUpdate();

            System.out.println(timeNow + " " + hostnameForLog + " " + eventName);

            return (rowsChanged == 1);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

    }

    public static boolean storeReadingV2(Reading reading) {

        try {
            if (!connection.isValid(15)) {
                startConnection();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        String originatingEntity = reading.getOriginating_entity();
        String immediateSource = reading.getImmediate_source();

        if (originatingEntity.equals(immediateSource)) {
            if (!isValidDataSourceID(originatingEntity)) {
                return false;
            }
        } else {
            if (!isValidDataSourceID(originatingEntity + "_via_" + immediateSource)) {
                return false;
            }
        }

        try (PreparedStatement storeReading = connection.prepareStatement(
                "INSERT INTO " + Main.hfSecrets.getProperty("secrets.database.readings_table_name") +
                        " (originating_entity, immediate_source, when_acquired, allergen_identifier," +
                        " measurement_scalar, measurement_unit, level_descriptor_canonical)" +
                        " VALUES(?, ?, ?, ?, ?, ?, ?);")) {

            // non-null fields
            storeReading.setString(1, originatingEntity);
            storeReading.setString(2, immediateSource);
            storeReading.setString(3, Time.ldtToSqlDatetime(reading.getWhen_acquired_ldt()));
            storeReading.setString(4, reading.getAllergen_identifier());

            // nullable fields
            if (reading.getMeasurement_scalar() != null) {
                storeReading.setInt(5, reading.getMeasurement_scalar());
            } else {
                storeReading.setNull(5, Types.NULL);
            }

            if (reading.getMeasurement_unit() != null) {
                storeReading.setString(6, reading.getMeasurement_unit());
            } else {
                storeReading.setNull(6, Types.NULL);
            }

            if (reading.getLevel_descriptor_canonical() != null) {
                storeReading.setString(7, reading.getLevel_descriptor_canonical().name());
            } else {
                storeReading.setNull(7, Types.NULL);
            }

            int rowsChanged = storeReading.executeUpdate();
            return (rowsChanged == 1);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    static void reconcileTodaysReadings(String originatingEntity) {
        // delete any existing reconciled readings for this originatingEntity from earlier today
        try (PreparedStatement deleteTodaysReconciledReadings = connection.prepareStatement(
                "DELETE FROM reconciled_readings" +
                        "     WHERE originating_entity = ?" +
                        "     AND when_acquired >= ?;")) {
            deleteTodaysReconciledReadings.setString(1, originatingEntity);
            deleteTodaysReconciledReadings.setString(2, Time.getFirstMinuteOfTodaySQL());
            deleteTodaysReconciledReadings.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // reconcile today's readings from this source
        List<Reading> scrapedReadings = new ArrayList<>();
        try (PreparedStatement collectTodaysScrapedReadingsForSameOriginatingEntity = connection.prepareStatement(
                "SELECT * FROM " + Main.hfSecrets.getProperty("secrets.database.readings_table_name") +
                        " WHERE originating_entity = ?" +
                        " AND when_acquired >= ?;")) {

            collectTodaysScrapedReadingsForSameOriginatingEntity.setString(1, originatingEntity);
            collectTodaysScrapedReadingsForSameOriginatingEntity.setString(2, Time.getFirstMinuteOfTodaySQL());
            ResultSet rs = collectTodaysScrapedReadingsForSameOriginatingEntity.executeQuery();
            while (rs.next()) {
                Reading r = new Reading(rs.getString("allergen_identifier"));
                r.setOriginating_entity(rs.getString("originating_entity"));
                r.setImmediate_source(rs.getString("immediate_source"));
                r.setWhen_acquired_ldt(Time.sqlDatetimeToLdt(rs.getString("when_acquired")));
                r.setMeasurement_scalar(rs.getInt("measurement_scalar"));
                r.setMeasurement_unit(rs.getString("measurement_unit"));
                r.setLevel_descriptor_canonical(Reading.levelDescriptorCanonical.valueOf(rs.getString("level_descriptor_canonical")));
                scrapedReadings.add(r);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        Map<String, List<Reading>> todaysReadingsForSameOriginatingEntity = new HashMap<>();
        for (Reading r : scrapedReadings) {
            if (!todaysReadingsForSameOriginatingEntity.containsKey(r.getAllergen_identifier())) {
                todaysReadingsForSameOriginatingEntity.put(r.getAllergen_identifier(), new ArrayList<>());
            }
            todaysReadingsForSameOriginatingEntity.get(r.getAllergen_identifier()).add(r);
        }

        List<Reading> reconciledReadings = new ArrayList<>();

        for (Map.Entry<String, List<Reading>> me : todaysReadingsForSameOriginatingEntity.entrySet()) {
//            System.out.format("found %d reading%s for identifier \"%s\":\n", me.getValue().size(), (me.getValue().size() == 1 ? "" : "s"), me.getKey());

            Reading consolidatedReading = new Reading(me.getKey()); // constructor receives `allergen_identifier`
            consolidatedReading.setOriginating_entity(originatingEntity);
            consolidatedReading.setWhen_acquired_ldt(Time.getNowInCentralTimeLdt());

            Set<Integer> m_s = new TreeSet<>();
            Set<Reading.levelDescriptorCanonical> l_d_c = new TreeSet<>();
            for (Reading r : me.getValue()) {
                // collect measurement_scalar
                if (r.getMeasurement_scalar() != null && r.getMeasurement_scalar() >= 0) {
                    m_s.add(r.getMeasurement_scalar());
                }
                // collect level_descriptor_canonical
                if (r.getLevel_descriptor_canonical() != null) {
                    l_d_c.add(r.getLevel_descriptor_canonical());
                }
            }
            // reconcile measurement_scalar
            if (m_s.size() > 0) { // if we have any scalars at all
                m_s.forEach(consolidatedReading::setMeasurement_scalar); // assign the greatest (i.e., last in order) value
            } else {
                consolidatedReading.setMeasurement_scalar(null);
            }
            // reconcile level_descriptor_canonical
            if (l_d_c.size() > 0) { // if we have any levels at all
                l_d_c.forEach(consolidatedReading::setLevel_descriptor_canonical); // assign l_d_c's greatest (i.e., last) value
            } else {
                consolidatedReading.setLevel_descriptor_canonical(Reading.levelDescriptorCanonical.UNCERTAIN);
            }

            /*
                Logic for individual readings
             */
            if (consolidatedReading.getLevel_descriptor_canonical().compareTo(Reading.levelDescriptorCanonical.ABSENT) < 0) {
                if (consolidatedReading.getMeasurement_scalar() <= 0) {
                    consolidatedReading.setMeasurement_scalar(-1);
                }
            }

            reconciledReadings.add(consolidatedReading);
        }

        /*
            Any logic for the collection of reconciled readings
         */
        // create a utility map to help pull up readings as needed
//        Map<String, Reading> allergens = new HashMap<>();
//        for (Reading r : reconciledReadings) {
//            allergens.put(r.getAllergen_identifier(), r);
//        }

        // load these reconciled readings to database table `reconciled_readings`
        for (Reading r : reconciledReadings) {
            storeReconciledReading(r);
        }

//        System.out.format("%d readings pre-reconciliation\n%d readings post\n\n", scrapedReadings.size(), reconciledReadings.size());
    }

    public static boolean storeReconciledReading(Reading reading) {
        String originatingEntity = reading.getOriginating_entity();

        try (PreparedStatement storeReading = connection.prepareStatement(
                "INSERT INTO reconciled_readings" +
                        " (originating_entity, when_acquired," +
                        " allergen_identifier," +
                        " measurement_scalar, measurement_unit, level_descriptor_canonical)" +
                        " VALUES(?, ?, ?, ?, ?, ?);")) {

            // non-null fields
            storeReading.setString(1, originatingEntity);
            storeReading.setString(2, Time.ldtToSqlDatetime(reading.getWhen_acquired_ldt()));
            storeReading.setString(3, reading.getAllergen_identifier());

            // nullable fields
            if (reading.getMeasurement_scalar() != null) {
                storeReading.setInt(4, reading.getMeasurement_scalar());
            } else {
                storeReading.setNull(4, Types.NULL);
            }

            if (reading.getMeasurement_unit() != null) {
                storeReading.setString(5, reading.getMeasurement_unit());
            } else {
                storeReading.setNull(5, Types.NULL);
            }

            if (reading.getLevel_descriptor_canonical() != null) {
                storeReading.setString(6, reading.getLevel_descriptor_canonical().name());
            } else {
                storeReading.setNull(6, Types.NULL);
            }

            int rowsChanged = storeReading.executeUpdate();
            return (rowsChanged == 1);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean storeReport(LocalDateTime when_generated, String contents) {

        try (PreparedStatement storeReport = connection.prepareStatement(
                "INSERT INTO " + Main.hfSecrets.getProperty("secrets.database.reports_table_name") +
                        " (when_generated, contents)" +
                        " VALUES(?, ?);")) {


            storeReport.setString(1, Time.ldtToSqlDatetime(when_generated));
            storeReport.setString(2, contents);

            int rowsChanged = storeReport.executeUpdate();
            return (rowsChanged == 1);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<Reading> getReconciledReadingsForOriginatingEntity(String originatingEntity) {

        // rough draft is to print the report to console
        // but eventually we want to store the report in the database:
        //      - table: dailyReports
        //      - columns: id, originating_entity, when_report_filed, report_contents

        List<Reading> results = new ArrayList<>(); // what we'll return
        try (PreparedStatement collectTodaysReconciledReadingsForSameOriginatingEntity = connection.prepareStatement(
                "SELECT * FROM reconciled_readings" +
                        " WHERE originating_entity = ?" +
                        " AND when_acquired >= ?;")) {
            collectTodaysReconciledReadingsForSameOriginatingEntity.setString(1, originatingEntity);
            collectTodaysReconciledReadingsForSameOriginatingEntity.setString(2, Time.getFirstMinuteOfTodaySQL());
            ResultSet rs = collectTodaysReconciledReadingsForSameOriginatingEntity.executeQuery();
            while (rs.next()) {
                Reading r = new Reading(rs.getString("allergen_identifier"));
                r.setOriginating_entity(rs.getString("originating_entity"));
                r.setWhen_acquired_ldt(Time.sqlDatetimeToLdt(rs.getString("when_acquired")));
                r.setMeasurement_scalar(rs.getInt("measurement_scalar"));
                r.setMeasurement_unit(rs.getString("measurement_unit"));
                r.setLevel_descriptor_canonical(Reading.levelDescriptorCanonical.valueOf(rs.getString("level_descriptor_canonical")));
                results.add(r);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return results;
        }

        return results;
    }


    private static boolean isValidDataSourceID(String possibleDataSourceID) {
        for (String s : scrapingSourceIds) {
            if (s.equals(possibleDataSourceID)) {
                return true;
            }
        }
        return false;
    }


}
