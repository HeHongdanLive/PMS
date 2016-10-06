package com.gzmelife.app.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataUtil {
	/** 若number<10,在前面加0 */
	public static String addZeroBeforeNumber(int number) {
		return number > 9 ? "" + number : "0" + number;
	}
	
	/**
	 * 根据B单位的文件大小，得到M单位的格式，取一位小数
	 */
	public static String getFileSizeOfMBFromByte(long size) {
		size /= 1024; // 得到KB
		double result = 1.0 * size / 1024;
		result = (double)(Math.round(result * 10)) / 10;
		return result + "";
	}
	public final static boolean  isnotnull(Object object) {

		if (object == null) {
			return false;
		}
		if (object.toString().trim().equals("")) {
			return false;
		}
		return true;

	}
	/**
     * 描述：手机号格式验证.
     *
     * @param str 指定的手机号码字符串
     * @return 是否为手机号码格式:是为true，否则false
     */
 	public static Boolean isMobileNo(String str) {
 		Boolean isMobileNo = false;
 		try {
			Pattern p = Pattern.compile("^((13[0-9])|(15[^4,\\D])|(18[0-9])|(17[7]))\\d{8}$");
			Matcher m = p.matcher(str);
			isMobileNo = m.matches();
		} catch (Exception e) {
			e.printStackTrace();
		}
 		return isMobileNo;
 	}
 	
 	public static byte getIpEndByte(int i) {
//		return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF)
//				+ "." + (i >> 24 & 0xFF);
		return (byte) (i >> 24 & 0xFF);
	}
	
 	public static String getIp(int i) {
 		String ip = (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF)
				+ "." + (i >> 24 & 0xFF);
 		MyLog.i("ip=" + ip);
		return ip;
	}
 	
 	public static int hexToTen(byte b) {
 		return b & 0xff;
//    	int temp = b;
//		if (temp < 0) { 
//			temp += 256;
//		}
//		return temp;
    }
}
