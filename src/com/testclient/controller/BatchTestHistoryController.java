package com.alipics.testassets.testclient.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.util.HSSFColor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alipics.testassets.testclient.httpmodel.BatchTestReport;
import com.alipics.testassets.testclient.httpmodel.DataGridJson;
import com.alipics.testassets.testclient.httpmodel.Json;
import com.alipics.testassets.testclient.service.BatchTestHistoryService;


@Controller
public class BatchTestHistoryController {
	private static final Logger logger = Logger.getLogger(BatchTestHistoryController.class);
	
	@Autowired
	BatchTestHistoryService batchTestHistoryService;
	
	@RequestMapping(value="/getBatchHistories")
	@ResponseBody
	public DataGridJson getBatchHistories(@RequestParam String folderName) {
		return batchTestHistoryService.getBatchHistories(folderName);
	}
	
	@RequestMapping(value="/getTestRunInfo" )
	@ResponseBody
	public DataGridJson getTestRunInfo(@RequestParam String batchRunPath) {
		return batchTestHistoryService.getTestRunInfo(batchRunPath);
	}
	
	@RequestMapping(value="/getTestResultDetailInfo" )
	@ResponseBody
	public Json getTestResultDetailInfo(@RequestParam String testRunPath) {
		return batchTestHistoryService.getTestResultDetailInfo(testRunPath);
	}
	
	@RequestMapping(value="/deleteBatchHistory" )
	@ResponseBody
	public Json deleteBatchHistory(@RequestParam String batchRunPath) {
		return batchTestHistoryService.deleteBatchHistory(batchRunPath);
	}
	
	@RequestMapping(value="/deleteTestResultDetailInfoInBatchHistory" )
	@ResponseBody
	public void deleteTestResultDetailInfoInBatchHistory(@RequestParam String testRunPath) {
		File f=new File(testRunPath);
		if(f.exists())
			f.delete();
	}
	
	@RequestMapping(value="/exportBatchTestToExcel" )
	@ResponseBody
	public void exportBatchTestToExcel(HttpServletRequest request, HttpServletResponse response,@RequestParam String testPath,@RequestParam String batchTest) {
		String filename = "ApiTestReport_"+new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date())+".xls";
		//创建一个新的Excel  
		HSSFWorkbook workBook = new HSSFWorkbook();
		//创建sheet页  
		HSSFSheet sheet = workBook.createSheet("report");  
		// 设置列宽    
		sheet.setColumnWidth(0, 256*35);    
		sheet.setColumnWidth(1, 256*25);    
		sheet.setColumnWidth(2, 256*15);
		sheet.setColumnWidth(3, 256*10); 
		sheet.setColumnWidth(4, 256*60);    
		sheet.setColumnWidth(5, 256*100);
		sheet.setColumnWidth(6, 256*80);

		// 创建标题行 
		HSSFRow row0 = sheet.createRow(0);    
		// 设置行高    
		row0.setHeight((short) 400);
		String columns="API;测试时间;接口耗时（ms）;结果;请求内容;响应内容;检查点";int i=0;
		for(String column : columns.split(";")){
			HSSFCell cell = row0.createCell(i++);  
			cell.setCellValue(new HSSFRichTextString(column));   
		}
		testPath+=testPath.endsWith("/")? "BatchTesting/" : "/BatchTesting/"+batchTest;
		List<BatchTestReport> list=batchTestHistoryService.getBatchTestReport(testPath);
		i=0;
		for(BatchTestReport r : list){
			String result=r.getResult();
			HSSFRow row = sheet.createRow(++i);
			row.setHeight((short) 2500);
			HSSFCell cell = row.createCell(0);  
			cell.setCellValue(new HSSFRichTextString(r.getName()));   
			cell = row.createCell(1);  
			cell.setCellValue(new HSSFRichTextString(r.getTime()));   
			cell = row.createCell(2);  
			cell.setCellValue(new HSSFRichTextString(r.getDuration()));   
			cell = row.createCell(3);  
			cell.setCellValue(new HSSFRichTextString(result));   
			cell = row.createCell(4);  
			cell.setCellValue(new HSSFRichTextString(r.getRequest()));   
			cell = row.createCell(5);  
			String res=r.getResponse();
			if(res.length()>32766){
				res=res.substring(0, 32767);
			}
			cell.setCellValue(new HSSFRichTextString(res)); 
			cell = row.createCell(6);  
			String cp=r.getCheckpoint();
			if(cp.length()>32766){
				cp=cp.substring(0, 32767);
			}
			cell.setCellValue(new HSSFRichTextString(cp)); 
		}
		ServletOutputStream out=null;
		try {
			response.reset();
			response.setContentType("application/vnd.ms-excel");
			response.setHeader("Content-Disposition", "attachment;filename=" + filename);  
			response.setCharacterEncoding("utf-8");
			out = response.getOutputStream();
			workBook.write(out);
			workBook.close();
			response.flushBuffer();
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		  
	}
	
}
