package com.example.dw.androidscreenrecord;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by lhc on 2016/12/19.
 */

public class EncoderThread extends Thread {

    private MediaProjection mProjection; //帧数据，即编码的数据源
    private MediaCodec mEncoder;  //编码器
    private MediaMuxer mMuxer;   //将音视频数据合成多媒体文件
    private MediaCodec.BufferInfo mBufferInfo;
    private Surface mSurface;  //虚拟屏幕VirturalDiaplay的输出目的地
    private VirtualDisplay mVirtualDisplay;   //虚拟屏幕

    private int mWidth = 480;
    private int mHeight = 720;
    private int mDpi = 240;
    private int mBitRate = 5 * 1024 * 1024;   //编码位率，清晰度
    private int mFrameRate = 30;    //帧率，流畅度
    private int mIFrameInterval = 5; //帧与帧间的间隙

    private int mVideoTrackIndex = -1;   //视频轨索引

    private boolean mMuxerStarted = false;
    private AtomicBoolean mQuit = new AtomicBoolean(false);

    private String mSavePath;


    public static final String MINE_TYPE = "video/avc";
    public static final String TAG = "screen record";

    public EncoderThread(MediaProjection mediaProjection) {
        this.mProjection = mediaProjection;
        try {
            mEncoder = MediaCodec.createEncoderByType(MINE_TYPE);
            initEncoder();
            mSurface = mEncoder.createInputSurface();
            createVirturalDisplay();
            mSavePath = getSavePath();
            mBufferInfo =new  MediaCodec.BufferInfo();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public void initEncoder(){
        MediaFormat format = MediaFormat.createVideoFormat(MINE_TYPE,mWidth,mHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE,mBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE,mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,mIFrameInterval);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mEncoder.configure(format,null,null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }


    public final void quit() {
        mQuit.set(true);
    }


    public void createVirturalDisplay(){
        if(mProjection != null){
            mVirtualDisplay = mProjection.createVirtualDisplay(TAG, mWidth, mHeight,mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,mSurface,null,null);
        }
    }

    //利用MediaCodec编码surface中的数据
    private void recordVirtualDisplay() {
        while (!mQuit.get()) {
            int outputState = mEncoder.dequeueOutputBuffer(mBufferInfo, 1000);
            if (outputState == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                resetOutputFormat();
            }else if (outputState == MediaCodec.INFO_TRY_AGAIN_LATER) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }else if(outputState >= 0) {
                encodeToVideoTrack(outputState);
                mEncoder.releaseOutputBuffer(outputState, false);
            }
        }
    }

    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mEncoder.getOutputBuffer(index);
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            encodedData = null;
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);
        }
    }

    @Override
    public void run() {
        //实现编码
        mEncoder.start();
        mSavePath = mSavePath + "/" + System.currentTimeMillis() + ".mp4";
        try {
            //利用MediaMuxer将视频流合成为MP4文件
            mMuxer = new MediaMuxer(mSavePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            recordVirtualDisplay();
        }catch (IOException e){
            e.printStackTrace();
        } finally{
            release();
        }
    }


    private void resetOutputFormat() {
        if (mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat = mEncoder.getOutputFormat();
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
    }

    private void release() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mProjection != null) {
            mProjection.stop();
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }

    }


    public String getSavePath(){
        String path = "/sdcard/emulated/0/";
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            path = Environment.getExternalStorageDirectory().getPath();
        }
        return path;
    }

}
