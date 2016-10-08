package com.gzmelife.app.device;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import cn.jpush.a.a.b;

import com.gzmelife.app.KappAppliction;
import com.gzmelife.app.activity.CheckUpdateActivity;
import com.gzmelife.app.activity.CookBookDetailActivity;
import com.gzmelife.app.fragment.DeviceFragment;
import com.gzmelife.app.tools.DataUtil;
import com.gzmelife.app.tools.FileUtils;
import com.gzmelife.app.tools.KappUtils;
import com.gzmelife.app.tools.MyLog;
import com.gzmelife.app.views.TipConfirmView;

/**
 * Socket通信类
 */
public class SocketTool {

	SocketToolFile socketToolFile = new SocketToolFile();//20160930独立获取文件提供给Socket

    private String TAG = "SocketTool";
    private Socket socket;

    /** 20160920通过Socket输出 */
    private OutputStream output;

    /** 20160920通过Socket输入 */
    private InputStream input;

    private Context context;
    private Activity activity;

//    private boolean isOtherFile = false;//20160927其他用户在上下载数据

    /** 当前正在发送命令 */
    private boolean isSendCMD = false; //

    /** 若指令发送不成功，3S后重发指令 */
    private int timeCnt = 0;//20160920当前的时间

  /** 20160920心跳时间 */
    private int timeCntHeart = 0;

  /** 记录所发的指令，用于后续若指令失败时重发 */
    private byte[] bufLastTemp;

    /** 记录重发次数，若超过3次则进行重连的操作 */
    private int MaxReCnt = 0;

    /** 是否超时 */
    private boolean RecTimeOut = false;

    private int num = 0;//20160920帧的下标【对比地址码（0：为匹配）】
    private byte[] bufTemp = new byte[256 * 256];//20160920（bufTemp[0]为0xA5）65536=初始化每一帧数据（临时字节数组）

    /** 指令发送是否成功，也作为是否处于空闲状态的判断，进行指令重发或心跳 */
    private boolean ConFalg = false;//20160920是否有指令

    public static HeartTimeCount heartTimer;//20160920启动倒计时

    private OnReceiver receiver;//20160920接收者（接口）

    private int fileNum = 0;//20160921文件列表总数

    /** 20160921当前请求帧 */
//    private int frmIndex = 0;//20160930
    private int frmIndex = 0;//

    /** 20160928分页显示时一页的数量 */
    private int maxIndex = 0;//

    /** 下发文件的总长度（int） */
    private int numDownZie = 0;

    /** 已经下发了的长度（int） */
    private int numDownNow = 0;
    // private int numUpZie = 0; // 上传到手机来的文件的大小
    /** 手机已经接收（int） */
    private int numUpNow = 0;

    /** 20160921接收（整个菜谱）的缓存数组（需指定大小） */
    private byte[] bufRecFile;

    /** 20160921准备上传的文件的缓存数组（10M） */
    private byte[] bufSendFile = new byte[10 * 1024 * 1024];//

    // private int MaxPacket = 2 * 1024; // 一次最大下发大小

    /** 每次最大下发大小（2K） */
    private int MaxPacket = 512;
    private List<String> downFileList = new ArrayList<String>(); // 菜谱文件列表
    private List<String> selfFileList = new ArrayList<String>(); // 录波文件列表

    /** 20160926当前获取第几帧的值（byte数组） */
    private byte[] bufACK = {0x00, 0x00};// ok

    /** 当三次重发失败后，三次重建tcp连接并发送指令。若为true，表示连接成功，不用再继续重建与连接，若三次后还为false，则给出失败提示 */
    private boolean isConnected = false;//20160920是否已经连接
    private int[] a = new int[0];

    /** 将当前获取第几帧的值转化为byte数组来传 */
    private void ACK(int index) {
        bufACK[0] = (byte) (index % 256);
        bufACK[1] = (byte) (index / 256);
    }


    public SocketTool(Context context, Activity activity, OnReceiver onReceiver) {
        this.context = context;
        this.activity = activity;
        this.receiver = onReceiver;
    }

    public SocketTool(Context context, OnReceiver onReceiver) {
        this.context = context;
        this.receiver = onReceiver;
    }

    public boolean isStartHeartTimer() {//20160920开始心跳计时
        return startHeart;
    }

    private boolean startHeart = false;//20160920标记是否有心跳

    /** 启动心跳计时 */
    public void startHeartTimer() {
        //Log.i(TAG, "startHeartTimer");
        heartTimer = new HeartTimeCount(Long.MAX_VALUE, 1000);
        heartTimer.start();//20160920倒计时
        startHeart = true;//20160920标记有心跳
        ConFalg = true;//20160920有指令（心跳）
    }

    /** 关闭连接 */
    public void closeSocket() {
        //Log.i(TAG, "closeSocket");
        try {
            if (output != null) {//20160920置空（输出）流
                output.close();
                output = null;
            }
            if (socket != null) {//20160920置空（客户端）socket
                socket.close();
                socket = null;
            }
            if (heartTimer != null) {//20160920置空倒计时
                heartTimer.cancel();
                heartTimer = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 每次的首次连接pms时，调用此方法，给三次连接机会，调用连接方法 */
    public void firstConnect() {//20160920
        isConnected = false;
        connectTimes = 3;
        connectHandler.sendEmptyMessage(0);//20160920连接PMS
        //Log.i(TAG, "firstConnect  connectHandler.sendEmptyMessage(0)");
    }

    /** 根绝ip与端口初始化socket连接 */
    public void initClientSocket() {//20160920
        //Log.i(TAG, "根绝ip与端口初始化socket连接--》》initClientSocket");
        try {
            // 连接服务器20160920（IP，端口号）
            socket = new Socket(Config.SERVER_HOST_IP, Config.SERVER_HOST_PORT);
            output = socket.getOutputStream();
            input = socket.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //20160921收发独立**************************************************************


    /** 20160921socket接收数据线程 */
    private class ReceiveRunnable implements Runnable {
		@Override
		public void run() {
			while (true) {
                byte[] resultTemp = new byte[1024 * 5];//20160928存放最原始数据（socket接过来的输入流）
                int len = 0;
                try {
                    if (input != null || !socket.isClosed() || socket.isConnected()) {//20160920输入流不为空//socket不为关闭状态//socket为连接状态
                        len = input.read(resultTemp);//20160920从输入流中读取数据的下一个字节（最大255）放到resultTemp里面
                    }

                    if (len == -1) {//20160923接收到的消息不是空的
                    } else {
                        byte[] result = new byte[len];
                        for (int i = 0; i < len; i++) {
                            result[i] = resultTemp[i];//20160920复制一份接收到的数据
                        }

                        Message msg = new Message();
                        msg.obj = result;//20160920把接收到的结果放到消息中
                        handler.sendMessage(msg);//20160920拿到数据后先判断校验码等信息，若检验正确再去处理否则给出提示


                        System.out.println(" ");
                        System.out.println("\r\nreceiveMessage===========================================================================");
                        for (int i = 0; i < result.length; i++) {
                            if (i == 3){
                                System.out.print("【 ");
                            }
                            if (i == 5){
                                System.out.print("】 | ");
                            }
                            if (i == 6){
                                System.out.print("| ");
                            }
                            System.out.print(byte2HexString(result[i])+" ");
                        }
                        System.out.println("\n\rreceiveMessage===========================================================================");
                        System.out.println(" ");


                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
		}
	}
    /** 20160921socket接收数据 */
    public void receiveMessage() {
    	ReceiveRunnable receiveRunnable = new ReceiveRunnable();
        Thread receiveThread = new Thread(receiveRunnable);
        if (receiveThread.isAlive()) {

		} else {
			receiveThread.start();
		}
    }


    /**
     * 20160921socket发送数据
     * @param msg 组装好的组装帧格式
     * @return
     */
    public byte[] sendMessage(final byte[] msg) {


        System.out.println(" ");
        System.out.println("\n\rsendMessage-------------------------------------------------------------------------------");
        for (int i = 0; i < msg.length; i++) {
            if (i == 3){
                System.out.print("【 ");
            }
            if (i == 5){
                System.out.print("】 | ");
            }
            if (i == 6){
                System.out.print("| ");
            }
            System.out.print(byte2HexString(msg[i])+" ");
        }
        System.out.println("\n\rsendMessage-------------------------------------------------------------------------------");
        System.out.println(" ");



        new Thread(new Runnable() {
            @Override
            public void run() {
                if (socket == null || output == null || socket.isClosed()) {
                    if (socket == null) {
                    } else {
                    }

                    if (receiver != null) {
                        receiver.onFailure(0);
                    }
                    return;
                }

                try {
                    if (msg != null) {
                        output.write(msg);//20160929通过Socket写（上传）出去
                        output.flush();
                    } else {
                        return;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                receiveMessage();//20160921调用接收方法

            }
        }).start();
        return msg;
    }
    //收发独立**************************************************************


    public interface OnReceiver {
        /**
         * flag 0: 不处理，1：下载成功，2：下载失败,3:下载数据的百分比,4:连接成功,5:删除文件成功，6：获取设备状态成功, 7 :传文件到智能锅成功，8：传文件到智能锅的百分比 ,9:对时功能
         *
         * @param cookBookFileList
         * @param flag 标记
         * @param now 已经传送了的大小
         * @param all 文件总大小
         */
        void onSuccess(List<String> cookBookFileList, int flag, int now, int all);

        /** flag 默认为0;-1：下载文件，文件大小=0; */
        void onFailure(int flag);
    }

    /** 单指令，只要发送指令，不需要传参数，如连接、心跳等 */
    public void PMS_Send(byte[] bufTemp1) {
        int addNum = 0;
        byte[] bufTemp = new byte[bufTemp1.length + 5];

        bufTemp[0] = (byte) 0xA5;

        bufTemp[1] = (byte) (bufTemp1.length % 256 + 1);
        bufTemp[2] = (byte) (bufTemp1.length / 256);

        for (int i = 0; i < bufTemp1.length; i++) {
            bufTemp[i + 3] = bufTemp1[i];
        }

        bufTemp[bufTemp1.length + 3] = Config.clientPort;

        for (int i = 1; i < bufTemp1.length + 4; i++) {
            addNum += bufTemp[i];
        }
        bufTemp[bufTemp1.length + 4] = (byte) (addNum % 256);

        try {
            isSendCMD = true;

            timeCnt = 0;
            timeCntHeart = 0;

            bufLastTemp = new byte[bufTemp.length];
            for (int i = 0; i < bufTemp.length; i++) {
                bufLastTemp[i] = bufTemp[i];
            }
            // 记录所发的指令，用于后续若指令失败时重发
            bufLastTemp = new byte[bufTemp.length];
            sendMessage(bufTemp);//20160920socket发送数据且接收数据
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送指令与参数//20160926
     * @param bufTemp1 功能码+子功能码
     * @param bufTemp2 当前请求帧（序号/序号+数据）
     */
    public void PMS_Send(byte[] bufTemp1, byte[] bufTemp2) {

    	/** 20160929//addNum：算数和（绝对值的和） */
        int addNum = 0;
        int i = 0;

        int len = bufTemp1.length + bufTemp2.length + 1;// PMS格式长度+数据长度+校验码//20160929（功能码+子功能码）+（当前请求帧（序号/序号+数据））+（算数和（绝对值的和））

      /** 20160920bufTemp：组装帧格式 */
        byte[] bufTemp = new byte[len + 4];
        bufTemp[0] = (byte) 0xA5;
        bufTemp[1] = (byte) (len % 256);//20160920与256长度L低字节
        bufTemp[2] = (byte) ((len / 256) % 256);//20160920长度L高字节
        bufTemp[3] = bufTemp1[0];//20160920功能码
        bufTemp[4] = bufTemp1[1];//20160920子功能码
        //20160929地址码（A5H）+长度L低字节+长度L高字节+功能码+子功能码=

        if (Config.clientPort == -1) {// 客户端IP的最后一个字节
            return;
        }

        bufTemp[5] = Config.clientPort;//20160920客户端地址（第6位）
      //20160929地址码（A5H）+长度L低字节+长度L高字节+功能码+子功能码+客户端地址（5）=
//        Log.i(TAG, "客户端地址-HHD-》》" + byte2HexString(bufTemp[5]));
        //Log.i(TAG, "PMS_Send(byte[] bufTemp1, byte[] bufTemp2)  客户端地址：" + byte2HexString(bufTemp[5]));
        for (i = 6; i < len + 3; i++) {
            bufTemp[i] = bufTemp2[i - 6];
          //20160929地址码（A5H）+长度L低字节+长度L高字节+功能码+子功能码+客户端地址+【帧序号+菜谱数据】
        }

        for (i = 1; i < len + 3; i++) {
            addNum += bufTemp[i];
        }

        bufTemp[len + 3] = (byte) (addNum % 256);//20160929组装校验码
      //20160929帧格式=地址码（A5H）+长度L低字节+长度L高字节+功能码+子功能码+客户端地址+【帧序号+菜谱数据】+SUM校验码

        try {
            isSendCMD = true;//20160920当前正在发送命令（true：发送中；false：空闲中）
            sendMessage(bufTemp);//2160929发送一帧数据
            Log.i(TAG, "向PMS发送的数据bufTemp"+bufTemp);
        } catch (Exception e) {
            e.printStackTrace();
        }

        timeCnt = 0;
        timeCntHeart = 0;

        bufLastTemp = new byte[bufTemp.length];
        for (i = 0; i < bufTemp.length; i++) {
            bufLastTemp[i] = bufTemp[i];
        }
    }

    /** 拿到数据后先判断校验码等信息，若检验正确再去处理否则给出提示 */
    Handler handler = new Handler(new Callback() {//20160920解析接收
        @Override
        public boolean handleMessage(Message msg) {//20160920msg：功能码
            //Log.i(TAG, "handleMessage(Message msg)" + String.valueOf(msg.what));
            byte[] result = (byte[]) msg.obj;//20160920把封装的消息对象（接收设备端的数据）还原为字节数组（包括“0xA5”）
//            Log.i(TAG, "接收到的原始数据（result）长度："+result.length);
            try {
                boolean UartS = false;//20160920（true为地址码正确）
                int len = 0;//20160920数据长度=功能码+子功能码+数据
                boolean run = true;//20160920是否为解析帧数据状态

                while (run) {
                    if (UartS) {
                        //20160922判断服务器回复内容是否为当前客户端地址*************************************************************************************
//                        if (Config.clientPort != result[5]) {
//                            Log.i(TAG, "回复内容不是自己的-HHD-》》是"+Config.clientPort+"的数据");
//                            KappUtils.showToast(context, "回复内容不是自己的");
//                        }
//                        KappUtils.showToast(context, "是自己的结果--》》继续解析");


                        //20160922*************************************************************************************


                    	/*
                    	 * 把接收到的原始数据加载到初始化的缓冲数组中
                    	 */
                        if (num < 3) {
                            bufTemp[num] = result[num];//20160920bufTemp：临时数据//result：接收到数据
                            num++;//20160920循环对比（地址码+长度L）
                        } else if (num == 3) {//20160920功能码
                            len = DataUtil.hexToTen(bufTemp[1]) + DataUtil.hexToTen(bufTemp[2]) * 256;//20160920拼接长度L（长度L低字节+长度L高字节）并赋值
                            bufTemp[num] = result[num];//20160920功能码
                            num++;
                        } else if (num > 3) {
                            if (num < len + 3 && num < result.length) {//20160920当前下标小于源数据和结果长度
                                bufTemp[num] = result[num];//20160920子功能码
                                num++;
                            } else {//20160920数据长度等于源数据长度
                                if (num < result.length) {//20160920下标小于结果长度
                                    bufTemp[num] = result[num];//20160922客户端地址
                                    num++;
                                }
//                                Log.i(TAG, "首次复制接收到的数据到初始化数组（bufTemp=result）的长度："+bufTemp.length);
                                /*
                            	 * 把接收到的原始数据加载到初始化的缓冲数组中
                            	 */



                                int check = 0;
                                for (int i = 1; i < num - 1; i++) {
                                    int temp = bufTemp[i];
                                    if (temp < 0) {//20160920还原数据（每个字节）
                                        temp += 256;
                                    }
                                    check += temp;//20160920最终数据（除“地址码”、“长度L”、“客户端地址”）
                                }
//                                Log.i(TAG, "check长度="+check);
                                if (DataUtil.hexToTen(bufTemp[num - 1]) == (check % 256)) {//20160923待请教问题是否为“SUM校验码”//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

                                    /** 20160923解析数据入口 */
                                    Done(bufTemp, num);//20160923接收到的数据（临时数组=256 * 256=65536）：bufTemp//原始数据长度：num
//                                    Log.i(TAG, "Done接到每一帧数据的长度【" + bufTemp.length+ "】" );
//                                    Log.i(TAG, "功能码【" + byte2HexString(bufTemp[3]) + "】子功能码【" + byte2HexString(bufTemp[4]) + "】客户端地址【" + byte2HexString(bufTemp[5]) + "】");//20160920
                                    run = false;
                                    return true;//20160920？？？
                                } else {
//                                    Log.i(TAG, "接收到的校验码【" + DataUtil.hexToTen(bufTemp[num - 1]) + "】子功能码【" + (check % 256) + "】数据长度【" + len);//20160920
                                    if (len > 0) {
                                        run = false;//20160920如果长度大于0则重新解析
                                    }
                                }

                                if (len > 0) {//20160920重置
                                    UartS = false;
                                    num = 0;
                                }
                            }
                        }
                    } else {//20160920没有地址码（接收到的数据）
                        byte A0 = (byte) 0xA5;
                        byte aa = result[0];//20160920对比地址码
                        if (aa == A0) {
                            num = 0;
                            bufTemp[num] = aa;
                            UartS = true;
                            num++;

                            timeCnt = 0;//20160920当前时间
                            timeCntHeart = 0;//20160920心跳时间
                            RecTimeOut = false;//20160920是否超时
//                            Log.i(TAG, "【" +Integer.toHexString(DataUtil.hexToTen(bufTemp[num])));
                        } else {
                            Log.i(TAG, "第一个字节不是OxA5");
                        }
                    }
                }
            } catch (Exception e) {

                e.printStackTrace();
//                Log.i(TAG, "-HHD-》》PMS设备断开连接！");
                //Log.e("MyError", "PMS设备断开连接！");
                Config.SERVER_HOST_NAME = "";
                if (receiver != null) {
                    receiver.onFailure(0);
                }
                ConFalg = false;//20160920/** 指令发送是否成功，也作为是否处于空闲状态的判断，进行指令重发或心跳 */
                return false;
            }
            return false;
        }
    });

    /**
     * 得到pms返回的（校验码通过（handler））数据，根据实际情况做不同的处理
     */
    private void Done(byte[] buf, int num) {//20160928接收到的数据（临时数组=256 * 256=65536）：bufTemp//原始数据长度：num
        int len = DataUtil.hexToTen(bufTemp[1]) + DataUtil.hexToTen(bufTemp[2]) * 256;//20160923数据长度
//        Log.i(TAG, "功能码+子功能码+数据（len）=" + len);
//        Log.i(TAG, "原始数据长度（len+4）=" + num);
        //Log.i(TAG, "Done(byte[] buf, int num) 接收数据--》》功能码：" + byte2HexString(buf[3]) + "--》功能子码：" + byte2HexString(buf[4]) + "-->数据域长度：" + String.valueOf(len));
        ConFalg = true;
        isSendCMD = false;

//        Log.i(TAG, "buf[5]解析客户端IP地址-HHD-》》" + byte2HexString(buf[5]));
        if (buf[5] != Config.clientPort) {
            Log.i(TAG, "-HHD-》》客户端地址不匹配，处理报文且读取其他用户指令");
//			KappUtils.showToast(context, "其他客户端正在操作");
            switch (buf[3]) {

//            case (byte) 0xF0:
//                break;
//                
//            case (byte) 0xF1:
//                break;
//                
//            case (byte) 0xF2:
//                break;
//            
//            case (byte) 0xF3:
//            	if (buf[4] == 0x00) {
//           		 Config.isOtherFile = true;
//              } else if (buf[4] == 0x01) {
//           	   Config.isOtherFile = true;
//              } else if (buf[4] == 0x02) {//遍历完毕
//           	   Config.isOtherFile = false;
//              }
//                break;
//            
//            case (byte) 0xF4:
//            	 if (buf[4] == 0x00) {//获取录波文件大小
//            		 Config.isOtherFile = true;
//               } else if (buf[4] == 0x01) {//上召录波数据
//            	   Config.isOtherFile = true;
//               } else if (buf[4] == 0x02) {//录波发送结束
//            	   Config.isOtherFile = false;
//               } else if (buf[4] == 0x03) {//中断文件传输
//            	   Config.isOtherFile = false;
//               }
//            	break;
//            
//            case (byte) 0xF5:
//            	if (buf[4] == 0x00) {//下发录波文件大小
//           		 Config.isOtherFile = true;
//              } else if (buf[4] == 0x01) {//下发录波数据
//           	   Config.isOtherFile = true;
//              } else if (buf[4] == 0x02) {//数据发送结束
//           	   Config.isOtherFile = false;
//              } else if (buf[4] == 0x03) {//中断文件传输
//           	   Config.isOtherFile = false;
//              }
//            	break;
//            
//            case (byte) 0xF6:
//            	if (buf[4] == 0x00) {//删除录波文件
//              		 Config.isOtherFile = true;
//                 } else if (buf[4] == 0x01) {//删除下载菜谱
//              	   Config.isOtherFile = true;
//                 }
//            	break;
//            
//            case (byte) 0xF7:
//            	if (buf[4] == 0x00) {//查询状态
//             		 Config.isOtherFile = true;
//                } 
//            	break;
//            
//            case (byte) 0xF8:
//            	if (buf[4] == 0x00) {//连接确认
//              		 Config.isOtherFile = true;
//                 } else if (buf[4] == 0x01) {//抢占控制权
//              	   Config.isOtherFile = true;
//                 } else if (buf[4] == 0x02) {//心跳报文
//              	   Config.isOtherFile = true;
//                 } else if (buf[4] == 0x03) {//断开连接
//              	   Config.isOtherFile = false;
//                 }
//            	break;





                case (byte) 0xF3:
                    if (buf[4] == 0x00) {
//                        Config.isOtherFile = true;//20160927
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】获取录波文件数量");
                        break;
                    } else if (buf[4] == 0x01) {
                        Config.isOtherFile = true;//20160927
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】查询录波文件列表");
                        break;
                    } else if (buf[4] == 0x02) {
                        Config.isOtherFile = false;//20160927
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】遍历完毕");
                        break;
                    }
                    break;

                case (byte) 0xF4:
                    if (buf[4] == 0x00) {
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】获取录波文件大小");
                        break;
                    } else if (buf[4] == 0x01) {

                    	Config.isOtherFile = true;//20160927

                        break;
                    } else if (buf[4] == 0x02) {
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】录波发送结束");
                        Config.isOtherFile = false;//20160927
                        break;
                    } else if (buf[4] == 0x03) {
                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】中断文件传输");
                        Config.isOtherFile = false;//20160927
                        break;
                    }
                    break;

                case (byte) 0xF5:
                    if (buf[4] == 0x00) {
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】获取录波文件大小");
                        break;
                    } else if (buf[4] == 0x01) {

                        Config.isOtherFile = true;//20160927
                        break;

                    } else if (buf[4] == 0x02) {
//                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】数据发送结束");
                        Config.isOtherFile = false;//20160927
                        break;
                    }
                    break;

                case (byte) 0xF6:
                    if (buf[4] == 0x00) {
                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】生成录波文件操作");
                        break;
                    } else if (buf[4] == 0x01) {
                        KappUtils.showToast(context, "【"+byte2HexString(buf[5])+"】删除APP下载菜谱文件操作");
                        break;
                    }
                    break;
            }
            return;//20160923不是自己的消息不往下执行
        }


//        Log.i(TAG, "-HHD-》》客户端地址相同");
        switch (buf[3]) {

            case (byte) 0xF3: // 文件列表
                if (buf[4] == 0x00) { // 得到文件数目
                    fileNum = DataUtil.hexToTen(bufTemp[6]) + DataUtil.hexToTen(bufTemp[7]) * 256;//20160928录波文件数量低字节+高字节=录波文件数量（十进制）
                    if (DeviceFragment.fileFlag) {// true表示录波文件，false表示菜谱文件
                        selfFileList.clear();//20160928//clear()从此列表中移除所有项
                    } else {
                        downFileList.clear();
                    }

                    if (fileNum > 0) {//20160928文件列表总数>0
                        frmIndex = 1;//20160928当前请求帧
                        maxIndex = fileNum / 25;//20160920分页显示
                        if ((fileNum % 25) > 0) {
                            maxIndex++;
                        }
                        ACK(frmIndex);//20160920当前请求帧：转化为byte数组
                        PMS_Send(Config.bufListFile, bufACK);//20160920//F3 01：查询录波文件列表：bufListFile//bufACK：当前请求帧下标+1=请求下一帧
                    } else {//20160928文件列表总数<0
                        if (receiver != null) {
                            receiver.onSuccess(null, 0, 0, 0);
                        }
                    }
                } else if (buf[4] == 0x01) { // 得到文件名称
                    String filename = "";
                    for (int index = 8; index < num - 40; index += 40) { // 9->8//20160928//第8帧开始是数据//
                        String aa = "";
                        try {
                            aa = new String(buf, index, 40, "gbk");//20160928使用指定的编码解码指定的 byte 数组
                        } catch (UnsupportedEncodingException e1) {
                            e1.printStackTrace();
                        }
                        try {
                            filename = aa.replace("\0", "");//20160928“空格”替换“\0”
                        } catch (Exception e) {
                            filename = aa;
                        }

                        if (DeviceFragment.fileFlag) {//20160920标识录波/菜谱
                            selfFileList.add(filename);//20160920添加到录波列表
                        } else {
                            downFileList.add(filename);//20160920添加到菜谱列表
                        }
                    }

                    if (DeviceFragment.fileFlag) {
                        if (selfFileList.size() < fileNum) { // 加帧判断//20160928列表长度小于文件（菜谱）总数
                            frmIndex++;
                            ACK(frmIndex);
                            PMS_Send(Config.bufListFile, bufACK);
                        } else {
                            PMS_Send(Config.bufListFileOver);//20160928遍历完成F3 02
                        }
                    } else {
                        if (downFileList.size() < fileNum) { // 加帧判断
                            frmIndex++;
                            ///System.out.println("菜谱:" + "请求文件列表第" + frmIndex + "帧");
                            ACK(frmIndex);
                            PMS_Send(Config.bufListFile, bufACK);
                        } else {
                            ///System.out.println("菜谱:请求文件列表获取结束帧");
                            PMS_Send(Config.bufListFileOver);
                        }
                    }
                } else if (buf[4] == 0x02) { // 遍历完成
                    if (DeviceFragment.fileFlag) {
                        ///System.out.println("录波：收到遍历文件结束帧");
                        if (receiver != null) {
                            receiver.onSuccess(selfFileList, 0, 0, 0);
                        }
                    } else {
                        ///System.out.println("菜谱：收到遍历文件结束帧");
                        if (receiver != null) {
                            receiver.onSuccess(downFileList, 0, 0, 0);
                        }
                    }
                }
                break;
            case (byte) 0xF4: // 上召文件
                if (buf[4] == 0x01) { // 得到文件数据
                    upFile(buf/* , num */);
                } else if (buf[4] == 0x00) { // 得到文件长度
                	//fileLen：本录波文件的长度
                    int fileLen = DataUtil.hexToTen(buf[6]) + DataUtil.hexToTen(buf[7]) * 256 + DataUtil.hexToTen(buf[8]) * 256 * 256 + DataUtil.hexToTen(buf[9]) * 256 * 256 * 256;
                    if (fileLen == 0) {
                        if (receiver != null) {
                            receiver.onFailure(-1);
                        }
                        return;
                    }
                    numUpNow = 0;

                    bufRecFile = new byte[fileLen];//20160928创建一个和文件大小一样的缓存byte数组

                    frmIndex = 1;
                    maxIndex = fileLen / MaxPacket;
                    if ((fileLen % MaxPacket) > 0) {
                        maxIndex++;
                    }

                    ACK(frmIndex);
                    PMS_Send(Config.bufFileAck, bufACK);
                } else if (buf[4] == 0x02) {

                    DeviceFragment.saveFileName = FileUtils.PMS_FILE_PATH
                            + FileUtils.getFileName(DeviceFragment.saveFileName);
                    FileUtils.writeTextToFile(DeviceFragment.saveFileName, bufRecFile);

                    if (receiver != null) {
                        receiver.onSuccess(null, 1, 0, 0);
                    } else {
                        receiver.onFailure(0);
                    }
                }
                break;








            case (byte) 0xF5: // 下传文件
                Log.i(TAG, "Done(byte[] buf, int num)  case (byte) 0xF5: // 下传文件      buf[6]--> " + byte2HexString(buf[4])
                        + "--->" + byte2HexString(buf[6]));
                if (buf[4] == 0x00) { // 发送文件大小和文件名，得到确认
                    if (buf[6] == 0x01) {
                        // Log.i(TAG, "Done(byte[] buf, int num) case (byte) 0xF5:
                        // buf[4] == 0x00 微波炉01确认");

                        numDownNow = 0;
                        MyLog.d("收到文件下载--确认下载");

                        frmIndex = 1;
                        DownFile(frmIndex);
                    } else if (buf[6] == 0x00) {
                        // Log.i(TAG, "Done(byte[] buf, int num) case (byte) 0xF5:
                        // buf[4] == 0x00 微波炉00拒绝");
                        if (receiver != null) {
                            receiver.onFailure(50000);
                        }
                    }
                } else if (buf[4] == 0x01) { // 发送文件一帧，得到确认
                    if (buf[6] == 0x01) {
                        MyLog.d("收到文件下载--下载第" + frmIndex + "帧");

                        frmIndex++;

                        if (receiver != null) {
                            ///System.out.println(" ConFalg==receiver != null" + ConFalg);
                            receiver.onSuccess(null, 8, numDownNow, numDownZie);
                        } else {
                            ///System.out.println(" ConFalg==receiver == null" + ConFalg);
                            // receiver.onFailure(0);
                            ///System.out.println("上传失败停止" + 0 + "");
                        }
                        if (numDownZie > numDownNow) {
                            DownFile(frmIndex);
                        } else {
                            MyLog.d("请求文件下载--下载结束帧");
                            PMS_Send(Config.bufDownFileStop);
                        }
                    } else if (buf[6] == 0x00) {
                        MyLog.d("收到文件下载--重发第" + frmIndex + "帧");
                        DownFile(frmIndex);
                    }
                } else if (buf[4] == 0x02) {
                    MyLog.d("收到文件下载--下载结束帧");
                    if (receiver != null) {
                        receiver.onSuccess(null, 7, 0, 0);
                    } else {
                        // receiver.onFailure(0);
                    }
                }
                break;











            case (byte) 0xF6:
                if (buf[4] == (byte) 0x00) {
                    if (buf[6] == 0x01) {
                        if (receiver != null) {
                            receiver.onSuccess(null, 5, 0, 0);
                        }
                    } else {
                        if (receiver != null) {
                            receiver.onFailure(0);
                        }
                    }
                } else if (buf[4] == (byte) 0x01) {
                    if (buf[6] == 0x01) {
                        if (receiver != null) {
                            receiver.onSuccess(null, 5, 0, 0);
                        }
                    } else {
                        if (receiver != null) {
                            receiver.onFailure(0);
                        }
                    }
                }
                break;
            case (byte) 0xF7:
                if (buf[4] == (byte) 0x00) {
                    Config.SYSTEM_A = ((DataUtil.hexToTen(buf[6]) + 256 * DataUtil.hexToTen(buf[7])) * 1650.0 / 48803.38944) + "A";
                    Config.SYSTEM_V = ((DataUtil.hexToTen(buf[8]) + 256 * DataUtil.hexToTen(buf[9])) / 10.0) + "V";
                    Config.SYSTEM_W = DataUtil.hexToTen(buf[15]) * 10 + "W";
                    Config.PMS_TEMP = ((DataUtil.hexToTen(buf[10]) + 256 * DataUtil.hexToTen(buf[11])) / 100.0) + "度";
                    Config.ROOM_TEMP = ((DataUtil.hexToTen(buf[12]) + 256 * DataUtil.hexToTen(buf[13])) / 100.0) + "度";
                    switch (DataUtil.hexToTen(buf[14])) {
                        case 0:
                            Config.PMS_STATUS = "POWEROFF";
                            break;
                        case 1:
                            Config.PMS_STATUS = "POWERON";
                            break;
                        case 2:
                            Config.PMS_STATUS = "POWERSTANDBY";
                            break;
                        case 3:
                            Config.PMS_STATUS = "POWERHALT";
                            break;
                    }

                    Config.PMS_ERRORS.clear();
                    StringBuffer sb = new StringBuffer();
                    for (int i = 17; i > 15; i--) {
                        String hex = Integer.toHexString(buf[i]);
                        int ihex = Integer.parseInt(hex);
                        String r = "";
                        if (ihex < 10) {
                            r = String.format("%02d", ihex);
                        } else {
                            r = ihex + "";
                        }
                        sb.append(r);
                    }


                    String result = hexString2binaryString(sb.toString());
                    StringBuffer sBuf = new StringBuffer();
                    for (int i = result.length(); i > 0; i--) {
                        sBuf.append(result.substring(i - 1, i));
                    }
                    for (int i = 0; i < result.length(); i++) {
                        if (sBuf.substring(i, i + 1).equals("1")) {
                            Config.PMS_ERRORS.add((i) + "");
                        }
                    }

                    if (receiver != null) {
                        receiver.onSuccess(null, 6, 0, 0);
                    }
                } else {
                    if (receiver != null) {
                        receiver.onFailure(0);
                    }
                }
                break;
            case (byte) 0xF8:
                /** 链接成功 */
                if (buf[4] == (byte) 0x00) { // 连接确认报文，回复PMS的MAC
                    isConnected = true;
                    if (receiver != null) {
                        receiver.onSuccess(null, 4, 0, 0);
                    }

                    // RefreshFileList(); //获取文件
                } else if (buf[4] == (byte) 0x02) { // 心跳报文
                }
                break;

            case (byte) 0xF2: { // 对时
                if (buf[4] == (byte) 0x00) {
                    if (receiver != null) {
                        receiver.onSuccess(null, 9, 0, 0);
                    }
                }
            }
            break;
        }
    }

    private String hexString2binaryString(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0)
            return "";
        String bString = "", tmp;
        for (int i = 0; i < hexString.length(); i++) {
            tmp = "0000" + Integer.toBinaryString(Integer.parseInt(hexString.substring(i, i + 1), 16));
            bString += tmp.substring(tmp.length() - 4);
        }
        return bString;
    }

    /**
     * 20160929向PMS发送文件//从pms下载文件到手机时，根据当前第几次下载，计算出指令来发送
     * index：当前请求帧下标
     */
    private void DownFile(int index) {
        //Log.i(TAG, "DownFile(int index)" + String.valueOf(index));
        /** 20160929lenth：剩下的长度（int）//所需下载长度为剩下的长度 但不大于最大请求长度 */
        int lenth = numDownZie - numDownNow;//20160929总长-已发送=lenth（剩下没发送）
        if (lenth > MaxPacket) {
            lenth = MaxPacket;
        }//20160929否则（剩下的长度<每次最大下发大小（512））

        /** 20160929//bufR：组装当前请求帧=帧序号（2）+菜谱数据（1024*2） */
        byte[] bufR = new byte[lenth + 2];//20160929//2048+2
        bufR[0] = (byte) (index % 256);//相当于十进制除10取余
        bufR[1] = (byte) (index / 256);//相当于十进制除10
        //bufR[0+1]=帧序号=帧序号低字节+帧序号高字节

        for (int i = 0; i < lenth; i++) {
        	bufR[i + 2] = bufSendFile[(index - 1) * MaxPacket + i];
            //当前请求帧=总文件[（当前请求帧下标-1）*每次大小+i]
        }

        PMS_Send(Config.bufDownFileData, bufR);
        
        /*numDownNow = numDownNow + lenth;*/
        numDownNow = numDownNow + lenth;

    }








    /** 重发机制和心跳 */
    class HeartTimeCount extends CountDownTimer {//20160913CountDownTimer：倒计时器

        public HeartTimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onFinish() {
        }

        @Override
        public void onTick(long millisUntilFinished) {
            try {

                if (!TextUtils.isEmpty(Config.SERVER_HOST_NAME) && socket != null && socket.isConnected()) {
                    timeCnt++;
                    if (timeCnt > 9) {
                        RecTimeOut = true;
                        timeCnt = 0;
                    }

                    if (ConFalg) {
                        if (RecTimeOut && isSendCMD) {
                            MaxReCnt++;
                            sendMessage(bufLastTemp);
                            isSendCMD = true;
                            timeCnt = 0;
                            RecTimeOut = false;
                            if (MaxReCnt > 2) {
                                MaxReCnt = 0;
                                ConFalg = false;
                                KappUtils.showToast(context, "操作失败，进行重连");
                                ///System.out.println("达到最大重发次数,重新建立连接帧");
                                Config.SERVER_HOST_NAME = "";
                                connectTimes = 3;
                                isConnected = false;
                                connectHandler.sendEmptyMessage(0);
                            }
                        }

                        /** 心跳计时，30S无操作发送心跳指令 */
                        timeCntHeart++;

                        if (timeCntHeart % 10 == 0) {
                            ///System.out.println("心跳计时:" + timeCntHeart);
                        }

                        if (timeCntHeart >= 30) {
                            ///System.out.println("请求发送心跳包");
                            PMS_Send(Config.bufHearbeat);
                            timeCntHeart = 0;
                        }
                    }
                }
            } catch (Exception e) {
            }
        }
    }

    /** 三次重建tcp连接与指令的机会 */
    int connectTimes = 3;

    /** 关于连接模块做的处理 */
    Handler connectHandler = new Handler(new Callback() {//20160920实现Handler.Callback接口接收消息
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case 0://20160914（首次）连接PMS
                    if (!isConnected && connectTimes > 0) {
                        connectTimes--;
                        connectHandler.sendEmptyMessage(1);//20160920重新连接（3次--）
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                Looper.prepare();
                                closeSocket();//20160920关闭之前的连接
                                initClientSocket();//20160920根绝ip与端口初始化socket连接
                                PMS_Send(Config.bufConnect);//20160914连接确认报文
                                new Handler().postDelayed(new Runnable() {//20160920延迟2秒执行
                                    @Override
                                    public void run() {
                                        connectHandler.sendEmptyMessage(0);//20160920连接PMS
                                    }
                                }, 2000);
                                Looper.loop();
                            }
                        }).start();
                    } else {
                        if (!isConnected) {
                            if (receiver != null) {
                                receiver.onFailure(0);
                            }
                            KappAppliction.state = 2;
                            KappUtils.showToast(context, "与PMS连接已经断开");
                        }
                    }
                    break;
                case 1://20160920重新连接
                    break;
            }
            return false;
        }
    });

    /**
     * 从PMS下载数据到手机本地 buf 当前接收到的传来的数据，可以得到此次数据的长度以及数据
     *  @param buf 有效数据在前面一段；65536=初始化每一帧数据（临时字节数组）（buf[0]为0xA5）
     */
    private void upFile(byte[] buf/* , int num */) {//20160928buf：不能超过1032
//        int fileLen = DataUtil.hexToTen(buf[1]) + DataUtil.hexToTen(buf[2]) * 256 - 5;//200160928//fileLen：长度L：功能码、子功能码以及数据域的长度之和
    	//5=功能码+子功能码+客户端地址+帧序号低字节+帧序号高字节=长度L-有效数据

        int fileLen = DataUtil.hexToTen(buf[1]) + DataUtil.hexToTen(buf[2]) * 256 - 9;//200160928//fileLen：长度L：功能码、子功能码以及数据域的长度之和
      //9=功能码+子功能码+客户端地址+(录波文件的长度0~7位+录波文件的长度8~15位+录波文件的长度16~23位+录波文件的长度24~31位)+帧序号低字节+帧序号高字节=长度L-有效数据

        int allFileLen = DataUtil.hexToTen(buf[6]) + DataUtil.hexToTen(buf[7]) * 256 + DataUtil.hexToTen(buf[8]) * 256 * 256 + DataUtil.hexToTen(buf[9]) * 256 * 256 * 256;
        for (int i = 0; i < 1037; i++) {

        	Log.i(TAG, byte2HexString(buf[i]));
		}


        Log.i(TAG, "收到第" + (DataUtil.hexToTen(buf[10]) + DataUtil.hexToTen(buf[11]) * 256) + "帧,长度=" + fileLen + ",当前接收总长度：" + (numUpNow + fileLen)+ ",HHD-->总长度：" + allFileLen);
        for (int i = 0; i < fileLen; i++) {
//            bufRecFile[numUpNow + i] = buf[i + 8];//20160928//bufRecFile：当前菜谱文件总长度
            bufRecFile[numUpNow + i] = buf[i + 12];//20160928//bufRecFile：当前菜谱文件总长度
        }
//        Log.i(TAG, "每一帧数据的长度修改后===================================【" + bufRecFile.length+ "】" );

        for (int j= 0;j < bufRecFile.length; j++) {
//        	Log.i(TAG, "下载的数组【" + byte2HexString(bufRecFile[j])+ "】" );
		}

        numUpNow += fileLen;// 40380 40124//20160928//fileLen：进度总长度（int）
        if (receiver != null) { // 界面上实时显示当前下载进度
            receiver.onSuccess(null, 3, numUpNow, bufRecFile.length);
        }
        // 接收完毕发送结束指令，否则继续请求数据
        Log.i(TAG, "当前进度="+numUpNow+"---文件总长度="+bufRecFile.length);
        if (numUpNow == bufRecFile.length) {//20160928需要修改：帧序号=文件的长度/每帧大小
            PMS_Send(Config.bufFileStop);
        } else {
            frmIndex++;
            ACK(frmIndex);
            PMS_Send(Config.bufFileAck, bufACK);//frmIndex（int）=帧序号=bufACK（byte）
        }
    }

    /**
     * 传送手机里面的固件到pms里面,根据文件路径得出所需要的指令并传给pms
     */
    public void doSendFilePMS() {
        String fileName;
        File file;
        file = new File(CheckUpdateActivity.result1.getPath());
        int index = CheckUpdateActivity.result1.getPath().lastIndexOf('/');
        if (index > 0) {
            index = index + 1;
        }
        fileName = CheckUpdateActivity.result1.getPath().substring(index);
        if (fileName.length() > 39) {
            Looper.prepare();
            KappUtils.showToast(context, "文件名称超长，请重新选择");
            Looper.loop();
            if (receiver != null) {
                receiver.onFailure(0);
            }
            return;
        }

        // 要发送的文件数据
        byte[] bufFile = FileUtils.getBytesFromFile(file);
        //MyLog.d("文件大小: " + bufFile.length);
        int check = 0;
        bufSendFile = new byte[bufFile.length];
        for (int i = 0; i < bufFile.length; i++) {
            bufSendFile[i] = bufFile[i];
            check += bufFile[i];
        }

        // 文件名的字节长度
        byte[] arr = null;
        try {
            arr = fileName.getBytes("gb2312");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // 所传参数，文件名长度+文件长度（均为字节长度）
        byte[] bufFileInfo = new byte[arr.length + 8];
        bufFileInfo[0] = (byte) (bufFile.length % 256);
        bufFileInfo[1] = (byte) ((bufFile.length / 256) % 256);
        bufFileInfo[2] = (byte) ((bufFile.length / 256 / 256) % 256);
        bufFileInfo[3] = (byte) ((bufFile.length / 256 / 256 / 256) % 256);

        bufFileInfo[4] = (byte) (check % 256);
        bufFileInfo[5] = (byte) ((check / 256) % 256);
        bufFileInfo[6] = (byte) ((check / 256 / 256) % 256);
        bufFileInfo[7] = (byte) ((check / 256 / 256 / 256) % 256);

        numDownNow = 0;
        numDownZie = (int) bufFile.length;

        for (int i = 0; i < arr.length; i++) {
            bufFileInfo[i + 8] = arr[i];
        }
        ConFalg = false;
        PMS_Send(Config.bufDownFileInfo, bufFileInfo);
    }

    /**
     * 传送手机里面的菜谱信息到pms里面,根据文件路径得出所需要的指令并传给pms
     */
    public void doSendFile() {
        //Log.i(TAG, "doSendFile()");

    	/**
    	 * 20160929菜谱文件名
    	 */
        String fileName;

        /**
         * 20160929保存在手机的菜谱文件
         */
        File file;
        if (CookBookDetailActivity.stat == false) {
            file = new File(CookBookDetailActivity.filePath);
            // 文件名长度的限制
            fileName = CookBookDetailActivity.filePath.substring(CookBookDetailActivity.filePath.lastIndexOf('/'));
        } else {
            file = new File(CookBookDetailActivity.newFilePath);
            // 文件名长度的限制
            fileName = CookBookDetailActivity.newFilePath.substring(CookBookDetailActivity.newFilePath.lastIndexOf('/'));
        }

        if (fileName.startsWith("\\")) {
            fileName = fileName.replace("\\", "");
        }
        if (fileName.startsWith("/")) {
            fileName = fileName.replace("/", "");
        }
        //MyLog.d("文件名: " + fileName);
        if (fileName.length() > 39) {
            Looper.prepare();
            KappUtils.showToast(context, "文件名称超长，请重新选择");
            Looper.loop();
            if (receiver != null) {
                receiver.onFailure(0);
            }
            return;
        }

        /**
         * 要发送的文件数据//20160929菜谱文件转byte数组
         */
        byte[] bufFile = FileUtils.getBytesFromFile(file);
//        Log.i(TAG, "bufFile源文件大小"+bufFile.length+"<---------------->");

        /**
         * 20160929//check=bufFile的每个byte转int的和
         */
        int check = 0;
        bufSendFile = new byte[bufFile.length];
        for (int i = 0; i < bufFile.length; i++) {
            bufSendFile[i] = bufFile[i];
            check += bufFile[i];
        }

        /**
         * 文件名的字节长度
         */
        byte[] arr = null;
        try {
            arr = fileName.getBytes("gbk");//20160929菜谱文件名按"gbk"编码到arr数组
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        /**
         * 20160929//bufFileInfo=文件的长度+文件名长度=帧格式-（地址码[0]+长度L低字节+长度L高字节+功能码+子功能码+客户端地址[5]<->+SUM校验码）
         */
        byte[] bufFileInfo = new byte[arr.length + 8]; //所传参数，文件名长度+文件长度（均为字节长度）
        // byte[] bufFileInfo = new byte[arr.length + 4];
        //20160929
        bufFileInfo[0] = (byte) (bufFile.length % 256);
        bufFileInfo[1] = (byte) ((bufFile.length / 256) % 256);
        bufFileInfo[2] = (byte) ((bufFile.length / 256 / 256) % 256);
        bufFileInfo[3] = (byte) ((bufFile.length / 256 / 256 / 256) % 256);

        //20160929
        bufFileInfo[4] = (byte) (check % 256);
        bufFileInfo[5] = (byte) ((check / 256) % 256);
        bufFileInfo[6] = (byte) ((check / 256 / 256) % 256);
        bufFileInfo[7] = (byte) ((check / 256 / 256 / 256) % 256);

        numDownNow = 0;
        numDownZie = (int) bufFile.length;
//        Log.i(TAG, "doSendFile()：numDownZie：下发文件大-->     " + numDownZie);

        for (int i = 0; i < arr.length; i++) {
            bufFileInfo[i + 8] = arr[i];
        }

        ConFalg = false;
        PMS_Send(Config.bufDownFileInfo, bufFileInfo);
      //20160929//bufFileInfo=文件的长度+文件名长度=帧格式-（长度L低字节+长度L高字节+功能码+子功能码+客户端地址（5））


    }











    /**
     * 方式三
     *
     * @param //bytes
     * @return
     */
    public static String toHex(byte b) {
        String result = Integer.toHexString(b & 0xFF);
        if (result.length() == 1) {
            result = '0' + result;
        }
        return result;
    }

    private static char[] HexCode = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * byte2HexString
     *
     * @param b
     * @return
     */
    public static String byte2HexString(byte b) {//20160913byte转字符串
        StringBuffer buffer = new StringBuffer();
        buffer.append(HexCode[(b >>> 4) & 0x0f]);
        buffer.append(HexCode[b & 0x0f]);
        return buffer.toString();
    }

    public static int byte2int(byte[] res) {
        // 一个byte数据左移24位变成0x??000000，再右移8位变成0x00??0000

        int targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00) // | 表示安位或
                | ((res[2] << 24) >>> 8) | (res[3] << 24);
        return targets;
    }
}
