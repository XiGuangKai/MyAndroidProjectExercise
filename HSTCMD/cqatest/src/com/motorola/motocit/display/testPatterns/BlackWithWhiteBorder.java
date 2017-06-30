/*
 * Copyright (c) 2012 - 2014 Motorola Mobility, Inc.
 * All Rights Reserved
 *
 * The contents of this file are Motorola Confidential Restricted (MCR).
 */

package com.motorola.motocit.display.testPatterns;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import com.motorola.motocit.TestUtils;

public class BlackWithWhiteBorder extends BlackPattern
{
    protected GLSurfaceView mGLSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        if (TAG == null)
        {
            TAG = "TestPattern_BlackWithWhiteBorder";
        }
        super.onCreate(savedInstanceState);

        setRequestedOrientation(1);

        mGLSurfaceView = new GLSurfaceView(this);
        mGLSurfaceView.setRenderer(new BlackWithWhiteBorder_Renderer());
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setContentView(mGLSurfaceView);
        if (mGestureListener != null)
        {
            mGLSurfaceView.setOnTouchListener(mGestureListener);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // invalidate current view so onDraw is always called to make sure
        // commServer will get ACK packet
        mGLSurfaceView.requestRender();
    }

    public class BlackWithWhiteBorder_Renderer implements GLSurfaceView.Renderer
    {
        private ShortBuffer mFillVertexBuffer;
        private ByteBuffer mFillTexCoordBuffer;
        private ByteBuffer mFillColorBuffer;
        private int mFillNumElements;

        private ShortBuffer mLineVertexBuffer;
        private ByteBuffer mLineTexCoordBuffer;
        private ByteBuffer mLineColorBuffer;
        private int mLineNumElements;

        private int mWidth;
        private int mHeight;

        @Override
        public void onDrawFrame(GL10 gl) {
            // Clear background to black
            gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

            // Use scissor to clear central area inside display offset
            gl.glEnable(GL10.GL_SCISSOR_TEST);
            gl.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
            gl.glDisable(GL10.GL_SCISSOR_TEST);

            // Draw black horizontal lines for background
            gl.glVertexPointer(2, GL10.GL_SHORT, 0, mFillVertexBuffer);
            gl.glTexCoordPointer(2, GL10.GL_BYTE, 0, mFillTexCoordBuffer);
            gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, mFillColorBuffer);
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, mFillNumElements);

            // Draw white border lines
            gl.glVertexPointer(2, GL10.GL_SHORT, 0, mLineVertexBuffer);
            gl.glTexCoordPointer(2, GL10.GL_BYTE, 0, mLineTexCoordBuffer);
            gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, mLineColorBuffer);
            gl.glDrawArrays(GL10.GL_LINE_LOOP, 0, mLineNumElements);

            if (bInvalidateViewOn)
            {
                mGLSurfaceView.requestRender();
            }

            if (sendPassPacket == true)
            {
                sendPassPacket = false;
                sendStartActivityPassed();
            }
        }

        private ByteBuffer allocNativeByteBuffer(int length) {
            ByteBuffer bb = ByteBuffer.allocateDirect(length);
            bb.order(ByteOrder.nativeOrder());
            return bb;
        }

        private ShortBuffer allocNativeShortBuffer(int length) {
            return allocNativeByteBuffer(2*length).asShortBuffer();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            DisplayWidth = width;
            DisplayHeight = height;

            final byte[] colorWhite = new byte [] { (byte)255, (byte)255, (byte)255, (byte)255 };
            final byte[] colorBlack = new byte [] { (byte)0, (byte)0, (byte)0, (byte)255 };
            final byte[] colorBlue = new byte [] { (byte)0, (byte)0, (byte)255, (byte)255 };

            final int texSize = 64;
            final byte[] texCoordWhite = new byte [] { (byte)16, (byte)32 };
            final byte[] texCoordBlack = new byte [] { (byte)48, (byte)32 };

            int xLeftOffset = TestUtils.getDisplayXLeftOffset();
            int xRightOffset = TestUtils.getDisplayXRightOffset();
            int yTopOffset = TestUtils.getDisplayYTopOffset();
            int yBottomOffset = TestUtils.getDisplayYBottomOffset();
            int xmin = 1 + xLeftOffset;
            int ymin = 1 + yTopOffset;
            int xmax = width - 1 - xRightOffset;
            int ymax = height - 1 - yBottomOffset;



            // Create a triangle strip with 2 vertices per line on the display
            // All vertices reference a single pixel of the texture which contains black
            mFillNumElements = 2*((ymax-ymin)+1);
            mFillVertexBuffer = allocNativeShortBuffer(2*mFillNumElements);
            mFillTexCoordBuffer = allocNativeByteBuffer(2*mFillNumElements);
            mFillColorBuffer = allocNativeByteBuffer(4*mFillNumElements);

            for (int y = ymin; y <= ymax; y++)
            {
                mFillVertexBuffer.put((short)xmin).put((short)y);
                mFillTexCoordBuffer.put(texCoordBlack);
                mFillColorBuffer.put(colorWhite);

                mFillVertexBuffer.put((short)xmax).put((short)y);
                mFillTexCoordBuffer.put(texCoordBlack);
                mFillColorBuffer.put(colorWhite);
            }



            mFillVertexBuffer.position(0);
            mFillTexCoordBuffer.position(0);
            mFillColorBuffer.position(0);

            // Create a line loop which connects the 4 corners of the display
            // All vertices reference a single pixel of the texture which contains white
            mLineNumElements = 4;
            mLineVertexBuffer = allocNativeShortBuffer(2*mLineNumElements);
            mLineTexCoordBuffer = allocNativeByteBuffer(2*mLineNumElements);
            mLineColorBuffer = allocNativeByteBuffer(4*mLineNumElements);

            mLineVertexBuffer.put((short)(xmin-1)).put((short)(ymin-1));
            mLineVertexBuffer.put((short)(xmax+0)).put((short)(ymin-1));
            mLineVertexBuffer.put((short)(xmax+0)).put((short)(ymax+0));
            mLineVertexBuffer.put((short)(xmin-1)).put((short)(ymax+0));

            for (int i = 0; i < mLineNumElements; i++)
            {
                mLineTexCoordBuffer.put(texCoordWhite);
                mLineColorBuffer.put(colorWhite);
            }

            mLineVertexBuffer.position(0);
            mLineTexCoordBuffer.position(0);
            mLineColorBuffer.position(0);



            // Create a texture filled with blue, with one white pixel and one black pixel
            ByteBuffer texData = allocNativeByteBuffer(4*texSize*texSize);
            for (int i = 0; i < (texSize * texSize); i++ )
            {
                texData.put(colorBlue);
            }
            texData.position(4*((texCoordWhite[1]*texSize)+texCoordWhite[0]));
            texData.put(colorWhite);
            texData.position(4*((texCoordBlack[1]*texSize)+texCoordBlack[0]));
            texData.put(colorBlack);
            texData.position(0);


            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
            gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
            gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_RGBA, texSize, texSize, 0, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, texData);
            gl.glEnable(GL10.GL_TEXTURE_2D);
            gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);


            // Set viewport to cover screen and scissor to cover region inside display offset
            gl.glViewport(0, 0, width, height);

            gl.glScissor(xLeftOffset, yBottomOffset, width - (xLeftOffset + xRightOffset), height - (yTopOffset + yBottomOffset));

            // Set texture matrix to use pixels as texture coordinates
            gl.glMatrixMode(GL10.GL_TEXTURE);
            gl.glLoadIdentity();
            gl.glScalef(1.0f/texSize, 1.0f/texSize, 1.0f);
            gl.glTranslatef(0.5f, 0.5f, 0.0f);

            // Set projection matrix to use pixel coordinates with (0, 0) at top left
            gl.glMatrixMode(GL10.GL_PROJECTION);
            gl.glLoadIdentity();
            gl.glOrthof(0.0f, width, height, 0.0f, -1.0f, 1.0f);

            // Shift x, y by 3/8 so that lines and polygons will be properly aligned
            gl.glMatrixMode(GL10.GL_MODELVIEW);
            gl.glLoadIdentity();
            gl.glTranslatef(0.375f, 0.375f, 0.0f);

            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev)
    {
        // When running from CommServer normally ignore KeyDown event
        if ((wasActivityStartedByCommServer() == true) || !TestUtils.getPassFailMethods().equalsIgnoreCase("VOLUME_KEYS"))
        {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {

            contentRecord("testresult.txt", "Display Pattern - Black White Border:  PASS" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_PASS, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {

            contentRecord("testresult.txt", "Display Pattern - Black White Border:  FAILED" + "\r\n\r\n", MODE_APPEND);

            logTestResults(TAG, TEST_FAIL, null, null);

            try
            {
                Thread.sleep(1000, 0);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            systemExitWrapper(0);
        }
        else if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (modeCheck("Seq"))
            {
                Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

                return false;
            }
            else
            {
                systemExitWrapper(0);
            }
        }

        return true;
    }

    @Override
    public boolean onSwipeRight()
    {
        contentRecord("testresult.txt", "Display Pattern - Black White Border:  FAILED" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_FAIL, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeLeft()
    {

        contentRecord("testresult.txt", "Display Pattern - Black White Border:  PASS" + "\r\n\r\n", MODE_APPEND);

        logTestResults(TAG, TEST_PASS, null, null);

        try
        {
            Thread.sleep(1000, 0);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        systemExitWrapper(0);
        return true;
    }

    @Override
    public boolean onSwipeUp()
    {
        return true;
    }

    @Override
    public boolean onSwipeDown()
    {
        if (modeCheck("Seq"))
        {
            Toast.makeText(this, getString(com.motorola.motocit.R.string.mode_notice), Toast.LENGTH_SHORT).show();

            return false;
        }
        else
        {
            systemExitWrapper(0);
        }
        return true;
    }
}
