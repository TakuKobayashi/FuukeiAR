package kobayashi.taku.com.fuukeiar;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class MainActivity extends Activity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private static final String STREETVIEW_BUNDLE_KEY = "StreetViewBundleKey";

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private static final int REQUEST_CHECK_LOCATION_PERMISSION = 2;
    private CameraWrapper mCamera;
    private SurfaceHolder mHolder;
    private Handler mCallMainThreadHandler;

    private ExtendStreetViewPanoramaView mStreetViewPanoramaView;
    private StreetViewPanorama mStreetViewPanorama;

    private GoogleApiClient mGoogleApiClient;
    private FusedLocationProviderClient mFusedLocationClient;
    private Pair<Double, Double> mLatLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // スクリーンセーバをオフにする
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        mCallMainThreadHandler = new Handler();

        mHolder = generateSurfaceHolder();

        Button takePictureButton = (Button) findViewById(R.id.take_picture_button);
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTakePictureThread();
            }
        });

        Bundle streetViewBundle = null;
        if (savedInstanceState != null) {
            streetViewBundle = savedInstanceState.getBundle(STREETVIEW_BUNDLE_KEY);
        }
        mStreetViewPanoramaView = (ExtendStreetViewPanoramaView) findViewById(R.id.street_panorama_view);
        mStreetViewPanoramaView.onCreate(streetViewBundle);
        mStreetViewPanoramaView.getStreetViewPanoramaAsync(new OnStreetViewPanoramaReadyCallback() {
            @Override
            public void onStreetViewPanoramaReady(StreetViewPanorama streetViewPanorama) {
                mStreetViewPanorama = streetViewPanorama;
                mStreetViewPanorama.setStreetNamesEnabled(true);
                mStreetViewPanorama.setUserNavigationEnabled(true);
                mStreetViewPanorama.setZoomGesturesEnabled(true);
                mStreetViewPanorama.setPanningGesturesEnabled(true);
                if(mFusedLocationClient != null){
                    // 最後の位置情報取得
                    Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                    if(location != null){
                        mStreetViewPanorama.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                    }
                }
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        Log.d(Config.TAG, "connect");
                        connectLocation();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        mGoogleApiClient.connect();
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                    }
                })
                .addApi(LocationServices.API)
                .build();

        Util.requestPermissions(this, REQUEST_CODE_CAMERA_PERMISSION);
    }

    private void connectLocation(){
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(3000);
        locationRequest.setFastestInterval(0);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        /*
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .setNeedBle(true)
                .addLocationRequest(mLocationRequest);
        Task<LocationSettingsResponse> result = LocationServices.getSettingsClient(MainActivity.this).checkLocationSettings(builder.build());
            result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
                @Override
                public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                    task.getResult();
                    try {
                        LocationSettingsResponse response = task.getResult(ApiException.class);
                    } catch (ApiException exception) {
                        switch (exception.getStatusCode()) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                try {
                                    ResolvableApiException resolvable = (ResolvableApiException) exception;
                                    resolvable.startResolutionForResult(MainActivity.this, REQUEST_CHECK_LOCATION_PERMISSION);
                                } catch (IntentSender.SendIntentException e) {
                                    e.printStackTrace();
                                } catch (ClassCastException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                break;
                        }
                    }
                }
            });
        */
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);
        if(mStreetViewPanorama != null){
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if(location != null){
                mStreetViewPanorama.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
            }
        }
        mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper());
    }

    private void stopLocation(){
        if(mFusedLocationClient != null){
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    private LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Log.d(Config.TAG, "lat:" + locationResult.getLastLocation().getLatitude() + "lon:" + locationResult.getLastLocation().getLongitude());
            if(mStreetViewPanorama != null){
                Location location = locationResult.getLastLocation();
                mStreetViewPanorama.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                /*
                        if(mLatLocation == null || mLatLocation.first != location.getLatitude() || mLatLocation.second != location.getLongitude()){
                            mStreetViewPanorama.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                            mLatLocation = new Pair<Double, Double>(location.getLatitude(), location.getLongitude());
                        }
                */
            }
        }
    };

    private void startTakePictureThread(){
        if(mCamera != null){
            Thread savePictureThread = new Thread(new Runnable() {
                private String mSaveImagePath;

                @Override
                public void run() {
                    Bitmap takeImage = mCamera.takePreviewPicture();
                    if(takeImage != null){
                        mSaveImagePath = ExternalStorageManager.getFilePath(".png");
                        boolean saveSuccess = ExternalStorageManager.saveImage(MainActivity.this, takeImage, mSaveImagePath);
                        if(saveSuccess){
                            mCallMainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if(!isFinishing()){
                                        Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
                                        intent.putExtra("saveImagePath", mSaveImagePath);
                                        startActivity(intent);
                                    }
                                }
                            });
                        }
                    }
                }
            });
            savePictureThread.setDaemon(true);
            savePictureThread.start();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode != REQUEST_CODE_CAMERA_PERMISSION)
            return;
        if(!Util.existConfirmPermissions(this)){
            connectLocation();
            startCamera(mHolder);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mStreetViewPanoramaView.onResume();
        mGoogleApiClient.connect();
        if(!Util.existConfirmPermissions(this)){
            startCamera(mHolder);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
        stopLocation();
        mGoogleApiClient.disconnect();
        mStreetViewPanoramaView.onPause();
    }

    private void stopCamera(){
        if(mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void startCamera(SurfaceHolder holder){
        if(mCamera == null){
            mCamera = new CameraWrapper();
        }
        mCamera.start(holder);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mStreetViewPanoramaView.onDestroy();
    }

    private SurfaceHolder generateSurfaceHolder(){
        SurfaceView preview = (SurfaceView) findViewById(R.id.camera_preview);
        SurfaceHolder holder = preview.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCamera();
            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startCamera(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                startCamera(holder);
            }
        });
        if(Build.VERSION.SDK_INT < 11){
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        return holder;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mStreetViewBundle = outState.getBundle(STREETVIEW_BUNDLE_KEY);
        if (mStreetViewBundle == null) {
            mStreetViewBundle = new Bundle();
            outState.putBundle(STREETVIEW_BUNDLE_KEY, mStreetViewBundle);
        }

        mStreetViewPanoramaView.onSaveInstanceState(mStreetViewBundle);
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
