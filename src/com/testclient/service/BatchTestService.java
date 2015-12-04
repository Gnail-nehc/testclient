package com.alipics.testassets.testclient.service;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alipics.testassets.testclient.enums.BatchTestingFolderName;
import com.alipics.testassets.testclient.enums.HistoryFolderName;
import com.alipics.testassets.testclient.enums.LabFolderName;
import com.alipics.testassets.testclient.enums.LoopParameterNameInForm;
import com.alipics.testassets.testclient.enums.RunningStatus;
import com.alipics.testassets.testclient.enums.SeperatorDefinition;
import com.alipics.testassets.testclient.enums.TestSetFileName;
import com.alipics.testassets.testclient.enums.TestStatus;
import com.alipics.testassets.testclient.enums.TimeFormatDefiniation;
import com.alipics.testassets.testclient.factory.JsonObjectMapperFactory;
import com.alipics.testassets.testclient.httpmodel.BatchTestItem;
import com.alipics.testassets.testclient.httpmodel.DataGridJson;
import com.alipics.testassets.testclient.httpmodel.Json;
import com.alipics.testassets.testclient.httpmodel.TestExecutionJson;
import com.alipics.testassets.testclient.httpmodel.TestExecutionStore;
import com.alipics.testassets.testclient.httpmodel.TestHistoryItem;
import com.alipics.testassets.testclient.httpmodel.TestResultItem;
import com.alipics.testassets.testclient.model.HttpTarget;
import com.alipics.testassets.testclient.model.KeyValue;
import com.alipics.testassets.testclient.model.Parameter;
import com.alipics.testassets.testclient.utils.Auto;
import com.alipics.testassets.testclient.utils.FileNameUtils;
import com.alipics.testassets.testclient.utils.MyFileUtils;
import com.alipics.testassets.testclient.utils.TemplateUtils;



@Service("batchTestService")
public class BatchTestService {
	private static final Logger logger = Logger.getLogger(TestHistoryService.class);
	@Autowired
	TestExecuteService testExecuteService;
	@Autowired
	LabEnvironmentService labEnvironmentService;
	
	public synchronized String executeBatchTest(String dirPath,Date date){
		SimpleDateFormat format = new SimpleDateFormat(TimeFormatDefiniation.timeFolderFormat);			
		String runid=format.format(null==date?new Date():date);
		String batchtestingfolderpath=dirPath+"/"+BatchTestingFolderName.folderName+"/"+runid;
		try{
			List<String> list=new ArrayList<String>();
			if(dirPath.endsWith("-d"))
				getTestsInfoUnderSpecificDir(dirPath,list);
			else
				getTestsInfoUnderSpecificRunningSet(dirPath,list);
			for(String defaultTest : list){
				try{
					String[] info = defaultTest.split(SeperatorDefinition.seperator);
					String testpath=info[0];
					String testfilename=info[1];
					Map<String,Map<String,String>> global_reference_in=new HashMap<String,Map<String,String>>(),global_reference_out=new HashMap<String,Map<String,String>>();
					//一条case可能循环跑了多次
					List<TestResultItem> tris = executeTestByPath(testpath,global_reference_in,global_reference_out);
					for(TestResultItem testresult : tris){
						generateBatchExecutionResultFile(batchtestingfolderpath,testpath,testresult,testfilename);
					}
				}catch(Exception ex){
					logger.error(ex.getMessage());
				}
			}
		}catch(Exception ex){
			logger.error(ex.getMessage());
		}
		return batchtestingfolderpath;
	}
	
	public List<TestResultItem> executeTestByPath(String testpath,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		List<TestResultItem> list = new ArrayList<TestResultItem>();
		try{
			Map parameters=new HashMap();
			TestResultItem testresult = new TestResultItem();
    		String teststarttime=testresult.getTime();
    		try{
    			testExecuteService.setupAction(testpath,parameters,global_reference_in,global_reference_out);
    			parameters = testExecuteService.getRequestParameterMap(testpath,global_reference_in,global_reference_out);
    			testresult = testExecuteService.getTestResultItem(testpath,parameters);
				if(!testresult.getResult().equals(TestStatus.exception)){
					testExecuteService.getCheckpointsAndResultFromFile(testpath,parameters,testresult.getResponseInfo(),testresult);
				}
    		}catch(Exception e){
				testresult.setResult(TestStatus.exception);
				testresult.setComment("批量执行失败.\n"+e.getClass().toString()+e.getMessage());
			}
			finally{
				testExecuteService.teardownAction(testpath,parameters,testresult.getResponseInfo(),global_reference_in,global_reference_out);
				generateHistoryFileByTestPath(testpath,testresult);
				list.add(testresult);
			}
		}catch(Exception e){
			TestResultItem testresult = new TestResultItem();
    		testresult.setDuration("");
    		testresult.setResult(TestStatus.exception);
    		testresult.setComment("getRequestParameterMap失败.\n"+e.getClass().toString()+e.getMessage());
    		generateHistoryFileByTestPath(testpath,testresult);
    		list.add(testresult);
		}
		return list;
	}
	
	public void executeTestByPathWithoutCheckpoint(String testpath,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		try{
			Map<String,Object> parameters=new HashMap<String,Object>();
			parameters = testExecuteService.getRequestParameterMap(testpath,global_reference_in,global_reference_out);
			Map<String,String> reqparas=global_reference_in.containsKey(testpath) ? global_reference_in.get(testpath) : new HashMap<String,String>();
			for(Entry<String,Object> en : parameters.entrySet()){
				String val=en.getValue().toString();
				String name=en.getKey().toString();
				reqparas.put(name, val);
			}
			global_reference_in.put(testpath, reqparas);
			String looptimes=(String)parameters.get(LoopParameterNameInForm.name);
	        looptimes=looptimes!=null ?looptimes:"1";
	        String[] loopparas=looptimes.split(SeperatorDefinition.seperator);
	        looptimes=loopparas[0];
	        if(!looptimes.isEmpty() && StringUtils.isNumeric(looptimes)){
	        	for(int i=0;i<Integer.parseInt(looptimes);i++){
	        		if(loopparas.length>1)
		        		Thread.sleep(Integer.parseInt(StringUtils.isNumeric(loopparas[1])?loopparas[1]:"1"));
	        		TestResultItem testresult = new TestResultItem();
	        		String teststarttime=testresult.getTime();
	        		try{
	        			testExecuteService.setupAction(testpath,parameters,global_reference_in,global_reference_out);
	        			testExecuteService.getTestResultItem(testpath,parameters);
	        		}catch(Exception e){
	    			}
	    			finally{
	    				testExecuteService.teardownAction(testpath,parameters,testresult.getResponseInfo(),global_reference_in,global_reference_out);
	    			}
	        	}
	        }
		}catch(Exception e){
		}
	}

	public TestExecutionJson runTest(String source,int loop,Date date){
		TestExecutionStore store = TestExecutionStore.getInstance();
		Map<String,TestExecutionJson> result=store.getResultStore();
		TestExecutionJson json = new TestExecutionJson();
		if(result.containsKey(source)){
			json=result.get(source);
		}
		int total=json.getTotal(),pass=0,fail=0,invalid=0,error=0,norun=json.getNorun();
		while(loop-->0){
			if(null!=source && !source.isEmpty()){
				SimpleDateFormat format = new SimpleDateFormat(TimeFormatDefiniation.standard);
				if(null==date){
					date=new Date();
				}
				String time=format.format(date);
				if(source.endsWith("-c")){
					Map<String,Map<String,String>> global_reference_in=new HashMap<String,Map<String,String>>(),global_reference_out=new HashMap<String,Map<String,String>>();
					List<TestResultItem> tris = executeTestByPath(source,global_reference_in,global_reference_out);
					total=tris.size();
					for(TestResultItem tri : tris){
						String res=tri.getResult();
						if(res.endsWith(TestStatus.pass) || res.equalsIgnoreCase("p")){
							pass++;
						}else if(res.endsWith(TestStatus.fail) || res.equalsIgnoreCase("f")){
							fail++;
						}else if(res.endsWith(TestStatus.invalid) || res.equalsIgnoreCase("i")){
							invalid++;
						}else if(res.endsWith(TestStatus.exception) || res.equalsIgnoreCase("e")){
							error++;
						}else{
							norun++;
						}
					}
					String status=norun==0 ? RunningStatus.completed : RunningStatus.suspend+","+(pass+fail+invalid+error)+"/"+total+" be executed";
					json.setLasttime(json.getTime());
					json.setTime(time);
					json.setLaststatus(json.getStatus());
					json.setStatus(status);
					json.setRunningtimes(json.getRunningtimes()+"+1");
					//json.setReportview("http://test:!qazxsw2@192.168.81.33/testclient#report="+source);
					json.setTotal(total);
					json.setPass(pass);
					json.setFail(fail);
					json.setInvalid(invalid);
					json.setError(error);
					json.setNorun(total-pass-fail-invalid-error);
				}else{
					String filename=source.contains("/")?source:getRunningSetFoldername(source);
					String batchTestingFolderPath=executeBatchTest(filename,date);
					json=retrieveBatchTestExecutionJson(source,date,batchTestingFolderPath,null);
				}
			}else{
				json.setStatus("parameter 'source' configuration error.");
			}
		}
		//update resultstore for source
		result.put(source, json);
		store.setResultStore(result);
		return json;
	}
	
	public TestExecutionJson retrieveBatchTestExecutionJson(String source,Date date,String batchTestingFolderPath, String runningstatus){
		TestExecutionStore store = TestExecutionStore.getInstance();
		Map<String,TestExecutionJson> result=store.getResultStore();
		TestExecutionJson json = new TestExecutionJson();
		if(result.containsKey(source)){
			json=result.get(source);
		}
		int total=json.getTotal(),pass=0,fail=0,invalid=0,error=0,norun=json.getNorun();
		date=null!=date?date:new Date();
		String time=new SimpleDateFormat(TimeFormatDefiniation.timeFolderFormat).format(date);
		if(null==batchTestingFolderPath || batchTestingFolderPath.isEmpty()){
			batchTestingFolderPath="/"+BatchTestingFolderName.folderName+"/"+time;
			if(!source.contains("/")){
				String fullname=getRunningSetFoldername(source);
				batchTestingFolderPath="root/"+LabFolderName.folder+"/"+fullname+batchTestingFolderPath;
			}else{
				batchTestingFolderPath=source+batchTestingFolderPath;
			}
		}
		File folder=new File(batchTestingFolderPath);
		if(!folder.exists()){
			batchTestingFolderPath=StringUtils.substringBeforeLast(batchTestingFolderPath, "/");
			File f=new File(batchTestingFolderPath);
			batchTestingFolderPath=getRecentTest(f);
			folder=new File(f,batchTestingFolderPath);
		}
		for(String filename : folder.list()){
			String res=StringUtils.substringAfterLast(filename, SeperatorDefinition.seperator);
			if(res.endsWith(TestStatus.pass) || res.equalsIgnoreCase("p")){
				pass++;
			}else if(res.endsWith(TestStatus.fail) || res.equalsIgnoreCase("f")){
				fail++;
			}else if(res.endsWith(TestStatus.invalid) || res.equalsIgnoreCase("i")){
				invalid++;
			}else if(res.endsWith(TestStatus.exception) || res.equalsIgnoreCase("e")){
				error++;
			}
		}
		norun=total-folder.list().length;
		if(null==runningstatus || runningstatus.isEmpty()){
			runningstatus=norun==0 ? RunningStatus.completed : RunningStatus.suspend+","+folder.list().length+"/"+total+" be executed";
		}
		//json.setTime(new SimpleDateFormat(TimeFormatDefiniation.standard).format(date));
		json.setStatus(runningstatus);
		//json.setTotal(total);
		json.setPass(pass);
		json.setFail(fail);
		json.setInvalid(invalid);
		json.setError(error);
		json.setNorun(norun);
		result.put(source, json);
		store.setResultStore(result);
		return json;
	}
	
	//return .json history filename 
	private void generateHistoryFileByTestPath(String testpath,TestResultItem testresult){
		//generate history file
		String filename=FileNameUtils.getResultFile(testresult.getTime(),testresult.getDuration(),testresult.getResult());
		try {
			File history=new File(testpath+"/"+HistoryFolderName.folderName);
			if(!history.exists()){
				history.mkdirs();
			}
			File file=new File(history,filename);
			file.createNewFile();
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();	
			mapper.writeValue(file, testresult);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error("写历史记录文件失败：\n" + e.getClass().toString() + e.getMessage());
		}
	}
	
	private void generateBatchExecutionResultFile(String batchtestingfolderpath,String testpath,TestResultItem testresult,String testfilename){
		try {
			//create folder under BatchTesting
			MyFileUtils.makeDir(batchtestingfolderpath);
			//generate test item file under BatchTesting
			String teststarttime = testresult.getTime();
			String duration = testresult.getDuration();
			String result = testresult.getResult();
			testfilename=FileNameUtils.getTestRunFile(testfilename, teststarttime, duration, result);	
			File file=new File(batchtestingfolderpath,testfilename);
			file.createNewFile();
			String history=FileNameUtils.getResultFile(testresult.getTime(),testresult.getDuration(),testresult.getResult());
			String content=testpath+"/"+HistoryFolderName.folderName+SeperatorDefinition.testInfoSeperator+history;
			FileUtils.writeStringToFile(file, content);
		}catch (IOException e) {
			logger.error("写批量执行结果文件失败：\n" + e.getClass().toString() + e.getMessage());
		}
	}
	
	
	public DataGridJson getRecentTestItems(String dirPath){
		DataGridJson j = new DataGridJson();
		if(dirPath.isEmpty())
			return j;
		List<BatchTestItem>	row=new ArrayList<BatchTestItem>();
		if(!dirPath.endsWith("-c")){
			row=getDefaultTestItems(dirPath);
			File dir=new File(dirPath+"/"+BatchTestingFolderName.folderName);
			String recentRunid=getRecentTest(dir);
			dir=new File(dirPath+"/BatchTesting/"+recentRunid);
			String[] files = dir.list();
			for(String filename : files){
				String seperator=SeperatorDefinition.seperator;
				String tname=filename.split(seperator)[0];
				for(BatchTestItem bti : row){
					if(bti.getName().equals(tname) && !bti.isDoesrun()){
						BatchTestItem item= new BatchTestItem();
						item.setName(tname);
						item.setTestpath(bti.getTestpath());
						item.setPath(dirPath+"/"+BatchTestingFolderName.folderName+"/"+recentRunid+"/"+filename);
						item.setTime(filename.split(seperator)[1]);
						item.setDuration(filename.split(seperator)[2]);
						item.setStatus(StringUtils.substringBefore(filename.split(seperator)[3], "."));
						item.setDoesrun(true);
						row.remove(bti);
			            row.add(item);
						break;
					}
				}
			}
		}else{
			File f=new File(dirPath+"/"+HistoryFolderName.folderName);
			if(f.exists() && f.isDirectory()){
				String filename=getRecentTest(f);
				if(!filename.isEmpty()){
					String name=StringUtils.substringAfterLast(filename, "/");
					if(name.endsWith("-c")){
						name=StringUtils.substringBeforeLast(name, "-c");
					}
					String time=filename.split(SeperatorDefinition.seperator)[0];
					String duration=filename.split(SeperatorDefinition.seperator)[1];
					String status=filename.split(SeperatorDefinition.seperator)[2];
					status=status.isEmpty()?"r":status;
					BatchTestItem item= new BatchTestItem();
					item.setName(name);
					item.setTime(time);
					item.setDuration(duration);
					item.setStatus(status);
					item.setTestpath(dirPath);
					item.setPath(dirPath+"/"+HistoryFolderName.folderName+"/"+filename);
					row.add(item);
				}
			}
		}
		j.setRows(row);
		j.setTotal(row.size());
		return j;
	}

	public Json getDetailTestResultByTestPath(String path){
		Json j =new Json();
		try{
			TestResultItem tri=new TestResultItem();
			File file = new File(path);
			if(file.exists() && file.isFile()){
				if(!path.contains(HistoryFolderName.folderName)){
					String content = FileUtils.readFileToString(file);
					String historypath=content.split(SeperatorDefinition.testInfoSeperator)[0];
					String historyfile=content.split(SeperatorDefinition.testInfoSeperator)[1];
					file=new File(historypath);
					file=new File(file,historyfile);
				}
				if(file.exists()){
					ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
					tri = mapper.readValue(file, TestResultItem.class);
				}
				else{
					tri.setComment("历史记录已删除，是否删除该条目");
				}
			}
			else
				tri.setResult(TestStatus.ready);
			j.setObj(tri);
			j.setSuccess(true);
		}catch (IOException e) {
			j.setSuccess(false);
			j.setMsg("获取历史记录失败,"+e.getClass().toString()+": "+e.getMessage());
			logger.error("获取历史记录失败", e);
		}
		return j;
	}
	
	public Json deleteTestInfo(String folder, String time){
		Json j =new Json();
		try{
			File dir = new File(folder+"/"+BatchTestingFolderName.folderName);
			String[] files = dir.list();
			for(int i=0;i<files.length;i++){
				String recentTimeFolder = getRecentTest(files);
				if(recentTimeFolder!=""){
					File timeFolder=new File(folder+"/BatchTesting/"+recentTimeFolder);
					for(String testInfo : timeFolder.list()){
						if(testInfo.startsWith(time)){
							File f=new File(timeFolder,testInfo);
			        		if(f.isFile() && f.exists()){     
			                    f.delete();
			                    j.setSuccess(true);
			                    break;
			        		}	
						}
					}
					files=files.toString().replace(recentTimeFolder, "").replace("[", "").replace("]", "").replace(" ", "").split(",");
				}
			}
		} catch (Exception e) {
			j.setSuccess(false);
			logger.error("删除TestInfo失败", e);
		}
		return j;
	}
	
	private String getRecentTest(File dir){
		String[] list = dir.list();
		if(list.length>0)
			return Collections.max(Arrays.asList(list));
		else
			return "";
	}
	
	private String getRecentTest(String[] list){
		if(list.length>0)
			return Collections.max(Arrays.asList(list));
		else
			return "";
	}
	
	private List<BatchTestItem> getDefaultTestItems(String dirPath){
		List<BatchTestItem> row=new ArrayList<BatchTestItem>();
		List<String> testsinfo=new ArrayList<String>();
		returnTestsInfoUnderSpecificDir(dirPath,testsinfo);
		for(String info : testsinfo){
			String[] arr=info.split(SeperatorDefinition.seperator);
			if(arr.length==2){
				BatchTestItem bti = new BatchTestItem();
				bti.setTestpath(arr[0]);
				bti.setName(arr[1]);
				bti.setStatus(TestStatus.ready);
				row.add(bti);
			}
		}
		return row;
	}
	
	public void returnTestsInfoUnderSpecificDir(String dirPath,List<String> testPaths){
		MyFileUtils.makeDir(dirPath+"/"+BatchTestingFolderName.folderName);
		if(dirPath.endsWith("-d"))
			getTestsInfoUnderSpecificDir(dirPath,testPaths);
		else
			getTestsInfoUnderSpecificRunningSet(dirPath,testPaths);
	}
	
	private void getTestsInfoUnderSpecificDir(String dirPath,List<String> list){
		File dir=new File(dirPath);
		String folder[] = dir.list();
		for (String f : folder) {
			String childpath=dirPath.replace("\\", "/")+"/"+f;
            if(f.endsWith("-d")){
            	getTestsInfoUnderSpecificDir(childpath, list);
            }
            else if(f.endsWith("-c")){
            	String filename=f.substring(0, f.length()-2);
            	list.add(childpath+SeperatorDefinition.seperator+filename);
            }
        }
	}
	
	private void getTestsInfoUnderSpecificRunningSet(String dirPath,List<String> list){
		String runningSetFullName=dirPath;
		if(!dirPath.contains("@")){
			for(String fn : new File("root/"+LabFolderName.folder).list()){
				if(fn.startsWith(dirPath)){
					runningSetFullName="root/"+LabFolderName.folder+"/"+fn;
					break;
				}
			}
		}
		String[] tests=labEnvironmentService.getAllTestPathInRunningSet(runningSetFullName);
		for(String testpath : tests){
			String[] arr=testpath.split("/");
			String filename=arr[arr.length-1];
			if(filename.endsWith("-c")){
				String testname=filename.substring(0, filename.length()-2);
				list.add(testpath+SeperatorDefinition.seperator+testname);
			}
		}
	}
	
	//folder: path or runningset name
	public int getNumberOfCaseFromFolder(String folder){
		//dir
		int total=0;
		File f=new File(folder);
		if(folder.contains("/")){
			total=getNumberOfCaseUnderDir(f);
		}else{//runningset
			if(!folder.isEmpty()){
				f=new File("root/"+LabFolderName.folder+"/"+folder+"/"+TestSetFileName.TestSet);
				if(f.exists() && f.isFile()){
					try {
						String content=FileUtils.readFileToString(f);
						total=content.split(SeperatorDefinition.seperator).length;
					} catch (IOException e) {
						// TODO Auto-generated catch block
						total=0;
						logger.error("读文件异常，"+e.getClass()+e.getMessage());
					}
				}
			}
		}
		return total;
	}
	
	private int getNumberOfCaseUnderDir(File f){
		int total = 0;
		if(f.exists() && f.isDirectory()){
			if(f.getName().endsWith("-d")){
				for(File child : f.listFiles()){
					total += getNumberOfCaseUnderDir(child);
				}
			}else if(f.getName().endsWith("-c")){
				total = 1;
			}
		}
		return total;
	}
	
	public String getRunningSetFoldername(String name){
		File f=new File("root/"+LabFolderName.folder);
		if(f.exists() && f.isDirectory()){
			for(String filename : f.list()){
				if(filename.startsWith(name+SeperatorDefinition.seperator))
					return filename;
			}
		}
		return "";
	}

}
