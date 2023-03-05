package Hayfevrly.Model;

public class Geo {

    /*
        • coordinate system is WGS84
        • use meters internally, but display to user either m, km, or mi (user preference)
        • use degrees generally, but radians are used inside some functions (e.g., haversineDistanceMeters())

     */

    public static double haversineDistanceMeters(double lat1, double long1, double lat2, double long2) {
        final double R = 6_371_000.0; // Earth's radius in meters

        final double φ1 = lat1 * Math.PI / 180; // φ (lat.) and λ (long.) are in radians
        final double φ2 = lat2 * Math.PI / 180;
        final double Δφ = Math.abs(lat2 - lat1) * Math.PI / 180;
        final double Δλ = Math.abs(long2 - long1) * Math.PI / 180;

        final double a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                        Math.sin(Δλ / 2) * Math.sin(Δλ / 2);

        final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        final double d = R * c; // d = distance in meters
        return d;
    }

    public static double bearingDegrees(double sourceLat, double sourceLong, double destLat, double destLong) {
        return 0.0;
    }

    public static double reverseBearingDegrees(double bearing) {
        return 0.0;
    }

    public static double compassPointToDegrees(String point) {
        return 0.0;
    }

    public static String degreesToCompassPoint(double degrees, int numberOfWinds) {
        // numberOfWinds could be: 4, 8, 16
        return "";
    }


}



/*

    all images:
    pixel dimensions (x, y): 1130 by 2347

    upper left marker:
    red pixel (255, 0, 0)
    T intersection btw
        Shackleford Cty
    Callahan Cty, Eastland Cty
    is at:
    32.515907226932725, -99.114546087242

    upper-right marker:
    blue pixel (0, 0, 255)
    T intersection btw
        Dallas Cty
    Ellis Cty, Kaufman Cty
    is at:
    32.54523150606743, -96.52960931823775

    num pixels btw upper markers: deltap
    num miles btw upper markers: deltami
    mi/px along upper latitude: ???
    pixel row associated with this mi/px: ???

    - - - - - - - - - - - - - - - - - - - - - -

    bottom left marker:
    green pixel (0, 255, 0)
    cross intersection btw
    La Salle Cty, McMullen Cty,
    Webb Cty, Duval Cty
    is at:
    28.057142709979196, -98.80512636020913

    bottom right marker:
    cyan pixel (0, 255, 255)
    triple point of
                      Refugio Cty
    San Patricio Cty, Aransas Cty
    is at:
    28.07543207765058, -97.26024571028886

    num pixels btw lower markers: deltap
    num miles btw lower markers: deltami
    mi/px along lower latitude: ???
    pixel row associated with this mi/px: ???

    - - - - - - - - - - - - - - - - - - - - - -

    TODO: find: delta of miles/vertical pixel = ??? (this will be constant everywhere in image)
    TODO: find: from top of image to bottom (southerly), delta of miles/horizontal pixel = ???

    TODO: Tim, avoid accumulation errors. don't keep adding the delta. Instead calculate
    percentage across row, and multiple pixels by appropriate delta.

    TODO: L:\Dropbox\Hayfevrly\species\Juniperus ashei\distribution\biomass, larger scale

 */
