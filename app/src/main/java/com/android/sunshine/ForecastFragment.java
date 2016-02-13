package com.android.sunshine;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
import android.widget.ListView;

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


public class ForecastFragment extends Fragment {

    ArrayAdapter<String> mForecastAdapter=null;

    Context context;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshForecast();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();

        if (i == R.id.action_refresh) {
            refreshForecast();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void refreshForecast() {
        FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = sp.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));

        fetchWeatherTask.execute(location);
    }




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mForecastAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, new ArrayList<String>());
        ListView forecastView = (ListView) rootView.findViewById(R.id.listview_forecast);
        forecastView.setAdapter(mForecastAdapter);

        forecastView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast=mForecastAdapter.getItem(position);
                Intent i=new Intent(getActivity().getApplicationContext(),DetailActivity.class).putExtra(Intent.EXTRA_TEXT,forecast);
                startActivity(i);

            }
        });

        return rootView;
    }


    class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private final String MODE = "json";
        private final String UNITS = "metric";
        private final int days = 7;
        private final String APPID = "63025d7b31e0d94e94033250b6f05303";

        private final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";

        private final String QUERY_PARAM = "q";
        private final String FORMAT_PARAM = "mode";
        private final String UNITS_PARAM = "units";
        private final String DAYS_PARAM = "cnt";

        private final String APPID_PARAM = "APPID";


        private String getReadableDateString(long time) {
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        private String formatHighLows(double high, double low, String unitType) {

            if (unitType.equals(getString(R.string.pref_units_imperial))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }

            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            Time dayTime = new Time();
            dayTime.setToNow();

            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            dayTime = new Time();

            String[] resultStrs = new String[numDays];

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(getString(R.string.pref_units_key), getString(R.string.pref_units_metric));

            for (int i = 0; i < weatherArray.length(); i++) {
                String day;
                String description;
                String highAndLow;

                JSONObject dayForecast = weatherArray.getJSONObject(i);

                long dateTime;
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low, unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.d(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }

        @Override
        protected void onPostExecute(String[] result){
            if(result!=null){
                mForecastAdapter.clear();
                for(String dayFrostStr:result){
                    mForecastAdapter.add(dayFrostStr);
                }
            }
        }

        @Override
        protected String[] doInBackground(String... params) {

            if (params.length == 0) {
                return null;
            }

            BufferedReader reader = null;

            String forecatJsonStr = null;

            HttpURLConnection urlConnection = null;

            try {
                Uri buildUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, MODE)
                        .appendQueryParameter(UNITS_PARAM, UNITS)
                        .appendQueryParameter(DAYS_PARAM, String.valueOf(days))
                        .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                        .build();

                Log.d(LOG_TAG, "URL = " + buildUri.toString());

                URL url = new URL(buildUri.toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();


                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;

                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }

                forecatJsonStr = buffer.toString();

                Log.d(LOG_TAG, "JSON = " + forecatJsonStr);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "Error closing stream ", e);
                    }
                }
            }

            try {
                return getWeatherDataFromJson(forecatJsonStr, days);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
