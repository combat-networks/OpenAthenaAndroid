package com.openathena;

import android.util.Log;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.io.InputStream;
import java.io.PrintWriter;

import java.lang.Math;

import java.lang.IllegalArgumentException;
import java.lang.NullPointerException;

import com.openathena.RequestedValueOOBException;
import com.openathena.geodataAxisParams;

import mil.nga.tiff.*;

public class GeoTIFFParser {

    public static String TAG = GeoTIFFParser.class.getSimpleName();

    private File geofile;

    private TIFFImage tiffImage;
    private List<FileDirectory> directories;
    private FileDirectory directory;
    private Rasters rasters;

    private geodataAxisParams xParams;
    private geodataAxisParams yParams;

    GeoTIFFParser() {
        geofile = null;

        TIFFImage tiffImage = null;
        List<FileDirectory> directories = null;
        FileDirectory directory = null;
        Rasters rasters = null;

    }

    GeoTIFFParser(File geofile) throws IllegalArgumentException{
        this();
        this.geofile = geofile;
        loadGeoTIFF(geofile);
    }

    /**
     * Loads a GeoTIFF Digital Elevation Model geofile into the parent GeoTIFFParser object's instance
     * <p>
     *     This function takes in a Java file object and loads it into the parent GeoTIFFParser object.
     *     Once loaded, the GeoTIFFParser object (via {@link com.openathena.GeoTIFFParser#getAltFromLatLon(double, double)} will be able to provide the nearest elevation value from a given latitude longitude pair
     *
     * </p>
     * @param geofile a Java file object which should represent a GeoTIFF DEM
     * @throws IllegalArgumentException if geofile cannot be read or is rotated or skewed
     */
    public void loadGeoTIFF(File geofile) throws IllegalArgumentException {
        this.geofile = geofile;
  /*      this.geodata = gdal.Open(geofile);
        this.geoTransform = getGeoTransform(geodata);*/

        try {
            tiffImage = TiffReader.readTiff(geofile);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read geofile: " + e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }

        directories = tiffImage.getFileDirectories();
        directory = directories.get(0);
        rasters = directory.readRasters();

        for (int i = 0; i < directories.size(); i++ ) {
            FileDirectory aDirectory = directories.get(i);
            Log.d("info", "\nFile Directory:");
            Log.d("info", String.valueOf(i));
            Log.d("info","\n");
            Rasters theseRasters = aDirectory.readRasters();
            Log.d("info","\n");
            Log.d("info","Rasters:");
            Log.d("info", "Width: " + rasters.getWidth());
            Log.d("info", "Height: " + rasters.getHeight());
            Log.d("info", "Number of Pixels: " + rasters.getNumPixels());
            Log.d("info", "Samples Per Pixel: " + rasters.getSamplesPerPixel());
            Log.d("info", "Bits Per Sample: " + rasters.getBitsPerSample());

            Log.d("info", "0,0 is: " + theseRasters.getPixel(0, 0)[0].doubleValue() );

        }

        List<Double> pixelAxisScales = directory.getModelPixelScale();
        if (pixelAxisScales.get(2) != 0.0d) {
            throw new IllegalArgumentException("ERROR: failed to load a rotated or skewed GeoTIFF!");
        }
        List<Double> tiePoint = directory.getModelTiepoint();
        Number imgWidth = directory.getImageWidth();
        Number imgHeight = directory.getImageHeight();

        Log.d("info", "pixelAxisScales:" + pixelAxisScales.toString());
        Log.d("info", "tiePoint: " + tiePoint);
        Log.d("info", "imgWidth: " + imgWidth );
        Log.d("info", "imgHeight: " + imgHeight);

        this.xParams = new geodataAxisParams();
        this.xParams.start = tiePoint.get(3);
        this.xParams.stepwiseIncrement = pixelAxisScales.get(0);
        this.xParams.numOfSteps = imgWidth.longValue();
        this.xParams.calcEndValue();

        this.yParams = new geodataAxisParams();
        this.yParams.start = tiePoint.get(4);
        this.yParams.stepwiseIncrement = -1.0d * pixelAxisScales.get(1);
        this.yParams.numOfSteps = imgHeight.longValue();
        this.yParams.calcEndValue();
    }

    /**
     * Gets the spacing between X datapoints of the loaded GeoTIFF DEM
     * @return double degrees between each datapoint along the X direction
     */
    public double getXResolution() { return xParams.stepwiseIncrement; }

    /**
     * Gets the spacing between Y datapoints of the loaded GeoTIFF DEM
     * @return double degrees between each datapoint along the Y direction
     */
    public double getYResolution() { return yParams.stepwiseIncrement; }

    /**
     * Gets the number of columns (width) of the loaded GeoTIFF DEM
     * @return long number of pixels equivalent to the DEM's width
     */
    public long getNumCols() { return xParams.numOfSteps; }

    /**
     * Gets the number of rows (height) of the loaded GeoTIFF DEM
     * @return long number of pixels equivalent to the DEM's height
     */
    public long getNumRows() { return yParams.numOfSteps; }

    /**
     * Gets the minimum longitude (inclusive) covered by the loaded GeoTIFF DEM
     * @return double the longitude of the western-most datapoint of the loaded GeoTIFF DEM
     */
    public double getMinLon() { return Math.min(xParams.end, xParams.start); }

    /**
     * Gets the maximum longitude (inclusive) covered by the loaded GeoTIFF DEM
     * @return double the longitude of the eastern-most datapoint of the loaded GeoTIFF DEM
     */
    public double getMaxLon() { return Math.max(xParams.end, xParams.start); }

    /**
     * Gets the minimum latitude (inclusive) covered by the loaded GeoTIFF DEM
     * @return double the latitude of the southern-most datapoint of the loaded GeoTIFF DEM
     */
    public double getMinLat() { return Math.min(yParams.end, yParams.start); }

    /**
     * Gets the maximum latitude (inclusive) covered by the loaded GeoTIFF DEM
     * @return double the latitude of the northern-most datpoint of the loaded GeoTIFF DEM
     */
    public double getMaxLat() { return Math.max(yParams.end, yParams.start); }

    /**
     * Using the loaded GeoTIFF DEM, obtains the nearest elevation value for a given Lat/Lon pair
     * <p>
     *     This function returns the nearest elevation value to the given Lat/Lon pair, without interpolation
     * </p>
     * @param lat The latitude of the result desired. [-90, 90]
     * @param lon The longitude of the result desired. [-180, 180]
     * @return double the altitude of the terrain near the given Lat/Lon, in meters above the WGS84 reference ellipsoid
     * @throws RequestedValueOOBException
     */
    public double getAltFromLatLon(double lat, double lon) throws RequestedValueOOBException {
        if (geofile == null || rasters == null || xParams == null || yParams == null) {
            throw new NullPointerException("getAltFromLatLon pre-req was null!");
        }
        if ( xParams.numOfSteps <= 0 || yParams.numOfSteps <= 0) {
            throw new IllegalArgumentException("getAltFromLatLon dataset was empty!");
        }
//        Log.d(TAG, "lat: " + lat + " lon: " + lon);

        double x0 = xParams.start;
        double x1 = xParams.end;
        double dx = xParams.stepwiseIncrement;
        long ncols = xParams.numOfSteps;

        double y0 = yParams.start;
        double y1 = yParams.end;
        double dy = yParams.stepwiseIncrement;
        long nrows = yParams.numOfSteps;

//        Log.d(TAG, "x0: " + x0 + " x1: " + x1);
//        Log.d(TAG, "y0: " + y0 + " y1: " + y1);

        // Out of Bounds (OOB) check
        if (( lat > getMaxLat() || getMinLat() > lat ) || ( lon > getMaxLon() || getMinLon() > lon)) {
            throw new RequestedValueOOBException("getAltFromLatLon arguments out of bounds!", lat, lon);
        }

        long[] xNeighbors = binarySearchNearest(x0, ncols, lon, dx);
        long xL = xNeighbors[0];
        long xR = xNeighbors[1];
        long xIndex;
        if (Math.abs(lon - (x0 + xL * dx)) < Math.abs(lon - (x0 + xR * dx))) {
            xIndex = xL;
        } else {
            xIndex = xR;
        }

        long[] yNeighbors = binarySearchNearest(y0, nrows, lat, dy);
        long yT = yNeighbors[0];
        long yB = yNeighbors[1];
        long yIndex;
        if (Math.abs(lat - (y0 + yT * dy)) < Math.abs(lat - (y0 + yB * dy))) {
            yIndex = yT;
        } else {
            yIndex = yB;
        }

        // https://gdal.org/java/org/gdal/gdal/Dataset.html#ReadRaster(int,int,int,int,int,int,int,byte%5B%5D,int%5B%5D)
        // https://gis.stackexchange.com/questions/349760/get-elevation-of-geotiff-using-gdal-bindings-in-java
//        geodata.ReadRaster((int) xIndex, (int) yIndex, 1, 1, 1, 1, 6, floatToBytes(result), band);
        double result = rasters.getPixel((int) xIndex, (int) yIndex)[0].doubleValue();
        return result;
    }

    /**
     * Performs a binary search, returning indicies pointing to the two closest values to the input
     * @param start the start value, in degrees, of an axis of the geofile
     * @param n the number of items in an axis of the geofile
     * @param val an input value for which to find the two closest indicies
     * @param dN the change in value for each increment of the index along an axis of the geofile
     * @return
     */
    long[] binarySearchNearest(double start, long n, double val, double dN) {
        long[] out = new long[2];
        if ( n <= 0 ) { // dataset is empty
            return null;
        }

        if ( n == 1 ) { // if only one elevation datapoint; exceedingly rare
            if (Double.compare(start, val) <= 0.00000001d) {
                return new long[]{(long) 0, (long) 0};
            } else {
                return null;
            }
        }

        if (dN == 0.0d) {
            return null;
        }

        boolean isDecreasing = (dN < 0.0d);
        if (isDecreasing) {
            // if it's in decreasing order, uh, don't do that. Make it increasing instead!
            double reversedStart = start + n * dN;
            double reversedDN = -1.0d * dN;

            long[] recurseResult = binarySearchNearest(reversedStart, n, val, reversedDN);
            long a1 = recurseResult[0];
            long a2 = recurseResult[1];

            // kinda weird, but we reverse index result since we reversed the list
            a1 = n - a1 - 1;
            a2 = n - a2 - 1;
            return new long[]{a1, a2};
        }

        long L = 0;
        long lastIndex = n - 1;
        long R = lastIndex;
        while (L <= R) {
            long m = (long) Math.floor((L + R) / 2);
            if (start + m * dN < val) {
                L = m + 1;
            } else if (start + m * dN > val) {
                R = m - 1;
            } else {
                // exact match
                return new long[]{m, m};
            }
        }

        // if we've broken out of the loop, L > R
        //     which means the markers have flipped
        //     therfore, either list[L] or list[R] must be closest to val
        return new long[]{R, L};
    }
}
