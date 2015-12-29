package com.testclient.service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.testclient.enums.BatchTestingFolderName;
import com.testclient.enums.LabFolderName;
import com.testclient.enums.SeperatorDefinition;
import com.testclient.enums.TestSetFileName;
import com.testclient.enums.TimeFormatDefiniation;
import com.testclient.httpmodel.DataGridJson;
import com.testclient.httpmodel.Json;
import com.testclient.httpmodel.RunningSet;
import com.testclient.utils.MyFileUtils;


@Service("labEnvironmentService")
public class LabEnvironmentService {
	private static final Logger logger = Logger.getLogger(LabEnvironmentService.class);
	
	public Json addRunningSet(String rootName,String name, String testset){	
		Json j = new Json();
		try {
			String author=SecurityContextHolder.getContext().getAuthentication().getName();
			SimpleDateFormat format = new SimpleDateFormat(TimeFormatDefiniation.runningSet);			
			String time=format.format(new Date());
			String filename=name+SeperatorDefinition.seperator+time+SeperatorDefinition.seperator+author;
			MyFileUtils.makeDir(rootName+"/"+LabFolderName.folder);
			MyFileUtils.makeDir(rootName+"/"+LabFolderName.folder+"/"+filename);
			File f=new File(rootName+"/"+LabFolderName.folder,filename);
			f=new File(f,TestSetFileName.TestSet);
			f.createNewFile();
			FileUtils.writeStringToFile(f, testset);
			j.setSuccess(true);
		} catch (Exception e) {
			j.setMsg(e.getClass()+": "+e.getMessage());
			j.setSuccess(false);
		}
		return j;
	} 
	
	public Json modifyRunningSet(String runningset, String name, String testset,boolean isDeletion){	
		Json j = new Json();
		try {
			File f=new File(runningset);
			if(f.exists() && f.isDirectory()){
				if(isDeletion){
					File folder=new File(f,BatchTestingFolderName.folderName);
					if(folder.exists()){
						MyFileUtils.deleteDirectory(folder.getPath());
					}
				}
				File ts=new File(f,TestSetFileName.TestSet);
				FileUtils.writeStringToFile(ts, testset);
				String author=SecurityContextHolder.getContext().getAuthentication().getName();
				SimpleDateFormat format = new SimpleDateFormat(TimeFormatDefiniation.runningSet);			
				String time=format.format(new Date());
				String filename=name+SeperatorDefinition.seperator+time+SeperatorDefinition.seperator+author;
				String rootName=StringUtils.substringBefore(runningset, "/"+LabFolderName.folder).replace("/", "");
				File updateddir=new File(rootName+"/"+LabFolderName.folder+"/"+filename);
				f.renameTo(updateddir);
				j.setSuccess(true);
			}else{
				j.setMsg("没有找到该运行集！");
				j.setSuccess(false);
			}
		} catch (Exception e) {
			j.setMsg(e.getClass()+": "+e.getMessage());
			j.setSuccess(false);
		}
		return j;
	} 
	
	public DataGridJson getAllRunningSet(String rootName){
		DataGridJson j = new DataGridJson();
		List<RunningSet> row=new ArrayList<RunningSet>();
		File folder=new File(rootName,LabFolderName.folder);
		if(folder.exists()){
			String[] children=folder.list();
			for(String runset : children){
				String[] fields=runset.split(SeperatorDefinition.seperator);
				if(fields.length>1){
					row.add(new RunningSet(fields[0],fields[1],fields[2]));
				}
			}
			j.setRows(row);
		}
		j.setTotal(row.size());
		return j;	
	}
	
	public void deleteRunningSet(String foldername){
		File folder=new File(foldername);
		if(foldername.startsWith("root/"+LabFolderName.folder) && folder.exists()){
			MyFileUtils.deleteDirectory(foldername);
		}
	}
	
	public String[] getAllTestPathInRunningSet(String runningSetPath){
		String content="";
		File f=new File(runningSetPath);
		if(f.exists()){
			f=new File(f,TestSetFileName.TestSet);
			if(f.exists() && f.isFile()){
				try {
					content=FileUtils.readFileToString(f);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					logger.error("读文件异常，"+e.getClass()+e.getMessage());
				}
			}
		}
		return content.split(SeperatorDefinition.seperator);
	}
	
	public List<String> getNotExistingTestPaths(String runningSetPath){
		List<String> list = new ArrayList<String>();
		String[] testpaths=getAllTestPathInRunningSet(runningSetPath);
		for(String path : testpaths){
			if(!new File(path).exists())
				list.add(path);
		}
		return list;
	}
	
}
