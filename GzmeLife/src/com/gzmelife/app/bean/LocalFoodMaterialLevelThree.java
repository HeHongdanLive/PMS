package com.gzmelife.app.bean;

import org.xutils.db.annotation.Column;
import org.xutils.db.annotation.Table;

@Table(name = "localFoodMaterialLevelThree")
public class LocalFoodMaterialLevelThree {
	@Column(isId = true, name = "id")
	private int fsId;
	@Column(name = "name")
	private String fsName;
	@Column(name = "weight")
	private int weight;
	@Column(name = "pid")
	private int pid; // 分类ID
	private boolean isChecked;
	
	private String uid;
	
	
	public boolean isChecked() {
		return isChecked;
	}
	public void setChecked(boolean isChecked) {
		this.isChecked = isChecked;
	}
	public int getPid() {
		return pid;
	}
	public void setPid(int pid) {
		this.pid = pid;
	}
	public int getId() {
		return fsId;
	}
	public LocalFoodMaterialLevelThree setId(int id) {
		this.fsId = id;
		return this;
	}
	
	public String getName() {
		return fsName;
	}
	
	public LocalFoodMaterialLevelThree setName(String name) {
		this.fsName = name;
		return this;
	}
	public int getWeight() {
		return weight;
	}
	public void setWeight(int weight) {
		this.weight = weight;
	}
	
	public String getuid() {
		return uid;
	}
	public void setuid(String uid) {
		this.uid = uid;
	}
}
