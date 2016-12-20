package com.example.dw.androidscreenrecord;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by lhc on 2016/12/20.
 */

public class DecoderThread extends Thread implements EncoderThread.EncodeListener{

    private int mWidth;
    private int mHeight;
    private MediaCodec mDecoder;
    private Surface mSurface;
    private MediaCodec.BufferInfo mBufferInfo;

    private int count;
    private static final int timeout = 1000;
    public static final String MINE_TYPE = "video/avc";

    public DecoderThread(Surface surface,int width,int height){
        this.mWidth = width;
        this.mHeight = height;
        this.mSurface = surface;
        try {
            mDecoder = MediaCodec.createDecoderByType(MINE_TYPE);
        }catch (IOException e){
            e.printStackTrace();
        }
        initDecoder();
        mBufferInfo = new MediaCodec.BufferInfo();
    }

    public void initDecoder(){
        MediaFormat format = MediaFormat.createVideoFormat(MINE_TYPE,mWidth,mHeight);
        mDecoder.configure(format,mSurface,null,0);
    }

    @Override
    public void run() {
        mDecoder.start();
    }

    @Override
    public void onEncode(byte [] data ,int offset,int length) {
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        int inputBufferIndex = mDecoder.dequeueInputBuffer(timeout);
        if(inputBufferIndex >= 0){
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(data,offset,length);
            mDecoder.queueInputBuffer(inputBufferIndex,offset,length,count * 100000 /3,0);
            count++;
        }

        int outputBufferIndex = mDecoder.dequeueOutputBuffer(mBufferInfo,-1);
        while(outputBufferIndex >= 0){
            mDecoder.releaseOutputBuffer(outputBufferIndex,true);
            outputBufferIndex = mDecoder.dequeueOutputBuffer(mBufferInfo,timeout);
        }
    }


    public void release() {
        if (mDecoder != null) {
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
    }
}
