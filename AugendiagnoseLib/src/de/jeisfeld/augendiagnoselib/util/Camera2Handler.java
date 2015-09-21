package de.jeisfeld.augendiagnoselib.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import de.jeisfeld.augendiagnoselib.Application;
import de.jeisfeld.augendiagnoselib.R;
import de.jeisfeld.augendiagnoselib.activities.CameraActivity.FlashMode;
import de.jeisfeld.augendiagnoselib.activities.CameraActivity.OnPictureTakenHandler;

/**
 * A handler to take pictures with the camera via the new Camera interface.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressWarnings("static-access")
public class Camera2Handler implements CameraHandler {

	/**
	 * The activity using the handler.
	 */
	private Activity mActivity;

	/**
	 * The FrameLayout holding the preview.
	 */
	private FrameLayout mPreviewFrame;

	/**
	 * Flag indicating if the camera is in preview state.
	 */
	private boolean mIsInPreview = false;

	/**
	 * The handler called when the picture is taken.
	 */
	private OnPictureTakenHandler mOnPictureTakenHandler;

	/**
	 * Constructor of the Camera1Handler.
	 *
	 * @param activity
	 *            The activity using the handler.
	 * @param previewFrame
	 *            The FrameLayout holding the preview.
	 * @param preview
	 *            The view holding the preview.
	 * @param onPictureTakenHandler
	 *            The handler called when the picture is taken.
	 */
	public Camera2Handler(final Activity activity, final FrameLayout previewFrame, final TextureView preview,
			final OnPictureTakenHandler onPictureTakenHandler) {
		this.mActivity = activity;
		this.mPreviewFrame = previewFrame;
		this.mTextureView = preview;
		this.mOnPictureTakenHandler = onPictureTakenHandler;
	}

	/**
	 * Tag for the {@link Log}.
	 */
	private static final String TAG = "Camera2Handler";

	/**
	 * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
	 * {@link TextureView}.
	 */
	private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

		@Override
		public void onSurfaceTextureAvailable(final SurfaceTexture texture, final int width, final int height) {
			openCamera(width, height);
		}

		@Override
		public void onSurfaceTextureSizeChanged(final SurfaceTexture texture, final int width, final int height) {
			configureTransform(width, height);
		}

		@Override
		public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
		}

	};

	/**
	 * ID of the current {@link CameraDevice}.
	 */
	private String mCameraId;

	/**
	 * An {@link AutoFitTextureView} for camera preview.
	 */
	private TextureView mTextureView;

	/**
	 * A {@link CameraCaptureSession } for camera preview.
	 */
	private CameraCaptureSession mCaptureSession;

	/**
	 * A reference to the opened {@link CameraDevice}.
	 */
	private CameraDevice mCameraDevice;

	/**
	 * The {@link android.util.Size} of camera preview.
	 */
	private Size mPreviewSize;

	/**
	 * The surface displaying the preview.
	 */
	private Surface mSurface;

	/**
	 * The autoexposure mode.
	 */
	private int mCurrentAutoExposureMode = CaptureRequest.CONTROL_AE_MODE_OFF;

	/**
	 * The flash mode.
	 */
	private int mCurrentFlashMode = CaptureRequest.FLASH_MODE_OFF;

	/**
	 * An additional thread for running tasks that shouldn't block the UI.
	 */
	private HandlerThread mBackgroundThread = null;

	/**
	 * A {@link Handler} for running tasks in the background.
	 */
	private Handler mBackgroundHandler;

	/**
	 * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
	 */
	private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(@NonNull final CameraDevice cameraDevice) {
			// This method is called when the camera is opened. We start camera preview here.
			mCameraOpenCloseLock.release();
			mCameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(@NonNull final CameraDevice cameraDevice) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(@NonNull final CameraDevice cameraDevice, final int error) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;

			Log.e(Application.TAG, "Error on camera - " + error);
			DialogUtil.displayToast(mActivity, R.string.message_dialog_failed_to_open_camera);
		}

	};

	/**
	 * An {@link ImageReader} that handles still image capture.
	 */
	private ImageReader mImageReader;

	/**
	 * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
	 * still image is ready to be saved.
	 */
	private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
		@Override
		public void onImageAvailable(final ImageReader reader) {
			Image image = reader.acquireNextImage();
			ByteBuffer buffer = image.getPlanes()[0].getBuffer();
			byte[] data = new byte[buffer.remaining()];
			buffer.get(data);
			image.close();

			mOnPictureTakenHandler.onPictureTaken(data);
		}
	};

	/**
	 * {@link CaptureRequest.Builder} for the camera preview.
	 */
	private CaptureRequest.Builder mPreviewRequestBuilder;

	/**
	 * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}.
	 */
	private CaptureRequest mPreviewRequest;

	/**
	 * The current state of camera state for taking pictures.
	 *
	 * @see #mCaptureCallback
	 */
	private CameraState mState = CameraState.STATE_PREVIEW;

	/**
	 * A {@link Semaphore} to prevent the app from exiting before closing the camera.
	 */
	private Semaphore mCameraOpenCloseLock = new Semaphore(1);

	/**
	 * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
	 */
	private CaptureCallback mCaptureCallback = new CaptureCallback() {

		private void process(final CaptureResult result) {
			switch (mState) {
			case STATE_PREVIEW:
				// We have nothing to do when the camera preview is working normally.
				break;
			case STATE_WAITING_LOCK:
				Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
				if (afState == null) {
					captureStillPicture();
				}
				else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
						|| CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
					// CONTROL_AE_STATE can be null on some devices
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
						mState = CameraState.STATE_PICTURE_TAKEN;
						captureStillPicture();
					}
					else {
						runPrecaptureSequence();
					}
				}
				break;
			case STATE_WAITING_UNLOCK:
				unlockFocus();
				break;
			case STATE_WAITING_PRECAPTURE:
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState == null
						|| aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
						|| aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
					mState = CameraState.STATE_WAITING_NON_PRECAPTURE;
				}
				break;
			case STATE_WAITING_NON_PRECAPTURE:
				// CONTROL_AE_STATE can be null on some devices
				Integer aeState2 = result.get(CaptureResult.CONTROL_AE_STATE);
				if (aeState2 == null || aeState2 != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
					mState = CameraState.STATE_PICTURE_TAKEN;
					captureStillPicture();
				}
				break;
			default:
				break;
			}
		}

		@Override
		public void onCaptureProgressed(@NonNull final CameraCaptureSession session,
				@NonNull final CaptureRequest request,
				@NonNull final CaptureResult partialResult) {
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(@NonNull final CameraCaptureSession session,
				@NonNull final CaptureRequest request,
				@NonNull final TotalCaptureResult result) {
			process(result);
		}

	};

	/**
	 * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
	 * width and height are at least as large as the respective requested values, and whose aspect
	 * ratio matches with the specified value.
	 *
	 * @param choices
	 *            The list of sizes that the camera supports for the intended output class
	 * @param width
	 *            The minimum desired width
	 * @param height
	 *            The minimum desired height
	 * @param aspectRatio
	 *            The aspect ratio
	 * @return The optimal {@code Size}, or an arbitrary one if none were big enough
	 */
	private static Size chooseOptimalPreviewSize(final Size[] choices, final int width, final int height, final Size aspectRatio) {
		// Collect the supported resolutions that are at least as big as the preview Surface
		List<Size> bigEnough = new ArrayList<Size>();
		Size biggest = null;

		int w = aspectRatio.getWidth();
		int h = aspectRatio.getHeight();
		for (Size option : choices) {
			if (option.getHeight() * w == option.getWidth() * h) {
				if (option.getHeight() >= height && option.getWidth() >= width) {
					bigEnough.add(option);
				}
				if (biggest == null || option.getHeight() > biggest.getHeight()) {
					biggest = option;
				}
			}
		}

		// Pick the smallest of those, assuming we found any
		if (bigEnough.size() > 0) {
			return Collections.min(bigEnough, new CompareSizesBySmallestSide());
		}
		else if (biggest != null) {
			return biggest;
		}
		else {
			Log.e(TAG, "Couldn't find any suitable preview size");
			return choices[0];
		}
	}

	@Override
	public final void startPreview() {
		if (mIsInPreview) {
			reconfigureCamera();
		}
		else {
			startBackgroundThread();

			if (mTextureView.isAvailable()) {
				openCamera(mTextureView.getWidth(), mTextureView.getHeight());
			}
			else {
				mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
			}
		}
	}

	@Override
	public final void stopPreview() {
		mIsInPreview = false;
		closeCamera();
		stopBackgroundThread();
	}

	/**
	 * Sets up member variables related to camera.
	 *
	 * @param width
	 *            The width of available size for camera preview
	 * @param height
	 *            The height of available size for camera preview
	 */
	private void setUpCameraOutputs(final int width, final int height) {
		CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
		try {
			for (String cameraId : manager.getCameraIdList()) {
				CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

				// We don't use a front facing camera in this sample.
				Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
				if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
					continue;
				}

				StreamConfigurationMap map = characteristics.get(
						CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
				if (map == null) {
					continue;
				}

				// For still image captures, we use the largest available size.
				Size largest = Collections.max(
						Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
						new CompareSizesBySmallestSide());
				mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /* maxImages */2);
				mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

				// Danger, W.R.! Attempting to use too large a preview size could exceed the camera
				// bus' bandwidth limitation, resulting in gorgeous previews but the storage of
				// garbage capture data.
				mPreviewSize = chooseOptimalPreviewSize(map.getOutputSizes(SurfaceTexture.class),
						width, height, largest);

				// Resize frame to match aspect ratio
				float aspectRatio = ((float) mPreviewSize.getWidth()) / mPreviewSize.getHeight();

				LayoutParams layoutParams = mPreviewFrame.getLayoutParams();
				if (mPreviewFrame.getWidth() > aspectRatio * mPreviewFrame.getHeight()) {
					layoutParams.width = Math.round(mPreviewFrame.getHeight() * aspectRatio);
					layoutParams.height = mPreviewFrame.getHeight();
				}
				else {
					layoutParams.width = mPreviewFrame.getWidth();
					layoutParams.height = Math.round(mPreviewFrame.getWidth() / aspectRatio);
				}
				mPreviewFrame.setLayoutParams(layoutParams);

				mCameraId = cameraId;
				return;
			}
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
		catch (NullPointerException e) {
			Log.e(Application.TAG, "Camera2 API seems to be not supported", e);
			DialogUtil.displayToast(mActivity, R.string.message_dialog_failed_to_open_camera);
			// TODO: fallback to normal Camera API
		}
	}

	/**
	 * Opens the camera specified by {@link Camera2Handler#mCameraId}.
	 *
	 * @param width
	 *            the width of the preview
	 * @param height
	 *            the height of the preview
	 */
	private void openCamera(final int width, final int height) {
		mIsInPreview = true;
		setUpCameraOutputs(width, height);
		configureTransform(width, height);
		CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
		try {
			if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) { // MAGIC_NUMBER
				throw new RuntimeException("Time out waiting to lock camera opening.");
			}
			manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
		}
	}

	/**
	 * Closes the current {@link CameraDevice}.
	 */
	private void closeCamera() {
		try {
			mCameraOpenCloseLock.acquire();
			if (null != mCaptureSession) {
				mCaptureSession.close();
				mCaptureSession = null;
			}
			if (null != mCameraDevice) {
				mCameraDevice.close();
				mCameraDevice = null;
			}
			if (null != mImageReader) {
				mImageReader.close();
				mImageReader = null;
			}
		}
		catch (InterruptedException e) {
			throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
		}
		finally {
			mCameraOpenCloseLock.release();
		}
	}

	/**
	 * Creates a new {@link CameraCaptureSession} for camera preview.
	 */
	private void createCameraPreviewSession() {
		try {
			SurfaceTexture texture = mTextureView.getSurfaceTexture();
			assert texture != null;

			// We configure the size of default buffer to be the size of camera preview we want.
			texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

			// This is the output Surface we need to start preview.
			mSurface = new Surface(texture);

			// Here, we create a CameraCaptureSession for camera preview.
			mCameraDevice.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface()),
					new CameraCaptureSession.StateCallback() {

						@Override
						public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
							// The camera is already closed
							if (mCameraDevice == null) {
								return;
							}

							// When the session is ready, we start displaying the preview.
							mCaptureSession = cameraCaptureSession;

							doPreviewConfiguration();
						}

						@Override
						public void onConfigureFailed(
								@NonNull final CameraCaptureSession cameraCaptureSession) {
							mActivity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									DialogUtil.displayToast(mActivity, R.string.message_dialog_failed_to_open_camera_display);
								}
							});
						}
					}, mBackgroundHandler);
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
	 * This method should be called after the camera preview size is determined in
	 * setUpCameraOutputs and also the size of `mTextureView` is fixed.
	 *
	 * @param viewWidth
	 *            The width of `mTextureView`
	 * @param viewHeight
	 *            The height of `mTextureView`
	 */
	private void configureTransform(final int viewWidth, final int viewHeight) {
		if (null == mTextureView || null == mPreviewSize || null == mActivity) {
			return;
		}
		int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
		Matrix matrix = new Matrix();
		RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
		RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
		float centerX = viewRect.centerX();
		float centerY = viewRect.centerY();
		if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
			bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
			matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
			float scale = Math.max(
					(float) viewHeight / mPreviewSize.getHeight(),
					(float) viewWidth / mPreviewSize.getWidth());
			matrix.postScale(scale, scale, centerX, centerY);
			matrix.postRotate(90 * (rotation - 2), centerX, centerY); // MAGIC_NUMBER
		}
		else if (Surface.ROTATION_180 == rotation) {
			matrix.postRotate(180, centerX, centerY); // MAGIC_NUMBER
		}
		mTextureView.setTransform(matrix);
	}

	/**
	 * Initiate a still image capture.
	 */
	@Override
	public final void takePicture() {
		lockFocus();
	}

	/**
	 * Lock the focus as the first step for a still image capture.
	 */
	private void lockFocus() {
		try {
			// This is how to tell the camera to lock focus.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the lock.
			mState = CameraState.STATE_WAITING_LOCK;

			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Run the precapture sequence for capturing a still image. This method should be called when
	 * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
	 */
	private void runPrecaptureSequence() {
		try {
			// This is how to tell the camera to trigger.
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
					CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			// Tell #mCaptureCallback to wait for the precapture sequence to be set.
			mState = CameraState.STATE_WAITING_PRECAPTURE;
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Capture a still picture. This method should be called when we get a response in
	 * {@link #mCaptureCallback} from both {@link #lockFocus()}.
	 */
	private void captureStillPicture() {
		try {
			if (null == mActivity || null == mCameraDevice) {
				return;
			}
			// This is the CaptureRequest.Builder that we use to take a picture.
			final CaptureRequest.Builder captureBuilder =
					mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(mImageReader.getSurface());

			// Use the same AE and AF modes as the preview.
			captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			captureBuilder.set(CaptureRequest.FLASH_MODE, mCurrentFlashMode);
			captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, mCurrentAutoExposureMode);

			mCaptureSession.stopRepeating();
			mState = CameraState.STATE_WAITING_UNLOCK;
			mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, mBackgroundHandler);

			mOnPictureTakenHandler.onTakingPicture();
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Unlock the focus. This method should be called when still image capture sequence is
	 * finished.
	 */
	private void unlockFocus() {
		try {
			// Reset the auto-focus trigger
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
			// After this, the camera will go back to the normal state of preview.
			mState = CameraState.STATE_PREVIEW;
			mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
		}
		catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Starts a background thread and its {@link Handler}.
	 */
	private void startBackgroundThread() {
		if (mBackgroundThread == null) {
			mBackgroundThread = new HandlerThread("CameraBackground");
			mBackgroundThread.start();
			mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
		}
	}

	/**
	 * Stops the background thread and its {@link Handler}.
	 */
	private void stopBackgroundThread() {
		if (mBackgroundThread != null) {
			mBackgroundThread.quitSafely();
			try {
				mBackgroundThread.join();
				mBackgroundThread = null;
				mBackgroundHandler = null;
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public final void setFlashlightMode(final FlashMode flashlightMode) {
		if (flashlightMode == null) {
			mCurrentAutoExposureMode = CaptureRequest.CONTROL_AE_MODE_OFF;
		}
		else {
			switch (flashlightMode) {
			case OFF:
				mCurrentFlashMode = CaptureRequest.FLASH_MODE_OFF;
				mCurrentAutoExposureMode = CaptureRequest.CONTROL_AE_MODE_OFF;
				break;
			case ON:
				mCurrentFlashMode = CaptureRequest.FLASH_MODE_SINGLE;
				mCurrentAutoExposureMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
				break;
			case TORCH:
				mCurrentFlashMode = CaptureRequest.FLASH_MODE_TORCH;
				mCurrentAutoExposureMode = CaptureRequest.CONTROL_AE_MODE_ON;
				break;
			default:
				mCurrentFlashMode = CaptureRequest.FLASH_MODE_OFF;
				mCurrentAutoExposureMode = CaptureRequest.CONTROL_AE_MODE_OFF;
				break;
			}
		}

		if (mCameraDevice == null) {
			return;
		}

		reconfigureCamera();
	}

	/**
	 * Reconfigure the camera with new flash and focus settings.
	 */
	private void reconfigureCamera() {
		if (mCameraDevice != null) {
			try {
				mCaptureSession.stopRepeating();

				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
				mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);

				doPreviewConfiguration();
			}
			catch (CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Do the setting of flash and focus settings.
	 */
	private void doPreviewConfiguration() {
		if (mCameraDevice != null) {
			mState = CameraState.STATE_PREVIEW;
			try {
				// Need to recreate the complete request from scratch - reuse will fail.
				mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
				mPreviewRequestBuilder.addTarget(mSurface);

				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
				mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, mCurrentFlashMode);
				mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, mCurrentAutoExposureMode);
				mPreviewRequest = mPreviewRequestBuilder.build();
				mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
			}
			catch (CameraAccessException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Compares two {@code Size}s based on their areas.
	 */
	static class CompareSizesBySmallestSide implements Comparator<Size> {
		@Override
		public int compare(final Size lhs, final Size rhs) {
			int leftSize = Math.min(lhs.getWidth(), lhs.getHeight());
			int rightSize = Math.min(rhs.getWidth(), rhs.getHeight());

			if (leftSize > rightSize) {
				return 1;
			}
			else if (rightSize > leftSize) {
				return -1;
			}

			// prefer landscape
			return Integer.signum(lhs.getWidth() - lhs.getHeight());
		}
	}

	/**
	 * Camera states.
	 */
	enum CameraState {
		/**
		 * Camera state: Showing camera preview.
		 */
		STATE_PREVIEW,

		/**
		 * Camera state: Waiting for the focus to be locked.
		 */
		STATE_WAITING_LOCK,

		/**
		 * Camera state: Waiting for the focus to be locked.
		 */
		STATE_WAITING_UNLOCK,

		/**
		 * Camera state: Waiting for the exposure to be precapture state.
		 */
		STATE_WAITING_PRECAPTURE,

		/**
		 * Camera state: Waiting for the exposure state to be something other than precapture.
		 */
		STATE_WAITING_NON_PRECAPTURE,

		/**
		 * Camera state: Picture was taken.
		 */
		STATE_PICTURE_TAKEN
	}

}