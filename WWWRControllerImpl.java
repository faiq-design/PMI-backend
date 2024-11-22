package com.marksman.census.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

import com.marksman.census.client.bo.Activity;
import com.marksman.census.client.bo.MarginCalculator;
import com.marksman.census.client.bo.ShopRoute;
import com.marksman.census.client.bo.SummaryTag;
import com.marksman.census.constants.SurveyorType;
import com.marksman.census.constants.SysConstants;
import com.marksman.census.service.ClientLoggingServiceImpl;
import com.marksman.census.service.ValidationService;
import com.marksman.census.service.WWWRServiceImpl;
import com.marksman.census.util.StringUtils;

@Controller
public class WWWRControllerImpl implements WWWRController {

	protected Logger logger = Logger.getLogger(this.getClass());

	@Autowired
	private ClientLoggingServiceImpl clientLoggingService;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private WWWRServiceImpl wwwrServiceImpl;

	@Override
	public @ResponseBody
	Map<String, Object> login(String imei, String mCode, String password,
			String surveyorType, HttpServletRequest request,
			HttpServletResponse response) {

		Map<String, Object> jsonMap = null;
		logger.info("login request against imei : " + imei + ", m code : "
				+ mCode + ", surveyorType:" + surveyorType);
		clientLoggingService
				.insertActivityLog(request, response, "WW WR Login");
		// String version = request.getHeader(SysConstants.VERSION);
		// if ("1.6".equalsIgnoreCase(StringUtils.extractVersion(version)))
		// {
		if (validationService.validateSurveyor(mCode, password,
				SurveyorType.valueOf(surveyorType), imei, response)) {
			// surveyorService.updateForceLoginStatus(mCode);
			jsonMap = wwwrServiceImpl.login(imei, mCode, request, response);
		}

		return jsonMap;
	}

	@Override
	@ResponseBody
	public Activity getActivity(HttpServletRequest request,
			HttpServletResponse response) {
		int id = Integer.parseInt(request.getHeader("surveyorId"));
		clientLoggingService.insertActivityLog(request, response,
				"Start WW WR Activity");
		return wwwrServiceImpl.getActivity(request, response, id);
	}

	@Override
	@ResponseBody
	public Activity endActivity(HttpServletRequest request,
			HttpServletResponse response) {
		int id = Integer.parseInt(request.getHeader("surveyorId"));
		clientLoggingService.insertActivityLog(request, response,
				"End WW WR Activity");
		return wwwrServiceImpl.endActivity(request, response, id);
	}

	@Override
	public @ResponseBody
	Map<String, Object> syncData(String version, HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		clientLoggingService.insertActivityLog(request, response, "WW WR Sync");
		Map<String, Object> jsonMap = wwwrServiceImpl.syncData(version,
				request, response);
		return jsonMap;
	}

	@Override
	@ResponseBody
	public ArrayList<SummaryTag> getSummary(HttpServletRequest request,
			HttpServletResponse respons, Integer surveyorId, String type) {

		clientLoggingService.insertActivityLog(request, respons,
				"WW WR Summary");
		return wwwrServiceImpl.getSummary(surveyorId, type);

	}

	@Override
	@ResponseBody
	public Object shopList(Integer routeId, Integer dsrId,Integer amId, HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException {
	    clientLoggingService.insertActivityLog(request, response, "WW WR Shop List");

	    String version = request.getHeader("version");
	    if (version != null && version.compareTo("3.1") > 0) {
	        return wwwrServiceImpl.shopListWithVersionCheck(routeId, dsrId,amId, response);
	    } else {
	        return wwwrServiceImpl.shopList(routeId, dsrId, response);
	    }
	}

	@Override
	@ResponseBody
	public Map<String, Object> checkUpdates(SurveyorType appType,
			HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		return wwwrServiceImpl.checkUpdates(appType, request, response);
	}
	
	@Override
	@ResponseBody
	public Map<String, Object> getActivityStatus(
			HttpServletRequest request, HttpServletResponse response,Integer surveyorId) {

		Map<String, Object> jsonMap = null;
		jsonMap = wwwrServiceImpl.activityStatus( request, response,surveyorId);

		return jsonMap;

	}
	
	@Override
	@ResponseBody
	public Map<String, Object> getEndActivityStatus(
			HttpServletRequest request, HttpServletResponse response,Integer surveyorId, Integer activityId) {

		Map<String, Object> jsonMap = null;
		jsonMap = wwwrServiceImpl.endActivityStatus( request, response,surveyorId, activityId);

		return jsonMap;

	}
	
	@Override
	@ResponseBody
	public Object getMarginCalculator(
			HttpServletRequest request, HttpServletResponse response) {

		Object jsonMap = null;
		String version = request.getHeader(SysConstants.VERSION);
		jsonMap = wwwrServiceImpl.marginCalculators( request, response, version);

		return jsonMap;

	}
	
	@Override
	@ResponseBody
	public Map<String, Object> syncCeFaq( HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		clientLoggingService.insertActivityLog(request, response,
				"SYNC-CE-FAQ");
		Map<String, Object> jsonMap = wwwrServiceImpl.syncCeFaq(
				request, response);
		return jsonMap;
	}
	
	@ResponseBody
	public Object remarkShopList( Integer amId,Integer rsmId, HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException {
	    clientLoggingService.insertActivityLog(request, response, "WW WR Shop List");

	   
	        return wwwrServiceImpl.remarkShopList( amId,rsmId, response);
	  
	}
	
	@Override
	@ResponseBody
	public Map<String, Object> syncRedFlag( HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		clientLoggingService.insertActivityLog(request, response,
				"SYNC-RED-FLAG");
		Map<String, Object> jsonMap = wwwrServiceImpl.syncRedFlag(
				request, response);
		return jsonMap;
	}

}
