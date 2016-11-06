package com.example.alex.psunshine;


import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static com.example.alex.psunshine.FragmentForecast.adapter;
import static com.example.alex.psunshine.FragmentForecast.forecastStr;


/**
 * A simple {@link Fragment} subclass.
 */
public class FragmentForecast extends Fragment {

    public static List<String> forecastStr = new ArrayList<>();
    public static ArrayAdapter<String> adapter;



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        setHasOptionsMenu(true);
        Log.d("log", "FragmentForecast OnCreateView");
        return inflater.inflate(R.layout.fragment_forecast, container, false);


    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d("log", "FragmentForecast onStart");
        showForecast(getView());
        getRefreshWeather();


    }

    public void showForecast(View v) {

        ListView listView = (ListView) v.findViewById(R.id.forecast_list_view);

        adapter = new ArrayAdapter<>(getContext(), R.layout.forecast_row, new ArrayList<String>(forecastStr));
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d("log","Clicked "+i);
               // Toast.makeText(getContext(),adapter.getItem(i), Toast.LENGTH_SHORT).show();
                startActivity(new Intent(getContext(),DetailActivity.class).putExtra("details",adapter.getItem(i)));
            }
        });


    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        inflater.inflate(R.menu.menu_forecast, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    public void getRefreshWeather(){
        FetchForecast getWeather = new FetchForecast();

        String location;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        location = preferences.getString(getString(R.string.pref_location),getString(R.string.pref_def_location));

        getWeather.execute(location);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                Log.d("log", "Refresh");
                getRefreshWeather();
                break;
            case  R.id.menu_setting:
                startActivity(new Intent(getContext(),SettingActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }
}

class FetchForecast extends AsyncTask<String, Void, String[]> {
    String forecastJSon;


    /* The date/time conversion code is going to be moved outside the asynctask later,
      * so for convenience we're breaking it out into its own method now.
      */
    private String getReadableDateString(long time) {
        // Because the API returns a unix timestamp (measured in seconds),
        // it must be converted to milliseconds in order to be converted to valid date.
        SimpleDateFormat shortenedDateFormat;
        shortenedDateFormat = null;

        shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");


        return shortenedDateFormat.format(time);
    }

    /**
     * Prepare the weather high/lows for presentation.
     */
    private String formatHighLows(double high, double low) {
        // For presentation, assume the user doesn't care about tenths of a degree.
        long roundedHigh = Math.round(high);
        long roundedLow = Math.round(low);

        return roundedHigh + "/" + roundedLow;
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     * <p>
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {

        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";
        final String OWM_WEATHER = "weather";
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";
        final String OWM_DESCRIPTION = "main";

        JSONObject forecastJson;
        forecastJson = new JSONObject(forecastJsonStr);
        JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

        // OWM returns daily forecasts based upon the local time of the city that is being
        // asked for, which means that we need to know the GMT offset to translate this data
        // properly.

        // Since this data is also sent in-order and the first day is always the
        // current day, we're going to take advantage of that to get a nice
        // normalized UTC date for all of our weather.

        Time dayTime;
        dayTime = new Time();
        dayTime.setToNow();

        // we start at the day returned by local time. Otherwise this is a mess.
        int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

        // now we work exclusively in UTC
        dayTime = new Time();

        String[] resultStrs = new String[numDays];
        for (int i = 0; i < weatherArray.length(); i++) {
            // For now, using the format "Day, description, hi/low"
            String day;
            String description;
            String highAndLow;

            // Get the JSON object representing the day
            JSONObject dayForecast = weatherArray.getJSONObject(i);

            // The date/time is returned as a long.  We need to convert that
            // into something human-readable, since most people won't read "1400356800" as
            // "this saturday".
            long dateTime;
            // Cheating to convert this to UTC time, which is what we want anyhow
            dateTime = dayTime.setJulianDay(julianStartDay + i);
            day = getReadableDateString(dateTime);

            // description is in a child array called "weather", which is 1 element long.
            JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);

            // Temperatures are in a child object called "temp".  Try not to name variables
            // "temp" when working with temperature.  It confuses everybody.
            JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
            double high = temperatureObject.getDouble(OWM_MAX);
            double low = temperatureObject.getDouble(OWM_MIN);

            highAndLow = formatHighLows(high, low);
            resultStrs[i] = day + " - " + description + " - " + highAndLow;
        }

        for (String s : resultStrs) {
            Log.d("log", "Forecast entry: " + s);
        }
        return resultStrs;

    }

    @Override
    protected String[] doInBackground(String... strings) {
        final String API_KEY = "6f3233a88c7fecd9649644cce6a03e8d";
        if (strings == null) {
            return null;
        }
        HttpURLConnection httpConnect = null;
        BufferedReader bufferedReader = null;
        String format = "json";
        String units = "metric";
        int numDays = 7;


        try {
            final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
            final String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";
            final String APPID_PARAM = "APPID";



            Uri link = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, strings[0])
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                    .appendQueryParameter(APPID_PARAM, API_KEY)
                    .build();

            URL url;
            url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=" + strings[0] + "&mode=json&units=metric&cnt=7&APPID=6f3233a88c7fecd9649644cce6a03e8d");
            httpConnect = (HttpURLConnection) url.openConnection();
            httpConnect.setRequestMethod("GET");
            httpConnect.connect();

            InputStream inStream = httpConnect.getInputStream();
            StringBuilder buffer = new StringBuilder();
            if (inStream == null) {
                return null;
            }
            bufferedReader = new BufferedReader(new InputStreamReader(inStream));
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                buffer.append(line).append("\n");
            }
            if (buffer.length() == 0) {
                return null;
            }

            forecastJSon = buffer.toString();
            Log.d("log", forecastJSon);
            httpConnect.disconnect();


        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            return getWeatherDataFromJson(forecastJSon, numDays);


        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String[] strings) {
        if (strings != null) {
            adapter.clear();
            for (String dayForecastStr : strings) {
                adapter.add(dayForecastStr);
            }
        }
    }
}

