package kobayashi.taku.com.fuukeiar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.util.ArrayList;

public class MainActivity extends Activity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1;
    private CameraWrapper mCamera;
    private SurfaceHolder mHolder;
    private Handler mCallMainThreadHandler;

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

        Util.requestPermissions(this, REQUEST_CODE_CAMERA_PERMISSION);
    }

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
            setupMobileVisionCamera();
            startCamera(mHolder);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!Util.existConfirmPermissions(this)){
            setupMobileVisionCamera();
            startCamera(mHolder);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    private void stopCamera(){
        if(mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void startCamera(SurfaceHolder holder){
        if(mCamera != null) {
            mCamera.start(holder);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setupMobileVisionCamera() {
        if(mCamera == null){
            mCamera = new CameraWrapper();
        }
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

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
