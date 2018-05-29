package com.example.swainstha.dronefly;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.*;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    Marker marker;
    private Socket socket;

    private final String urlString = "http://192.168.1.119:3000";

    ListView statusListView;
    ArrayList<StatusData> statusList;
    StatusListAdapter statusListAdapter;

    boolean markerChanged = false;
    boolean makeDronePath = false;
    boolean loadCurrentPosition = false;
    boolean firstMissionLoad = true;

    JSONObject data;
    JSONObject mission;
    RelativeLayout relativeLayout;
    boolean showHide = false;

    Circle circle1;
    Circle circle2;
    Circle circle3;
    Polyline lineH;
    Polyline lineV;
    ArrayList<Polyline> dronePath;
    ArrayList<Polyline> missionPath;
    boolean circleUnCirle = false;

    Button show;
    Button circle;
    Button fly;
    Button downMission;
    Button clearMission;
    EditText circleRadius;

    LatLng home;
    LatLng prevLatLng;
    LatLng prevLatLngMission;
    ArrayList<Marker> missionMarker;
    int missionMarkerindex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        initSocket();

        missionMarker = new ArrayList<>();
        dronePath = new ArrayList<>();
        missionPath = new ArrayList<>();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        statusList = new ArrayList<>();
        statusListView = this.findViewById(R.id.statusListView);
        relativeLayout = findViewById(R.id.cell1);
        relativeLayout.setVisibility(View.GONE);

        circleRadius = findViewById(R.id.circle_radius);

        show = findViewById(R.id.show);
        show.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!showHide) {
                    relativeLayout.setVisibility(View.VISIBLE);
                    show.setText("HIDE");
                    showHide = true;
                } else {
                    relativeLayout.setVisibility(View.GONE);
                    show.setText("SHOW");
                    showHide = false;
                }
            }
        });

        circle = findViewById(R.id.circle);
        circle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("INFO","HIDDEN");
                if(!circleUnCirle) {
                    LatLng latLng = home;
                    int radius;
                    try {
                        radius = Integer.parseInt(circleRadius.getText().toString());
                    } catch(NumberFormatException e){
                        radius = 50;
                    }
                    circle1 = mMap.addCircle(new CircleOptions()
                            .center(latLng)
                            .radius(radius).strokeWidth(1)
                            .strokeColor(Color.WHITE));
                    circle2 = mMap.addCircle(new CircleOptions()
                            .center(latLng)
                            .radius(2 * radius).strokeWidth(1)
                            .strokeColor(Color.WHITE));
                    circle3 = mMap.addCircle(new CircleOptions()
                            .center(latLng)
                            .radius(3 * radius).strokeWidth(1)
                            .strokeColor(Color.WHITE));

                    Geo geo = new Geo(latLng,3 * radius);
                    ArrayList<Double> list = geo.calculate();
                    Log.i("Info",list.toString());

                    lineH = mMap.addPolyline(new PolylineOptions()
                            .add(new LatLng(list.get(0), list.get(1)), new LatLng(list.get(4), list.get(5)))
                            .width(1)
                            .color(Color.WHITE));
                    lineV = mMap.addPolyline(new PolylineOptions()
                            .add(new LatLng(list.get(2), list.get(3)), new LatLng(list.get(6), list.get(7)))
                            .width(1)
                            .color(Color.WHITE));
                    circleUnCirle = true;
                    circle.setText("CLEAR");
                } else {
                    circle1.remove();
                    circle2.remove();
                    circle3.remove();
                    lineH.remove();
                    lineV.remove();
                    circleUnCirle = false;
                    circle.setText("CIRCLE");
                }
            }
        });

        fly = findViewById(R.id.fly);
        fly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SendCommand sendCommand = new SendCommand();
                try {
                    String res = sendCommand.execute("fly").get();
                    makeDronePath = true;
                    Log.i("INFO", res);
                } catch(ExecutionException e) {
                    Log.i("INFO","Execution exception");
                } catch(InterruptedException i) {
                    Log.i("INFO","Interrupted exception");
                }
            }
        });

        downMission = findViewById(R.id.download_mission);
        downMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearMission();
                SendCommand sendCommand = new SendCommand();
                try {
                    String res = sendCommand.execute("getMission","{\"mission\":\"1\",\"device\":\"android\"}").get();
                    Log.i("INFO", res);
                } catch(ExecutionException e) {
                    Log.i("INFO","Execution exception");
                } catch(InterruptedException i) {
                    Log.i("INFO","Interrupted exception");
                }
//                final String res = "{\"0\":{\"lat\":27.686328887939453,\"lng\":85.3176498413086},\"1\":{\"lat\":27.687082290649414,\"lng\":85.31800842285156,\"command\":16,\"alt\":10},\"2\":{\"lat\":27.686342239379883,\"lng\":85.31832885742188,\"command\":16,\"alt\":10},\"3\":{\"lat\":27.68666648864746,\"lng\":85.31977844238281,\"command\":16,\"alt\":10},\"4\":{\"lat\":27.687395095825195,\"lng\":85.32050323486328,\"command\":16,\"alt\":10},\"5\":{\"lat\":27.68668556213379,\"lng\":85.32115936279297,\"command\":16,\"alt\":10},\"6\":{\"lat\":27.686038970947266,\"lng\":85.32144927978516,\"command\":16,\"alt\":10},\"7\":{\"lat\":27.68529510498047,\"lng\":85.32125854492188,\"command\":16,\"alt\":10},\"8\":{\"lat\":27.685609817504883,\"lng\":85.32025146484375,\"command\":16,\"alt\":10},\"9\":{\"lat\":27.685344696044922,\"lng\":85.31883239746094,\"command\":16,\"alt\":10},\"10\":{\"lat\":27.68467903137207,\"lng\":85.31873321533203,\"command\":16,\"alt\":10},\"11\":{\"lat\":27.684364318847656,\"lng\":85.31867980957031,\"command\":16,\"alt\":10},\"12\":{\"lat\":27.68467903137207,\"lng\":85.31807708740234,\"command\":16,\"alt\":10},\"13\":{\"lat\":27.685049057006836,\"lng\":85.31759643554688,\"command\":16,\"alt\":10},\"14\":{\"lat\":27.68475914001465,\"lng\":85.31611633300781,\"command\":16,\"alt\":10},\"15\":{\"lat\":27.683958053588867,\"lng\":85.31551361083984,\"command\":16,\"alt\":10},\"16\":{\"lat\":27.684717178344727,\"lng\":85.31472778320312,\"command\":16,\"alt\":10},\"17\":{\"lat\":27.68563461303711,\"lng\":85.31436157226562,\"command\":16,\"alt\":10},\"18\":{\"lat\":27.686586380004883,\"lng\":85.31474304199219,\"command\":16,\"alt\":10},\"19\":{\"lat\":27.68584632873535,\"lng\":85.3152084350586,\"command\":16,\"alt\":10},\"20\":{\"lat\":27.686010360717773,\"lng\":85.31674194335938,\"command\":16,\"alt\":10},\"21\":{\"lat\":27.686803817749023,\"lng\":85.3167953491211,\"command\":16,\"alt\":10},\"22\":{\"lat\":27.686147689819336,\"lng\":85.31733703613281,\"command\":16,\"alt\":10}}";
//
//                try {
//                    mission = new JSONObject(res);
//                    for(int i=0;i < mission.length();i++){
//                        JSONObject j = mission.getJSONObject(Integer.toString(i));
//                        LatLng latLng = new LatLng(j.getDouble("lat"),j.getDouble("lng"));
//                        if(i == 0) {
//                            missionMarker.add(mMap.addMarker(new MarkerOptions().position(latLng).title("TakeOff")
//                                    .icon(BitmapDescriptorFactory.defaultMarker())));
//                        } else if(Integer.parseInt(j.getString("command")) == 16) {
//                            missionMarker.add(mMap.addMarker(new MarkerOptions().position(latLng).title("WayPoint " + i).snippet("Alt: " + j.getString("alt"))
//                                    .icon(BitmapDescriptorFactory.defaultMarker())));
//                        } else {
//                            missionMarker.add(mMap.addMarker(new MarkerOptions().position(latLng).title("Land")
//                                    .icon(BitmapDescriptorFactory.defaultMarker())));
//                        }
//
//                        if(firstMissionLoad) {
//                            prevLatLngMission = latLng;
//                            firstMissionLoad = false;
//                        }
//
//                        missionPath.add(mMap.addPolyline(new PolylineOptions()
//                                .add(prevLatLngMission, latLng)
//                                .width(1)
//                                .color(Color.RED)));
//
//                        prevLatLngMission = latLng;
//                    }
//
//                } catch(JSONException e) {
//                    Log.i("INFO","Json Exception");
//                }
            }
        });

        clearMission = findViewById(R.id.clear_mission);
        clearMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                try {
//                    dronePath.remove();
//                }catch(NullPointerException npe) {
//                    Log.i("INFO","Drone path not defined");
//                }
                clearMission();

            }
        });

        //creating listview
        statusList.add(new StatusData("Lat: ","0"));
        statusList.add(new StatusData("Long: ","0"));
        statusList.add(new StatusData("AltR: ","0"));
        statusList.add(new StatusData("Alt: ","0"));
        statusList.add(new StatusData("Sat: ","0"));
        statusList.add(new StatusData("HDOP: ","0"));
        statusList.add(new StatusData("FIX: ","0"));
        statusList.add(new StatusData("Head: ","0"));
        statusList.add(new StatusData("GS: ","0"));
        statusList.add(new StatusData("AS: ","0"));
        statusList.add(new StatusData("Mode: ","0"));
        statusList.add(new StatusData("Arm: ","0"));
        statusList.add(new StatusData("EKF: ","0"));
        statusList.add(new StatusData("Status: ","0"));
        statusList.add(new StatusData("LiDar: ","0"));
        statusList.add(new StatusData("Volt: ","0"));
        statusList.add(new StatusData("Conn: ","0"));

        statusListAdapter = new StatusListAdapter(this,statusList);
        LayoutInflater inflater = getLayoutInflater();
        inflater.inflate(R.layout.status_list_view,statusListView, false);

        statusListView.setAdapter(statusListAdapter);
    }



    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Kupondole.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        home = new LatLng(27.686328887939453, 85.3176498413086);
        marker = mMap.addMarker(new MarkerOptions().position(home).title("Marker in Kupondole")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.red)).alpha(0.7f).flat(true)
                .rotation(0.0f));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(home, 17.0f));

        //Move the camera to the user's location and zoom in!
        //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(nic, 17.0f));
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

    }

    public void initSocket() {

        //creating a socket to connect to the node js server using socket.io.client
        try {

            //check for internet connection
            if(!isOnline()) {
                throw new Exception();
            }

            //generating a random number for join id in the server
            Random rand = new Random();
            int  id = rand.nextInt(50) + 1;

            socket = IO.socket(urlString); //specifying the url
            socket.connect(); //connecting to the server

            SendCommand sendCommand = new SendCommand();
            try {

                //check for internet connectivity
                if(!isOnline()) {
                    throw new Exception();
                }

                //execute worker thread to send data
                sendCommand.execute("joinAndroid").get();

            }catch (InterruptedException e) {
                e.printStackTrace();
                Log.i("INFO","Failed Sending");
            } catch (ExecutionException e) {
                e.printStackTrace();
                Log.i("INFO","Failed Sending");
            }

            //callback functions for socket connected, message received and socket disconnected

            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                }

            }).on("copter-data", new Emitter.Listener() {

                @Override
                public void call(Object... args) {

                    final String res = args[0].toString();
                    Log.i("INFO",res);

                    try {
                        data = new JSONObject(res);
                    } catch(JSONException e) {
                        Log.i("INFO","Json Exception");
                    }
                    runOnUiThread(new Runnable() {
                        public void run() {
                            try {

                                int i = 0;
                                Iterator iter = data.keys();
                                while(iter.hasNext()){
                                    String key = (String)iter.next();
                                    String value = data.getString(key);
                                    statusList.get(i).setValue(value);
                                    i++;
                                }
//                                for (int i = 0; i < statusList.size(); i++) {
//                                    statusList.get(i).setValue(data.names().get(i).toString());
//                                }
                                statusListAdapter.notifyDataSetChanged();
                                LatLng currentLatLng = new LatLng(Double.parseDouble(data.getString("lat").toString()),
                                        Double.parseDouble(data.getString("lng").toString()));


                                if(loadCurrentPosition == false) {
                                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17.0f));
                                    loadCurrentPosition = true;
                                    prevLatLng = currentLatLng;
                                    home = currentLatLng;
                                }

                                if(data.getString("arm").toString() == "true" && markerChanged) {
                                    marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.green));
                                    markerChanged = false;
                                } else if(data.getString("arm").toString() == "false" && !markerChanged){
                                    marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.red));
                                    markerChanged = true;
                                }
                                marker.setRotation(Float.parseFloat(data.getString("head").toString()));

                                marker.setPosition(currentLatLng);

                                if(makeDronePath) {
                                    dronePath.add(mMap.addPolyline(new PolylineOptions()
                                            .add(prevLatLng, currentLatLng)
                                            .width(1)
                                            .color(Color.GREEN)));
                                }
                                prevLatLng = currentLatLng;

                            } catch(JSONException e) {

                                Log.i("INFO","Json exception in status data receive");
                                e.printStackTrace();

                            }
                        }
                    });
                    //Toast.makeText(getContext(), args[0].toString(), Toast.LENGTH_SHORT).show();
                }

            }).on("Mission", new Emitter.Listener() {

                @Override
                public void call(Object... args) {

                    final String res = args[0].toString();
                    //final String res = "{\"0\":{\"lat\":27.686328887939453,\"lng\":85.3176498413086},\"1\":{\"lat\":27.687082290649414,\"lng\":85.31800842285156,\"command\":16,\"alt\":10},\"2\":{\"lat\":27.686342239379883,\"lng\":85.31832885742188,\"command\":16,\"alt\":10},\"3\":{\"lat\":27.68666648864746,\"lng\":85.31977844238281,\"command\":16,\"alt\":10},\"4\":{\"lat\":27.687395095825195,\"lng\":85.32050323486328,\"command\":16,\"alt\":10}}";
                    Log.i("INFO",res);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                mission = new JSONObject(res);
                                for(int i=0;i < mission.length();i++){
                                    JSONObject j = mission.getJSONObject(Integer.toString(i));
                                    LatLng latLng = new LatLng(j.getDouble("lat"),j.getDouble("lng"));
                                    if(i == 0) {
                                        missionMarker.add(mMap.addMarker(new MarkerOptions().position(latLng).title("TakeOff")
                                                .icon(BitmapDescriptorFactory.defaultMarker())));
                                    } else if(Integer.parseInt(j.getString("command")) == 16) {
                                        missionMarker.add(mMap.addMarker(new MarkerOptions().position(latLng).title("WayPoint " + i).snippet("Alt: " + j.getString("alt"))
                                                .icon(BitmapDescriptorFactory.defaultMarker())));
                                    } else if (Integer.parseInt(j.getString("command")) == 21){
                                        missionMarker.add(mMap.addMarker(new MarkerOptions().position(latLng).title("Land")
                                                .icon(BitmapDescriptorFactory.defaultMarker())));
                                    }

                                    if(firstMissionLoad) {
                                        prevLatLngMission = latLng;
                                        firstMissionLoad = false;
                                    }

                                    missionPath.add(mMap.addPolyline(new PolylineOptions()
                                            .add(prevLatLngMission, latLng)
                                            .width(1)
                                            .color(Color.RED)));

                                    prevLatLngMission = latLng;
                                }

                            } catch(JSONException e) {
                                Log.i("INFO","Json Exception");
                            }

                        }
                    });
                    //Toast.makeText(getContext(), args[0].toString(), Toast.LENGTH_SHORT).show();
                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                }

            });
        } catch(URISyntaxException e) {
            Log.i("INFO","Uri syntax exception");
        } catch(Exception e) {
            Log.i("INFO", "No internet connection");
            Toast.makeText(this, "No Internet", Toast.LENGTH_LONG).show();
        }

    }

    //check if the device is connected to the internet
    protected boolean isOnline() {

        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        //check if the connection id wifi or mobile data
        boolean isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        return isConnected;

    }

    //sending data to the node js server so it can forward it, to the raspberry pi in drone to start the flight
    public class SendCommand extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            try {
                //sending message to server
                socket.emit(urls[0]);
                Log.i("INFO",urls[0]);
                return "Success";

            } catch (Exception e) {
                e.printStackTrace();
                Log.i("INFO","Failed Sending. May be no internet connection");
                return "Failed";
            }
        }

        @Override
        protected void onPostExecute(String result) {

            //Log.i("INFO",result);
            result = "";
        }
    }

    public void clearMission() {

        firstMissionLoad = true;
        try {
            for(int i=0;i< missionPath.size();i++) {
                missionPath.get(i).remove();
            }
        }catch(NullPointerException npe) {
            Log.i("INFO","Mission path not defined");
        }
        try {
            for(int i=0;i< missionMarker.size();i++) {
                missionMarker.get(i).remove();
            }
        }catch(NullPointerException npe) {
            Log.i("INFO","Mission marker not defined");
        }
    }
}
