/*
 * Copyright (C) 2012 S2H Mobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.s2hmobile.carlib.location;

import java.util.List;

import android.app.Service;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

public class LocationHelper {
	
    /**
     * Allowed accuracy drop of a new location.
     */
    public static final float ALLOWED_ACCURACY_DELTA = 30.0F; // 30 meters
    
    /**
     * Maximum age (in milliseconds) for a location to be considered recent.
     */
    public static final long DEFAULT_TIME_LIMIT = 1000 * 60 * 3; // 3 minutes
	
	private LocationHelper() {}
	
	/**
	 * Checks if the caller implements the OnLocationListener interface. Asks for
	 * the last best location. If it is not good enough, requests a single update.
	 * @param context for the location system service
	 * @param listener the service, must implement OnLocationUpdateListener
	 */
	public static void requestLocation(Context context, Service listener) {
		OnLocationUpdateListener callback = null;
	    try {
	  	  callback = (OnLocationUpdateListener) listener;
	    } catch(ClassCastException e) {
	  	  android.util.Log.e("LocationHelper",
	  			  "Service must implement OnLocationUpdateListener.", e);
	    }
	    if (callback != null) {
			// determine the time limit
			long limit = System.currentTimeMillis() - DEFAULT_TIME_LIMIT;
			// ask for last best location
			Location lastBestLocation = getLastBestLocation(context, limit);
			// evaluate the result
			if (isLocationAccepted(lastBestLocation, limit)){
				callback.onLocationUpdate(lastBestLocation);
			} else {
				ILocationFinder finder = createInstance(context, callback);
				finder.oneShotUpdate(lastBestLocation);
			}
	    }
	}
	
	/**
	 * Find the most accurate and timely previously detected location 
	 * using all the location providers.
	 * @param context the context, to get the location manager
	 * @param minTime the time limit
	 * @return The most accurate and / or timely previously detected location.
	 */
	public static Location getLastBestLocation(Context context, long minTime) {
		Location bestResult = null;
	    float bestAccuracy = Float.MAX_VALUE;
	    long bestTime = Long.MIN_VALUE;
	    LocationManager locationManager = 
	    		(LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
	    List<String> matchingProviders = locationManager.getAllProviders();
	    for (String provider: matchingProviders) {
	      Location location = locationManager.getLastKnownLocation(provider);
	      if (location != null) {
	        float accuracy = location.getAccuracy();
	        long time = location.getTime();    
	        if (minTime < time && accuracy < bestAccuracy) {
	          // This location fix is younger than minTime, its accuracy is better than the current best value.
	          bestResult = location;
	          bestAccuracy = accuracy;
	          bestTime = time;
	        }
	        else if (time < minTime && bestAccuracy == Float.MAX_VALUE && bestTime < time) {
	          // first condition not met so far, this candidate is older than minTime but younger than current bestResult  
	          bestResult = location;
	          // accuracy not updated, so the condition can be met by the next candidate
	          bestTime = time;
	        }
	      }
	    }
	    return bestResult;
	}

    /** Compares the new location to the current best locations. Returns the
     *  "better" location. The allowed time delta is set to two minutes.
     *  @param newLocation the new location to evaluate
     *  @param currentBestLocation the current best fix
     */
	public static Location betterLocation(Location newLocation,
			Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return newLocation;
		}
		// Check whether the new location fix is younger or older
		final long allowedTimeDelta = 1000 * 60 * 2, // 2 minutes
				timeDelta = newLocation.getTime() - currentBestLocation.getTime();
		if (timeDelta > allowedTimeDelta) {
    	   // newLocation is more than two minutes younger than current one, user has likely moved
           return newLocation;
		} else if (timeDelta < -allowedTimeDelta) {
           // newLocation is more than two minutes older than current one
           return currentBestLocation;
		}
		// the location times are less than two minutes apart, compare the accuracies
		final float accuracyDelta = newLocation.getAccuracy() - currentBestLocation.getAccuracy();
		if (accuracyDelta < 0) {
    	   // the new location fix is more accurate than the current best fix     	   
           return newLocation;
		} else if (timeDelta > 0 && !(accuracyDelta > ALLOWED_ACCURACY_DELTA)) {
    	   // candidate location is younger and not significantly less accurate
    	   return newLocation;
		}
		// candidate location is older or significantly less accurate
		return currentBestLocation;
	}

	private static boolean isLocationAccepted(Location location, long minTime){
		if (location != null){
			long time = location.getTime();
	 		float accuracy = location.getAccuracy();
	  	    return time > minTime && accuracy < ALLOWED_ACCURACY_DELTA;
		} else {
			return false;
		}
	}
	
  /**
   * Wraps the LocationFinder instance, depending on platform version.
   * @param context the context
   * @return ILocationFinder the instance of the ILocationFinder
   */
  private static ILocationFinder createInstance(Context context,
		  OnLocationUpdateListener callback) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD ?
    		new GingerbreadLocationFinder(context, callback) :
    			new FroyoLocationFinder(context, callback);
  }
  
  public interface OnLocationUpdateListener{
    /**
     * Handle the result of the location update.
     * @param location the new location
     */
    void onLocationUpdate(Location location);
  }
}