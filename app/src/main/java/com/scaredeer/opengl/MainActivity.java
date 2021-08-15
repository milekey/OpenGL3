package com.scaredeer.opengl;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_COMPILE_STATUS;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_LINK_STATUS;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.GL_VALIDATE_STATUS;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glAttachShader;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glCompileShader;
import static android.opengl.GLES20.glCreateProgram;
import static android.opengl.GLES20.glCreateShader;
import static android.opengl.GLES20.glDeleteProgram;
import static android.opengl.GLES20.glDeleteShader;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetProgramInfoLog;
import static android.opengl.GLES20.glGetProgramiv;
import static android.opengl.GLES20.glGetShaderInfoLog;
import static android.opengl.GLES20.glGetShaderiv;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glLinkProgram;
import static android.opengl.GLES20.glShaderSource;
import static android.opengl.GLES20.glUniform4f;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glValidateProgram;
import static android.opengl.GLES20.glVertexAttribPointer;
import static android.opengl.GLES20.glViewport;
import static android.opengl.Matrix.frustumM;
import static android.opengl.Matrix.multiplyMM;
import static android.opengl.Matrix.setLookAtM;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = "MainActivity";

    private static final int BYTES_PER_FLOAT = 4; // Java float is 32-bit = 4-byte
    private static final int POSITION_COMPONENT_COUNT = 2; // x, y（※ z は常に 0 なので省略）
    // STRIDE は要するに、次の頂点データセットへスキップするバイト数を表したもの。
    // 一つの頂点データセットは頂点座標 x, y の 2 つで構成されているが、
    // 次の頂点を処理する際に、2 つ分のバイト数をスキップする必要が生じる。
    private static final int STRIDE = POSITION_COMPONENT_COUNT * BYTES_PER_FLOAT;

    private static final float[] TILE = new float[]{ // x, y
            0f, 0f, // 左下
            0f, 256f, // 左上
            256f, 0f, // 右下
            256f, 256f, // 右上
    };

    // Attributes
    private static final String A_POSITION = "a_Position";
    private int aPosition;
    // Uniforms
    private static final String U_MVP_MATRIX = "u_MVPMatrix";
    private int uMVPMatrix;
    private static final String U_COLOR = "u_Color";
    private int uColor;

    private static final String VERTEX_SHADER = String.format(
            "uniform mat4 %s;\n" +
                    "attribute vec4 %s;\n" +
                    "void main() {\n" +
                    "    gl_Position = %s * %s;\n" +
                    "}",
            U_MVP_MATRIX, A_POSITION, U_MVP_MATRIX, A_POSITION
    );
    private static final String FRAGMENT_SHADER = String.format(
            "precision mediump float;\n" +
                    "uniform vec4 %s;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = %s;\n" +
                    "}",
            U_COLOR, U_COLOR
    );

    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mVPMatrix = new float[16];

    private GLSurfaceView mGLSurfaceView;
    private FloatBuffer mVertexData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        mGLSurfaceView = new GLSurfaceView(this);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setRenderer(this);

        setContentView(mGLSurfaceView);

        // JavaVM (float) -> DirectBuffer (FloatBuffer) -> OpenGL
        mVertexData = ByteBuffer
                .allocateDirect(TILE.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(TILE);
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy");
        mGLSurfaceView.setRenderer(null);
        mGLSurfaceView = null;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        super.onResume();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        mGLSurfaceView.onPause();
        super.onPause();
    }

    // GLSurfaceView.Renderer (1/3)
    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        Log.v(TAG, "onSurfaceCreated");

        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Compile the shaders.
        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        // Link them into a shader program.
        int program = linkProgram(vertexShader, fragmentShader);
        validateProgram(program);

        // Use this program.
        glUseProgram(program);

        // Retrieve uniform locations for the shader program.
        uMVPMatrix = glGetUniformLocation(program, U_MVP_MATRIX);
        uColor = glGetUniformLocation(program, U_COLOR);

        // Retrieve attribute locations for the shader program.
        aPosition = glGetAttribLocation(program, A_POSITION);

        // Bind our data, specified by the variable vertexData, to the vertex
        // attribute at location of A_POSITION.
        mVertexData.position(0);
        glVertexAttribPointer(
                aPosition,
                POSITION_COMPONENT_COUNT,
                GL_FLOAT,
                false,
                STRIDE,
                mVertexData
        );
        glEnableVertexAttribArray(aPosition);
    }

    // GLSurfaceView.Renderer (2/3)
    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        Log.v(TAG, "onSurfaceChanged");
        // Set the OpenGL viewport to fill the entire surface.
        glViewport(0, 0, width, height);

        /*
        frustum は lookAtM で決定される視軸に対して相対的な視界範囲を決めるという形で使われるものと思われ、
        （Web に見つかるのは perspective の用例ばかりであり、
        frustum についてはあくまでも自分で実地に色々と値を代入してテストしてみた結果による判断ではあるが）
        必ず、x 方向, y 方向は ± で指定する必要があるようである。（near/far にしても、
        絶対的な z 座標「位置」ではなくて、カメラ位置からの相対的な z 方向の「距離」を指定しているのと同じように。）

        特に near プレーンは単なるクリッピングエリアを決めるだけでなく、left/right/bottom/top と合わせて、
        視野角を決定する要因にもなるので、クリッピングエリアを変更するつもりで、気安くいじらないこと。

        far プレーンについては、等倍（ドット・バイ・ドット）の位置（z 座標は 0）を指定している。
        near プレーンはその半分の距離の位置（z 座標は -width/4）にあるとし、left/right/bottom/top を描画領域
        の 1/4 ずつの値にすることで、near プレーンにおいて縦横の幅が半分のサイズの長方形となるようにしている。

        こうすることで、far プレーンで等倍が実現できるので、そこに置くオブジェクトのサイズはそのまま画面の
        ピクセルサイズに基くことが可能となる。
         */
        frustumM(mProjectionMatrix, 0,
                -width / 4f, width / 4f, -height / 4f, height / 4f,
                width / 4f, width / 2f);

        /*
        UV 座標系と同じに扱うには、右手系のまま、単に、カメラの上下を引っくり返せば、Y 軸が下向きになるばかりでなく、
        Z 軸も奥に向って正方向となり、色々と感覚的にシームレスになる。これが upY = -1 の理由である。
        また、centerX/centerY は視線を常に z 軸に平行とするため、eyeX/eyeY と共通にする。
        eyeZ については本来は任意なのだが、わかりやすいので画面横幅を斜辺とする直角二等辺三角形になるようにする値を
        選ぶ（つまり width/2 の距離）。かつ z = 0 が、斜辺の位置とする（つまり z 座標は -width/2）。
        eyeZ = -eyeX となっているわけである。
         */
        setLookAtM(
                mViewMatrix, 0,
                width / 2f, height / 2f, -width / 2f,
                width / 2f, height / 2f, 1,
                0, -1, 0
        );

        multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        // Pass the matrix into the shader program.
        glUniformMatrix4fv(uMVPMatrix, 1, false, mVPMatrix, 0);
    }

    // GLSurfaceView.Renderer (3/3)
    @Override
    public void onDrawFrame(GL10 gl10) {
        // Clear the rendering surface.
        glClear(GL_COLOR_BUFFER_BIT);

        // Draw a tile.
        glUniform4f(uColor, 1.0f, 0, 0, 1.0f); // 赤色
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); // N 字形の順に描く
    }

    /**
     * Compiles a shader, returning the OpenGL object ID.
     * <p>
     * cf. https://media.pragprog.com/titles/kbogla/code/AirHockey1/src/com/airhockey/android/util/ShaderHelper.java
     *
     * @param type       GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
     * @param shaderCode String data of shader code
     * @return the OpenGL object ID (or 0 if compilation failed)
     */
    private static int compileShader(int type, String shaderCode) {
        // Create a new shader object.
        final int shaderObjectId = glCreateShader(type);
        if (shaderObjectId == 0) {
            Log.w(TAG, "Could not create new shader.");
            return 0;
        }

        // Pass in (upload) the shader source.
        glShaderSource(shaderObjectId, shaderCode);

        // Compile the shader.
        glCompileShader(shaderObjectId);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        glGetShaderiv(shaderObjectId, GL_COMPILE_STATUS, compileStatus, 0);

        // Print the shader info log to the Android log output.
        Log.v(TAG, String.format(
                "Results of compiling source:\n%s\n%s",
                shaderCode, glGetShaderInfoLog(shaderObjectId)
        ));

        // Verify the compile status.
        if (compileStatus[0] == 0) {
            // If it failed, delete the shader object.
            glDeleteShader(shaderObjectId);
            Log.w(TAG, "Compilation of shader failed.");
            return 0;
        }

        // Return the shader object ID.
        return shaderObjectId;
    }

    /**
     * Links a vertex shader and a fragment shader together into an OpenGL
     * program. Returns the OpenGL program object ID, or 0 if linking failed.
     * <p>
     * cf. https://media.pragprog.com/titles/kbogla/code/AirHockey1/src/com/airhockey/android/util/ShaderHelper.java
     *
     * @param vertexShaderId   OpenGL object ID of vertex shader
     * @param fragmentShaderId OpenGL object ID of fragment shader
     * @return OpenGL program object ID (or 0 if linking failed)
     */
    private static int linkProgram(int vertexShaderId, int fragmentShaderId) {
        // Create a new program object.
        final int programObjectId = glCreateProgram();
        if (programObjectId == 0) {
            Log.w(TAG, "Could not create new program");
            return 0;
        }

        // Attach the vertex shader to the program.
        glAttachShader(programObjectId, vertexShaderId);
        // Attach the fragment shader to the program.
        glAttachShader(programObjectId, fragmentShaderId);

        // Link the two shaders together into a program.
        glLinkProgram(programObjectId);

        // Get the link status.
        final int[] linkStatus = new int[1];
        glGetProgramiv(programObjectId, GL_LINK_STATUS, linkStatus, 0);

        // Print the program info log to the Android log output.
        Log.v(TAG, "Results of linking program:\n" + glGetProgramInfoLog(programObjectId));

        // Verify the link status.
        if (linkStatus[0] == 0) {
            // If it failed, delete the program object.
            glDeleteProgram(programObjectId);
            Log.w(TAG, "Linking of program failed.");
            return 0;
        }

        // Return the program object ID.
        return programObjectId;
    }

    /**
     * Validates an OpenGL program. Should only be called when developing the
     * application.
     * <p>
     * cf. https://media.pragprog.com/titles/kbogla/code/AirHockey1/src/com/airhockey/android/util/ShaderHelper.java
     *
     * @param programObjectId OpenGL program object ID to validate
     * @return boolean
     */
    private static boolean validateProgram(int programObjectId) {
        glValidateProgram(programObjectId);
        final int[] validateStatus = new int[1];
        glGetProgramiv(programObjectId, GL_VALIDATE_STATUS, validateStatus, 0);
        Log.v(TAG, String.format(
                "Results of validating program: %d\nLog:\n%s",
                validateStatus[0], glGetProgramInfoLog(programObjectId)
        ));

        return validateStatus[0] != 0;
    }
}