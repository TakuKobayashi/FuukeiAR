package kobayashi.taku.com.fuukeiar;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.io.File;

public class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // スクリーンセーバをオフにする
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.take_picture_preview);

        ImageView previewImage = (ImageView) findViewById(R.id.take_picture_view);
        Glide.with(this).load(Uri.fromFile(new File(getIntent().getStringExtra("saveImagePath")))).into(previewImage);

        ImageView facebookButton = (ImageView) findViewById(R.id.facebook_image_button);
        facebookButton.setImageBitmap(Util.loadImageFromAsset(this, "icons/pct_facebook.png"));
        facebookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });


        ImageView tweetButton = (ImageView) findViewById(R.id.tweet_image_button);
        tweetButton.setImageBitmap(Util.loadImageFromAsset(this, "icons/pct_twitter.png"));
        tweetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        ImageView lineButton = (ImageView) findViewById(R.id.line_image_button);
        lineButton.setImageBitmap(Util.loadImageFromAsset(this, "icons/pct_line.png"));
        lineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(PreviewActivity.this);
                builder.setChooserTitle(getString(R.string.app_name));
                builder.setText("#ma_2017");
                builder.setType("image/png");
                builder.addStream(Uri.fromFile(new File(getIntent().getStringExtra("saveImagePath"))));
                builder.startChooser();
            }
        });

        ImageView backButton = (ImageView) findViewById(R.id.back_image_button);
        backButton.setImageBitmap(Util.loadImageFromAsset(this, "pct_back.png"));
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Util.releaseImageView((ImageView) findViewById(R.id.take_picture_view));
        Util.releaseImageView((ImageView) findViewById(R.id.facebook_image_button));
        Util.releaseImageView((ImageView) findViewById(R.id.tweet_image_button));
        Util.releaseImageView((ImageView) findViewById(R.id.line_image_button));
        Util.releaseImageView((ImageView) findViewById(R.id.back_image_button));
    }
}
