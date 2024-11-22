package com.marksman.census.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.marksman.census.client.bo.Activity;
import com.marksman.census.client.bo.ShopRoute;
import com.marksman.census.client.bo.SummaryTag;
import com.marksman.census.constants.SurveyorType;

public interface WWWRService
{
	public Map<String, Object> login(String imei, String mCode, HttpServletRequest request,
			HttpServletResponse response);
	
	public Activity getActivity(HttpServletRequest request,
			HttpServletResponse response, Integer surveyorId);
	
	public Activity endActivity(HttpServletRequest request,
			HttpServletResponse response, Integer surveyorId);
	
	public Map<String, Object> syncData(String version, HttpServletRequest request,
			HttpServletResponse response);
	
	public ArrayList<SummaryTag> getSummary(Integer surveyorId, String type);
	
	public List<ShopRoute> shopList(Integer routeId, Integer dsrId, HttpServletResponse response);
	
	public Map<String, Object> checkUpdates(SurveyorType appType, HttpServletRequest request,
			HttpServletResponse response);
	
	public Map<String, Object> syncCeFaq( HttpServletRequest request,
			HttpServletResponse response);
	
	public Map<String, Object> syncRedFlag( HttpServletRequest request,
			HttpServletResponse response);
}
