package com.alipics.testassets.testclient.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alipics.testassets.testclient.enums.ActionType;
import com.alipics.testassets.testclient.enums.ApiKeyword;
import com.alipics.testassets.testclient.enums.CheckPointResult;
import com.alipics.testassets.testclient.enums.CheckPointType;
import com.alipics.testassets.testclient.enums.HistoryFolderName;
import com.alipics.testassets.testclient.enums.LoopParameterNameInForm;
import com.alipics.testassets.testclient.enums.PreConfigType;
import com.alipics.testassets.testclient.enums.SeperatorDefinition;
import com.alipics.testassets.testclient.enums.TestStatus;
import com.alipics.testassets.testclient.factory.JsonObjectMapperFactory;
import com.alipics.testassets.testclient.httpmodel.CheckPointItem;
import com.alipics.testassets.testclient.httpmodel.Json;
import com.alipics.testassets.testclient.httpmodel.MixActionSettingContainer;
import com.alipics.testassets.testclient.httpmodel.MixActionSettingInfo;
import com.alipics.testassets.testclient.httpmodel.PreConfigItem;
import com.alipics.testassets.testclient.httpmodel.ServiceBoundDataItem;
import com.alipics.testassets.testclient.httpmodel.SqlEntity;
import com.alipics.testassets.testclient.httpmodel.TestHistoryInfo;
import com.alipics.testassets.testclient.httpmodel.TestResultItem;
import com.alipics.testassets.testclient.model.CheckPointContianer;
import com.alipics.testassets.testclient.model.HttpTarget;
import com.alipics.testassets.testclient.model.KeyValue;
import com.alipics.testassets.testclient.model.Parameter;
import com.alipics.testassets.testclient.model.PreConfigContainer;
import com.alipics.testassets.testclient.model.SqlQueryReturn;
import com.alipics.testassets.testclient.utils.Auto;
import com.alipics.testassets.testclient.utils.FileNameUtils;
import com.alipics.testassets.testclient.utils.HTTPFacade;
import com.alipics.testassets.testclient.utils.HttpServletRequestUtils;
import com.alipics.testassets.testclient.utils.JdbcUtils;
import com.alipics.testassets.testclient.utils.TemplateUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;


@Service("testExecuteService")
public class TestExecuteService {
	private static final String NODE_PATH="/opt/node";
	private static final String NODE_MODULES="/opt/node_modules";
	private final String SIGN="__DATASIGN__";
	private final String FIELD2BEREPLACED="sign";
	private final String SECRETKEY="21218CCA77804D2BA1922C33E0151105";
	
	
	private static final Logger logger = Logger.getLogger(TestExecuteService.class);
	@Autowired
	BatchTestService batchTestService;
	@Autowired
	OutputParameterService outputParameterService;
	
	public Json executeTestInFront(HttpServletRequest request) {
		Json j = new Json();
		Map<String,Map<String,String>> global_reference_in=new HashMap<String,Map<String,String>>(),global_reference_out=new HashMap<String,Map<String,String>>();
		List<TestResultItem> objlist=new ArrayList<TestResultItem>();
		Map requestmap =new HashMap();
		try{
			String reqbody=HttpServletRequestUtils.getHttpServletRequestBody(request);
			String path = HttpServletRequestUtils.getParameter(request, reqbody ,"testPath");
			if(path==null || path.isEmpty()){
				j.setSuccess(false);
				j.setMsg("path is null or empty");
				return j;
			}
	        String looptimes=HttpServletRequestUtils.getParameter(request, reqbody ,LoopParameterNameInForm.name);
	        looptimes=(looptimes!=null && !looptimes.isEmpty() )?looptimes:"1";
	        String[] loopparas=looptimes.split(SeperatorDefinition.seperator);
	        looptimes=loopparas[0];
	        if(!looptimes.isEmpty() && StringUtils.isNumeric(looptimes)){
	        	
	        	for(int i=0;i<Integer.parseInt(looptimes);i++){
	        		try{
	        			setupAction(path,requestmap,global_reference_in,global_reference_out);
	        			requestmap = getRequestParameterMap(reqbody,path,global_reference_in,global_reference_out);
						TestResultItem testresult = getTestResultItem(path,requestmap);
						
						if(loopparas.length>1)
			        		Thread.sleep(Integer.parseInt(StringUtils.isNumeric(loopparas[1])?loopparas[1]:"1"));
						
						if(!testresult.getResult().equals(TestStatus.exception)){
							getCheckpointsAndResultFromFile(path, requestmap, testresult.getResponseInfo(),testresult);
							j.setSuccess(true);
						}else{
							j.setMsg("执行异常：\n" + testresult.getComment());
							j.setSuccess(false);
						}
						j.setObj(testresult);
		        	}catch(Exception e){
		        		j.setMsg(e.getClass()+e.getMessage());
		        		j.setSuccess(false);
		        	}finally{
		    			try{
		    				TestResultItem result=(TestResultItem)j.getObj();
		    				objlist.add(result);
		    				
		    				teardownAction(path,requestmap,result.getResponseInfo(),global_reference_in,global_reference_out);
		    				
		    			}catch(Exception e){
		    				j.setMsg(e.getClass()+e.getMessage());
		    				j.setSuccess(false);
		    			}
		    		}
	        	}
	        	j.setObj(objlist);
	        }else{
	        	j.setMsg("循环次数必须为自然数！");
	        	j.setSuccess(false);
	        }
		}catch(Exception e){
			j.setMsg(e.getClass()+e.getMessage());
			logger.error(e.getClass()+e.getMessage());
			j.setSuccess(false);
		}
		return j;
	}
	
	public void generateHistoryFile(HttpServletRequest request){
		String reqbody=HttpServletRequestUtils.getHttpServletRequestBody(request);
		JSONObject obj=JSONObject.fromObject(reqbody);
		String foldername =obj.getString("foldername");
		String reqstate=obj.getString("reqstate");
		if(reqstate.equalsIgnoreCase("success")){
			String json=obj.getString("testresultitemcollectionjson");//.replaceAll("__AND", "&");
			JSONArray ja= JSONArray.fromObject(json);
			for(int i=0;i<ja.length();i++){
				TestResultItem tri=new TestResultItem();
				try{
					JSONObject itemobj=ja.getJSONObject(i);
					String result=itemobj.getString("result");
					tri.setResult(result);
					if(!result.equals(TestStatus.exception)){
						Set<CheckPointItem> cps=new HashSet<CheckPointItem>();
						
						tri.setTime(itemobj.getString("time"));
						tri.setRequestInfo(itemobj.getString("requestInfo"));
						tri.setResponseInfo(itemobj.getString("responseInfo"));
						tri.setDuration(itemobj.getString("duration"));
						
						JSONArray jsonarr=JSONArray.fromObject(itemobj.get("checkPoint"));
						for(int j=0;j<jsonarr.length();j++){
							CheckPointItem item=(CheckPointItem)JSONObject.toBean(jsonarr.getJSONObject(j), CheckPointItem.class);
							cps.add(item);
						}
						tri.setCheckPoint(cps);
					}else
						tri.setComment(itemobj.getString("comment"));
				}catch(Exception e){
					tri.setDuration("");
					tri.setResult(TestStatus.exception);
					tri.setComment(e.getClass().toString()+": "+e.getMessage());
				}finally{
					generateHistoryFile(foldername, tri);
				}
			}
		}else{
			TestResultItem tri=new TestResultItem();
			tri.setDuration("");
			tri.setResult(TestStatus.exception);
			String comment="";
			String json=obj.getString("obj");
			if(json.startsWith("{") && json.endsWith("}"))
				comment=JSONObject.fromObject(json).get("comment").toString();
			else if(json.startsWith("[") && json.endsWith("]"))
				comment=JSONArray.fromObject(json).getJSONObject(0).get("comment").toString();
			tri.setComment(comment);
			generateHistoryFile(foldername, tri);
		}
	}

	public void setupAction(String testPath,Map requestmap,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		executeMixAction(testPath, ActionType.setup, requestmap, null,global_reference_in,global_reference_out);
	}
	
	public void teardownAction(String testPath,Map requestParas,String response,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		executeMixAction(testPath, ActionType.teardown, requestParas, response,global_reference_in,global_reference_out);
	}
	
	private int executeSqlActionFromJson(String testPath, String actionType, String sqlactionstr, Map reqParas, String response){
		try{
			sqlactionstr = parseText(sqlactionstr,testPath,reqParas,null,null);
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			SqlEntity e = mapper.readValue(sqlactionstr, SqlEntity.class);
			String source=e.getSource();
			String server=e.getServer();
			String port=e.getPort();
			String username=e.getUsername();
			String password=e.getPassword();
			String database=e.getDatabase();
			String sql=e.getSql();
			if(actionType.equalsIgnoreCase(ActionType.teardown)){
				sql=processOutputParameter(testPath, response, sql);
			}
			return new JdbcUtils(source, server, port, username, password, database).executeSqlAction(sql);
		}catch(Exception ex){
			return 0;
		}
	}
	
	private void executeMixAction(String testPath, String action, Map reqParas,String response,
			Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		String filename= action.equalsIgnoreCase(ActionType.setup) ? FileNameUtils.getSetupActionPath(testPath) :
			FileNameUtils.getTeardownActionPath(testPath);
		File f=new File(filename);
		if(f.exists()){
			try {
				String settings = FileUtils.readFileToString(f, "UTF-8");
				settings = parseText(settings,testPath,reqParas,global_reference_in,global_reference_out);
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				MixActionSettingContainer c = mapper.readValue(f, MixActionSettingContainer.class);
				for(Entry<String,MixActionSettingInfo> entry : c.getMixActionSettings().entrySet()){
					MixActionSettingInfo info=entry.getValue();
					String setting=info.getSetting();
					String type=info.getType();
					if(type.equalsIgnoreCase("service")){
						if(new File(setting).exists()){
							batchTestService.executeTestByPathWithoutCheckpoint(setting,global_reference_in,global_reference_out);
						}
					}else if(type.equalsIgnoreCase("sql")){
						executeSqlActionFromJson(testPath,action,setting,reqParas,response);
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public Set<CheckPointItem> getCheckpointsAndResultFromFile(String foldername,Map parameters, String responseinfo, TestResultItem testresult){
		try{
			File checkpoint=new File(FileNameUtils.getCheckPointsFilePath(foldername));
			if(checkpoint.exists()){
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				String ckstr = FileUtils.readFileToString(checkpoint, "UTF-8");
				ckstr=parseText(ckstr,foldername,parameters,null,null);
				CheckPointContianer c = mapper.readValue(ckstr, CheckPointContianer.class);
				testresult.setResult(TestStatus.pass);
				if(c.getCheckPoint().entrySet().size()==0){
					testresult.setResult(TestStatus.invalid);
					return testresult.getCheckPoint();
				}
				//String responsebody=responseinfo.substring(responseinfo.indexOf("[body]:")+1);
				for(Entry<String,CheckPointItem> entry:c.getCheckPoint().entrySet()){
					CheckPointItem item = new CheckPointItem();
					item=entry.getValue();
					String checktype=item.getType();
					String checkInfo=item.getCheckInfo();
					item.setCheckInfo(checkInfo);
					if(checktype.equals(CheckPointType.CheckSql)){
						checkInfo=processOutputParameter(foldername, responseinfo, checkInfo);
						addCheckPointItemsForDBVerification(testresult,checkInfo,responseinfo);
					}else if(checktype.equals(CheckPointType.CheckJsExp)){
						String[] arr=checkInfo.split(SeperatorDefinition.checkInfoSeperator);
						String objtext=responseinfo.replaceAll("\\n","").replaceAll("\\r","");
						objtext=StringUtils.substringAfter(objtext, arr[0]);
						if(!arr[1].isEmpty()){
							objtext=StringUtils.substringBefore(objtext, arr[1]);
						}
						addCheckPointItemsForJsExpVerification(testresult,arr[2].split("`"),objtext.trim());
					}else{
						boolean r=false;
						if(checktype.equals(CheckPointType.CheckContain)){
							r = responseinfo.contains(checkInfo);
						}else if(checktype.equals(CheckPointType.CheckRegExp)){
							try{
								r = responseinfo.replaceAll("\\n","").replaceAll("\\r","").matches(checkInfo);
							}catch(Exception e){
								r=false;
								item.setCheckInfo(checkInfo+"\n"+"正则表达式异常："+e.getMessage());
							}
						}
						if(r){
							item.setResult(CheckPointResult.pass);
						}else{
							item.setResult(CheckPointResult.fail);
							if(testresult.getResult().equalsIgnoreCase(CheckPointResult.pass))
								testresult.setResult(TestStatus.fail);
						}
						testresult.getCheckPoint().add(item);
					}
				}
			}
			else
				testresult.setResult(TestStatus.invalid);
		}catch(Exception e){
			logger.error("test execute error",e);
		}
		return testresult.getCheckPoint();
	}
	
	private void addCheckPointItemsForDBVerification(TestResultItem testresult, String setting, String response){
		String[] arr=setting.split(SeperatorDefinition.checkInfoSeperator);
		if(arr.length==8){
			String source=arr[0];
			String server=arr[1];
			String port=arr[2];
			String username=arr[3];
			String password=arr[4];
			String db=arr[5];
			String sql=arr[6];
			String data=arr[7];
			SqlQueryReturn sqr= new JdbcUtils(source,server,port,username,password,db).getReturnedColumnsAndRows(sql);
			for(String item : data.split(SeperatorDefinition.verifiedDataRow)){
				String[] a=item.split(SeperatorDefinition.verifiedDataItem);
				String column=a[0];
				String rowIndex=a[1];
				String comparedType=a[2];
				String expectedValue=a[3].trim();
				String actualValue=new JdbcUtils(source,server,port,username,password,db).getValueByColumnAndRowIndex(sqr,column,rowIndex);
				actualValue=actualValue.trim();
				boolean res=false;
				if(comparedType.equalsIgnoreCase("equal")){
					res=expectedValue.equalsIgnoreCase(actualValue);
				}else if(comparedType.equalsIgnoreCase("contain")){
					res=expectedValue.contains(actualValue);
				}else if(comparedType.equalsIgnoreCase("regExp")){
					res=actualValue.matches(expectedValue);
				}else if(comparedType.equalsIgnoreCase("equalFromResponse")){
					String[] str = expectedValue.split(SeperatorDefinition.shrinkResponseSeperator);
					expectedValue=getParaValueFromResponse(response,str[0],str[1],Integer.parseInt(str[2]));
					res=expectedValue.equalsIgnoreCase(actualValue);
				}
				CheckPointItem cp=new CheckPointItem();
				cp.setName("Verify Column: "+column+" in DB: "+db);
				cp.setType("sql "+comparedType);
				cp.setCheckInfo("Expected: "+expectedValue+"; Actual: "+actualValue);
				cp.setResult(res ? CheckPointResult.pass : CheckPointResult.fail);
				testresult.getCheckPoint().add(cp);
				if(testresult.getResult().equalsIgnoreCase(CheckPointResult.pass)){
					if(!res)
						testresult.setResult(CheckPointResult.fail);
				}
			}
		}
	}
	
	//需要modejs支持
	private void addCheckPointItemsForJsExpVerification(TestResultItem testresult, String[] exps, String objtext){
		String objDef="";
		String res="";
		if(!objtext.isEmpty()){
			if(objtext.indexOf("{")>-1 & objtext.indexOf("{")<objtext.indexOf("}")){
				objDef="var obj=JSON.parse('"+objtext.replace(" ", "").replace("'", "\"")+"');";
			}
			//环境安装：xmldom npm包
			else if(objtext.indexOf("<")>-1 & objtext.indexOf("<")<objtext.indexOf(">")){
				objDef="var DOMParser = require('"+NODE_MODULES+"/xmldom').DOMParser;var obj=new DOMParser().parseFromString('"+objtext.replace("'", "\"")+"','text/xml');";
			}
			for(int i=0;i<exps.length;i++){
				objDef+="console.info("+exps[i]+");";
			}
			
			String filename=new Date().getTime()+".js";
			File f=new File(filename);
			try{
				f.createNewFile();
				FileUtils.writeStringToFile(f, objDef);
				Runtime runtime = Runtime.getRuntime();
				Process p = runtime.exec(NODE_PATH+" "+f.getAbsolutePath());
				InputStream err = p.getErrorStream();
				InputStream is = p.getInputStream();
				p.getOutputStream().close(); 
				res = IOUtils.toString(err,"gbk");
				res += IOUtils.toString(is,"gbk");	
				res = StringUtils.substringBeforeLast(res,"\\n");
				int exitVal = p.waitFor();
			}catch(Exception e){
				res=e.getMessage();
			}finally{
				if(f.exists())
					f.delete();
			}
		}
		res = res!=null ? res:"";
		String[] result=res.split("\n");
		for(int i=0;i<exps.length;i++){
			CheckPointItem cp=new CheckPointItem();
			cp.setName("Verify content by js expression "+(i+1));
			cp.setType("jsExp");
			cp.setCheckInfo(exps[i]);
			boolean r=Boolean.parseBoolean(result.length==exps.length ? result[i] : "false");
			cp.setResult(r ? CheckPointResult.pass : CheckPointResult.fail);
			testresult.getCheckPoint().add(cp);
			if(testresult.getResult().equalsIgnoreCase(CheckPointResult.pass)){
				if(!r)
					testresult.setResult(CheckPointResult.fail);
			}
		}
		
	}
	
	
	private String getParameterValueAfterRequest(String extraInfo){
		String[] parainfo=extraInfo.split(SeperatorDefinition.paraForReferencedService);
		String path=parainfo[0];
		String lb=parainfo[1];
		String rb=parainfo[2];
		String res = getTestResponseBody(path,null,null).getObj().toString();
		return getParaValueFromResponse(res,lb,rb,1);
	}
		
	private String getParaValueFromResponse(String response,String lb,String rb,int times){
		String[] arr= StringUtils.substringsBetween(response, lb, rb);
		String res="";
		if(arr!=null){
			res=arr.length>=times ? arr[times-1] : "";
		}
		return res;
	}
	
	//bacuse the function could be used in parsing request parameters,it doesn't include parsing output parameter
	private String parseText(String val,String path,Map<String,Object> request,
			Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		try {
			if(val.contains("[[") && val.contains("]]"))
				val=processEnv(loadEnv(path),val);
			//if defaultvalue is returned function of Auto class.
			val = parseOtherServiceReqParameter(val,global_reference_in,global_reference_out);
			val = parseOtherServiceOutParameter(val,global_reference_out);
			val = TemplateUtils.getString(val, request);
			return val;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			return val;
		}
	}
	
	//for back-end test execution usage
	public Map<String,Object> getRequestParameterMap(String path,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		Map<String,Object> request=new HashMap<String,Object>();
		try {
			request.put("auto", new Auto());
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String targetjson="";
			Map<String, Parameter> paras = new HashMap<String, Parameter>();
			if(path.endsWith("-c")){
				targetjson=FileNameUtils.getHttpTarget(path);
				HttpTarget target = mapper.readValue(new File(targetjson), HttpTarget.class);
				paras=target.getParameters();
			}
			for(Parameter p : paras.values()){
				String name=p.getName();
				String val=p.getDefaultValue();
				if(global_reference_in.containsKey(path)){
					Map<String,String> reqparas=global_reference_in.get(path);
					if(reqparas.containsKey(name)){
						val=reqparas.get(name);
						request.put(name, val);
						continue;
					}
				}
				val=parseText(val,path,request,global_reference_in,global_reference_out);
				request.put(name, val);
			}
			request=getParametersFromPreConfigFile(path,request,global_reference_in,global_reference_out);
		}catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().toString()+": "+e.getMessage());
		}
		return request;
	}
	
	//for submit form usage
	private Map<String,Object> getRequestParameterMap(String reqbody,String path,
			Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		Map<String,Object> requestmap =new HashMap<String,Object>();
		try{
			Map<String,String> map=HttpServletRequestUtils.getParameterMapFromFormData(reqbody); 
	        requestmap.put("auto", new Auto());
	        for(Entry entry: map.entrySet()){ 
	        	String parakey = entry.getKey().toString();
	        	String paravalue = entry.getValue().toString();
	        	paravalue=parseText(paravalue,path,requestmap,global_reference_in,global_reference_out);
	        	requestmap.put(parakey, paravalue);
	        }
	        requestmap=getParametersFromPreConfigFile(path,requestmap,global_reference_in,global_reference_out);
		}catch(Exception e){
			logger.error(e.getClass().toString()+": "+e.getMessage());
		}
		return requestmap;
	}
		
	public String parseOtherServiceReqParameter(String val,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		if(StringUtils.contains(val, ApiKeyword.preapi+"(")){
			String path=StringUtils.substringBetween(val, ApiKeyword.preapi+"(", ")");
			String name=StringUtils.substringAfter(val, path+")").trim();
			if(global_reference_in.containsKey(path)){
				Map<String,String> reqparas=global_reference_in.get(path);
				if(reqparas.containsKey(name)){
					return reqparas.get(name);
				}
			}
			Map<String,Object> paras = getRequestParameterMap(path.trim(),global_reference_in,global_reference_out);
			for(Entry<String,Object> en : paras.entrySet()){
				if(en.getKey().equalsIgnoreCase(name)){
					val=en.getValue().toString();
					Map<String,String> reqparas=global_reference_in.containsKey(path) ? global_reference_in.get(path) : new HashMap<String,String>();
					reqparas.put(name, val);
					global_reference_in.put(path, reqparas);
					break;
				}
			}
		}
		return val;
	}
	
	public String parseOtherServiceOutParameter(String val,Map<String,Map<String,String>> global_reference_out){
		if(StringUtils.contains(val, ApiKeyword.outvar+"(")){
			String path=StringUtils.substringBetween(val, ApiKeyword.outvar+"(", ")");
			String outpara=StringUtils.substringAfter(val, path+")").trim();
			if(global_reference_out.containsKey(path)){
				Map<String,String> resparas=global_reference_out.get(outpara);
				if(resparas.containsKey(outpara)){
					return resparas.get(outpara);
				}
			}
			val = processOutputParameter(path, "{{"+outpara+"}}");
			Map<String,String> outparas=global_reference_out.containsKey(path) ? global_reference_out.get(path) : new HashMap<String,String>();
			outparas.put(outpara, val);
			global_reference_out.put(path, outparas);
		}
		return val;
	}
	
	private Map<String,Object> getParametersFromPreConfigFile(String testPath,Map<String,Object> request,
			Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		Map<String,Object> para=new HashMap<String,Object>();
		para.putAll(request);
		try {
			File f=new File(FileNameUtils.getPreConfigFilePath(testPath));	
			if(f.exists()){
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				PreConfigContainer c = mapper.readValue(f, PreConfigContainer.class);
				//默认前置service数据绑定设置中不引用输入/输出参数
				for(Entry<String,PreConfigItem> entry:c.getPreConfig().entrySet()){
					String type=entry.getValue().getType();
					if(type.equalsIgnoreCase(PreConfigType.service)){
						String setting=entry.getValue().getSetting();
						String[] arr=setting.split(SeperatorDefinition.paraForReferencedService);
						String path=arr[0];
						String[] configs=arr[1].split(SeperatorDefinition.queryBoundRow);
						String res="";
						Map<String,String> reqparas=global_reference_in.containsKey(path) ? global_reference_in.get(path) : new HashMap<String,String>();
						for(String item : configs){
							String[] info=item.split(SeperatorDefinition.queryBoundItem);
							if(global_reference_in.containsKey(path)){
								if(reqparas.containsKey(info[0])){
									para.put(info[0], reqparas.get(info[0]));
									continue;
								}
							}
							String lb=info[1];
							String rb=info[2];
							int times=Integer.parseInt(info[3]);
							if(res.isEmpty()){
								res = getTestResponseBody(path,global_reference_in,global_reference_out).getObj().toString();
							}
							String value=getParaValueFromResponse(res,lb,rb,times);
							para.put(info[0], value);
							reqparas.put(info[0], value);
						}
						if(!reqparas.containsKey(path)){
							global_reference_in.put(path, reqparas);
						}
					}
				}
				String preconfigstr = FileUtils.readFileToString(f, "UTF-8");
				preconfigstr=parseText(preconfigstr,testPath,para,global_reference_in,global_reference_out);
				c = mapper.readValue(preconfigstr, PreConfigContainer.class);
				for(Entry<String,PreConfigItem> entry:c.getPreConfig().entrySet()){
					String type=entry.getValue().getType();
					if(type.equalsIgnoreCase(PreConfigType.query)){
						String setting=entry.getValue().getSetting();
						String[] arr=setting.split(SeperatorDefinition.paraForReferencedService);
						String datasource=arr[0];
						String server=arr[1];
						String port=arr[2];
						String username=arr[3];
						String password=arr[4];
						String db=arr[5];
						String sql=arr[6];
						String[] configs=arr[7].split(SeperatorDefinition.queryBoundRow);
						SqlQueryReturn sqr= new JdbcUtils(datasource,server,port,username,password,db).getReturnedColumnsAndRows(sql);
						for(String item : configs){
							String[] info=item.split(SeperatorDefinition.queryBoundItem);
							String columnLabel=info[1];
							String rowIndex=info[2];
							String value=new JdbcUtils(datasource,server,port,username,password,db).getValueByColumnAndRowIndex(sqr,columnLabel,rowIndex);
							para.put(info[0], value);	
						}
					}
				}
			}
		}catch (IOException e) {
			logger.error(e.getClass()+e.getMessage());
		}
		return para;
	}
	
	
	public void generateHistoryFile(String foldername, TestResultItem testresult) {
		try{
			String folder = foldername + "/"+HistoryFolderName.folderName;
			String filename = FileNameUtils.getResultFile(testresult.getTime(), testresult.getDuration(),testresult.getResult());
			File dir=new File(folder);
			if(!dir.exists()){
				dir.mkdirs();
			}
			File file=new File(dir,filename);
			if(!file.exists()){
				file.createNewFile();
			}
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			mapper.writeValue(file, testresult);
		} catch (JsonGenerationException e) {
			logger.error("生成历史记录文件失败", e);
		} catch (JsonMappingException e) {
			logger.error("生成历史记录文件失败", e);
		} catch (IOException e) {
			logger.error("生成历史记录文件失败", e);
		}
	}
	
	private int executeHttpServiceRequest(String path, Map request){
		int reponsestatus=0;
		try{
			Map<String,String> evnmap=loadEnv(path);
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String httptargetjson=FileNameUtils.getHttpTarget(path);
			HttpTarget target = mapper.readValue(new File(httptargetjson), HttpTarget.class);
			String url=processEnv(evnmap,target.getPath());
			url=TemplateUtils.getString(url, request);
			HTTPFacade hf=new HTTPFacade();
			hf.setRequesttimeout(600*1000);
			hf.setUrl(url);
			String body=processEnv(evnmap,target.getRequestBody());
			body=TemplateUtils.getString(body, request);
			Set<KeyValue> headset=target.getHeads();
			for(KeyValue kv:headset){
				hf.addHeaderValue(kv.getKey(), kv.getValue());
			}
			if(body==null || body.trim().equals("")){
				hf.get();
			}else{
				for(Object e : request.entrySet()){
					Object v=((Entry<String,String>)e).getValue();
					if(v instanceof String){
						String k=((Entry<String,String>)e).getKey();
						hf.addParamValue(k, v.toString());
					}
				}
				hf.addRequestBody(body);
				hf.postWithQueryStrInUrl();
			}
			reponsestatus= hf.getStatus();
		}catch(Exception e){
			logger.error(e.getClass()+e.getMessage());
		}
		return reponsestatus;
	}
	
	private void executeServiceRequest(String path, Map request){
		if(path.endsWith("-c")){
			executeHttpServiceRequest(path,request);
		}
	}
	
	public TestResultItem getTestResultItem(String folderName, Map request){
		TestResultItem testresult=new TestResultItem();
		if(folderName.endsWith("-c")){
			testresult=getHttpTestResultItem(folderName,request);
		}
		return testresult;
	}
	
	public Json getTestResponseBody(String path,Map<String,Map<String,String>> global_reference_in,Map<String,Map<String,String>> global_reference_out){
		Json j=new Json();
		Map params=new HashMap();
		String res="";
		try{
			params = getRequestParameterMap(path,global_reference_in,global_reference_out);
			setupAction(path,params,global_reference_in,global_reference_out);
			TestResultItem tri = getTestResultItem(path,params);
			if(!tri.getResult().equals(TestStatus.exception)){
				j.setSuccess(true);
				j.setObj(tri.getResponseInfo());
				res=tri.getResponseInfo();
			}else{
				j.setSuccess(false);
				j.setMsg(tri.getComment());
				res=tri.getComment();
			}
		}catch(Exception ex){
			j.setSuccess(false);
			j.setMsg(ex.getMessage());
			logger.error(ex);
		}finally{
			teardownAction(path,params,res,global_reference_in,global_reference_out);
		}
		return j;
	}
	
	public String parseDataSign(String body){
		String encodedString=body.replace("=", "");
		if(encodedString.contains(SIGN)){
			if(encodedString.contains(SIGN+"&")){
				encodedString=encodedString.replace(FIELD2BEREPLACED+SIGN+"&", "");
			}else{
				encodedString=encodedString.replace("&"+FIELD2BEREPLACED+SIGN, "");
			}
			String[] arr=encodedString.split("&");
			Arrays.sort(arr);
			String newbody="";
			for(int i=0;i<arr.length;i++){
				newbody+=arr[i];
			}
			newbody+=SECRETKEY;
			String md5=getMd5Code(newbody);
			return body.replace(SIGN, md5);
		}else{
			return body;
		}
	}
	
	private String getMd5Code(String encodedString){
		MessageDigest md=null; 
        try { 
            md=MessageDigest.getInstance("md5"); 
        } catch (NoSuchAlgorithmException e) { 
            e.printStackTrace(); 
        }
        md.reset();
        md.update(encodedString.getBytes()); 
        byte[] encodedPassword=md.digest(); 
        StringBuffer sb=new StringBuffer(); 
        for(int i=0;i<encodedPassword.length;i++){ 
        	if ((encodedPassword[i] & 0xff) < 0x10) {
				sb.append("0");
			}
			sb.append(Long.toString(encodedPassword[i] & 0xff, 16));
        } 
        return sb.toString();
	}
	
	private String urlEncodeValueForKVReqBody(String body){
		String ret="";
		for(String kv : body.split("&")){
			String value=StringUtils.substringAfter(kv, "=");
			String encoded=value;
			try {
				if(encoded.contains("\"") || encoded.contains("'"))
					encoded=URLEncoder.encode(value, "utf-8");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
			}
			ret+=kv.replace(value, encoded)+"&";
		}
		return ret.substring(0, ret.length()-1);
	}
	
	private TestResultItem getHttpTestResultItem(String path, Map request){
		TestResultItem testresult=new TestResultItem();
		try{	
			String requestinfo="";
			String resopnseinfo="";
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String httptargetjson=FileNameUtils.getHttpTarget(path);
			HttpTarget target = mapper.readValue(new File(httptargetjson), HttpTarget.class);
			String url=retrieveString(target.getPath(),path, request).trim();
			String body=retrieveString(target.getRequestBody(),path, request);
			body=parseDataSign(body);
			if(body.contains("&") && body.contains("=")){
				body=urlEncodeValueForKVReqBody(body);
			}
//			String data=StringUtils.substringBetween(body, "data=", "}");
//			String d=URLEncoder.encode(data+"}", "utf-8");
//			body=body.replace(data+"}", d);
			boolean ishttps=url.startsWith("https") ? true : false;
			HTTPFacade hf=new HTTPFacade(ishttps);
			hf.setRequesttimeout(600*1000);
			hf.setUrl(url);
			
			requestinfo="[url]:\n"+url+"\n[request headers]:\n";	
			Set<KeyValue> headset=target.getHeads();
			for(KeyValue kv:headset){
				String k=retrieveString(kv.getKey(),path, request);
				String v=retrieveString(kv.getValue(),path, request);
				hf.addHeaderValue(k, v);
				requestinfo+=k + ":"+v+"\n";
			}
			requestinfo+="[request body]:\n"+URLDecoder.decode(body,"utf-8");
			
			String method=target.getMethod();
			long start = System.currentTimeMillis();
			if(body==null || body.trim().equals("")){
				if(null==method || method.isEmpty() || method.equals("default")){
					hf.get();
				}else if(method.equals("PUT")){
					hf.put();
				}else if(method.equals("DELETE")){
					hf.delete();
				}
				
			}else{
				//add form parameters to url params
//				for(Object e : request.entrySet()){
//					Object v=((Entry<String,String>)e).getValue();
//					if(v instanceof String){
//						String k=((Entry<String,String>)e).getKey();
//						hf.addParamValue(k, v.toString());
//					}
//				}
				hf.addRequestBody(body);
				if(null==method || method.isEmpty() || method.equals("default")){
					hf.postWithQueryStrInUrl();
				}else if(method.equals("PUT")){
					hf.putWithQueryStrInUrl();
				}else if(method.equals("DELETE")){
					hf.deleteWithQueryStrInUrl();
				}
			}
			long end = System.currentTimeMillis();
			long duration = end - start;
			testresult.setDuration(String.valueOf(duration));
			
			String responsebody=hf.getResponseBody();
			int responsestatus=hf.getStatus();
			String responseheader="";
			if(!responsebody.isEmpty()){
				responseheader=hf.getResponseheaders();
			}
			logger.info("REQUEST finish with status:"+responsestatus+"\nresponse body:"+responsebody+"\n reponse heads:"+responseheader);
			resopnseinfo="[status]:\n" + responsestatus + "\n" ;
			resopnseinfo+="[response headers]:\n" + responseheader + "\n" ;
			resopnseinfo+="[body]:\n" + responsebody;
			if(responsestatus!=0){
				requestinfo=requestinfo.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&apos;", "'").replaceAll("&quot;","\"").replaceAll("&amp;", "&");
				resopnseinfo=resopnseinfo.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&apos;", "'").replaceAll("&quot;","\"").replaceAll("&amp;", "&");
				testresult.setRequestInfo(requestinfo);
				testresult.setResponseInfo(resopnseinfo);
			}else{
				testresult.setResult(TestStatus.exception);
				testresult.setComment("communication failure! response status:"+responsestatus);
			}
		}catch(Exception e){
			testresult.setResult(TestStatus.exception);
			testresult.setComment(e.getClass().toString()+": "+e.getMessage());
		}
		return testresult;
	}
	
	private String retrieveString(String content,String folderName, Map request){
		try {
			Map<String,String> evnmap=loadEnv(folderName);
			content=processEnv(evnmap,content);
			content=TemplateUtils.getString(content, request);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return content;
	}
	
	public Map<String,String> loadEnv(String testPath){
		Map<String,String> m=new HashMap<String,String>();
		File f=new File(FileNameUtils.getEnvFilePath(testPath));
		while(true){
			if(f.exists()){
				try {
					String fs=FileUtils.readFileToString(f);
					if(!fs.isEmpty()){
						String[] arr=fs.split("\n");
						for(String s:arr){
							String[] kv=s.split("=");
							String k=kv[0].trim();
							if(!m.containsKey(k)){
								if(kv.length==2){
									m.put(k, kv[1].trim());
								}else if(kv.length==1){
									m.put(k, "");
								}else if(kv.length>2){
									m.put(k,StringUtils.substringAfter(s, "=").trim());
								}
							}
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			String parentFileName=f.getParentFile().getName();
			if(StringUtils.substringAfterLast(parentFileName, "-").length()!=1){
				break;
			}else
				f=new File(FileNameUtils.getEnvFilePath(f.getParentFile().getParent()));
		}
		return m;
	}
	
	public String processEnv(Map<String,String> m,String content){
		String result=content;
		if(content.contains("[[") && content.contains("]]")){
			for(Entry<String, String> e:m.entrySet()){
				result=result.replace("[["+e.getKey()+"]]", e.getValue());
			}
		}
		return result;
	}
	
	public String processVariableInEnv(Map<String,String> m,String variable){
		variable=variable.replace("[[", "").replace("]]", "");
		return m.get(variable);
	}
	
	private List<ServiceBoundDataItem> loadOutputParameter(String testPath){
		return outputParameterService.getOutputParameterDataItems(testPath).getRows();
	}
	
	public String processOutputParameter(String testPath,String responseInfo,String content){
		int pos1=content.indexOf("{{");
		int pos2=content.indexOf("}}");
		while(pos1>=0 && pos1<pos2){
			List<ServiceBoundDataItem> parameters = loadOutputParameter(testPath);
			String name=content.substring(pos1+2, pos2);
			String value="";
			for(ServiceBoundDataItem p : parameters){
				if(name.equalsIgnoreCase(p.getName())){
					String lb=p.getLb();
					String rb=p.getRb();
					String times=p.getTimes();
					value=getParaValueFromResponse(responseInfo,lb,rb,Integer.parseInt(times));
					break;
				}
			}
			content=content.replace("{{"+name+"}}", value);
			pos1=content.indexOf("{{");
			pos2=content.indexOf("}}");
		}
		return content;
	}
	
	public String processOutputParameter(String testPath,String content){
		int pos1=content.indexOf("{{");
		int pos2=content.indexOf("}}");
		String responseinfo="";
		if(pos2>pos1 && pos1>=0){
			Json j=getTestResponseBody(testPath,null,null);
			if(j.isSuccess()){
				responseinfo=j.getObj().toString();
			}
		}
		while(pos1>=0 && pos1<pos2){
			List<ServiceBoundDataItem> parameters = loadOutputParameter(testPath);
			String name=content.substring(pos1+2, pos2);
			String value="";
			for(ServiceBoundDataItem p : parameters){
				if(name.equalsIgnoreCase(p.getName())){
					String lb=p.getLb();
					String rb=p.getRb();
					String times=p.getTimes();
					if(!responseinfo.isEmpty())
						value=getParaValueFromResponse(responseinfo,lb,rb,Integer.parseInt(times));
					break;
				}
			}
			content=content.replace("{{"+name+"}}", value);
			pos1=content.indexOf("{{");
			pos2=content.indexOf("}}");
		}
		return content;
	}
	
	public static void main(String args[]){
		String body="timestamp=1446025321000&v=1.0&channelCode=TAOBAO&appVersion=2.9.101&data={\"cityCode\":\"\",\"page\":0}&api=ykse.film.getHotFilms&lang=zh-cn";
		String encoded=body.replace("=", "");
		System.out.println(encoded);
//		String key="";
//		StringUtils.substringAfter("qqwww123","q");
//		String exp="JSON.parse(\"{\\\\\"id\\\\\":1}\").id==1";
//		String filename=new Date().getTime()+".js";
//		File f=new File(filename);
//		try{
//			f.createNewFile();
//			FileUtils.writeStringToFile(f, "console.log(eval(\""+exp.replace("\"", "\\\"")+"\"))");
//			Runtime runtime = Runtime.getRuntime();
//			Process p = runtime.exec("cmd /k node "+f.getAbsolutePath());
//			InputStream is = p.getInputStream();
//			OutputStream os = p.getOutputStream();
//			os.close();
//			key = IOUtils.toString(is,"gbk");
//			key=StringUtils.substringBetween(key, "", "\n\r");
//		}catch(Exception e){
//			key=e.getMessage();
//			
//		}finally{
//			if(f.exists())
//				f.delete();
//		}
//		System.out.println(key);
		
		
		
	}
	
	
}
