package com.marksman.census.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.marksman.census.bo.Dsr;
import com.marksman.census.bo.Surveyor;
import com.marksman.census.client.bo.Activity;
import com.marksman.census.client.bo.CheckInInfo;
import com.marksman.census.client.bo.DsrStockList;
import com.marksman.census.client.bo.FAQ;
import com.marksman.census.client.bo.MarginCalculator;
import com.marksman.census.client.bo.RedFlagChatResponse;
import com.marksman.census.client.bo.ShopRoute;
import com.marksman.census.client.bo.SingleSurvey;
import com.marksman.census.client.bo.SummaryData;
import com.marksman.census.client.bo.SummaryTag;
import com.marksman.census.client.bo.SyncData;
import com.marksman.census.client.bo.SyncQuestion;
import com.marksman.census.client.bo.Time;
import com.marksman.census.constants.DateTimeConstants;
import com.marksman.census.constants.SurveyorType;
import com.marksman.census.util.DateTimeUtilities;
import com.marksman.census.util.StringUtils;

public class WWWRDao extends BaseDao {

	public Activity getActivityById(final int activityId) {

		String query = "SELECT id activity_id , `activity_date` start_time, activity_client_id FROM day_activities WHERE id =  ? ";

		logger.debug("Query: " + query.toString());
		try {
			return getJdbcTemplate().queryForObject(query.toString(),
					new Object[] { activityId }, new RowMapper<Activity>() {

						@Override
						public Activity mapRow(ResultSet rs, int rowNum)
								throws SQLException {

							Activity activity = new Activity();
							activity.setActivityId(rs.getInt("activity_id"));
							activity.setTime(new Time(rs
									.getString("start_time"), ""));
							activity.setActivityClientId(rs
									.getString("activity_client_id"));
							return activity;
						}
					});
		} catch (EmptyResultDataAccessException erdae) {
			logger.error("No record found against activity Id : " + activityId);
			return null;
		} catch (Exception e) {
			logger.error("Error while getting activity Id : " + activityId, e);
			return null;
		}
	}

	public int insertActivity(final SingleSurvey singleSurvey,
			final String status) throws Exception {
		try {
            final String query = "INSERT INTO day_activities (  surveyor_id, `area_id`, `dsr_id`, activity_type, review_type, activity_date, activity_client_id, active, "
                    + "created_datetime, build_version, employee_id, visit_date, am_id, am_employee_id) SELECT ?, ?, ?,?, ?, ?,?, ?, ?,?,employee_id,DATE('"
                    + singleSurvey.getActivity().getActivityDate()
                    + "'),?,? FROM v_surveyor WHERE id=?  ON DUPLICATE KEY UPDATE active = 'N'";
            logger.info(" dsr query = "+query);
			KeyHolder keyHolder = new GeneratedKeyHolder();
			getJdbcTemplate().update(new PreparedStatementCreator() {

				@Override
				public PreparedStatement createPreparedStatement(
						Connection connection) throws SQLException {
					PreparedStatement ps = connection.prepareStatement(query,
							new String[] { "id" });
					ps.setInt(1, singleSurvey.getSurveyorId());
					ps.setInt(2, singleSurvey.getActivity().getAreaId());
					ps.setInt(3, singleSurvey.getDsrId());
					ps.setString(4, singleSurvey.getActivity()
							.getActivityType());
					ps.setString(5, singleSurvey.getActivity().getReviewType());
					ps.setString(6, singleSurvey.getActivity()
							.getActivityDate());
					ps.setString(7, singleSurvey.getActivity()
							.getActivityClientId());
					ps.setString(8, status);
					ps.setString(9, DateTimeUtilities
							.getCurrentDate(DateTimeConstants.DATE_TIME_FORMAT));
					ps.setString(10, singleSurvey.getBuildVersion());
				    ps.setInt(11, singleSurvey.getAmId());
				    ps.setInt(12, singleSurvey.getAmEmployeeId());
					ps.setInt(13, singleSurvey.getSurveyorId());
					return ps;
				}
			}, keyHolder);

			return keyHolder.getKey().intValue();
		} catch (Exception e) {
			logger.error(e, e);
			return (Integer) 0;
		}
	}

	public void insertQuestionData(final SingleSurvey singleSurvey,
			final int activityId) throws Exception {
		StringBuilder query = new StringBuilder(
				"INSERT INTO day_activity_details (activity_id, question_id , remark_id, remark_value,created_datetime, ");
		query.append(" question_type_id,image_url,image_datetime) VALUES (?,?,?,?,?,?,?,?)");
		logger.debug("Query: " + query);
		try {
			final int batchSize = singleSurvey.getQuestionData().size();
			getJdbcTemplate().batchUpdate(query.toString(),
					new BatchPreparedStatementSetter() {

						public int getBatchSize() {

							return batchSize;
						}

						public void setValues(PreparedStatement ps, int i)
								throws SQLException {

							logger.debug("Batch Size = " + getBatchSize()
									+ "   i=" + i);

							SyncQuestion questionData = singleSurvey
									.getQuestionData().get(i);
							ps.setInt(1, activityId);
							ps.setInt(2, questionData.getQuestionId());
							ps.setInt(3, questionData.getRemarkId());
							ps.setString(4, questionData.getOptionValue());
							ps.setString(
									5,
									DateTimeUtilities
											.getCurrentDate(DateTimeConstants.DATE_TIME_FORMAT));
							ps.setInt(6, questionData.getQuestionTypeId());
							if (questionData.getImage() != null) {
								ps.setString(7, questionData.getImage()
										.getImageUrl());
								ps.setString(8, questionData.getImage().getImageTime());
							} else {
								ps.setString(7, "");
								ps.setString(8, "");
							}
						}

					});
		} catch (Exception e) {
			logger.error(e, e);
		}

	}

	public int updateStartActivityStatus(int activityId) {
		String query = " update day_activities set active = 'N' where id = ? ";

		return getJdbcTemplate().update(query, new Object[] { activityId });

	}

	public SummaryData getSummary(final Integer surveyorId, final String type) {
		StringBuilder query = new StringBuilder();
		if (type.equalsIgnoreCase("WW")) {
			query.append(" SELECT su.ww_target total_planned, COUNT(DISTINCT IF( da.`activity_type` = 'START', da.`id`, NULL ) ) total_attempted, ");
			query.append(" COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) ) total_completed,COALESCE(UnApproved, 0) AS UnApproved, COUNT(ms.`id`) shops_attempted, ");
			query.append(" IF(su.ww_target - COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) )<0, 0, ");
			query.append(" su.ww_target - COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) )) remaining");
			query.append(" FROM surveyor su LEFT JOIN `day_activities` da ON su.id = da.surveyor_id  AND da.review_type = 'WW'   AND MONTH(da.activity_date) = MONTH(NOW()) AND YEAR(da.activity_date) = YEAR(NOW()) ");
			query.append(" LEFT JOIN `merchandiser_surveys` ms ON ms.`activity_id` = da.`id`LEFT JOIN (SELECT  COUNT(*) AS UnApproved, surveyor_id FROM day_activities WHERE activity_type = 'start' ");
			query.append(" AND activity_client_id NOT IN  (SELECT DISTINCT activity_client_id  FROM day_activities WHERE activity_type = 'end') AND surveyor_id = ? AND review_type = 'WW'");
			query.append(" AND MONTH(activity_date) = MONTH(NOW()) AND YEAR(activity_date) = YEAR(NOW())) s  ON s.surveyor_id = su.id  WHERE su.`id` = ?  ");

		} else if (type.equalsIgnoreCase("WR")) {
			query.append(" SELECT su.wr_target total_planned, COUNT(DISTINCT IF( da.`activity_type` = 'START', da.`id`, NULL ) ) total_attempted, ");
			query.append(" COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) ) total_completed,COALESCE(UnApproved, 0) AS UnApproved, COUNT(ms.`id`) shops_attempted, ");
			query.append(" IF(su.wr_target - COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) )<0, 0, ");
			query.append(" su.wr_target - COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) )) remaining");
			query.append(" FROM surveyor su LEFT JOIN `day_activities` da ON su.id = da.surveyor_id  AND da.review_type = 'WR'   AND MONTH(da.activity_date) = MONTH(NOW()) AND YEAR(da.activity_date) = YEAR(NOW())  ");
			query.append(" LEFT JOIN `merchandiser_surveys` ms ON ms.`activity_id` = da.`id`LEFT JOIN (SELECT  COUNT(*) AS UnApproved, surveyor_id FROM day_activities WHERE activity_type = 'start' ");
			query.append(" AND activity_client_id NOT IN  (SELECT DISTINCT activity_client_id  FROM day_activities WHERE activity_type = 'end') AND surveyor_id = ? AND review_type = 'WR'");
			query.append(" AND MONTH(activity_date) = MONTH(NOW()) AND YEAR(activity_date) = YEAR(NOW())) s  ON s.surveyor_id = su.id  WHERE su.`id` = ? ");
		}
		else if (type.equalsIgnoreCase("SA")) {
			query.append(" SELECT su.wr_target total_planned, COUNT(DISTINCT IF( da.`activity_type` = 'START', da.`id`, NULL ) ) total_attempted, ");
			query.append(" COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) ) total_completed,COALESCE(UnApproved, 0) AS UnApproved, COUNT(ms.`id`) shops_attempted, ");
			query.append(" IF(su.wr_target - COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) )<0, 0, ");
			query.append(" su.wr_target - COUNT(DISTINCT IF( da.`activity_type` = 'END', da.`id`, NULL ) )) remaining");
			query.append(" FROM surveyor su LEFT JOIN `day_activities` da ON su.id = da.surveyor_id  AND da.review_type = 'SA'   AND MONTH(da.activity_date) = MONTH(NOW()) AND YEAR(da.activity_date) = YEAR(NOW())  ");
			query.append(" LEFT JOIN `merchandiser_surveys` ms ON ms.`activity_id` = da.`id`LEFT JOIN (SELECT  COUNT(*) AS UnApproved, surveyor_id FROM day_activities WHERE activity_type = 'start' ");
			query.append(" AND activity_client_id NOT IN  (SELECT DISTINCT activity_client_id  FROM day_activities WHERE activity_type = 'end') AND surveyor_id = ? AND review_type = 'SA'");
			query.append(" AND MONTH(activity_date) = MONTH(NOW()) AND YEAR(activity_date) = YEAR(NOW())) s  ON s.surveyor_id = su.id  WHERE su.`id` = ? ");
		}

		logger.debug("Query: " + query.toString());
		SummaryData summaryData = null;
		try {

			summaryData = getJdbcTemplate().queryForObject(query.toString(),
					new Object[] { surveyorId,surveyorId}, new RowMapper<SummaryData>() {

						public SummaryData mapRow(ResultSet rs, int rowNum)
								throws SQLException {
							SummaryData summaryData = new SummaryData();
							if (type.equalsIgnoreCase("WW")) {
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL PLANNED WW", rs
												.getString("total_planned"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"REMAINING WW", rs
												.getString("remaining"), "Y"));
								
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL SUCCESSFULL WW", rs
												.getString("total_completed"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL UNSUCCESSFULL WW", rs
												.getString("UnApproved"),
										"Y"));
								
								summaryData.addSummaryTags(new SummaryTag(
										"SHOPS ACCOMPANIED", rs
												.getString("shops_attempted"),
										"Y"));
								
							} else if (type.equalsIgnoreCase("WR")) {
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL PLANNED WR", rs
												.getString("total_planned"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"REMAINING WR", rs
												.getString("remaining"), "Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL SUCCESSFULL WR", rs
												.getString("total_completed"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL UNSUCCESSFULL WR", rs
												.getString("UnApproved"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"SHOPS BACK CHECKED", rs
												.getString("shops_attempted"),
										"Y"));
								
							}
							else if (type.equalsIgnoreCase("SA")) {
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL SUCCESSFULL RA", rs
												.getString("total_completed"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"TOTAL UNSUCCESSFULL RA", rs
												.getString("UnApproved"),
										"Y"));
								summaryData.addSummaryTags(new SummaryTag(
										"SHOPS BACK CHECKED", rs
												.getString("shops_attempted"),
										"Y"));
								
							}

							return summaryData;
						}
					});
		}

		catch (Exception ex) {
			logger.error(
					"Error occurred while retrieving Summary Against surveyor id"
							+ surveyorId, ex);
		}
		return summaryData;
	}

	public int insertVisitSyncData(final SyncData syncData) {

		final StringBuilder query = new StringBuilder(
				"INSERT IGNORE INTO merchandiser_surveys ( shop_id, dsr_id  , image_url , visit_type ,");
		query.append(" visit_status , visit_date , remark_id , start_time , end_time , latitude , longitude, build_version , shelf_display_image_url , ");
		query.append(" surveyor_id , audio_url , tposm_display_image_url , madeAvailableRemark , notInterestedRemarkId , drop_size_remarkId , activity_id ,");
		query.append(" imei , employee_id, de_id ,area_id,`location_remark_id`, `shop_visit_remark`, `shop_remark_image_url`, `remark_reason`, `sub_remark_id`, is_segment_updated,dsr_employee_id )");
		query.append("  SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,area_id,?,?,?,?,?,?,? FROM shops WHERE shops.`id` = ?");

		logger.debug("Query: " + query);
		// source
		// https://github.com/albertattard/how-to-get-auto-generated-key-with-jdbctemplate
		/* The newly generated key will be saved in this object */
		final KeyHolder holder = new GeneratedKeyHolder();
		try {
			final PreparedStatementCreator psc = new PreparedStatementCreator() {
				@Override
				public PreparedStatement createPreparedStatement(
						final Connection connection) throws SQLException {
					final PreparedStatement ps = connection.prepareStatement(
							query.toString(), Statement.RETURN_GENERATED_KEYS);
					ps.setInt(1, syncData.getVisit().getShopId());
					ps.setInt(2, syncData.getDsrId());
					if (syncData.getVisit().getVisitImage() == null) {
						ps.setString(3, "");
					} else {
						ps.setString(3, syncData.getVisit().getVisitImage()
								.getImageUrl());
					}

					ps.setString(4, syncData.getVisit().getVisitType());

					ps.setString(5, syncData.getVisit().getVisitStatus());
					ps.setString(6, syncData.getVisit().getDateTime());
					ps.setInt(7, syncData.getVisit().getRemarkId());
					if (syncData.getVisit().getTime() != null) {
						ps.setString(8, syncData.getVisit().getTime()
								.getStartTime());
						ps.setString(9, syncData.getVisit().getTime()
								.getEndTime());
					} else {
						ps.setString(8, "");
						ps.setString(9, "");
					}
					if (syncData.getVisit().getLocation() != null) {
						ps.setString(10, syncData.getVisit().getLocation()
								.getLatitude());
						ps.setString(11, syncData.getVisit().getLocation()
								.getLongitude());
					} else {
						ps.setString(10, "");
						ps.setString(11, "");
					}
					ps.setString(12, syncData.getBuildVersion().split("_")[0]);
					if (syncData.getVisit().getShelfDisplay() != null) {
						ps.setString(13, syncData.getVisit().getShelfDisplay()
								.getImageUrl());
					} else {
						ps.setString(13, "");
					}

					ps.setInt(14, syncData.getSurveyorId());
					if (syncData.getVisit().getAudioFile() != null) {
						ps.setString(15, syncData.getVisit().getAudioFile()
								.getUrl());
					} else {
						ps.setString(15, "");
					}

					if (syncData.getVisit().getTposmImage() == null
							|| syncData.getVisit().getIsTposmAvailable()
									.equals("N")) {
						ps.setString(16, "");
					} else if (syncData.getVisit().getTposmImage() != null
							|| syncData.getVisit().getIsTposmAvailable()
									.equals("Y")) {
						ps.setString(16, syncData.getVisit().getTposmImage()
								.getImageUrl());
					} else {
						ps.setString(16, "");
					}
					if (syncData.getVisit().getDropSize() != null) {
						ps.setInt(17, syncData.getVisit().getDropSize()
								.getMadeAvailableRemark());
					} else {
						ps.setInt(17, 0);
					}
					if (syncData.getVisit().getDropSize() != null) {
						ps.setInt(18, syncData.getVisit().getDropSize()
								.getNotInterestedRemarkId());
					} else {
						ps.setInt(18, 0);
					}
					if (syncData.getVisit().getDropSize() != null) {
						ps.setInt(19, syncData.getVisit().getDropSize()
								.getRemarkId());
					} else {
						ps.setInt(19, 0);
					}
					ps.setInt(20, syncData.getVisit().getActivityId());
					ps.setString(21, syncData.getImei());
					ps.setInt(22, syncData.getEmployeeId());
					ps.setInt(23, syncData.getVisit().getDeId());
					ps.setInt(24, !StringUtils.isNullOrEmptyInteger(syncData
							.getVisit().getLocationRemarkId()) ? syncData
							.getVisit().getLocationRemarkId() : -2);
					if (syncData.getVisit().getVisitRemarks() != null) {
						ps.setString(25, syncData.getVisit().getVisitRemarks());
					} else {
						ps.setString(25, null);
					}
					if (syncData.getVisit().getRemarkImage() != null) {
						ps.setString(26, syncData.getVisit().getRemarkImage()
								.getImageUrl());
					} else {
						ps.setString(26, null);
					}
					if (syncData.getVisit().getRemarkReason() != null) {
						ps.setString(27, syncData.getVisit().getRemarkReason());
					} else {
						ps.setString(27, null);
					}
					ps.setInt(28, syncData.getVisit()
							.getTemporaryCloseRemarkId());
					if (syncData.getVisit().getIsSegmentUpdated() != null) {
						ps.setString(29, syncData.getVisit()
								.getIsSegmentUpdated());
					} else {
						ps.setString(29, "N");
					}
					ps.setInt(30, syncData.getDsrEmployeeId());

					ps.setInt(31, syncData.getVisit().getShopId());
					return ps;
				}
			};

			getJdbcTemplate().update(psc, holder);
		} catch (Exception e) {
			logger.error(e, e);
		}

		return holder.getKey().intValue();

	}

	public void insertSyncQuestionData(final SyncData syncData,
			final int surveyId) throws Exception {
		StringBuilder query = new StringBuilder(
				"INSERT INTO merchandiser_survey_questions (question_id, remark_id , shop_id , merchandiser_surveyor_id , created_datetime , option_value , image_url, image_datetime");
		query.append(" , question_type_id ) VALUES (?,?,?,?,?,?,?,?,?)");
		logger.debug("Query: " + query);
		try {
			final int batchSize = syncData.getVisit().getQuestionData().size();
			getJdbcTemplate().batchUpdate(query.toString(),
					new BatchPreparedStatementSetter() {

						public int getBatchSize() {

							return batchSize;
						}

						public void setValues(PreparedStatement ps, int i)
								throws SQLException {

							logger.debug("Batch Size = " + getBatchSize()
									+ "   i=" + i);

							SyncQuestion questionData = syncData.getVisit()
									.getQuestionData().get(i);
							ps.setInt(1, questionData.getQuestionId());
							ps.setInt(2, questionData.getRemarkId());
							ps.setInt(3, surveyId);
							ps.setInt(4, syncData.getSurveyorId());
							ps.setString(
									5,
									DateTimeUtilities
											.getCurrentDate(DateTimeConstants.DATE_TIME_FORMAT));
							ps.setString(6, questionData.getOptionValue());
							if (questionData.getImage() != null) {
								ps.setString(7, questionData.getImage()
										.getImageUrl());
								ps.setString(8, "");
							} else if (questionData.getQuestionImage() != null) {
								ps.setString(7, questionData.getQuestionImage()
										.getImageUrl());
								ps.setString(8, questionData.getQuestionImage()
										.getImageTime());
							} else {
								ps.setString(7, "");
								ps.setString(8, "");
							}
							ps.setInt(9, questionData.getQuestionTypeId());

						}

					});
		} catch (Exception e) {
			logger.error(e, e);
		}

	}

	public List<Dsr> getDsrList(Integer surveyorId) {

		StringBuilder query = new StringBuilder(
				"SELECT d.id,dsr_emp.`employee_name` full_name , sd.surveyor_id,d.dsr_type, dsr_emp.id dsr_employee_id ");
		query.append(" FROM dsr d INNER JOIN surveyor_dsrs sd ON d.id = sd.`dsr_id` INNER JOIN `employee` dsr_emp ON dsr_emp.`id` = d.`dsr_employee` ");
		query.append(" WHERE d.active = 'Y' AND sd.active ='Y'");

		logger.debug("Query: " + query.toString());

		return getJdbcTemplate().query(query.toString(), new Object[] {},
				new RowMapper<Dsr>() {

					@Override
					public Dsr mapRow(ResultSet rs, int rowNum)
							throws SQLException {

						Dsr dsr = new Dsr();
						dsr.setId(rs.getInt("id"));
						dsr.setFullName(rs.getString("full_name"));
						dsr.setType(rs.getString("dsr_type"));
						dsr.setSurveyorId(rs.getInt("surveyor_id"));
						dsr.setEmployeeId(rs.getInt("dsr_employee_id"));

						return dsr;
					}

				});
	}

	public SingleSurvey getSurveyorLastStartActivity(Integer surveyorId) {

		StringBuilder query = new StringBuilder();
		query.append(" SELECT da.`id`,da.`surveyor_id`,da.`activity_type`, da.`review_type`, da.`activity_client_id`,da.`dsr_id`, e.id dsr_employee_id, DATE_FORMAT(da.activity_date, '%Y-%m-%d %H:%i:%s') AS activity_date, ");
		query.append(" da.am_id,da.am_employee_id FROM `day_activities` da INNER JOIN dsr d ON d.id = da.dsr_id INNER JOIN employee e ON e.id = d.dsr_employee ");
		query.append(" WHERE da.`surveyor_id` = " + surveyorId
				+ " AND da.active = 'Y' AND da.`activity_type` = 'START' ");

		logger.debug("Query: " + query);
		SingleSurvey singleSurvey = null;
		try {
			singleSurvey = getJdbcTemplate().queryForObject(query.toString(),
					new RowMapper<SingleSurvey>() {

						public SingleSurvey mapRow(ResultSet rs, int rowNum)
								throws SQLException {

							SingleSurvey singleSurvey = new SingleSurvey();
							singleSurvey.setSurveyorId(rs.getInt("surveyor_id"));
							singleSurvey.setDsrId(rs.getInt("dsr_id"));
							singleSurvey.setEmployeeId(rs
									.getInt("dsr_employee_id"));
							Activity activity = new Activity();
							activity.setActivityClientId(rs
									.getString("activity_client_id"));
							activity.setActivityId(rs.getInt("id"));
							activity.setActivityType(rs
									.getString("activity_type"));
							activity.setReviewType(rs.getString("review_type"));
							activity.setActivityDate(rs.getString("activity_date"));
							singleSurvey.setAmId(rs.getInt("am_id"));
							singleSurvey.setAmEmployeeId(rs.getInt("am_employee_id"));
							singleSurvey.setActivity(activity);
							return singleSurvey;
						}
					});
		} catch (EmptyResultDataAccessException erde) {

			logger.error("No last start activity against surveyor : "
					+ surveyorId);
		} catch (Exception ex) {

			logger.error("Error occurred while retrieving surveyor : "
					+ surveyorId, ex);

		}

		return singleSurvey;
	}

	public List<Surveyor> getAMList() {

		StringBuilder query = new StringBuilder(
				" SELECT s.`id`, s.`m_code`, CONCAT(s.`m_code`,' - ',s.`full_name`) AS `full_name` , s.`surveyor_type`, tm.id supervisor_id, s.`employee_id` FROM  surveyor tm  ");
		query.append(" INNER JOIN surveyor_regions sr ON sr.`surveyor_id` = tm.`id` AND tm.surveyor_type='RSM' INNER JOIN `v_surveyor` s");
		query.append(" ON sr.region_id = s.region_id WHERE tm.`active` = 'Y' AND s.`active` = 'Y' AND s.surveyor_type ='AM' ");

		logger.debug("Query: " + query);

		return getJdbcTemplate().query(query.toString(), new Object[] {},
				new RowMapper<Surveyor>()

				{
					@Override
					public Surveyor mapRow(ResultSet rs, int rowNum)
							throws SQLException {
						Surveyor supervisor = new Surveyor();
						supervisor.setId(rs.getInt("id"));
						supervisor.setFullName(rs.getString("full_name"));
						supervisor.setmCode(rs.getString("m_code"));
						supervisor.setSurveyorType(SurveyorType.valueOf(rs
								.getString("surveyor_type")));
						supervisor.setSupervisorId(rs.getInt("supervisor_id"));
						supervisor.setDeEmployeeId(rs.getInt("employee_id"));
						return supervisor;
					}
				});
	}

	public List<ShopRoute> shopList(Integer routeId, Integer dsrId)
			throws Exception {
		final StringBuilder query = new StringBuilder();

		query.append(" SELECT s.id, s.shop_code, CONCAT(s.`shop_code`,' - ',TRIM(UPPER(s.shop_title)),' - ',sg.`title`) shop_title, s.phone, s.owner_name, s.area_id,");
		query.append(" s.vicinity, s.landmark, s.city_id, s.address,  s.is_chiller_allocated, s.abnormal, s.street_lane,s.phase, s.road, s.sub_area, s.shop_area, ");
		query.append(" d.id dsr_id, dsr_emp.`employee_name` dsr_name,s.remark_id, s.group_id, s.category_id, s.validation_status,  ");
		query.append(" s.new_shop, pposm,s.latitude, s.longitude, ");
		query.append(" is_incomplete, dr.`route_id`, a.`title` `area`, s.de_validation_status ");

		query.append(" FROM `dsr_routes` dr INNER JOIN `dsr_shops` ds ON dr.`id` = ds.`dsr_route_id` INNER JOIN `shops` s ON ds.`shop_id` = s.`id` ");
		query.append(" INNER JOIN `dsr` d ON dr.`dsr_id` = d.`id` INNER JOIN `employee` dsr_emp ON dsr_emp.`id` = d.`dsr_employee` ");
		query.append(" INNER JOIN `areas` a ON a.`id` = s.`area_id` ");
		query.append(" INNER JOIN `shop_group` sg ON sg.`id` = s.`group_id`  ");

		query.append(" WHERE dr.`active` = 'Y' AND s.active = 'Y' AND a.active = 'Y' ");

		// query.append(" SELECT 1 id, '' shop_code, '' shop_title, 1 phone,  '' owner_name, 1 area_id,");
		// query.append(" '' vicinity, '' landmark, 1 city_id, '' address,  '1' is_chiller_allocated, '1' abnormal,  '1' street_lane, '1' phase, '1' road,  '1' sub_area, '1' shop_area, ");
		// query.append(" 1 dsr_id, '1' dsr_name, 1 remark_id, 1 group_id, 1 category_id, '1' validation_status,  ");
		// query.append(" '1' new_shop, 'N' pposm, '1' latitude, '1' longitude, ");
		// query.append(" '1' is_incomplete, 1 `route_id`, '1' `area`,  '1' de_validation_status ");

		if (routeId > 0) {

			query.append(" AND dr.`route_id` =  " + routeId);
		}
		if (dsrId > 0) {

			query.append(" AND dr.`dsr_id` = " + dsrId);
		}


		logger.info("Query: " + query.toString());

		List<ShopRoute> list = new ArrayList<ShopRoute>();
		try {
			list = getJdbcTemplate().query(query.toString(), new Object[] {},
					new RowMapper<ShopRoute>() {

						@Override
						public ShopRoute mapRow(ResultSet rs, int rowNum)
								throws SQLException {

							ShopRoute shop = new ShopRoute();
							shop.setId(rs.getInt("id"));
							shop.setCityId(rs.getInt("city_id"));

							shop.setShopCode(rs.getString("shop_code"));
							shop.setShopTitle(rs.getString("shop_title"));
							shop.setAddress(rs.getString("address"));
							shop.setIsChillerAllocated(rs
									.getString("is_chiller_allocated"));
							shop.setPhone(rs.getString("phone"));
							shop.setOwnerName(rs.getString("owner_name"));
							shop.setAreaId(rs.getInt("area_id"));
							shop.setVicinity(rs.getString("vicinity"));

							shop.setLandmark(rs.getString("landmark"));
							shop.setStreetLane(rs.getString("street_lane"));

							shop.setPhase(rs.getString("phase"));
							shop.setRoad(rs.getString("road"));

							shop.setSubArea(rs.getString("sub_area"));
							shop.setShopArea(rs.getString("shop_area"));
							shop.setDsrId(rs.getInt("dsr_id"));
							shop.setDsrName(rs.getString("dsr_name"));
							shop.setRemarkId(rs.getInt("remark_id"));

							shop.setGroupId(rs.getInt("group_id"));
							shop.setCategoryId(rs.getInt("category_id"));
							shop.setValidationStatus(rs
									.getString("validation_status"));
							shop.setDeValidationStatus(rs
									.getString("de_validation_status"));
							shop.setNewShop(rs.getString("new_shop"));
							shop.setIsPposm(rs.getString("pposm"));
							shop.setLatitude(rs.getString("latitude"));
							shop.setLongitude(rs.getString("longitude"));
							shop.setIsIncomplete(rs.getString("is_incomplete"));
							shop.setRouteId(rs.getInt("route_id"));
							shop.setArea(rs.getString("area"));
							return shop;
						}

					});
		}

		catch (Exception ex) {
			logger.error(
					"Error occurred while retrieving ShopList Against surveyor id"
							+ dsrId, ex);
		}

		return list;
	}

	public List<Map<String, Object>> getAMSummary(Integer surveyorId,
			String date, Integer dsrId, String activityClientId) {

		StringBuilder query = new StringBuilder();

		query.append(" SELECT zm.`full_name` zm_name, rm.`full_name` rm_name,am.`full_name` am_name, bde.`full_name` bde_name,s.full_name surveyorName, da.review_type, ");
		query.append(" DATE(da.activity_date) activity_date, q.title question, dad.remark_value answer,");
		query.append(" get_rating(dad.id, q.question_type) rating, q.question_type ");
		query.append(" FROM day_activities da INNER JOIN day_activity_details dad ON dad.activity_id=da.id ");
		query.append(" AND da.activity_client_id='" + activityClientId + "'");
		query.append(" INNER JOIN questions q ON q.id=dad.question_id INNER JOIN v_surveyor s ON s.id=da.surveyor_id");
		if (surveyorId > -1) {
			query.append(" AND da.surveyor_id=" + surveyorId);
		}
		if (dsrId > -1) {
			query.append(" AND da.dsr_id=" + dsrId);
		}
		query.append(" AND q.id<>11");
		query.append(" INNER JOIN `v_dsr` bde  ON bde.`id` = da.`dsr_id`");
		query.append(" INNER JOIN surveyor_dsrs sd ON sd.`dsr_id` = bde.`id` AND sd.`active` = 'Y'");
		query.append(" INNER JOIN v_surveyor am ON am.`id` = sd.`surveyor_id` INNER JOIN surveyor_regions sr ON sr.`region_id` = am.`region_id`");
		query.append(" INNER JOIN v_surveyor rm ON sr.`surveyor_id` = rm.`id` AND rm.`surveyor_type` = 'RSM'");
		query.append(" INNER JOIN `surveyor_regions` sr1 ON sr1.`surveyor_id` = rm.`id`");
		query.append(" INNER JOIN surveyor_regions sr2 ON sr1.`region_id` = sr2.`region_id`");
		query.append(" INNER JOIN `v_surveyor` zm ON zm.`id` = sr2.`surveyor_id` AND zm.`surveyor_type` = 'ZM' ORDER BY bde.id ASC");
		List<Map<String, Object>> listMap = null;
		try {
			listMap = getJdbcTemplate().queryForList(query.toString(),
					new Object[] {});
		} catch (Exception e) {
			logger.error(e, e);
		} finally {
		}
		return listMap;
	}
	
	public SingleSurvey getNMLastStartActivity(Integer surveyorId) {

		StringBuilder query = new StringBuilder();
		query.append(" SELECT da.`id`,da.`surveyor_id`,da.`activity_type`, da.`review_type`, da.`activity_client_id`,da.`am_id`,e.id employee_id  ");
		query.append(" FROM `day_activities` da INNER JOIN surveyor s ON s.`id` = da.`am_id` INNER JOIN employee e ON e.id = s.`employee_id` ");
		query.append(" WHERE da.`surveyor_id` = " + surveyorId
				+ " AND da.active = 'Y' AND da.`activity_type` = 'START' ");

		logger.info("single Survey =: " + query);
		SingleSurvey singleSurvey = null;
		try {
			singleSurvey = getJdbcTemplate().queryForObject(query.toString(),
					new RowMapper<SingleSurvey>() {

						public SingleSurvey mapRow(ResultSet rs, int rowNum)
								throws SQLException {

							SingleSurvey singleSurvey = new SingleSurvey();
							singleSurvey.setSurveyorId(rs.getInt("surveyor_id"));
							singleSurvey.setAmId(rs.getInt("am_id"));
							singleSurvey.setAmEmployeeId(rs
									.getInt("employee_id"));
							Activity activity = new Activity();
							activity.setActivityClientId(rs
									.getString("activity_client_id"));
							activity.setActivityId(rs.getInt("id"));
							activity.setActivityType(rs
									.getString("activity_type"));
							activity.setReviewType(rs.getString("review_type"));
							singleSurvey.setActivity(activity);
							return singleSurvey;
						}
					});
		} catch (EmptyResultDataAccessException erde) {

			logger.error("No last start activity against surveyor : "
					+ surveyorId);
		} catch (Exception ex) {

			logger.error("Error occurred while retrieving surveyor : "
					+ surveyorId, ex);

		}

		return singleSurvey;
	}

	
	public String getActivityStatus(Integer surveyorId) {
		StringBuilder query = new StringBuilder();
		if (Arrays.asList(116, 97, 98, 99, 100, 114,129,130,131).contains(surveyorId))
       {
	     query.append(" SELECT 'Y' allow_status FROM day_activities da WHERE da.`visit_date` = DATE(NOW()) ");
	     query.append(" AND da.`surveyor_id` = ? ");
      }
       else
      {
	     query.append(" SELECT CASE WHEN COUNT(da.`active`) = 4 OR COUNT(da.`active`) = 5 THEN 'N' ELSE 'Y' END allow_status FROM day_activities da WHERE da.`visit_date` = DATE(NOW()) ");
	     query.append(" AND da.`surveyor_id` = ? ");
}  
		
		String activityStatus = "";
		try {
			activityStatus =  getJdbcTemplate().queryForObject(query.toString(),
					new Object[] { surveyorId }, new RowMapper<String>(){
				@Override
				public String mapRow(ResultSet rs, int rowNum)
						throws SQLException {

					return rs.getString("allow_status");
				}
			});
		}catch (EmptyResultDataAccessException erd) {

			logger.error("No Data found against activityStatus  : " + activityStatus);
		} catch (Exception e) {

			logger.error(e, e);
		}
		return activityStatus;
	}
	
	
	public String getendActivityStatus(Integer surveyorId, Integer activityId) {
		StringBuilder query = new StringBuilder();
		
		 if (Arrays.asList(116, 97, 98, 99, 100, 114,129,130,131).contains(surveyorId))
         {
	         query.append(" SELECT 'N' allow_status FROM merchandiser_surveys ms INNER JOIN surveyor s ");
	         query.append(" ON s.id = ms.surveyor_id  WHERE ms.`surveyor_id` = ? AND ms.`activity_id` = ? ");
         }
          else
         {
        	 query.append(" SELECT CASE WHEN COUNT(ms.activity_id) < s.wr_shops  THEN 'N' WHEN ms.activity_id IS NULL THEN 'N' ELSE 'Y' END allow_status FROM merchandiser_surveys ms INNER JOIN surveyor s ");
     		query.append(" ON s.id = ms.surveyor_id  WHERE ms.`surveyor_id` = ? AND ms.`activity_id` = ? ");
         }
		
		String activityStatus = "";
//		logger.info("status: "+ activityStatus);
//		logger.info("query: "+ query);
//		logger.info("surveyorId: "+ surveyorId);
//		logger.info("activityId: "+ activityId);
		try {
			activityStatus =  getJdbcTemplate().queryForObject(query.toString(),
					new Object[] { surveyorId, activityId }, new RowMapper<String>(){
				@Override
				public String mapRow(ResultSet rs, int rowNum)
						throws SQLException {

					return rs.getString("allow_status");
				}
			});
		}catch (EmptyResultDataAccessException erd) {

			logger.error("No Data found against endActivityStatus  : " + activityStatus);
		} catch (Exception e) {

			logger.error(e, e);
		}
		return activityStatus;
	}
	
	public List<Map<String, Object>> marginCalculatorList() {
		StringBuilder query = new StringBuilder();

	

		query.append(" SELECT pm.id,pm.`product_id` AS `productId`,p.`title` AS `productTitle`,pm.`retailer_type` AS `retailerType`,pm.`retailer_price_per_pack` AS `pricePerPack`,pm.`retailer_seling_price_per_pack` AS `sellingPricePerPack`,pm.`product_type` AS `productType`,p.`order_id` AS `orderId` ");
		query.append(" FROM `product_margins`pm INNER JOIN `products`p ON p.`id`=pm.`product_id` WHERE pm.active='Y' ");

			List<Map<String, Object>> listMap = null;
			try {

				listMap = getJdbcTemplate().queryForList(query.toString(),
						new Object[] { });

			} catch (Exception e) {
				logger.error(e, e);
			} finally {
			}
			return listMap;

		

	}
	public List<Map<String, Object>> trainingMaterial() {
		StringBuilder query = new StringBuilder();

	

		query.append(" SELECT mt.`id`,mt.`title`,mt.`type`,mt.`url` FROM `training_material` mt WHERE mt.`active` = 'Y' ");

			List<Map<String, Object>> listMap = null;
			try {

				listMap = getJdbcTemplate().queryForList(query.toString(),
						new Object[] { });

			} catch (Exception e) {
				logger.error(e, e);
			} finally {
			}
			return listMap;

		

	}
	
	public void insertCeFaq(FAQ ceFaq, Integer surveyorId) {
	    StringBuilder query = new StringBuilder(
	    		
	            "INSERT INTO `ce_survey_faq` (`surveyor_id`, `brand_id`, `faq`) VALUES (?, ?, ?)");

	    try {
	        getJdbcTemplate().update(query.toString(), surveyorId, ceFaq.getBrandId(), ceFaq.getFeedback());
	    } catch (Exception e) {
	        logger.error(e, e);
	    }
	}

	public List<Map<String, Object>> programList() {
		StringBuilder query = new StringBuilder();

		query.append(" SELECT p.`id`, p.`title`, s.`shop_id` `shopId`  FROM `shop_program` s  INNER JOIN `program` p  ON p.`id` = s.`program_id`  WHERE s.`active` = 'Y'  ");

			List<Map<String, Object>> listMap = null;
			try {

				listMap = getJdbcTemplate().queryForList(query.toString(),
						new Object[] { });

			} catch (Exception e) {
				logger.error(e, e);
			} finally {
			}
			return listMap;

		

	}
	
	public void insertRedFlagRemarkData(final SyncData syncData, final int surveyId) throws Exception {
	    String query = "INSERT INTO red_flag_remark (merchandiser_survey_id, sender_remark, sender_datetime, shop_id, sender_id, sender_name) VALUES (?,?,?,?,?,?)";
	    logger.debug("Query: " + query);
	    
	    try {
	        getJdbcTemplate().update(query, surveyId, syncData.getVisit().getRedFlagRemark(), 
	                DateTimeUtilities.getCurrentDate(DateTimeConstants.DATE_TIME_FORMAT),syncData.getVisit().getShopId(),syncData.getSurveyorId(),syncData.getSurveyorName());
	    } catch (Exception e) {
	        logger.error(e, e);
	        throw e;  // Re-throw the exception after logging
	    }
	}

	public List<Map<String, Object>> remarkShopList(Integer amId, Integer rsmId) {
		StringBuilder query = new StringBuilder();
		
		if (amId > 0) 
		{
		query.append(" SELECT DISTINCT sh.id, CONCAT(sh.id, ' - ', sh.shop_title) AS shopTitle, ");
		query.append(" rf.sender_remark, rf.sender_name, rf.receiver_remark, rf.receiver_name, ");
		query.append(" rf.is_close, rf.id AS record_id, rf.sender_datetime AS senderDatetime, ");
		query.append(" rf.receiver_datetime AS receiverDatetime ");
		query.append(" FROM  ");
		query.append(" v_surveyor am  INNER JOIN surveyor_regions sr ON sr.region_id = am.region_id  ");
		query.append(" INNER JOIN v_surveyor rm ON sr.surveyor_id = rm.id  AND rm.surveyor_type = 'RSM' INNER JOIN surveyor_regions sr1 ON sr1.surveyor_id = rm.id  ");
		query.append(" INNER JOIN surveyor_regions sr2 ON sr1.region_id = sr2.region_id INNER JOIN v_surveyor zm ON zm.id = sr2.surveyor_id AND zm.surveyor_type = 'ZM'  ");
		query.append(" INNER JOIN red_flag_remark rf ON rf.sender_id IN (rm.id, zm.id) INNER JOIN shops sh ON sh.`id` = rf.`shop_id` WHERE am.id = ? ");

		}
		else
		{
			query.append(" SELECT DISTINCT sh.id, CONCAT(sh.id, ' - ', sh.shop_title) AS shopTitle, ");
			query.append(" rf.sender_remark, rf.sender_name, rf.receiver_remark, rf.receiver_name, ");
			query.append(" rf.is_close, rf.id AS record_id, rf.sender_datetime AS senderDatetime, ");
			query.append(" rf.receiver_datetime AS receiverDatetime ");
			query.append(" FROM red_flag_remark rf INNER JOIN shops sh ON sh.`id` = rf.`shop_id` WHERE rf.`sender_id` = ? ");

		}
		

		List<Map<String, Object>> listMap = null;
		try {
			// Execute the query with the appropriate parameter
			listMap = getJdbcTemplate().queryForList(query.toString(),
					new Object[] { amId > 0 ? amId : rsmId });
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return listMap;
	}

	
	public void updateRedFlag(RedFlagChatResponse redFlag) {
	    StringBuilder query = new StringBuilder(
	    		
	            " UPDATE `red_flag_remark` rf SET rf.`receiver_id` = ? , rf.`receiver_name` = ? , rf.`receiver_remark` = ?, rf.`receiver_datetime` = ?, rf.`is_close` = ? WHERE rf.`id` = ? ");

	    try {
	        getJdbcTemplate().update(query.toString(), redFlag.getReceiverId(),redFlag.getReceiverName(),redFlag.getReceiverRemark(),
	        		DateTimeUtilities.getCurrentDate(DateTimeConstants.DATE_TIME_FORMAT),redFlag.getIsClose(), redFlag.getRecordId());
	    } catch (Exception e) {
	        logger.error(e, e);
	    }
	}
	
	public List<ShopRoute> amShopList(Integer routeId, Integer amId)
			throws Exception {
		final StringBuilder query = new StringBuilder();

		query.append(" SELECT s.id, s.shop_code, CONCAT(s.`shop_code`,' - ',TRIM(UPPER(s.shop_title)),' - ',sg.`title`) shop_title, s.phone, s.owner_name, s.area_id,");
		query.append(" s.vicinity, s.landmark, s.city_id, s.address,  s.is_chiller_allocated, s.abnormal, s.street_lane,s.phase, s.road, s.sub_area, s.shop_area, ");
		query.append(" d.id dsr_id, dsr_emp.`employee_name` dsr_name,s.remark_id, s.group_id, s.category_id, s.validation_status,  ");
		query.append(" s.new_shop, pposm,s.latitude, s.longitude, ");
		query.append(" is_incomplete, dr.`route_id`, a.`title` `area`, s.de_validation_status, am.`id` AS `amId` ");

		query.append(" FROM `dsr_routes` dr INNER JOIN `dsr_shops` ds ON dr.`id` = ds.`dsr_route_id` INNER JOIN `shops` s ON ds.`shop_id` = s.`id` ");
		query.append(" INNER JOIN `dsr` d ON dr.`dsr_id` = d.`id` INNER JOIN `employee` dsr_emp ON dsr_emp.`id` = d.`dsr_employee` ");
		query.append(" INNER JOIN `areas` a ON a.`id` = s.`area_id` ");
		query.append(" INNER JOIN `shop_group` sg ON sg.`id` = s.`group_id`  ");
	    query.append(" INNER JOIN surveyor_dsrs sd  ON sd.`dsr_id` = dr.`dsr_id` AND sd.active = 'Y' INNER JOIN surveyor am ON am.`id` = sd.`surveyor_id`  ");
		query.append(" WHERE dr.`active` = 'Y' AND s.active = 'Y' AND a.active = 'Y' ");

		// query.append(" SELECT 1 id, '' shop_code, '' shop_title, 1 phone,  '' owner_name, 1 area_id,");
		// query.append(" '' vicinity, '' landmark, 1 city_id, '' address,  '1' is_chiller_allocated, '1' abnormal,  '1' street_lane, '1' phase, '1' road,  '1' sub_area, '1' shop_area, ");
		// query.append(" 1 dsr_id, '1' dsr_name, 1 remark_id, 1 group_id, 1 category_id, '1' validation_status,  ");
		// query.append(" '1' new_shop, 'N' pposm, '1' latitude, '1' longitude, ");
		// query.append(" '1' is_incomplete, 1 `route_id`, '1' `area`,  '1' de_validation_status ");

		if (routeId > 0) {

			query.append(" AND dr.`route_id` =  " + routeId);
		}
		if (amId > 0) {

			query.append(" AND am.`id` =  " + amId);
		}

		query.append(" GROUP BY s.`id`, dr.`dsr_id`, am.id ");
		

		logger.info("Query: " + query.toString());

		List<ShopRoute> list = new ArrayList<ShopRoute>();
		try {
			list = getJdbcTemplate().query(query.toString(), new Object[] {},
					new RowMapper<ShopRoute>() {

						@Override
						public ShopRoute mapRow(ResultSet rs, int rowNum)
								throws SQLException {

							ShopRoute shop = new ShopRoute();
							shop.setId(rs.getInt("id"));
							shop.setCityId(rs.getInt("city_id"));

							shop.setShopCode(rs.getString("shop_code"));
							shop.setShopTitle(rs.getString("shop_title"));
							shop.setAddress(rs.getString("address"));
							shop.setIsChillerAllocated(rs
									.getString("is_chiller_allocated"));
							shop.setPhone(rs.getString("phone"));
							shop.setOwnerName(rs.getString("owner_name"));
							shop.setAreaId(rs.getInt("area_id"));
							shop.setVicinity(rs.getString("vicinity"));

							shop.setLandmark(rs.getString("landmark"));
							shop.setStreetLane(rs.getString("street_lane"));

							shop.setPhase(rs.getString("phase"));
							shop.setRoad(rs.getString("road"));

							shop.setSubArea(rs.getString("sub_area"));
							shop.setShopArea(rs.getString("shop_area"));
							shop.setDsrId(rs.getInt("dsr_id"));
							shop.setDsrName(rs.getString("dsr_name"));
							shop.setRemarkId(rs.getInt("remark_id"));

							shop.setGroupId(rs.getInt("group_id"));
							shop.setCategoryId(rs.getInt("category_id"));
							shop.setValidationStatus(rs
									.getString("validation_status"));
							shop.setDeValidationStatus(rs
									.getString("de_validation_status"));
							shop.setNewShop(rs.getString("new_shop"));
							shop.setIsPposm(rs.getString("pposm"));
							shop.setLatitude(rs.getString("latitude"));
							shop.setLongitude(rs.getString("longitude"));
							shop.setIsIncomplete(rs.getString("is_incomplete"));
							shop.setRouteId(rs.getInt("route_id"));
							shop.setArea(rs.getString("area"));
							shop.setAmId(rs.getInt("amId"));
							return shop;
						}

					});
		}

		catch (Exception ex) {
			logger.error(
					"Error occurred while retrieving ShopList Against surveyor id"
							+ amId, ex);
		}

		return list;
	}
	
	
	public List<Map<String, Object>> getBAAUDITSummary(Integer surveyorId,
			String date, Integer dsrId, String activityClientId) {

		StringBuilder query = new StringBuilder();

		query.append(" SELECT qt.`title`, qt.`question_type`, q.`title` `question`, msq.`option_value` `answer`,  ");
		query.append(" sh.`shop_title`, e.`employee_name`,s.`surveyor_type` FROM `merchandiser_survey_questions` msq  ");
		query.append(" INNER JOIN questions q ON q.`id` = msq.`question_id` INNER JOIN `question_types` qt  ");
		query.append("  ON qt.`id` = msq.`question_type_id` INNER JOIN shops sh  ON sh.`id` = msq.`shop_id` INNER JOIN surveyor s  ");
		query.append(" ON s.`id` = msq.`merchandiser_surveyor_id`INNER JOIN employee e  ON e.`id`  = s.`employee_id` ");
		query.append(" WHERE msq.`merchandiser_surveyor_id` =" + surveyorId);
		query.append(" AND DATE(msq.`created_datetime`) = '" + date + "'");
		query.append(" AND msq.`question_type_id` IN (70, 71, 72, 73, 77, 78, 79, 80, 81, 82)  ");
	
		List<Map<String, Object>> listMap = null;
		try {
			listMap = getJdbcTemplate().queryForList(query.toString(),
					new Object[] {});
		} catch (Exception e) {
			logger.error(e, e);
		} finally {
		}
		return listMap;
	}
	

}
