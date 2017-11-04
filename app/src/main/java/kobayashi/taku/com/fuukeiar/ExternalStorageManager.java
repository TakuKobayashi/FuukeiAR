package kobayashi.taku.com.fuukeiar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExternalStorageManager {
    public static final String DIRECTORY_NAME_TO_SAVE = "FuukeiAR";

    //画像をSDカードに保存する
    public static boolean saveImage(Context context, Bitmap bitmap, String strFilePath) {
        ContentResolver contentResolver = context.getContentResolver();
        String name = new StringBuffer().toString();
        // 保存する画像の情報をDBに登録してギャラリーなどで検索してデータが出てくるようにする。
        // 画像が保存されている場所の情報をとって来る
        Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.DATA, strFilePath);
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        contentValues.put(MediaStore.Images.Media.TITLE, name);
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        //contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        // DBに新しくとってきたコンテンツの情報を挿入する(contentResolver.insert)
        Uri imageUri = contentResolver.insert(mediaUri, contentValues);
        MediaStore.Images.Media.insertImage(contentResolver, bitmap, context.getString(R.string.app_name), null);
        try {
            // imageUriにあるファイルを開く(openOutputStream)
            OutputStream outputStream = contentResolver.openOutputStream(imageUri);
            // bitmap画像を圧縮する(圧縮後の拡張子,圧縮率,画像)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            //bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.close();
            File file = new File(strFilePath);
            // 画像を保存する(UriにあるデータをスキャンしmediaDBに登録する)
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    //保存するファイルのパスをとって来る
    public static String getFilePath(String file_extention) {
        String strFilePath = new String();
        String strTempName = new String();
        int i = 0;
        while (true) {
            String strExtDir = loadApplicationDirectoryPath();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy'-'MM'-'dd'_'HH':'mm':'ss");
            String timeString = sdf.format(new Date());

            if (i == 0) {
                // 年-月-日_時.分.秒 + 拡張子 または 年-月-日_時.分.秒 +_番号 + 拡張子
                strTempName = timeString + file_extention;
            } else {
                strTempName = timeString + "_" + i + file_extention;
            }
            strFilePath = strExtDir + strTempName;
            File file = new File(strFilePath);
            // 同じファイル名がないか調べる
            if (file.exists() == false){
                break;
            }
            i++;
        }
        return strFilePath;
    }

    //Android2.3以前のSDカードはAndroid4.0以降では第二メモリを指すので、SDカードに保存できる時はそちらに保存するようにパスを指定する
    public static String getRootDirectoryPath(){
        String path = "/sdcard2/";
        File file = new File(path);
        if(file.exists() && file.isDirectory()){
            return path;
        }else{
            return Environment.getExternalStorageDirectory().toString() + "/";
        }
    }

    public static String loadApplicationDirectoryPath(){
        String applicationPath = getRootDirectoryPath() + "/" + DIRECTORY_NAME_TO_SAVE;
        File file = new File(applicationPath);
        if(file.exists() && file.isDirectory()){
            return applicationPath + "/";
        }else{
            if(file.exists()){
                file.delete();
            }
            file.mkdir();
            return applicationPath + "/";
        }
    }

    //SDカードがマウント中かどうか調べる
    public static boolean checkSDcardMount() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
}
