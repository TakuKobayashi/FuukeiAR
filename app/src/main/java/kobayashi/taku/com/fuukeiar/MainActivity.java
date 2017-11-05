package kobayashi.taku.com.fuukeiar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
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

import com.bumptech.glide.Glide;
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
import com.google.android.gms.maps.StreetViewPanoramaView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.StreetViewPanoramaLocation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.List;

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
    private boolean mIsResume = false;

    private GoogleApiClient mGoogleApiClient;
    private FusedLocationProviderClient mFusedLocationClient;
    private Pair<Double, Double> mLatLocation = null;

    private SensorManager mSensorManager;
    private float[] mAccekerometer = new float[3];
    private float[] mGeomagnetic = new float[3];

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
                mStreetViewPanorama.setUserNavigationEnabled(false);
                mStreetViewPanorama.setZoomGesturesEnabled(false);
                mStreetViewPanorama.setPanningGesturesEnabled(false);
                mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(new StreetViewPanorama.OnStreetViewPanoramaChangeListener() {
                    @Override
                    public void onStreetViewPanoramaChange(StreetViewPanoramaLocation streetViewPanoramaLocation) {
                        Log.d(Config.TAG, "pc lat:" + streetViewPanoramaLocation.position.latitude + " lon:" + streetViewPanoramaLocation.position.longitude + " " + streetViewPanoramaLocation.links);
                    }
                });
                if(mFusedLocationClient != null){
                    // 最後の位置情報取得
                    showNearStreetView(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
                }
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        if(!mIsResume){
                            mStreetViewPanoramaView.onResume();
                        }
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

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Util.requestPermissions(this, REQUEST_CODE_CAMERA_PERMISSION);
    }

    private void showNearStreetView(Location location){
        if(location != null){
            // 第二引数は探し出す一番近くの場所半径メートル
            mStreetViewPanorama.setPosition(new LatLng(location.getLatitude(), location.getLongitude()), 1000);
        }
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
            showNearStreetView(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
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
                showNearStreetView(locationResult.getLastLocation());
                /*
                        if(mLatLocation == null || mLatLocation.first != location.getLatitude() || mLatLocation.second != location.getLongitude()){
                            mStreetViewPanorama.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                            mLatLocation = new Pair<Double, Double>(location.getLatitude(), location.getLongitude());
                        }
                */
            }
        }
    };

    private StreetViewPanoramaLocation mLocation;

    private void startTakePictureThread(){
        if(mCamera != null){
            mLocation = mStreetViewPanorama.getLocation();
            Thread savePictureThread = new Thread(new Runnable() {
                private String mSaveImagePath;

                @Override
                public void run() {
                    Bitmap takeImage = mCamera.takePreviewPicture();
                    Bitmap panorama = Util.getBitmapFromURL("https://maps.googleapis.com/maps/api/streetview?size=640x640&location=" + mLocation.position.latitude + "," + mLocation.position.longitude + "&key=" + getString(R.string.google_maps_key));
                    Bitmap saveImage = compositeImages(takeImage, panorama);
                    takeImage.recycle();
                    panorama.recycle();
                    if(takeImage != null){
                        mSaveImagePath = ExternalStorageManager.getFilePath(".png");
                        boolean saveSuccess = ExternalStorageManager.saveImage(MainActivity.this, saveImage, mSaveImagePath);
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

    private Bitmap compositeImages(Bitmap picture, Bitmap capture){
        int maxHeight = Math.max(capture.getHeight(), picture.getHeight());
        int pictureWidth = picture.getWidth() * maxHeight / picture.getHeight();
        int captureWidth = capture.getWidth() * maxHeight / capture.getHeight();

        Bitmap compositeImage = Bitmap.createBitmap(captureWidth + pictureWidth, maxHeight, Bitmap.Config.ARGB_8888);
        Bitmap copyImage = compositeImage.copy(Bitmap.Config.ARGB_8888, true);
        compositeImage.recycle();
        compositeImage = null;
        compositeImage = copyImage;
        Canvas bitmapCanvas = new Canvas(compositeImage);
        Paint paint = new Paint();
        bitmapCanvas.drawBitmap(picture, new Rect(0,0,picture.getWidth(),picture.getHeight()), new Rect(0,0,pictureWidth,maxHeight), paint);
        bitmapCanvas.drawBitmap(capture, new Rect(0,0,capture.getWidth(),capture.getHeight()), new Rect(pictureWidth,0,pictureWidth + captureWidth,maxHeight), paint);
        return compositeImage;
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener(){

        @Override
        public void onSensorChanged(SensorEvent event) {

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
        if(!Util.existConfirmPermissions(this)){
            startCamera(mHolder);
        }
        if(mGoogleApiClient.isConnected()){
            mIsResume = true;
            mStreetViewPanoramaView.onResume();
        }
        mSensorManager.registerListener(mSensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(mSensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
        stopLocation();
        mGoogleApiClient.disconnect();
        mIsResume = false;
        mStreetViewPanoramaView.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);
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
