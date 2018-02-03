package idv.orange_newman.simplecamera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.transition.ChangeBounds;
import android.transition.Slide;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;

import java.nio.ByteBuffer;
import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link CameraPreviewFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link CameraPreviewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CameraPreviewFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

    // TODO: Rename and change types of parameters

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // UI views
    private View parentView;
    private TextureView tvPreview;
    private ImageButton btnCapture;
    private SeekBar sbZoom;
    private Fragment captureShowFragment;

    // Camera
    private HandlerThread cameraBgThread;           // Background thread for camera
    private Handler cameraBgHandler;                // Handler generated from background thread
    private Handler mainHandler;
    private String cameraID;                        // Camera's ID
    private ImageReader imgReader;                  // Image reader to get captured image from buffer
    private CameraManager cameraManager;            // Android camera manager
    private CameraDevice cameraDevice;              // Camera device in use
    private CameraCaptureSession captureSession;    // Capture session in use
    private CameraCharacteristics cameraCharacteristics;    // Camera's characteristics
    private float maxDigitalZoom;
    private CaptureRequest.Builder previewRequestBuilder;   // Preview request builder
    private CaptureRequest previewRequest;                  // Preview request
    private boolean isManualFocusing;

    private float[] tvLastTouchDownPos = new float[2];

    private OnFragmentInteractionListener mListener;

    public CameraPreviewFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment CameraPreviewFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static CameraPreviewFragment newInstance() {
        CameraPreviewFragment fragment = new CameraPreviewFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        parentView = inflater.inflate(R.layout.fragment_camera_preview, container, false);
        return inflater.inflate(R.layout.fragment_camera_preview, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        // Preview texture view
        tvPreview = (TextureView) view.findViewById(R.id.tvPreview);
        // Capture button
        btnCapture = (ImageButton) view.findViewById(R.id.imgBtnCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        // Zoom in/out seek bar
        sbZoom = (SeekBar) view.findViewById(R.id.sbZoom);
        sbZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Rect activeArea = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        isManualFocusing = false;   // Initialize MF flag
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        dialogBuilder.setMessage(R.string.alert_perm_req_msg);
        dialogBuilder.setPositiveButton(R.string.btn_allow, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            }
        });
        dialogBuilder.setNegativeButton(R.string.btn_deny, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Activity activity = getActivity();
                if ( activity != null ) {
                    activity.finish();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        // Initialize camera background handler thread
        cameraBgThread = new HandlerThread("CameraBackgroundHandle");
        cameraBgThread.start();
        cameraBgHandler = new Handler(cameraBgThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize the place to show the captured image
        initImgReader();

        // Initialize texture view and camera
        if ( tvPreview.isAvailable() ) {
            initCamera();
        } else {
            initTextureView();
        }
    }

    private void initTextureView() {
        Log.d("Texture View", "Initialize texture view...");
        tvPreview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getActionMasked() ) {
                    case MotionEvent.ACTION_DOWN:
                        break;
                    case MotionEvent.ACTION_UP:
                        tvLastTouchDownPos[0] = event.getX();
                        tvLastTouchDownPos[1] = event.getY();
                        v.performClick();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        tvPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refocus();
            }
        });
        tvPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                initCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * Initialize image reader to read captured image from buffer.
     */
    private void initImgReader() {
        imgReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 1);
        imgReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);  // Save image from buffer to byte array
                image.close();  // Close captured image
                Log.d("Image reader", "Image byte array is acquired.");

                // Transit to image show fragment
                Slide slide = new Slide(Gravity.RIGHT);
                slide.setDuration(500);
                captureShowFragment = CaptureShowFragment.newInstance(bytes);
                captureShowFragment.setEnterTransition(slide);
                captureShowFragment.setReturnTransition(slide);
                captureShowFragment.setSharedElementEnterTransition(new ChangeBounds());
                getFragmentManager().beginTransaction().replace(R.id.fragment, captureShowFragment).addToBackStack(null).commit();
            }
        }, mainHandler);
    }

    /**
     * This function runs the following steps to open camera:
     * 1. Get camera manager from camera service
     * 2. Check and get permission of accessing camera if necessary
     * 3. Open camera from camera manager
     * 4. There is a camera session when camera is opened
     * 5. Create a "preview capture request" from camera request builder
     * 6. Set continuous preview request to camera session
     */
    private void initCamera() {
        Log.d("Camera", "Initialize camera...");
        cameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;
        cameraManager = (CameraManager) parentView.getContext().getSystemService(Context.CAMERA_SERVICE);

        // Check camera usage permission
        if (ActivityCompat.checkSelfPermission(parentView.getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }

        // Open camera service
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
            maxDigitalZoom = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) * 10;
            sbZoom.setMax((int)maxDigitalZoom); // Customize max value by camera's max digital zoom
            Log.d("Camera", "max digital zoom = " + String.valueOf(maxDigitalZoom));
            cameraManager.openCamera(cameraID, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        // Create continuous preview handler for camera
                        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        Surface surface = new Surface(tvPreview.getSurfaceTexture());
                        previewRequestBuilder.addTarget(surface);
                        cameraDevice.createCaptureSession(Arrays.asList(surface, imgReader.getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                if ( cameraDevice == null ) {
                                    return;
                                }
                                captureSession = session;
                                // Auto focus
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Auto exposure
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);

                                previewRequest = previewRequestBuilder.build();
                                try {
                                    captureSession.setRepeatingRequest(previewRequest, null, cameraBgHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                            }
                        }, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    cameraDevice.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {

                }
            }, mainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        // End camera device
        if ( captureSession != null ) {
            captureSession.close();
            captureSession = null;
        }
        if ( cameraDevice != null ) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if ( imgReader != null ) {
            imgReader.close();
            imgReader = null;
        }
        // End background thread
        cameraBgThread.quitSafely();
        try {
            cameraBgThread.join();
            cameraBgThread = null;
            cameraBgHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This function runs the following steps to capture a still image
     * 1. Create a capture request from request builder
     * 2. Set capture request to camera session
     * Note: the event "onImageAvailable" of image reader will be triggered after camera captures a still image successfully.
     */
    private void takePicture() {
        if ( cameraDevice == null ) {
            return;
        }
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imgReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            // Build camera capture request
            CaptureRequest request = captureRequestBuilder.build();
            captureSession.capture(request, null, cameraBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * On mobile phone, Phase Detection(PD) cannot be used to re-calibrate focus because there is no space to install AF sensor hardware.
     * Therefore, the simpler Contrast Detection(CD) is often used, here is its principle:
     * 1. Mark an area for re-calibrate focus
     * 2. Calculate the image difference thresholds
     * 3. Choose the highest threshold as the focus
     * This function runs the following steps to trigger re-focus procedures:
     * 1. Get the dimension of CCD/CMOS sensor
     * 2. In this case, the example focus area dimension is 150x150, create a MeteringRectangle object.
     * 3. Stop repeating preview
     * 3. Create one single AF as MF with the AF region, which is marked by the MeteringRectangle object
     * 4. Request the capture of MF
     * 5. Re-start repeating preview when MF is completed
     */
    private void refocus() {
        final Rect sensorArraySize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        // Calculate focus area
        final int x = (int) (tvLastTouchDownPos[0] / tvPreview.getWidth());
        final int y = (int) (tvLastTouchDownPos[1] / tvPreview.getHeight());
        final int halfFocusWidth = 150;
        final int halfFocusHeigth = 150;
        MeteringRectangle focusArea = new MeteringRectangle(
                Math.max(x - halfFocusWidth, 0),
                Math.max(y - halfFocusHeigth, 0),
                halfFocusWidth * 2,
                halfFocusHeigth * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1
        );

        // Capture callback handler
        CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                isManualFocusing = false;

                if ( request.getTag() == "FOCUS_TAG" ) {
                    // Manual focus is completed, return to continuous AF and AE
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                    previewRequest = previewRequestBuilder.build();
                    try {
                        captureSession.setRepeatingRequest(previewRequest, null, cameraBgHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e("Camera", "manual focus failure.");
                isManualFocusing = false;
            }
        };

        try {
            // Stop auto-focus
            captureSession.stopRepeating();
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            previewRequest = previewRequestBuilder.build();
            captureSession.capture(previewRequest, captureCallback, cameraBgHandler);

            // Request MF - one single time AF
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusArea});
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            previewRequestBuilder.setTag("FOCUS_TAG");  // Set this tag to distinguish from other actions
            previewRequest = previewRequestBuilder.build();
            captureSession.capture(previewRequest, captureCallback, cameraBgHandler);
            isManualFocusing = true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}
