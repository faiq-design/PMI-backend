package com.marksman.census.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.Gson;
import com.marksman.census.bo.Area;
import com.marksman.census.bo.Build;
import com.marksman.census.bo.Shop;
import com.marksman.census.bo.Surveyor;
import com.marksman.census.cache.ApplicationCacheService;
import com.marksman.census.client.bo.Activity;
import com.marksman.census.client.bo.BwuImage;
import com.marksman.census.client.bo.CheckInInfo;
import com.marksman.census.client.bo.DsrStockList;
import com.marksman.census.client.bo.FAQ;
import com.marksman.census.client.bo.MarginCalculator;
import com.marksman.census.client.bo.MeBwuImage;
import com.marksman.census.client.bo.RedFlagChatResponse;
import com.marksman.census.client.bo.ShopRoute;
import com.marksman.census.client.bo.SingleSurvey;
import com.marksman.census.client.bo.SummaryData;
import com.marksman.census.client.bo.SummaryTag;
import com.marksman.census.client.bo.SyncData;
import com.marksman.census.client.bo.SyncQuestion;
import com.marksman.census.client.bo.VisitImage;
import com.marksman.census.constants.CommonConstants;
import com.marksman.census.constants.DateTimeConstants;
import com.marksman.census.constants.SurveyorType;
import com.marksman.census.constants.SysConstants;
import com.marksman.census.crons.EmailSendingCron;
import com.marksman.census.dao.AreaDao;
import com.marksman.census.dao.BwusDao;
import com.marksman.census.dao.CitiesDao;
import com.marksman.census.dao.FamiliesDao;
import com.marksman.census.dao.IndustriesDao;
import com.marksman.census.dao.ProductsDao;
import com.marksman.census.dao.QuestionsDao;
import com.marksman.census.dao.RegionsDao;
import com.marksman.census.dao.ShopsDao;
import com.marksman.census.dao.SurveyorDao;
import com.marksman.census.dao.WWWRDao;
import com.marksman.census.logging.SyncLogging;
import com.marksman.census.message.MessageType;
import com.marksman.census.util.CommonUtil;
import com.marksman.census.util.DateTimeUtilities;
import com.marksman.census.util.FileUtils;
import com.marksman.census.util.StringUtils;

public class WWWRServiceImpl implements WWWRService {

	protected Logger logger = Logger.getLogger(this.getClass());
	private ResourceBundle bundle = ResourceBundle
			.getBundle(CommonConstants.PROPERTY_FILE_NAME);

	@Autowired
	private ApplicationCacheService applicationCacheService;

	@Autowired
	private SurveyorDao surveyorDao;
	@Autowired
	private ShopsDao shopsDao;
	@Autowired
	private AreaDao areaDao;
	@Autowired
	private CitiesDao citiesDao;
	@Autowired
	private QuestionsDao questionsDao;
	@Autowired
	private IndustriesDao industriesDao;
	@Autowired
	private FamiliesDao familiesDao;
	@Autowired
	private ProductsDao productsDao;
	@Autowired
	private BwusDao bwusDao;
	@Autowired
	private RegionsDao regionDao;
	@Autowired
	private WWWRDao wwwrDao;
	@Autowired
	private EmailSendingCron emailSendingCron;
	@Autowired
	ValidationServiceImpl validationServiceImpl;
	@Autowired
	SyncLogging syncLogging;
	@Autowired
	FileUtils fileUtils;
	@Autowired
	ServletContext servletContext;

	@Override
	public Map<String, Object> login(String imei, String mCode,
			HttpServletRequest request, HttpServletResponse response) {

		Build build = applicationCacheService.getBuildsMap().get("DE");
		String version = request.getHeader("version");
		if (version.contains("_")) {
			version = version.split("_")[0];
		}
		logger.info(" request version : " + version + ", current version : "
				+ build.getVersion() + " , WWWR Code : " + mCode + ", imei: "
				+ imei);
		if (Boolean.parseBoolean(bundle
				.getString(CommonConstants.VERSION_ALERT).trim())
				&& !build.getVersion().equalsIgnoreCase(version)) {
			response.setHeader("url", build.getDownloadUrl());
			response.setHeader("version", build.getVersion());
			CommonUtil.writeErrorMessage(
					HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED,
					MessageType.ERROR_VERSION, response);
			return null;
		} else {
			return wwwrLogin(imei, mCode, response);
		}
	}

	private Map<String, Object> wwwrLogin(String imei, String mCode,
			HttpServletResponse response) {

		Map<String, Object> jsonMap = new HashMap<String, Object>();
		Map<String, Object> data = new HashMap<String, Object>();
		try {
			Surveyor surveyor = surveyorDao.getSurveyorDetail(mCode);
			if (surveyor.getSurveyorType().equals(SurveyorType.NM)) {
				data.put("zmList", surveyorDao.getZMListByNM("ZM",surveyor.getId()));
				data.put("amList", wwwrDao.getAMList());
				data.put("rsmList", surveyorDao.getRSMList("RSM"));
			} else if (surveyor.getSurveyorType().equals(SurveyorType.ZM) ||surveyor.getSurveyorType().equals(SurveyorType.COM) || surveyor.getSurveyorType().equals(SurveyorType.FOM) ) {
				// List<Shop> shops = new ArrayList<Shop>();
				surveyor.setDsrList(wwwrDao.getDsrList(-1));
				data.put("amList", wwwrDao.getAMList());
				data.put("rsmList",
						surveyorDao.getSupervisorListPmi(surveyor.getId(), "RSM"));
				data.put("zmList", surveyorDao.getZMList("ZM"));
				// data.put("regions",
				// regionDao.getTmRegions(surveyor.getId(), -1));
				// data.put("shops", shops);
				data.put("areas", areaDao.getTmAreas(surveyor.getId()));
			} else if (surveyor.getSurveyorType().equals(SurveyorType.RSM)) {
				// List<Shop> shops = new ArrayList<Shop>();
				surveyor.setDsrList(surveyorDao.getDsrListByTMIdPMI(surveyor
						.getId()));
				data.put("amList",
						surveyorDao.getSupervisorListPmi(surveyor.getId(), "AM"));
				data.put("zmList", surveyorDao.getZMListRSM("ZM",surveyor.getId()));
				data.put("rsmList", surveyorDao.getRSMList("RSM"));
//				data.put("rsmList",
//						surveyorDao.getSupervisorList(surveyor.getId(), "RSM"));
				// data.put("regions",
				// regionDao.getTmRegions(surveyor.getId(), -1));
				// data.put("shops", shops);
				data.put("areas", areaDao.getTmAreas(surveyor.getId()));
			} else {
				surveyor.setDsrList(wwwrDao.getDsrList(surveyor.getId()));
				// data.put("shops", wwwrDao.shopList(-1,surveyor.getId()));
				data.put("areas", areaDao.getSurveyorAreas(surveyor.getId()));
				data.put(
						"wholesaleChannel",
						new ArrayList<String>(Arrays.asList(bundle
								.getString(SysConstants.WHOLE_SALE_CHANNEL)
								.trim().split(","))));
				data.put("zmList", surveyorDao.getZMListAM("ZM",surveyor.getId()));
				data.put("amList", wwwrDao.getAMList());
				data.put("rsmList", surveyorDao.getRSMList("RSM"));
			}
			data.put("surveyor", surveyor);
			data.put("visits", shopsDao.getShopVisits(surveyor.getId()));
			data.put("remarks", applicationCacheService.getRemarks());
			data.put("remarksTypes", applicationCacheService.getRemarksTypes());
			data.put("checkInInfo", surveyorDao
					.getSurveyorAttendanceAndDsrAttendance(surveyor.getId()));
			data.put("shopCategories",
					applicationCacheService.getShopsCategories());
			data.put("shopGroups", applicationCacheService.getShopsGroups());
			data.put(
					"landmarks",
					new ArrayList<String>(Arrays.asList(bundle
							.getString(SysConstants.LANDMARKS).trim()
							.split(","))));
			data.put(
					"segments",
					new ArrayList<String>(Arrays
							.asList(bundle.getString(SysConstants.SEGMENTS)
									.trim().split(","))));
			data.put(
					"profiles",
					new ArrayList<String>(Arrays.asList(bundle
							.getString(SysConstants.PROFILE).trim().split(","))));

			data.put(
					"areaType",
					new ArrayList<String>(Arrays.asList(bundle
							.getString(SysConstants.AREA_TYPE).trim()
							.split(","))));
			data.put("cities", citiesDao.getCities(surveyor.getId()));
			data.put("questions", applicationCacheService.getQuestions());
			data.put("questionTypes",
					applicationCacheService.getQuestionTypes());
			data.put("options", applicationCacheService.getOptions());
			data.put("questionOptions",
					applicationCacheService.getQuestionOptions());
			data.put("industries", applicationCacheService.getIndustries());
			data.put("families", applicationCacheService.getFamilies());
			data.put("products", applicationCacheService.getProducts());
			data.put("bwus", applicationCacheService.getBwu());
			data.put("industryBwus", applicationCacheService.getIndustryBwu());
			data.put("routes", applicationCacheService.getDsrRoutes());
			data.put("planogramList", wwwrDao.trainingMaterial());
			// last start activity sent in single survey
			if (surveyor.getSurveyorType().equals(SurveyorType.NM)) {

				data.put("singleSurvey",
						wwwrDao.getNMLastStartActivity(surveyor.getId()));
			} else {
				data.put("singleSurvey",
						wwwrDao.getSurveyorLastStartActivity(surveyor.getId()));
			}

			jsonMap.put("data", data);
		} catch (Exception ex) {

			logger.error("Exception occured while login against imei " + imei,
					ex);
			CommonUtil.writeErrorMessage(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					MessageType.ERROR_SERVER, response);
			return null;
		}
		return jsonMap;
	}

	@Override
	public Activity getActivity(HttpServletRequest request,
			HttpServletResponse response, Integer surveyorId) {

		// Map<String, Object> responMap = new HashMap<String, Object>();
		String imei = request.getHeader("imei");
		Activity createdActivity = null;
		if (validationServiceImpl.isValidImei(imei, response)) {
			try {

				boolean isMultiPart = ServletFileUpload
						.isMultipartContent(request);
				Gson gson = new Gson();
				SingleSurvey singleSurvey = null;
				if (isMultiPart) {
					ServletFileUpload upload = new ServletFileUpload();
					try {

						FileItemIterator itr = upload.getItemIterator(request);
						while (itr.hasNext()) {
							FileItemStream item = itr.next();
							String contentDisposition = item.getHeaders()
									.getHeader("Content-Disposition");
							String os = request.getHeader("os");
							logger.info("Content-Disposition:  "
									+ contentDisposition);
							logger.info("ios:  "
									+ os);
							if( os != null && os.contains("ios")){
								if (contentDisposition.contains("checkInInfo") ) {
									
									InputStream stream = item.openStream();
									String reader = Streams.asString(stream);
									logger.info("Build Version "
											+ request.getHeader("version")
											+ "\n validated Shop : " + reader);
									singleSurvey = gson.fromJson(reader,
											SingleSurvey.class);
									singleSurvey.setBuildVersion(request.getHeader(
											"version").split("_")[0]);
									singleSurvey.setImei(request.getHeader("imei"));
									syncLogging
											.saveLog(
													singleSurvey.getSurveyorId(),
													-1,
													DateTimeUtilities
															.getCurrentDate(DateTimeConstants.DATE_FORMAT3),
													reader, "start_activity_logs");
								}else{

									try {
										if (singleSurvey == null) {

											CommonUtil
													.writeErrorMessage(
															HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
															MessageType.ERROR_MULTIPART_ORDER,
															response);
											return null;
										}
										String imageName = extractImageName(contentDisposition);
//										logger.info("Image Type ::   " + imageName);
										String imageType = imageName
												.split("_")[0];
										// visitImages =
										// syncData.getVisit().getVisitImageArrayList();
										logger.info("Image Type ::   " + imageType);
										if (imageType
												.contains(CommonConstants.SIS_REMARK_IMAGE)) {
											logger.info("Saving IOS Start day image");
											String modifiedImageUrl = imageName.replace(".png", "");
											logger.info("singleSurvey:  "+singleSurvey);
											for(SyncQuestion questionData: singleSurvey.getQuestionData()) {
												if(questionData.getImage().getImageUrl().equals(modifiedImageUrl)) {
													String tempPath = this.saveQuestionImage(singleSurvey, item);
													questionData.getImage().setImageUrl(tempPath);;
													break;
												}
											}
											
										
										}
									} catch (Exception ex) {
										logger.error(ex, ex);
										CommonUtil
												.writeErrorMessage(
														HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
														MessageType.FILE_SAVING_SERVER,
														response);
										return null;
									}
								
									
								}
								
							}else{
							if (item.isFormField()) {
								InputStream stream = item.openStream();
								String reader = Streams.asString(stream);
								logger.info("Build Version "
										+ request.getHeader("version")
										+ "\n validated Shop : " + reader);
								singleSurvey = gson.fromJson(reader,
										SingleSurvey.class);
								singleSurvey.setBuildVersion(request.getHeader(
										"version").split("_")[0]);
								singleSurvey.setImei(request.getHeader("imei"));
								syncLogging
										.saveLog(
												singleSurvey.getSurveyorId(),
												-1,
												DateTimeUtilities
														.getCurrentDate(DateTimeConstants.DATE_FORMAT3),
												reader, "start_activity_logs");
							}
							else {
								try {
									if (singleSurvey == null) {

										CommonUtil
												.writeErrorMessage(
														HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
														MessageType.ERROR_MULTIPART_ORDER,
														response);
										return null;
									}
									String imageName = extractImageName(contentDisposition);
									logger.info("Image Type ::   " + imageName);
									String imageType = imageName
											.split("_")[0];
									// visitImages =
									// syncData.getVisit().getVisitImageArrayList();
									logger.info("Image Type ::   " + imageType);
									if (imageType
											.contains(CommonConstants.SIS_REMARK_IMAGE)) {
										logger.info("Saving visit shop image");
										
										for(SyncQuestion questionData: singleSurvey.getQuestionData()) {
											if(questionData.getImage().getImageUrl().contains(imageName)) {
												String tempPath = this.saveQuestionImage(singleSurvey, item);
												questionData.getImage().setImageUrl(tempPath);;
												break;
											}
										}
										
									
									}
								} catch (Exception ex) {
									logger.error(ex, ex);
									CommonUtil
											.writeErrorMessage(
													HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
													MessageType.FILE_SAVING_SERVER,
													response);
									return null;
								}
							}
						}
						}
					} catch (Exception ex) {
						logger.error(ex, ex);
						logger.error(
								"Error while inserting activity data agianst surveyor id : "
										+ singleSurvey.getSurveyorId(), ex);
						CommonUtil.writeErrorMessage(
								HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
								MessageType.ERROR_SERVER, response);
						return null;
					}

					if (singleSurvey != null) {
						createdActivity = this
								.saveSingleSurveyData(singleSurvey);
						// logger.info("Sending sync response against surveyor id "
						// + singleSurvey.getSurveyorId());
						// CommonUtil.writeErrorMessage(HttpServletResponse.SC_OK,
						// MessageType.SUCCESS, response);
					}

				} else {

					logger.error("Data is not multi part ");
					CommonUtil.writeErrorMessage(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							MessageType.ERROR_FORMAT_DATA, response);
					return null;
				}
			} catch (Exception e) {
				logger.error(e, e);

				CommonUtil.writeErrorMessage(
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						MessageType.FILE_SAVING_SERVER, response);
				return null;
			}
		} else {
			CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
					MessageType.ERROR_IMEI_PERMISSION, response);
			return null;
		}
		return createdActivity;

	}

	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	private Activity saveSingleSurveyData(SingleSurvey singleSurvey)
			throws Exception {

		logger.info("Saving single survey data into db");
		// sending active status "Y" to ensure this activity is started
		// it will be n when upon end activity request
		int activityId = wwwrDao.insertActivity(singleSurvey, "Y");
		if (activityId > 0) {
			wwwrDao.insertQuestionData(singleSurvey, activityId);
		}
		return wwwrDao.getActivityById(activityId);

	}

	@Override
	public Activity endActivity(HttpServletRequest request,
			HttpServletResponse response, Integer surveyorId) {

		// Map<String, Object> responMap = new HashMap<String, Object>();
		String imei = request.getHeader("imei");
		Activity createdActivity = null;
		if (validationServiceImpl.isValidImei(imei, response)) {
			try {

				boolean isMultiPart = ServletFileUpload
						.isMultipartContent(request);
				Gson gson = new Gson();
				SingleSurvey singleSurvey = null;
				if (isMultiPart) {
					ServletFileUpload upload = new ServletFileUpload();
					try {

						FileItemIterator itr = upload.getItemIterator(request);
						while (itr.hasNext()) {
							FileItemStream item = itr.next();
							String contentDisposition = item.getHeaders()
									.getHeader("Content-Disposition");
							String os = request.getHeader("os");
							logger.info("Content-Disposition:  "
									+ contentDisposition);
							logger.info("ios:  "
									+ os);
							if( os != null && os.contains("ios")){
								if (contentDisposition.contains("checkInInfo") ) {
									
									InputStream stream = item.openStream();
									String reader = Streams.asString(stream);
									logger.info("Build Version "
											+ request.getHeader("version")
											+ "\n validated Shop : " + reader);
									singleSurvey = gson.fromJson(reader,
											SingleSurvey.class);
									singleSurvey.setBuildVersion(request.getHeader(
											"version").split("_")[0]);
									singleSurvey.setImei(request.getHeader("imei"));
									syncLogging
											.saveLog(
													singleSurvey.getSurveyorId(),
													-1,
													DateTimeUtilities
															.getCurrentDate(DateTimeConstants.DATE_FORMAT3),
													reader, "start_activity_logs");
								}else{

									try {
										if (singleSurvey == null) {

											CommonUtil
													.writeErrorMessage(
															HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
															MessageType.ERROR_MULTIPART_ORDER,
															response);
											return null;
										}
										String imageName = extractImageName(contentDisposition);
									logger.info("Image Type ::   " + imageName);
										String imageType = imageName
												.split("_")[0];
										// visitImages =
										// syncData.getVisit().getVisitImageArrayList();
										logger.info("Image Type ::   " + imageType);
										if (imageType
												.contains(CommonConstants.SIS_REMARK_IMAGE)) {
											logger.info("Saving IOS Start day image");
											String modifiedImageUrl = imageName.replace(".png", "");
											
											for(SyncQuestion questionData: singleSurvey.getQuestionData()) {
												if(questionData.getImage().getImageUrl().equals(modifiedImageUrl)) {
													String tempPath = this.saveQuestionImage(singleSurvey, item);
													questionData.getImage().setImageUrl(tempPath);;
													break;
												}
											}
											
										
										}
									} catch (Exception ex) {
										logger.error(ex, ex);
										CommonUtil
												.writeErrorMessage(
														HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
														MessageType.FILE_SAVING_SERVER,
														response);
										return null;
									}
								
									
								}
								
							}else{
							if (item.isFormField()) {
								InputStream stream = item.openStream();
								String reader = Streams.asString(stream);
								logger.info("JsonActivity: " + reader);
								logger.info("Build Version "
										+ request.getHeader("version")
										+ "\n validated Shop : " + reader);
								singleSurvey = gson.fromJson(reader,
										SingleSurvey.class);
								singleSurvey.setBuildVersion(request.getHeader(
										"version").split("_")[0]);
								singleSurvey.setImei(request.getHeader("imei"));
								syncLogging
										.saveLog(
												singleSurvey.getSurveyorId(),
												-1,
												DateTimeUtilities
														.getCurrentDate(DateTimeConstants.DATE_FORMAT3),
												reader, "end_activity_logs");
							} else {
								try {
									if (singleSurvey == null) {

										CommonUtil
												.writeErrorMessage(
														HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
														MessageType.ERROR_MULTIPART_ORDER,
														response);
										return null;
									}
									String imageName = extractImageName(contentDisposition);
									logger.info("Image Type ::   " + imageName);
									String imageType = imageName
											.split("_")[0];
									// visitImages =
									// syncData.getVisit().getVisitImageArrayList();
									logger.info("Image Type ::   " + imageType);
									if (imageType
											.contains(CommonConstants.SIS_REMARK_IMAGE)) {
										logger.info("Saving visit shop image");
										
										for(SyncQuestion questionData: singleSurvey.getQuestionData()) {
											if(questionData.getImage().getImageUrl().contains(imageName)) {
												String tempPath = this.saveQuestionImage(singleSurvey, item);
												questionData.getImage().setImageUrl(tempPath);;
												break;
											}
										}
										
									
									}
									
								} catch (Exception ex) {
									logger.error(ex, ex);
									CommonUtil
											.writeErrorMessage(
													HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
													MessageType.FILE_SAVING_SERVER,
													response);
									return null;
								}
							}
						}
						}
					} catch (Exception ex) {
						logger.error(ex, ex);
						logger.error(
								"Error while inserting activity data agianst surveyor id : "
										+ singleSurvey.getSurveyorId(), ex);
						CommonUtil.writeErrorMessage(
								HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
								MessageType.ERROR_SERVER, response);
						return null;
					}

					if (singleSurvey != null) {
						createdActivity = this
								.saveEndActivityData(singleSurvey);
						// logger.info("Sending sync response against surveyor id "
						// + singleSurvey.getSurveyorId());
						// CommonUtil.writeErrorMessage(HttpServletResponse.SC_OK,
						// MessageType.SUCCESS, response);
					}

				} else {

					logger.error("Data is not multi part ");
					CommonUtil.writeErrorMessage(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							MessageType.ERROR_FORMAT_DATA, response);
					return null;
				}
			} catch (Exception e) {
				logger.error(e, e);

				CommonUtil.writeErrorMessage(
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						MessageType.FILE_SAVING_SERVER, response);
				return null;
			}
		} else {
			CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
					MessageType.ERROR_IMEI_PERMISSION, response);
			return null;
		}
		return createdActivity;

	}

	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	private Activity saveEndActivityData(SingleSurvey singleSurvey)
			throws Exception {

		logger.info("Saving single survey data into db");

		// changing active status to n of previous activity
		wwwrDao.updateStartActivityStatus(singleSurvey.getActivity()
				.getActivityId());

		// sending N in active status to show activity is completed
		int activityId = wwwrDao.insertActivity(singleSurvey, "N");
		if (activityId > 0) {
			wwwrDao.insertQuestionData(singleSurvey, activityId);
		}
		return wwwrDao.getActivityById(activityId);

	}

	@Override
	public Map<String, Object> syncData(String version,
			HttpServletRequest request, HttpServletResponse response)

	{
		Map<String, Object> responMap = new HashMap<String, Object>();
		String imei = request.getHeader("imei");
		if (validationServiceImpl.isValidImei(imei, response)) {
			try {
				// ArrayList<BwuImage> bwuImages = new ArrayList<BwuImage>();
				boolean isMultiPart = ServletFileUpload
						.isMultipartContent(request);
				Gson gson = new Gson();
				SyncData syncData = null;
				if (isMultiPart) {
					ServletFileUpload upload = new ServletFileUpload();
					try {
						FileItemIterator itr = upload.getItemIterator(request);
						while (itr.hasNext()) {
							FileItemStream item = itr.next();
							String contentDisposition = item.getHeaders()
									.getHeader("Content-Disposition");
							String os = request.getHeader("os");
							logger.info("Content-Disposition:  "
									+ contentDisposition);
							if( os != null && os.contains("ios")){
								   if (contentDisposition.contains("syncData") ) {
										InputStream stream = item.openStream();
										logger.debug("Stream :" + stream);
										String reader = Streams.asString(stream);
										logger.info("reader :" + reader);
										logger.info("Build Version "
												+ request.getHeader("version"));
										syncData = gson
												.fromJson(reader, SyncData.class);
										syncData.setBuildVersion(request
												.getHeader("version"));
										syncData.setImei(imei);

										syncLogging
												.saveLog(
														syncData.getSurveyorId(),
														syncData.getVisit().getShopId(),
														DateTimeUtilities
																.getCurrentDate(DateTimeConstants.DATE_FORMAT3),
														reader);

									} else {
							
										try {
											if (syncData == null) {

												CommonUtil
														.writeErrorMessage(
																HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
																MessageType.ERROR_MULTIPART_ORDER,
																response);
												return null;
											}
											String imageName = extractImageName(contentDisposition);
											String imageType = imageName
													.split("_")[0];
											// visitImages =
											// syncData.getVisit().getVisitImageArrayList();
											if (imageType
													.contains(CommonConstants.VISIT_SHOP_PICTURE)) {
												logger.info("Saving visit shop image");
												this.saveVisitImage(syncData, item);
											}
											String modifiedImageUrl = imageName.replace(".png", "");
											logger.info("SyncData:  "+syncData);
											if (imageType.contains(CommonConstants.SIS_REMARK_IMAGE)) {
											    logger.info("Question image for iOS");
											    


											    for (SyncQuestion questionData : syncData.getVisit().getQuestionData()) {
											        logger.info("Processing question: " + questionData);

											        // Check if the image is present and matches the modified image URL
											        if (questionData.getImage() != null && 
											            questionData.getImage().getImageUrl() != null && 
											            questionData.getImage().getImageUrl().equals(modifiedImageUrl)) {
											                
											            String tempPath = this.saveQuestionImage(syncData, item);
											            questionData.getImage().setImageUrl(tempPath);
											            logger.info("Updated image URL for question: " + questionData);
											            break; // Exit the loop after updating the image URL
											        } else {
											            logger.info("No matching image found for question: " + questionData);
											        }
											    }
											}

										} catch (Exception ex) {
											logger.error(ex, ex);
											logger.error("shop id "
													+ syncData.getSyncShop().getId());
											CommonUtil
													.writeErrorMessage(
															HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
															MessageType.FILE_SAVING_SERVER,
															response);
											return null;
										}
									}
								
							}else{
								   if (item.isFormField()) {
										InputStream stream = item.openStream();
										logger.debug("Stream :" + stream);
										String reader = Streams.asString(stream);
										logger.info("reader :" + reader);
										logger.info("Build Version "
												+ request.getHeader("version"));
										syncData = gson
												.fromJson(reader, SyncData.class);
										syncData.setBuildVersion(request
												.getHeader("version"));
										syncData.setImei(imei);

										syncLogging
												.saveLog(
														syncData.getSurveyorId(),
														syncData.getVisit().getShopId(),
														DateTimeUtilities
																.getCurrentDate(DateTimeConstants.DATE_FORMAT3),
														reader);

									} else {
										try {
											if (syncData == null) {

												CommonUtil
														.writeErrorMessage(
																HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
																MessageType.ERROR_MULTIPART_ORDER,
																response);
												return null;
											}
											String imageType = item.getName()
													.split("_")[0];
											// visitImages =
											// syncData.getVisit().getVisitImageArrayList();
											logger.info("Image Type ::   " + imageType);
											if (imageType
													.contains(CommonConstants.VISIT_SHOP_PICTURE)) {
												logger.info("Saving visit shop image");
												this.saveVisitImage(syncData, item);
											}
										} catch (Exception ex) {
											logger.error(ex, ex);
											logger.error("shop id "
													+ syncData.getSyncShop().getId());
											CommonUtil
													.writeErrorMessage(
															HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
															MessageType.FILE_SAVING_SERVER,
															response);
											return null;
										}
									}
							}
                         
						}
					} catch (Exception ex) {
						logger.error(ex, ex);
						logger.error("Error while saving sync data agianst shop : "
								+ syncData.getSyncShop().getId());
						CommonUtil.writeErrorMessage(
								HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
								MessageType.ERROR_SERVER, response);
						return null;
					}
					if (syncData != null) {
						syncVisit(syncData);
						logger.info("Sending Shop id in response "
								+ " shop id : "
								+ syncData.getVisit().getShopId()
								+ " and visit id : "
								+ syncData.getVisit().getId());
						responMap.put("syncResponse", CommonUtil
								.getResponseObj(
										syncData.getVisit().getShopId(),
										syncData.getVisit().getId(), -1));
					}
				} else {
					logger.error("Data is not multi part ");
					CommonUtil.writeErrorMessage(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							MessageType.ERROR_FORMAT_DATA, response);
					return null;
				}
			} catch (Exception e) {
				logger.error(e, e);
				CommonUtil.writeErrorMessage(
						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						MessageType.FILE_SAVING_SERVER, response);
				return null;
			}
		} else {
			CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
					MessageType.ERROR_IMEI_PERMISSION, response);
			return null;
		}
		return responMap;
	}
	
	private String extractImageName(String contentDispositionHeader) {
        String imageName = null;
        String[] parts = contentDispositionHeader.split(";");
        for (String part : parts) {
            if (part.trim().startsWith("filename=")) {
                imageName = part.substring(part.indexOf('=') + 1).trim().replace("\"", "");
                break;
            }
        }
        return imageName;
    }

	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	private void syncVisit(SyncData syncData) throws Exception {

		logger.info("Saving data into db");
		// checking if found existing survey then delete before dumping survey
		// data.
		syncData.getVisit().setClientShopId(syncData.getVisit().getShopId());
		if (shopsDao.isDuplicateMerchandiserSurveys(syncData.getVisit()
				.getShopId(), syncData.getSurveyorId(), syncData.getVisit()
                .getDateTime())) {
			logger.info("Duplicate survey found against shop id: "
					+ syncData.getVisit().getShopId());
			// shopsDao.deleteMerchandiserDuplicateSurvey(syncData.getVisit().getShopId(),
			// syncData.getSurveyorId(), syncData.getVisit().getDateTime());
			return;
		}

		// checking shop time.
		// if shop time is more than 20 minutes, it will be replaced with total
		// 20 minutes.
		// this.checkShopProductiveTime(syncData);
		this.saveVisitSyncData(syncData);

	}

	private void saveVisitSyncData(SyncData syncData) throws Exception {

		// A bug in client in which client shop id is sent instead of server
		// shop id.Due to this shop id is picked from merchandiser shops against
		// client shop id
		Integer shopId = shopsDao.getShopIdByClientShopId(syncData.getVisit()
				.getShopId(), syncData.getSurveyorId());
		if (shopId > 0) {
			logger.error("Bug on Client,client id  "
					+ syncData.getVisit().getShopId()
					+ "is sent instead of server id : " + shopId);
			syncData.getVisit().setShopId(shopId);
		}
		Integer surveyId = wwwrDao.insertVisitSyncData(syncData);

		try {
			if (syncData.getVisit().getQuestionData() != null
					&& syncData.getVisit().getQuestionData().size() > 0) {
				logger.info(" saving question data into db ");
				wwwrDao.insertSyncQuestionData(syncData, surveyId);
			}
			
			if (syncData.getVisit().getRedFlagRemark() != null){
				logger.info(" saving Red Flag Remark into db ");
				wwwrDao.insertRedFlagRemarkData(syncData, surveyId);
			}
		} catch (Exception e) {
			logger.error(e, e);
		}

	}

	@Override
	public ArrayList<SummaryTag> getSummary(Integer surveyorId, String type) {
		SummaryData summaryData = new SummaryData();
		summaryData = wwwrDao.getSummary(surveyorId, type);
		return summaryData.getSummarData();

	}

	public Map<String, Object> activityStatus(HttpServletRequest request,
			HttpServletResponse response, Integer surveyorId) {

		Map<String, Object> jsonMap = new HashMap<String, Object>();
		try {
			jsonMap.put("activityStatus",
					wwwrDao.getActivityStatus(surveyorId));

		} catch (Exception ex) {

			logger.error("Exception occured while getting activityStatus", ex);
			CommonUtil.writeErrorMessage(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					MessageType.ERROR_SERVER, response);
			return null;
		}
		return jsonMap;
	}
	
	public Map<String, Object> endActivityStatus(HttpServletRequest request,
			HttpServletResponse response, Integer surveyorId, Integer activityId) {

		Map<String, Object> jsonMap = new HashMap<String, Object>();
		try {
			jsonMap.put("activityStatus",
					wwwrDao.getendActivityStatus(surveyorId, activityId));

		} catch (Exception ex) {

			logger.error("Exception occured while getting endActivityStatus", ex);
			CommonUtil.writeErrorMessage(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					MessageType.ERROR_SERVER, response);
			return null;
		}
		return jsonMap;
	}
	
//	public Map<String, Object> marginCalculators(HttpServletRequest request,
//			HttpServletResponse response, String version) {
//		
//		
//		Map<String, Object> jsonMap = new HashMap<String, Object>();
//		
// 				try {
//		 
// 					jsonMap.put("", wwwrDao.marginCalculatorList());
//		 
// 					
//  				} catch (Exception ex) {
//	 
// 					logger.error("Exception occured while getting MarginCalculator", ex);
// 					CommonUtil.writeErrorMessage(
// 							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
// 							MessageType.ERROR_SERVER, response);
// 					return null;
// 			}
// 				return jsonMap;
//			}
//	public List<Map<String, Object>> marginCalculators(
//	        HttpServletRequest request,
//	        HttpServletResponse response,
//	        String version) {
//	    
//	    try {
//	        return wwwrDao.marginCalculatorList();
//	    } catch (Exception ex) {
//	        logger.error("Exception occurred while getting MarginCalculator", ex);
//	        CommonUtil.writeErrorMessage(
//	                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//	                MessageType.ERROR_SERVER, response);
//	        return Collections.emptyList(); // Or return null if that's more appropriate for your use case
//	    }
//	}

//	public Map<String, Object> marginCalculators(
//	        HttpServletRequest request,
//	        HttpServletResponse response,
//	        String version) {
//	    
//	    Map<String, Object> jsonMap = new HashMap<String, Object>();
//	    
//	    try {
//	        List<Map<String, Object>> marginCalculatorList = wwwrDao.marginCalculatorList();
//
//	        // Here, you might want to decide how to structure the Map based on your requirements
//	        // For example, if the list has a single element, you might want to use a specific key
//	        if (!marginCalculatorList.isEmpty()) {
//	            jsonMap.put("marginCalculatorData", marginCalculatorList.get(0));
//	        } else {
//	            // Handle the case when the list is empty
//	           // jsonMap.put("marginCalculatorData", Collections.emptyMap());
//	        }
//	    } catch (Exception ex) {
//	        logger.error("Exception occurred while getting MarginCalculator", ex);
//	        CommonUtil.writeErrorMessage(
//	                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//	                MessageType.ERROR_SERVER, response);
//	        return Collections.emptyMap(); // Or return null if that's more appropriate for your use case
//	    }
//	    
//	    return jsonMap;
//	}
	
//	public List<Map<String, Object>> marginCalculators(
//	        HttpServletRequest request,
//	        HttpServletResponse response,
//	        String version) {
//	    if(version.compareTo("2.4") < 0){
//	    	
//	    	try {
//		        return wwwrDao.marginCalculatorList();
//		    } catch (Exception ex) {
//		        logger.error("Exception occurred while getting MarginCalculator", ex);
//		        CommonUtil.writeErrorMessage(
//		                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//		                MessageType.ERROR_SERVER, response);
//		        return Collections.emptyList(); 
//		    }
//	    } else {
//	    	Map<String, Object> jsonMap = new HashMap<String, Object>();
//			try {
//
//				jsonMap.put("marginCalculatorList", wwwrDao.marginCalculatorList());
//				jsonMap.put("nonFilerDisclaimer", SysConstants.NON_FILER_DISCLAIMER);
//				jsonMap.put("filerDisclaimer", SysConstants.FILER_DISCLAIMER);
//				jsonMap.put("nonFilerMessage", SysConstants.NON_FILER_MESSAGE);
//				jsonMap.put("filerMessage", SysConstants.FILER_MESSAGE);
//
//				
//			} catch (Exception ex) {
//
//				logger.error("Exception occured while getting MarginCalculator", ex);
//				CommonUtil.writeErrorMessage(
//						HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//						MessageType.ERROR_SERVER, response);
//				return null;
//			}
//			return jsonMap;
//	    }
//	    
//	}
	public Object marginCalculators(
	        HttpServletRequest request,
	        HttpServletResponse response,
	        String version) {
	    if (version.compareTo("2.4") < 0) {
	        try {
	            return wwwrDao.marginCalculatorList();
	        } catch (Exception ex) {
	            logger.error("Exception occurred while getting MarginCalculator", ex);
	            CommonUtil.writeErrorMessage(
	                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                    MessageType.ERROR_SERVER, response);
	            return Collections.emptyList();
	        }
	    } else {
	        Map<String, Object> jsonMap = new HashMap<String, Object>();
	        try {
	            jsonMap.put("marginCalculatorList", wwwrDao.marginCalculatorList());
	            jsonMap.put("nonFilerDisclaimer", SysConstants.NON_FILER_DISCLAIMER);
	            jsonMap.put("filerDisclaimer", SysConstants.FILER_DISCLAIMER);
	            jsonMap.put("nonFilerMessage", SysConstants.NON_FILER_MESSAGE);
	            logger.info("Added nonFilerMessage: " + SysConstants.NON_FILER_MESSAGE);
	            jsonMap.put("filerMessage", SysConstants.FILER_MESSAGE);
	        } catch (Exception ex) {
	            logger.error("Exception occurred while getting MarginCalculator", ex);
	            CommonUtil.writeErrorMessage(
	                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                    MessageType.ERROR_SERVER, response);
	            return Collections.emptyMap();
	        }
	        return jsonMap;
	    }
	}


	
//	public Map<String, Object> marginCalculators(HttpServletRequest request,
//			HttpServletResponse response, String version) {
//		
//		
//		Map<String, Object> jsonMap = new HashMap<String, Object>();
//		
// 				try {
// 					List<Map<String, Object>> marginCalculatorList = wwwrDao.marginCalculatorList();
// 					// jsonMap.put("marginCalculatorList", wwwrDao.marginCalculatorList());
//		 
// 					
//  				} catch (Exception ex) {
//	 
// 					logger.error("Exception occured while getting MarginCalculator", ex);
// 					CommonUtil.writeErrorMessage(
// 							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
// 							MessageType.ERROR_SERVER, response);
// 					return null;
// 			}
// 				return jsonMap;
//			}
	
	
//	public Map<String, Object> marginCalculators(
//	        HttpServletRequest request,
//	        HttpServletResponse response,
//	        String version) {
//	    
//	    Map<String, Object> jsonMap = new HashMap<>();
//	    
//	    try {
//	        List<Map<String, Object>> marginCalculatorList = wwwrDao.marginCalculatorList();
//	        jsonMap.put("", marginCalculatorList);
//	    } catch (Exception ex) {
//	        logger.error("Exception occurred while getting MarginCalculator", ex);
//	        CommonUtil.writeErrorMessage(
//	                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//	                MessageType.ERROR_SERVER, response);
//	        return null;
//	    }
//	    
//	    return jsonMap;
//	}


//		if(version.compareTo("1.7") < 0) {
//	    	 List<Map<String, Object>> marginCalculatorList = wwwrDao.marginCalculatorList();
//	    	  try {
//	  	        return marginCalculatorList;
//	  	    } catch (Exception ex) {
//	  	        logger.error("Exception occurred while getting MarginCalculator", ex);
//	  	        CommonUtil.writeErrorMessage(
//	  	                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//	  	                MessageType.ERROR_SERVER, response);
//	  	        return null;
//	  	    }
//	    	
//	    }
//	    else
//	    {
//		Map<String, Object> jsonMap = new HashMap<String, Object>();
//		try {
//
//			jsonMap.put("marginCalculatorList", wwwrDao.marginCalculatorList());
//			jsonMap.put("nonFilerDisclaimer", SysConstants.NON_FILER_DISCLAIMER);
//			jsonMap.put("filerDisclaimer", SysConstants.FILER_DISCLAIMER);
//			jsonMap.put("nonFilerMessage", SysConstants.NON_FILER_MESSAGE);
//			jsonMap.put("filerMessage", SysConstants.FILER_MESSAGE);
//
//			
//		} catch (Exception ex) {
//
//			logger.error("Exception occured while getting MarginCalculator", ex);
//			CommonUtil.writeErrorMessage(
//					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
//					MessageType.ERROR_SERVER, response);
//			return null;
//		}
//		return jsonMap;
//	}
	
	@Override
	public List<ShopRoute> shopList(Integer routeId, Integer dsrId,
			HttpServletResponse response) {
		List<ShopRoute> shopList = null;
		try {
			shopList = wwwrDao.shopList(routeId, dsrId);
		} catch (Exception e) {
			logger.error(e, e);
		}
		return shopList;
	}
	
	public Map<String, Object> shopListWithVersionCheck(Integer routeId, Integer dsrId,Integer amId, HttpServletResponse response) {
	    Map<String, Object> jsonMap = new HashMap<>();
	    try {
	    	if(amId != null && amId > 0){
	    		jsonMap.put("shopList", wwwrDao.amShopList(routeId, amId));
	    	}
	    	else
	    	{
	    		jsonMap.put("shopList", wwwrDao.shopList(routeId, dsrId));
	    	}
	        
	        jsonMap.put("programMapList", wwwrDao.programList());
	    } catch (Exception ex) {
	        logger.error("Exception occurred while getting MarginCalculator", ex);
	        CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, MessageType.ERROR_SERVER, response);
	        return Collections.emptyMap();
	    }
	    return jsonMap;
	}

	@Override
	public Map<String, Object> checkUpdates(SurveyorType appType,
			HttpServletRequest request, HttpServletResponse response) {

		if (applicationCacheService.getBuildsMap().containsKey(
				appType.toString())) {

			Build build = applicationCacheService.getBuildsMap().get(
					appType.toString());
			String requestVersion = request.getHeader("version").split("_")[0];
			String imei = request.getHeader("imei");

			Integer surveyorId = Integer.parseInt(request
					.getHeader("surveyorId"));
			Surveyor surveyor = surveyorDao.getSurveyorById(surveyorId);
			int employeeId = surveyorId;
			if (request.getHeader("employeeId") != null) {
				employeeId = Integer.parseInt(request.getHeader("employeeId"));
			}

			/*
			 * logger.info(" request version : " + request.getHeader("version")
			 * + ", current version : " + build.getVersion());
			 */
			logger.info(" request version : " + requestVersion
					+ ", current version : " + build.getVersion()
					+ " , employeeId : " + employeeId + ", surveyorId : "
					+ surveyorId + ", imei: " + imei);

			if ("Y".equalsIgnoreCase(surveyor.getForceLogin())) {
				CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
						MessageType.ERROR_FORCE_LOGIN, response);
				return null;
			}
			if (Boolean.parseBoolean(bundle.getString(
					CommonConstants.IMEI_VALIDATION).trim())
					&& !validationServiceImpl.isValidImei(imei, response)) {

				CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
						MessageType.ERROR_IMEI_PERMISSION, response);
				return null;
			}
			if (!surveyorDao.isValidEmployeeCode(surveyorId, employeeId)) {
				CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
						MessageType.ERROR_INVALID_EMPLOYEECODE, response);
				return null;
			}
			/*
			 * if (Float.parseFloat(build.getVersion()) > 1.2 &&
			 * !this.isCorrectDateTime(request.getHeader("deviceDateTime"))) {
			 * CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
			 * MessageType.ERROR_TIME_DIFFERENCE, response); return; }
			 */
			if (request.getHeader("deviceDateTime") != null
					&& !this.isCorrectDateTime(request
							.getHeader("deviceDateTime"))) {
				CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN,
						MessageType.ERROR_TIME_DIFFERENCE, response);
				return null;
			}
			if (Boolean.parseBoolean(bundle.getString(
					CommonConstants.VERSION_ALERT).trim())
					&& !build.getVersion().equalsIgnoreCase(requestVersion)) {
				response.setHeader("url", build.getDownloadUrl());
				response.setHeader("version", build.getVersion());
				CommonUtil.writeErrorMessage(
						HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED,
						MessageType.ERROR_VERSION, response);
				return null;
				/*
				 * if ( build.getVersion().equalsIgnoreCase( requestVersion)) {
				 * CommonUtil.writeErrorMessage(HttpServletResponse.SC_OK,
				 * MessageType.SUCCESS, response); return; return
				 * surveyorServiceImpl.refreshData(imei, surveyorId,
				 * appType.toString(), request, response); } else {
				 * 
				 * response.setHeader("url", build.getDownloadUrl());
				 * response.setHeader("version", build.getVersion());
				 * CommonUtil.writeErrorMessage(
				 * HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED,
				 * MessageType.ERROR_VERSION, response); return null; }
				 */
			} else {
				return refreshData(imei, surveyor, appType.toString(), request,
						response);
			}
		}
		CommonUtil.writeErrorMessage(
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
				MessageType.ERROR_SERVER, response);
		return null;

	}

	protected boolean isCorrectDateTime(String deviceTime) {

		Date deviceDateTime = DateTimeUtilities.stringToDate(deviceTime,
				"yyyy-MM-dd HH:mm:ss");
		Calendar calendar1 = Calendar.getInstance();
		calendar1.setTime(deviceDateTime);
		long deviceMillies = calendar1.getTimeInMillis();

		// Date currentDate =
		// DateTimeUtilities.getCurrentDateInDate(DateTimeConstants.DATE_FORMAT);
		Date currentDate = DateTimeUtilities.getCurrentTimestamp();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(currentDate);
		long currentMillies = calendar.getTimeInMillis();

		/* / covert milliseconds to minutes / */
		float diff = Math.abs((currentMillies - deviceMillies) / 60000);
		int MinuteLimit = Integer.parseInt(bundle.getString(
				CommonConstants.TIME_DIFFERENCE).trim());
		if (diff > MinuteLimit) {
			// this.writeTimeDifferenceError(response);
			logger.error("Device Time is not correct, Please change your device time");
			return false;
		}
		return true;
	}

	public Map<String, Object> refreshData(String imei, Surveyor surveyor,
			String surveyorType, HttpServletRequest request,
			HttpServletResponse response) {

		Map<String, Object> jsonMap = new HashMap<String, Object>();
		Map<String, Object> data = new HashMap<String, Object>();
		try {
			// Surveyor surveyor = surveyorDao.getSurveyorById(surveyorId);

			data.put("surveyor", surveyor);
			jsonMap.put("data", data);
		} catch (Exception ex) {

			logger.error("Exception occured while login against imei " + imei,
					ex);
			CommonUtil.writeErrorMessage(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					MessageType.ERROR_SERVER, response);
			return null;
		}
		return jsonMap;
	}

	private void saveVisitImage(SyncData syncData, FileItemStream item)
			throws Exception {

		try {
			String temporaryPath = fileUtils.storeSurveyFileForDsr(
					servletContext.getRealPath("/"), syncData.getVisit()
							.getShopId(), CommonConstants.VISIT_SHOP_PICTURE,
					item);
			logger.info("temporaryPath : " + temporaryPath);
			syncData.getVisit().getVisitImage().setImageUrl(temporaryPath);
		} catch (Exception ex) {

			logger.error(
					"Error while saving Visit shop image in Visit Service : "
							+ syncData.getVisit().getShopId(), ex);
			throw ex;
		}
	}
	
	
	@Override
	public Map<String, Object> syncCeFaq(HttpServletRequest request, HttpServletResponse response) {
	    Map<String, Object> responseMap = new HashMap<String, Object>();
	    String surveyorId = request.getHeader("surveyorId");
	    if (validationServiceImpl.isValidImei(surveyorId, response)) {
	        try {
	            boolean isMultiPart = ServletFileUpload.isMultipartContent(request);
	            Gson gson = new Gson();
	            FAQ ceFaq = null;

	            if (isMultiPart) {
	                ServletFileUpload upload = new ServletFileUpload();
	                FileItemIterator itr = upload.getItemIterator(request);

	                while (itr.hasNext()) {
	                    FileItemStream item = itr.next();

	                    if (item.isFormField()) {
	                        InputStream stream = item.openStream();
	                        String reader = Streams.asString(stream);
	                        logger.info("Build Version " + request.getHeader("version"));
	                        logger.debug("\n validated Shop : " + reader);
	                        ceFaq = gson.fromJson(reader, FAQ.class);
	                    } else {
	                        if (ceFaq == null) {
	                            CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                                    MessageType.ERROR_MULTIPART_ORDER, response);
	                            return responseMap;
	                        }
	                    }
	                }

	                if (ceFaq != null) {
	                    saveCeFaqData(ceFaq, Integer.parseInt(surveyorId));
	                    // You can add any additional logic or response handling here
	                    responseMap.put("status", "success");
	                }

	            } else {
	                logger.error("Data is not multipart");
	                CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                        MessageType.ERROR_FORMAT_DATA, response);
	                return responseMap;
	            }
	        } catch (Exception e) {
	            logger.error(e, e);
	            CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                    MessageType.FILE_SAVING_SERVER, response);
	            return responseMap;
	        }
	    } else {
	        CommonUtil.writeErrorMessage(HttpServletResponse.SC_FORBIDDEN, MessageType.ERROR_IMEI_PERMISSION, response);
	        return responseMap;
	    }

	    return responseMap;
	}


	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	private void saveCeFaqData(FAQ ceFaq,Integer surveyorId)
			throws Exception {
		if (!ceFaq.getFeedback().isEmpty()) {

			wwwrDao.insertCeFaq(ceFaq,surveyorId);
			emailSendingCron.sendRemark(surveyorId,ceFaq.getFeedback());
				
			}
		


	}
	

	public Map<String, Object> remarkShopList(Integer amId,Integer rsmId, HttpServletResponse response) {
	    Map<String, Object> jsonMap = new HashMap<>();
	    List<Map<String, Object>> shopList = new ArrayList<>();
	    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	    try {
	        List<Map<String, Object>> originalList = wwwrDao.remarkShopList(amId,rsmId);
	        
	        // Use a map to group remarks by shop ID
	        Map<Integer, Map<String, Object>> shopMap = new HashMap<>();
	        
	        for (Map<String, Object> entry : originalList) {
	            Integer shopId = (Integer) entry.get("id");
	            String shopTitle = (String) entry.get("shopTitle");
	            String senderRemark = (String) entry.get("sender_remark");
	            String senderName = (String) entry.get("sender_name");
	            String receiverRemark = (String) entry.get("receiver_remark");
	            String receiverName = (String) entry.get("receiver_name");
	            String isClose = (String) entry.get("is_close");
	            Integer recordId = (Integer) entry.get("record_id");
	            String senderTime = null;
	            String receiverTime = null;

	            Object dateTimeObj = entry.get("senderDatetime");
	            if (dateTimeObj instanceof Timestamp) {
	            	senderTime = dateFormat.format((Timestamp) dateTimeObj);
	            } else if (dateTimeObj != null) {
	            	senderTime = dateTimeObj.toString();
	            }
	            
	            Object dateTimeObj1 = entry.get("receiverDatetime");
	            if (dateTimeObj1 instanceof Timestamp) {
	            	receiverTime = dateFormat.format((Timestamp) dateTimeObj1);
	            } else if (dateTimeObj1 != null) {
	            	receiverTime = dateTimeObj1.toString();
	            }
	            
	            
	            // If the shop is not already in the map, add it
	            if (!shopMap.containsKey(shopId)) {
	                Map<String, Object> shopDetails = new HashMap<>();
	                shopDetails.put("id", shopId);
	                shopDetails.put("shopTitle", shopTitle);
	                shopDetails.put("remarks", new ArrayList<Map<String, Object>>());
	                shopMap.put(shopId, shopDetails);
	            }
	            
	            // Add the remark to the shop's list of remarks
	            List<Map<String, Object>> remarks = (List<Map<String, Object>>) shopMap.get(shopId).get("remarks");
	            
	            Map<String, Object> remark = new HashMap<>();
	            remark.put("senderRemark", senderRemark);
	            remark.put("senderName", senderName);
	            remark.put("receiverRemark", receiverRemark);
	            remark.put("receiverName", receiverName);
	            remark.put("isClose", isClose);
	            remark.put("recordId", recordId); // Store recordId as Integer
	            remark.put("senderDatetime", senderTime); // Store dateTime as String
	            remark.put("receiverDatetime", receiverTime); // Store dateTime as String
	            
	            remarks.add(remark);
	        }

	        // Convert the map values to a list
	        shopList.addAll(shopMap.values());
	        jsonMap.put("redflagshops", shopList);
	        
	    } catch (Exception ex) {
	        logger.error("Exception occurred while getting MarginCalculator", ex);
	        CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, MessageType.ERROR_SERVER, response);
	        return Collections.emptyMap();
	    }
	    return jsonMap;
	}

	@Override
	public Map<String, Object> syncRedFlag(HttpServletRequest request, HttpServletResponse response) {
	    Map<String, Object> responseMap = new HashMap<String, Object>();
	        try {
	            boolean isMultiPart = ServletFileUpload.isMultipartContent(request);
	            Gson gson = new Gson();
	            RedFlagChatResponse redFlag = null;

	            if (isMultiPart) {
	                ServletFileUpload upload = new ServletFileUpload();
	                FileItemIterator itr = upload.getItemIterator(request);

	                while (itr.hasNext()) {
	                    FileItemStream item = itr.next();

	                    if (item.isFormField()) {
	                        InputStream stream = item.openStream();
	                        String reader = Streams.asString(stream);
	                        logger.info("Build Version " + request.getHeader("version"));
	                        logger.debug("\n validated Shop : " + reader);
	                        redFlag = gson.fromJson(reader, RedFlagChatResponse.class);
	                    } else {
	                        if (redFlag == null) {
	                            CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                                    MessageType.ERROR_MULTIPART_ORDER, response);
	                            return responseMap;
	                        }
	                    }
	                }
	                if (redFlag != null) {
	                	saveRedFlag(redFlag);
	                    // You can add any additional logic or response handling here
	                    responseMap.put("status", "success");
	                }

	            } else {
	                logger.error("Data is not multipart");
	                CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                        MessageType.ERROR_FORMAT_DATA, response);
	                return responseMap;
	            }
	        } catch (Exception e) {
	            logger.error(e, e);
	            CommonUtil.writeErrorMessage(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
	                    MessageType.FILE_SAVING_SERVER, response);
	            return responseMap;
	        }
	   

	    return responseMap;
	}
	
	@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Throwable.class)
	private void saveRedFlag(RedFlagChatResponse redFlag)
			throws Exception {
		logger.info(redFlag.getReceiverRemark()+"receive remark");
			logger.info(redFlag.getRecordId()+"record remark");
		if (!redFlag.getReceiverRemark().isEmpty()) {

			wwwrDao.updateRedFlag(redFlag);
				
			}
		


	}
	
	private String saveQuestionImage(SingleSurvey singleSurvey, FileItemStream item)
			throws Exception {

		 try {
			 logger.info("Creating IOS Path");
		            String temporaryPath = fileUtils.storeSurveyFileForDsr(
		                    servletContext.getRealPath("/"), singleSurvey.getSurveyorId(), CommonConstants.SIS_REMARK_IMAGE, item);
		            
		            logger.info("IOS image path ::   " + temporaryPath);
		            return temporaryPath;

		    } catch (Exception ex) {
		        logger.error("Error while saving Visit shop image in Visit Service : " + singleSurvey.getSurveyorId(), ex);
		        throw ex;
		    }
		 }
	
	
	private String saveQuestionImage(SyncData syncData, FileItemStream item)
			throws Exception {

		 try {
			 logger.info("Creating IOS Path");
		            String temporaryPath = fileUtils.storeSurveyFileForDsr(
		                    servletContext.getRealPath("/"), syncData.getSurveyorId(), CommonConstants.SIS_REMARK_IMAGE, item);
		            
		            logger.info("IOS image path ::   " + temporaryPath);
		            return temporaryPath;

		    } catch (Exception ex) {
		        logger.error("Error while saving Visit shop image in Visit Service : " + syncData.getSurveyorId(), ex);
		        throw ex;
		    }
		 }
	
}
