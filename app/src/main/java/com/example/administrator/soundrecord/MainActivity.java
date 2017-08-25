package com.example.administrator.soundrecord;

import android.net.sip.SipAudioCall;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private boolean isUploading = false;
    private int currSoundTime = 0;
    private int MAX_SOUND_TIME = 9;
    /**
     * 识别分贝的阈值
     */
    private float detectDb = 90;
    /**
     * 文件名称
     */
    private String fileName = "record_sound.wav";
    /**
     * 上传的文件
     */
    private String uploadFileName = "upload_file.wav";
    /**
     * 启动按钮
     */
    private Button btnStart;
    /**
     * 记录时间的秒数
     */
    private int recordSeconds = 0;
    /**
     * 是否正在运行录音
     */
    private Boolean isRunning = false;
    /**
     * 是否正在识别大声音
     */
    private Boolean isDetectHighVolume = false;
    /**
     * 声音记录组件
     */
    private MyMediaRecorder tempRecorder;
    /**
     * 需要上传的音频类
     */
    private MyMediaRecorder uploadRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    /**
     * 初始化
     */
    private void init(){
        tempRecorder = new MyMediaRecorder();

        uploadRecorder = new MyMediaRecorder();

        btnStart = (Button)findViewById(R.id.btn_start);

        btnStart.setOnClickListener(btnClickListener);
    }

    Handler handlerListenAudioChange = new Handler();

    Runnable myRunnableAudioChange = new Runnable() {
        @Override
        public void run() {
            float volume = tempRecorder.getMaxAmplitude();      //获取声压值

            if(volume > 0){
                float dBCount = 20 * (float)(Math.log10(volume));   //转化为分贝值
                Log.i("声压", dBCount +  "");
                if(dBCount >= detectDb && !isDetectHighVolume){
                    currSoundTime = 0;
                    isDetectHighVolume = true;

                    tempRecorder.stopRecording();

                   startRecord();
                }
                //分贝数超过阈值识别中..
                if(isDetectHighVolume){
                    currSoundTime++;

                    if(!isUploading && (dBCount < detectDb || currSoundTime >= MAX_SOUND_TIME)){

                        new Thread(myUploadFile).start();

                    }
                }

            }

            handlerUpdateRecordTime.postDelayed(this, 100);
        }
    };

    Runnable myUploadFile = new Runnable() {
        @Override
        public void run() {
            isUploading = true;

            String uploadResult = UploadUtil.uploadFile(tempRecorder.getMyRecAudioFile(), "http://www.baidu.com");

            Message uploadMessage = new Message();

            Bundle data = new Bundle();
            data.putString("upload_result", uploadResult);

            uploadMessage.setData(data);

            myHandlerUpload.sendMessage(uploadMessage);
        }
    };

    Handler myHandlerUpload = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Bundle data = msg.getData();

            String uploadResult = data.getString("upload_result");

            uploadResult = (uploadResult == null || uploadResult.equals("")) ? "上传后返回为空" : uploadResult;
            //开始上传声音
            Toast.makeText(getApplicationContext(), uploadResult, Toast.LENGTH_LONG).show();
        }
    };

    Handler handlerUpdateRecordTime = new Handler();

    Runnable myRunnable = new Runnable() {
        @Override
        public void run() {
            changeRecordTime();

            handlerUpdateRecordTime.postDelayed(this, 1000);
        }
    };

    /**
     * 改变记录时长
     */
    private void changeRecordTime(){
        recordSeconds++;

        int hours = 0, minutes = 0, seconds = 0;
        // 1分钟之内
        if(recordSeconds < 60){
            seconds = recordSeconds;
        }else if(recordSeconds < 3600){//1个小时之内
            minutes = (recordSeconds / 60);
            seconds = (recordSeconds % 60);
        }else{
            hours = (recordSeconds / 3600);
            minutes = ((recordSeconds % 3600) / 60);
            seconds = (recordSeconds - hours * 3600 - minutes * 60);
        }

        StringBuilder sbRecordTime = new StringBuilder();
        sbRecordTime.append(String.format("%02d", hours));
        sbRecordTime.append(":");
        sbRecordTime.append(String.format("%02d", minutes));
        sbRecordTime.append(":");
        sbRecordTime.append(String.format("%02d", seconds));

        setLabelRecordTime(sbRecordTime.toString());
    }

    /**
     * 显示记录时长
     * @param str
     */
    private void setLabelRecordTime(String str){
        TextView lb_time = (TextView)findViewById(R.id.lb_time);
        lb_time.setText(str);
    }
    /**
     * 按钮事件监听
     */
    private View.OnClickListener btnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.btn_start:
                    start();
                    break;
            }
        }
    };

    /**
     * 上传文件记录
     */
    private void startUploadRecord(){
        File myFile = FileUtil.createFile(uploadFileName);
        if(myFile == null){
            Toast.makeText(this, "上传文件创建失败", Toast.LENGTH_LONG).show();
        }else{
            uploadRecorder.setMyRecAudioFile(myFile);

            if(!uploadRecorder.startRecorder()){
                Toast.makeText(this, "上传文件启动录音失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    /**
     * 启动录音
     */
    private void startRecord(){
        File myFile = FileUtil.createFile(fileName);
        if(myFile == null){
            Toast.makeText(this, "创建文件失败", Toast.LENGTH_LONG).show();
        }else{
            tempRecorder.setMyRecAudioFile(myFile);

            if(tempRecorder.startRecorder()){
                handlerListenAudioChange.postDelayed(myRunnableAudioChange, 100);
            }else{
                Toast.makeText(this, "启动录音失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    /**
     * 开启录音
     */
    private void start(){
        if(isRunning){
            recordSeconds = 0;
            setLabelRecordTime("00:00:00");
            btnStart.setText("开始录音");
            handlerUpdateRecordTime.removeCallbacks(myRunnable);
        }else{
            changeRecordTime();
            btnStart.setText("停止录音");
            handlerUpdateRecordTime.postDelayed(myRunnable, 1000);
            startRecord();
        }
        isRunning = !isRunning;
    }

    /**
     * 停止录音
     */
    private void stop(){

    }
}
