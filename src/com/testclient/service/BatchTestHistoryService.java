package com.alipics.testassets.testclient.service;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.stereotype.Service;

import com.alipics.testassets.testclient.enums.BatchTestingFolderName;
import com.alipics.testassets.testclient.enums.SeperatorDefinition;
import com.alipics.testassets.testclient.enums.TimeFormatDefiniation;
import com.alipics.testassets.testclient.factory.JsonObjectMapperFactory;
import com.alipics.testassets.testclient.httpmodel.BatchRunItem;
import com.alipics.testassets.testclient.httpmodel.BatchTestItem;
import com.alipics.testassets.testclient.httpmodel.BatchTestReport;
import com.alipics.testassets.testclient.httpmodel.CheckPointItem;
import com.alipics.testassets.testclient.httpmodel.DataGridJson;
import com.alipics.testassets.testclient.httpmodel.Json;
import com.alipics.testassets.testclient.httpmodel.TestResultItem;


@Service("batchTestHistoryService")
public class BatchTestHistoryService {
	private static final Logger logger = Logger.getLogger(TestHistoryService.class);
	
	public DataGridJson getBatchHistories(String dirpath){
		DataGridJson dgj=new DataGridJson();
		List<BatchRunItem> row=new ArrayList<BatchRunItem>();
		File dir=new File(dirpath+"/"+BatchTestingFolderName.folderName);
		if(dir.exists()){
			SimpleDateFormat formatDateFrom = new SimpleDateFormat(TimeFormatDefiniation.timeFolderFormat);
			SimpleDateFormat formatDateTo = new SimpleDateFormat(TimeFormatDefiniation.format.replace(" SSS", ""));
			for(String timedir : dir.list()){
				BatchRunItem bri = new BatchRunItem();
				String time="";
				try {
					time = formatDateTo.format(formatDateFrom.parse(timedir));
				} catch (ParseException e) {
					logger.error(e.getMessage());
				}
				bri.setPath(dirpath+"/BatchTesting/"+timedir);
				bri.setTime(time);
				row.add(bri);
			}
		}
		dgj.setRows(row);
		dgj.setTotal(row.size());
		return dgj;
	}
	
	public Json deleteBatchHistory(String batchrunpath){
		Json j =new Json();
		try{			
			File dir = new File(batchrunpath);
			if(dir.exists()){
		        String files[] = dir.list();
		        for (String testinfo : files) {
		        	File f=new File(dir,testinfo);
		        	if(f.exists() && f.isFile()){
		        		String content = FileUtils.readFileToString(f);
						String historypath=content.split(SeperatorDefinition.testInfoSeperator)[0];
						String historyfile=content.split(SeperatorDefinition.testInfoSeperator)[1];
						File historyf=new File(historypath);
						historyf=new File(historyf,historyfile);
		        		if(historyf.isFile() && historyf.exists()){     
		        			historyf.delete();
		        		}
		        		f.delete();
		        	}
	            }
		        dir.delete();
		        if(!j.isSuccess())
                	j.setSuccess(true);
			}
		} catch (Exception e) {
			j.setSuccess(false);
			logger.error("删除TestResultItem失败", e);
		}
		return j;
	}
	
	public DataGridJson getTestRunInfo(String dirpath){
		DataGridJson dgj=new DataGridJson();
		List<BatchTestItem> row=new ArrayList<BatchTestItem>();
		File dir = new File(dirpath);
		if(dir.exists()){
			String seperator=SeperatorDefinition.seperator;
			for (String runinfo : dir.list()){
				BatchTestItem bti=new BatchTestItem();
				bti.setPath(dirpath+"/"+runinfo);
	            bti.setName(runinfo.split(seperator)[0]);
	            bti.setTime(runinfo.split(seperator)[1]);
	            bti.setDuration(runinfo.split(seperator)[2]);
	            String status=runinfo.split(seperator)[3];
	            status=StringUtils.substringBeforeLast(status, ".");
	            bti.setStatus(status);
	            row.add(bti);
			}
		}
		dgj.setRows(row);
		dgj.setTotal(row.size());
		return dgj;
	}
	
	public Json getTestResultDetailInfo(String testitempath){
		Json j =new Json();
		try{
			TestResultItem tri=new TestResultItem();
			File f=new File(testitempath);
			if(f.exists() && f.isFile()){
				String content = FileUtils.readFileToString(f);
				String historypath=content.split(SeperatorDefinition.testInfoSeperator)[0];
				String historyfile=content.split(SeperatorDefinition.testInfoSeperator)[1];
				String testPath=historypath.substring(0, historypath.length()-8);
				if(new File(testPath).exists()){
					f=new File(historypath);
					if(f.exists()){
						f=new File(f,historyfile);
						if(f.exists() && f.isFile()){
							ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			        		tri = mapper.readValue(f, TestResultItem.class);
			        		j.setObj(tri);
			        		j.setSuccess(true);
						}else{
							j.setMsg("文件"+content.replace(SeperatorDefinition.testInfoSeperator, "/")+"不存在或被删除");
							j.setSuccess(false);
						}
					}else{
						j.setMsg("文件夹"+historypath+"不存在或被删除");
						j.setSuccess(false);
					}
				}else{
					j.setMsg("测试"+testPath+"不存在或被删除");
					j.setSuccess(false);
				}
			}
		} catch (IOException e) {
			j.setSuccess(false);
			logger.error("获取TestResultItem失败", e);
		}
		return j;		
	}
	
	public List<BatchTestReport> getBatchTestReport(String path){
		List<BatchTestReport> list=new ArrayList<BatchTestReport>();
		File f=new File(path);
		if(f.exists()){
			for(File test : f.listFiles()){
				BatchTestReport r=new BatchTestReport();
				String filename=test.getName();
				String[] arr=filename.split("@");
				String name=arr[0];
				String time=arr[1];
				String request="",response="",checkpoint="";
				time=time.replace(time.split(" ")[1], time.split(" ")[1].replaceAll("-", ":"));
				String duration=arr[2];
				String result=StringUtils.substringBefore(arr[3], ".");
				String stuatus="";
				if(result.equalsIgnoreCase("p")){
					stuatus="通过";
				}else if(result.equalsIgnoreCase("f")){
					stuatus="失败";
				}else if(result.equalsIgnoreCase("i")){
					stuatus="无检查点";
				}else if(result.equalsIgnoreCase("r")){
					stuatus="待测";
				}else if(result.equalsIgnoreCase("e")){
					stuatus="配置错误";
				}
				try {
					ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
					String content=FileUtils.readFileToString(test);
					File history=new File(content.replace(">", "/"));
					if(history.exists() && history.isFile()){
						TestResultItem tri = mapper.readValue(history, TestResultItem.class);
						request=tri.getRequestInfo();
						String removedtext=StringUtils.substringBetween(request, "[request headers]:", "[request body]:");
						request=request.replace(removedtext, "").replace("[request headers]:", "");
						if(!result.equalsIgnoreCase("e")){
							response=tri.getResponseInfo();
							checkpoint="";
						}else{
							response=tri.getComment();
						}
						response=StringUtils.substringAfter(response, "[body]:");
						for(CheckPointItem item : tri.getCheckPoint()){
							checkpoint+="比较类型："+item.getType()+" "+item.getName()+" "+item.getCheckInfo()+" "+item.getResult()+"\n";
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					request=response="";
				} 
				r.setName(name);
				r.setTime(time);
				r.setDuration(duration);
				r.setResult(stuatus);
				r.setRequest(request);
				r.setResponse(response);
				r.setCheckpoint(checkpoint);
				list.add(r);
			}
		}
		return list;
	}
}
