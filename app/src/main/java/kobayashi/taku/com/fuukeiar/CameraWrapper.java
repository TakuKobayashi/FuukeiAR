package kobayashi.taku.com.fuukeiar;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.google.android.gms.common.images.Size;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class CameraWrapper {
	private static final int STACKABLE_FRAME_SIZE = 2;
	private int mCameraOrientation = 0;
	private Size mRequestedPreviewSize;
	private Size mPreviewSize;
	private Float mRequestedFps = null;
	private Camera mCamera;
	private ArrayDeque<byte[]> mFrameQueue = new ArrayDeque<byte[]>();

	public void setRequestedFps(float fps){
		mRequestedFps = fps;
	}

	public void setRequestedPreviewSize(int width, int height){
		mRequestedPreviewSize = new Size(width, height);
	}

	public void start(SurfaceHolder preview){
		int cameraId = 0;
		try {
			mCamera = Camera.open(cameraId); // attempt to get a Camera instance
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
			return;
		}
		Camera.Parameters cameraParams = mCamera.getParameters();
		mPreviewSize = new Size(cameraParams.getPreviewSize().width, cameraParams.getPreviewSize().height);
		List<int[]> fpsSupportRange = cameraParams.getSupportedPreviewFpsRange();
		cameraParams.setPreviewFormat(ImageFormat.NV21);
		//cameraParams.setRotation(mCameraOrientation);
		if(cameraParams.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
			cameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
		} else {
			Log.i(Config.TAG, "Camera auto focus is not supported on this device.");
		}

		mCamera.setParameters(cameraParams);
		mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				camera.addCallbackBuffer(data);
				mFrameQueue.offerFirst(data);
				if(mFrameQueue.size() > STACKABLE_FRAME_SIZE){
					for(int i = 0;i < mFrameQueue.size() - STACKABLE_FRAME_SIZE;++i){
						byte[] dataImage = mFrameQueue.pollLast();
					}
				}
			}
		});
		mCamera.addCallbackBuffer(putBuffer());
		mCamera.addCallbackBuffer(putBuffer());
		mCamera.addCallbackBuffer(putBuffer());
		mCamera.addCallbackBuffer(putBuffer());

		try {
			mCamera.setPreviewDisplay(preview);
		} catch (Exception e) {
			e.printStackTrace();
		}
		orientateCameraPreview(mCameraOrientation);
	}

	private byte[] putBuffer(){
		int bitsPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
		byte[] buffer;
		ByteBuffer byteBuffer;
		if((byteBuffer = ByteBuffer.wrap(buffer = new byte[(int)Math.ceil((double)((long)(mPreviewSize.getWidth() * mPreviewSize.getHeight() * bitsPerPixel)) / 8.0D) + 1])).hasArray() && byteBuffer.array() == buffer) {
			return buffer;
		} else {
			throw new IllegalStateException("Failed to create valid buffer for camera source.");
		}
	}

	public void orientateCameraPreview(int orientation){
		mCamera.stopPreview();
		mCamera.setDisplayOrientation(orientation);
		mCamera.startPreview();
	}
	public void stop() {
		if (mCamera != null){
			mCamera.cancelAutoFocus();
			mCamera.stopPreview();
			mCamera.setPreviewCallbackWithBuffer(null);
			mCamera.setPreviewCallback(null);
			try {
				mCamera.setPreviewDisplay(null);
			} catch (IOException e) {
				e.printStackTrace();
			}
			mCamera.release();
			mCamera = null;
		};
	}

	public void release(){
		stop();
	}

	public Bitmap takePreviewPicture() {
		ArrayList<byte[]> frames = new ArrayList<byte[]>(mFrameQueue);
		if(frames.size() > 0){
			byte[] lastFrame = frames.get(frames.size() - 1);
			int width = mPreviewSize.getWidth();
			int height = mPreviewSize.getHeight();
			int[] pixels = JNIUtil.decodeYUV420SP(lastFrame, width, height);
			Bitmap image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
			Bitmap mutableImage = image.copy(Bitmap.Config.ARGB_8888, true);
			image.recycle();
			image = null;
			return mutableImage;
		}else{
			Log.w(Config.TAG, "It is not capture frame!!");
			return null;
		}
	}

	public void addCallbackBuffer(byte[] buffer){
		mCamera.addCallbackBuffer(buffer);
	}

	public Size getPreviewSize(){
		return mPreviewSize;
	}

	public int getOrientation(){
		return mCameraOrientation;
	}
}