package com.gzmelife.app.device;

public class SocketToolFile {
	
    /**
     * 下发文件的总长度（int）
     */
    private int numDownZie = 0;
	
    /**
     * 20160921当前请求帧
     */
    private int frmIndex = 0;
    
    /**
     * 已经下发了的长度（int）
     */
    private int numDownNow = 0;
    
    
    
    
    

	public int getNumDownZie() {
		return numDownZie;
	}

	public void setNumDownZie(int numDownZie) {
		this.numDownZie = numDownZie;
	}

	public int getFrmIndex() {
		return frmIndex;
	}
	public int setAddFrmIndex() {
		frmIndex = frmIndex++;
		return frmIndex;
	}
	public void setFrmIndex(int frmIndex) {
		this.frmIndex = frmIndex;
	}

	public int getNumDownNow() {
		return numDownNow;
	}

	public void setNumDownNow(int numDownNow) {
		this.numDownNow = numDownNow;
	}


    
    
    
    
    
	
    
}
