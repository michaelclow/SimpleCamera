package idv.orange_newman.simplecamera;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.Slide;
import android.view.Gravity;

public class MainActivity extends AppCompatActivity implements CameraPreviewFragment.OnFragmentInteractionListener, CaptureShowFragment.OnFragmentInteractionListener {

    private CameraPreviewFragment previewFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Slide slide = new Slide(Gravity.LEFT);
        slide.setDuration(500);
        previewFragment = CameraPreviewFragment.newInstance();
        previewFragment.setReenterTransition(slide);
        previewFragment.setExitTransition(slide);
        previewFragment.setSharedElementEnterTransition(new ChangeBounds());
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }
}
