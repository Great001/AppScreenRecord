package com.example.dw.androidscreenrecord;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Created by lhc on 2016/12/19.
 */

//说明
// 跟之前的demo虽然同是屏幕录制生成MP4文件，但有区别，之前的是利用MediaRecorder实现对自动实现对音视频的编码并生成MP4文件
//利用Android 5.0后提供的屏幕录制api MediaProjectionManager MediaProjection  VirturalDisplay 实现录屏
//利用android 4.3之后提供的 MediaCodec 对VirturalDisplay输出的surface进行相应的编码
//利用Android 4.1之后提供的 MediaMuxer 实现对编码后的视频数据合成MP4文件

//由于时间关系，暂时还没有接入音轨录制，不过原理都是差不多的吧

public class MainActivity extends AppCompatActivity {

    private Button mBtnStart;
    private Button mBtnStop;
    private TextView mTvResult;

    private MediaProjection mediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private EncoderThread mEncoderThread;

    private boolean recordState = false;

    public static final int REQUEST_CODE = 111;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtnStart = (Button) findViewById(R.id.btn_start_record);
        mBtnStop = (Button) findViewById(R.id.btn_stop_record);
        mTvResult = (TextView) findViewById(R.id.tv_result);


        //通过系统服务获取MediaProjectionMananger
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecord();
            }
        });

        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecord();
            }
        });
    }

    public void startRecord() {
        checkPermission();
        //Android 5.0 后利用开放api开始屏幕录制
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_CODE);
        Toast.makeText(this, "开始录制......", Toast.LENGTH_SHORT).show();
        mTvResult.setVisibility(View.VISIBLE);
        mTvResult.setText("正在录制中......");
    }

    public void stopRecord() {
        //阻塞编码线程
        if (recordState) {
            mEncoderThread.interrupt();
            recordState = false;
            if(mEncoderThread != null){
                mEncoderThread.quit();
                mEncoderThread = null;
            }
        }
        Toast.makeText(this,"停止录制中......",Toast.LENGTH_SHORT).show();
        mTvResult.setText("完成录制，文件保存在" + getSavePath() + File.separator);
    }

    public void checkPermission() {
        //android 6.0后动态检测申请相关权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 11);
            }

            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, 12);
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);   //根据返回结果，获取MediaProjection--保存帧数据
            //开启编码线程进行编码
            mEncoderThread = new EncoderThread(mediaProjection);
            mEncoderThread.start();
            recordState = true;
        }
    }


    public String getSavePath(){
        String path = "/sdcard/emulated/0";
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            path = Environment.getExternalStorageDirectory().getPath();
        }
        return path;
    }
}
