package com.marksman.census.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.marksman.census.client.bo.Activity; 
import com.marksman.census.client.bo.ShopRoute;
import com.marksman.census.client.bo.SummaryTag;
import com.marksman.census.constants.SurveyorType;

public interface WWWRController {

	@RequestMapping(value = "/ww-wr/login", method = RequestMethod.POST)
	public Map<String, Object> login(String imei, String mCode,
			String password, String surveyorType, HttpServletRequest request,
			HttpServletResponse response);

	@RequestMapping(value = "/ww-wr/start-ww-wr-activity", method = RequestMethod.POST)
	public Activity getActivity(HttpServletRequest request,
			HttpServletResponse response);

	@RequestMapping(value = "/ww-wr/end-ww-wr-activity", method = RequestMethod.POST)
	public Activity endActivity(HttpServletRequest request,
			HttpServletResponse response);

	@RequestMapping(value = "/ww-wr/sync", method = RequestMethod.POST)
	public Map<String, Object> syncData(String version,
			HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException;

	@RequestMapping(value = "/ww-wr/summary", method = RequestMethod.GET)
	public ArrayList<SummaryTag> getSummary(HttpServletRequest request,
			HttpServletResponse respons, Integer surveyorId, String type);

	@RequestMapping(value = "/ww-wr/shop-list", method = RequestMethod.GET)
	public Object shopList(Integer routeId, Integer dsrId,Integer amId, HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException;

	@RequestMapping(value = "/ww-wr/chk/updates", method = RequestMethod.POST)
	public Map<String, Object> checkUpdates(SurveyorType appType,
			HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException;

	@RequestMapping(value = "/ww-wr/isActivityAllowed", method = RequestMethod.POST)
	public Map<String, Object> getActivityStatus(HttpServletRequest request,
			HttpServletResponse response,Integer surveyorId) throws ServletException, IOException;
	
	@RequestMapping(value = "/ww-wr/isEndActivityAllowed", method = RequestMethod.POST)
	public Map<String, Object> getEndActivityStatus(HttpServletRequest request,
			HttpServletResponse response,Integer surveyorId, Integer activityId) throws ServletException, IOException;
	
	@RequestMapping(value = "/app/margin-calculator", method = RequestMethod.GET)
	public Object getMarginCalculator(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException;
	
	@RequestMapping(value = "/ceFaqSync", method = RequestMethod.POST)
	public Map<String, Object> syncCeFaq( HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException;
	
	@RequestMapping(value = "/ww-wr/red-flag-shop-list", method = RequestMethod.GET)
	public Object remarkShopList( Integer amId, Integer rsmId, HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException;
	
	@RequestMapping(value = "/redFlag-chat-response", method = RequestMethod.POST)
	public Map<String, Object> syncRedFlag( HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException;
	

}
