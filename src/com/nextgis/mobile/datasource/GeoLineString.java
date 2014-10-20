/******************************************************************************
 * Project:  NextGIS mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), polimax@mail.ru
 ******************************************************************************
 *   Copyright (C) 2014 NextGIS
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ****************************************************************************/
package com.nextgis.mobile.datasource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.nextgis.mobile.util.GeoConstants.*;

public class GeoLineString extends GeoGeometry {

    private List<GeoRawPoint> coordinates;

    public GeoLineString() {
        coordinates = new ArrayList<GeoRawPoint>();
    }

    public List<GeoRawPoint> getCoordinates() {
        return coordinates;
    }

    public void add(double x, double y) {
        coordinates.add(new GeoRawPoint(x, y));
    }

    public void add(GeoRawPoint rpt) {
        coordinates.add(rpt);
    }

    public void remove(int index) {
        coordinates.remove(index);
    }

    @Override
    public int getType() {
        return GTLineString;
    }

    @Override
    public boolean project(int crs) {
        if (mCRS == CRS_WGS84 && crs == CRS_WEB_MERCATOR) {
            for (GeoRawPoint point : coordinates) {
                Geo.wgs84ToMercatorSphere(point);
            }
            return true;
        } else if (mCRS == CRS_WEB_MERCATOR && crs == CRS_WGS84) {
            for (GeoRawPoint point : coordinates) {
                Geo.mercatorToWgs84Sphere(point);
            }
            return true;
        }
        return false;
    }

    @Override
    public GeoEnvelope getEnvelope() {
        // TODO: implement it
        return null;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject jsonOutObject = new JSONObject();
        jsonOutObject.put(GEOJSON_TYPE, GEOJSON_TYPE_LineString);
        JSONArray coordinates = new JSONArray();
        jsonOutObject.put(GEOJSON_COORDINATES, coordinates);

        for (GeoRawPoint point : this.coordinates) {
            JSONArray pointCoordinates = new JSONArray();
            pointCoordinates.put(point.x);
            pointCoordinates.put(point.y);
            coordinates.put(pointCoordinates);
        }

        return jsonOutObject;
    }
}