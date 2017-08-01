package jf.gigapixel;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaRouter;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()   // or .detectAll() for all detectable problems
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textMain = (TextView)this.findViewById(R.id.textMain);
        textMain.setText("未连接第二屏幕");
        mainActivity = this;
        imageView_Test = (ImageView)(this.findViewById(R.id.imageView_Test));
        String ip = getIntent().getExtras().getString("ip");


        ((EditText)(this.findViewById(R.id.currentBrightnessLineEdit))).setEnabled(false);
        ((EditText)(this.findViewById(R.id.exposureLineEdit))).setEnabled(false);
        ((EditText)(this.findViewById(R.id.setLightBrightSpinBox))).setEnabled(false);

        try {
            socket = new Socket(ip, 3456);
            sin = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            sout = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));


            Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();

            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    while (true)
                    {
                        try
                        {
                            String str = sin.readLine();
                            Message message = new Message();
                            message.obj = (Object)str;
                            handler.sendMessage(message);
                        }
                        catch (Exception e)
                        {
                            Log.d(getClass().getSimpleName(), "Error: TCP thread");
                        }
                    }
                }
            }).start();

        }
        catch (Exception e)
        {
            Toast.makeText(this, "连接失败", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (callBack == null) {
            callBack = new RouteCallback();
            router = (MediaRouter)getSystemService(MEDIA_ROUTER_SERVICE);
        }

        handleRoute(router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO));
        router.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, callBack);

    }

    @Override
    protected void onStop() {
        clearPreso();
        if (router != null)
        {
            router.removeCallback(callBack);
        }
        super.onStop();
    }

    private void handleRoute(MediaRouter.RouteInfo route) {
        if (route == null)
        {
            clearPreso();
        }
        else
        {
            Display display=route.getPresentationDisplay();

            if (route.isEnabled() && display != null)
            {
                if (preso == null)
                {
                    showPreso(route);
                    Log.d(getClass().getSimpleName(), "enabled route");
                }
                else if (preso.getDisplay().getDisplayId() != display.getDisplayId())
                {
                    clearPreso();
                    showPreso(route);
                    Log.d(getClass().getSimpleName(), "switched route");
                }
                else
                {
                    // no-op: should already be set
                }
            }
            else
            {
                clearPreso();
                Log.d(getClass().getSimpleName(), "disabled route");
            }
        }
    }

    private void clearPreso()
    {
        if (preso != null)
        {
            preso.dismiss();
            textMain.setText("未连接第二屏幕");
            preso = null;
        }
    }

    private void showPreso(MediaRouter.RouteInfo route)
    {
        preso = new SimplePresentation(this, route.getPresentationDisplay(), this);
        preso.show();
        textMain.setText("已连接第二屏幕");

        // 向第二屏幕发送初始消息
        if (bitmap != null)
        {
            ((SimplePresentation)preso).showImage(bitmap);
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class RouteCallback extends MediaRouter.SimpleCallback
    {
        @Override
        public void onRoutePresentationDisplayChanged(MediaRouter router, MediaRouter.RouteInfo route)
        {
            handleRoute(route);
        }
    }

    // 对应第二屏幕的类，由Presentation继承
    private class SimplePresentation extends Presentation
    {
        SimplePresentation(Context ctxt, Display display, Activity mainActivity)
        {
            super(ctxt, display);
            activity = mainActivity;
        }

        private Activity activity; // 主屏幕的mainActivity

        @Override
        protected void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            int[] temp = new int[16];
            this.showImage(Bitmap.createBitmap(temp, 4, 4, Bitmap.Config.ARGB_8888));
        }

        // 第二屏幕接到消息，打印并向mainActivity返回消息
        public void showImage(final Bitmap bitmap)
        {
            ImageView imageView = new ImageView(getContext());
            imageView.setImageBitmap(bitmap);
            setContentView(imageView);
        }
    }

    private Socket socket = null;
    private BufferedReader sin = null;
    private BufferedWriter sout = null;

    public Handler handler = new Handler()
    {
        public void handleMessage(Message mess)
        {
            // Toast.makeText(mainActivity, "JSON received", Toast.LENGTH_SHORT).show();
            if (!isEditMode)
            {
                try
                {
                    JSONObject jsonObject = new JSONObject((String) mess.obj);

                    if (!isEditMode)
                    {
                        ((EditText)(mainActivity.findViewById(R.id.setLightBrightSpinBox))).setText(String.valueOf(jsonObject.getJSONObject("setLightBrightSpinBox").getInt("value")));
                        ((EditText)(mainActivity.findViewById(R.id.exposureLineEdit))).setText(jsonObject.getJSONObject("exposureLineEdit").getString("text"));
                        ((EditText)(mainActivity.findViewById(R.id.currentBrightnessLineEdit))).setText(jsonObject.getJSONObject("currentBrightnessLineEdit").getString("text"));
                    }

                    ((Button)(mainActivity.findViewById(R.id.connectTrigLightPushButton))).setText(jsonObject.getJSONObject("connectTrigLightPushButton").getString("text"));
                    ((Button)(mainActivity.findViewById(R.id.connectTrigLightPushButton))).setEnabled(jsonObject.getJSONObject("connectTrigLightPushButton").getString("enabled").equals("true"));

                    ((Button)(mainActivity.findViewById(R.id.readLightPushButton))).setText(jsonObject.getJSONObject("readLightPushButton").getString("text"));
                    ((Button)(mainActivity.findViewById(R.id.readLightPushButton))).setEnabled(jsonObject.getJSONObject("readLightPushButton").getString("enabled").equals("true"));

                    ((Button)(mainActivity.findViewById(R.id.shutterSwitchPushButton))).setText(jsonObject.getJSONObject("shutterSwitchPushButton").getString("text"));
                    ((Button)(mainActivity.findViewById(R.id.shutterSwitchPushButton))).setEnabled(jsonObject.getJSONObject("shutterSwitchPushButton").getString("enabled").equals("true"));

                    ((Button)(mainActivity.findViewById(R.id.setBrightnessPushButton))).setText(jsonObject.getJSONObject("setBrightnessPushButton").getString("text"));
                    ((Button)(mainActivity.findViewById(R.id.setBrightnessPushButton))).setEnabled(jsonObject.getJSONObject("setBrightnessPushButton").getString("enabled").equals("true"));

                    ((Button)(mainActivity.findViewById(R.id.startButton))).setText(jsonObject.getJSONObject("startButton").getString("text"));
                    ((Button)(mainActivity.findViewById(R.id.startButton))).setEnabled(jsonObject.getJSONObject("startButton").getString("enabled").equals("true"));

                    ((Button)(mainActivity.findViewById(R.id.stopButton))).setText(jsonObject.getJSONObject("stopButton").getString("text"));
                    ((Button)(mainActivity.findViewById(R.id.stopButton))).setEnabled(jsonObject.getJSONObject("stopButton").getString("enabled").equals("true"));

                    ((EditText)(mainActivity.findViewById(R.id.setLightBrightSpinBox))).setEnabled(jsonObject.getJSONObject("setLightBrightSpinBox").getString("enabled").equals("true") && isEditMode);
                    ((EditText)(mainActivity.findViewById(R.id.exposureLineEdit))).setEnabled(jsonObject.getJSONObject("exposureLineEdit").getString("enabled").equals("true") && isEditMode);
                    ((EditText) (mainActivity.findViewById(R.id.currentBrightnessLineEdit))).setEnabled(jsonObject.getJSONObject("currentBrightnessLineEdit").getString("enabled").equals("true") && isEditMode);


                    {
                        if (jsonObject.has("Image")) {


                            String str = jsonObject.getJSONObject("Image").getString("image");
                            // creates the bitmap
                            int width = jsonObject.getJSONObject("Image").getInt("width");
                            int height = jsonObject.getJSONObject("Image").getInt("height");
                            int[] colors = new int[width * height];
                            int pos = 0;
                            for (int y = 0; y < height; y++) {
                                for (int x = 0; x < width; x++) {
                                    int r = _cvt(str.charAt(pos)) * 16 + _cvt(str.charAt(pos + 1));
                                    int g = _cvt(str.charAt(pos + 2)) * 16 + _cvt(str.charAt(pos + 3));
                                    int b = _cvt(str.charAt(pos + 4)) * 16 + _cvt(str.charAt(pos + 5));
                                    int a = 255;
                                    colors[pos / 6] = ((a & 0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | ((b & 0xff) << 0);
                                    pos += 6;
                                }
                            }
                            bitmap = Bitmap.createBitmap(colors, 0, width, width, height, Bitmap.Config.ARGB_8888);
                            imageView_Test.setImageBitmap(bitmap);
                            ((SimplePresentation)(mainActivity.preso)).showImage(bitmap);
                        }
                    }

                }
                catch (org.json.JSONException e)
                {
                    Toast.makeText(mainActivity, "JSON解析错误1: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

        }
    };

    private int _cvt(char c)
    {
        return (c > '9') ? (c - 'a' + 10) : (c - '0');
    }
    private MainActivity mainActivity;
    private boolean isEditMode = false;

    private void writeJSON(JSONObject obj)
    {
        try
        {
            String str = obj.toString();
            sout.write(str);
            sout.newLine();
            sout.flush();
        }
        catch (Exception e)
        {
            Toast.makeText(mainActivity, "JSON写入错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void onButtonModeClick(View v)
    {

        if (isEditMode)
        {

            ((EditText)(this.findViewById(R.id.setLightBrightSpinBox))).setEnabled(false);
            ((EditText)(this.findViewById(R.id.exposureLineEdit))).setEnabled(false);
            ((EditText)(this.findViewById(R.id.currentBrightnessLineEdit))).setEnabled(false);

            ((Button)(this.findViewById(R.id.buttonStatus))).setText("Edit");

            try {

                JSONObject temp = null;
                //
                temp = new JSONObject();
                temp.put("type", "LineEdit");
                temp.put("text", ((EditText)(this.findViewById(R.id.currentBrightnessLineEdit))).getText().toString());
                temp.put("name", "currentBrightnessLineEdit");
                writeJSON(temp);

                //
                temp = new JSONObject();
                temp.put("type", "SpinBox");
                temp.put("value", Integer.parseInt(((EditText)(this.findViewById(R.id.setLightBrightSpinBox))).getText().toString()));
                temp.put("name", "setLightBrightSpinBox");
                writeJSON(temp);

                //
                temp = new JSONObject();
                temp.put("type", "LineEdit");
                temp.put("text", (((EditText)(this.findViewById(R.id.exposureLineEdit))).getText().toString()));
                temp.put("name", "exposureLineEdit");
                writeJSON(temp);


            }
            catch (org.json.JSONException e)
            {
                Toast.makeText(mainActivity, "JSON解析错误2", Toast.LENGTH_SHORT).show();
            }
            finally
            {
                isEditMode = false;
            }


        }
        else
        {


            ((EditText)(this.findViewById(R.id.setLightBrightSpinBox))).setEnabled(true);
            ((EditText)(this.findViewById(R.id.exposureLineEdit))).setEnabled(true);
            ((EditText)(this.findViewById(R.id.currentBrightnessLineEdit))).setEnabled(true);

            ((Button)(this.findViewById(R.id.buttonStatus))).setText("Done");

            isEditMode = true;
        }
    }

    private void onButtonClick(String buttonName)
    {
        JSONObject temp = new JSONObject();

        try
        {
            temp.put("type", "PushButton");
            temp.put("name", buttonName);
            writeJSON(temp);
        }
        catch (org.json.JSONException e)
        {
            Toast.makeText(mainActivity, "JSON解析错误3", Toast.LENGTH_SHORT).show();
        }

    }

    public void onConnectTrigLightPushButtonClick(View v)
    {
        onButtonClick("connectTrigLightPushButton");
    }

    public void onReadLightPushButtonClick(View v)
    {
        onButtonClick("readLightPushButton");
    }

    public void onShutterSwitchPushButtonClick(View v)
    {
        onButtonClick("shutterSwitchPushButton");
    }

    public void onSetBrightnessPushButtonClick(View v)
    {
        onButtonClick("setBrightnessPushButton");
    }

    public void onStartButtonClick(View v)
    {
        onButtonClick("startButton");
    }

    public void onStopButtonClick(View v)
    {
        onButtonClick("stopButton");
    }

    public void onButtonMoveUpClick(View v)
    {
        onButtonClick("buttonMoveUp");
    }

    public void onButtonMoveDownClick(View v)
    {
        onButtonClick("buttonMoveDown");
    }

    public void onButtonMoveLeftClick(View v)
    {
        onButtonClick("buttonMoveLeft");
    }

    public void onButtonMoveRightClick(View v)
    {
        onButtonClick("buttonMoveRight");
    }

    private MediaRouter router = null;
    private Presentation preso = null;
    private MediaRouter.SimpleCallback callBack = null;
    private TextView textMain = null;
    private Bitmap bitmap = null;
    private ImageView imageView_Test = null;
}
