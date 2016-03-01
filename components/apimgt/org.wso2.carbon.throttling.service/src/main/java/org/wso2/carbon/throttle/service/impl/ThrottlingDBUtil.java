/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.throttle.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.throttle.service.dto.ThrottledEventDTO;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ThrottlingDBUtil {

    private static final Log log = LogFactory.getLog(ThrottlingDBUtil.class);

    private static volatile DataSource dataSource = null;
    private static final String DB_CHECK_SQL = "SELECT * FROM AM_SUBSCRIBER";

    private static final String DB_CONFIG = "Database.";

    public static void initialize() throws Exception {
        if (dataSource != null) {
            return;
        }

        synchronized (ThrottlingDBUtil.class) {
            if (dataSource == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Initializing data source");
                }
                String dataSourceName = "apimgt_throttle_ds";

                if (dataSourceName != null) {
                    try {
                        Context ctx = new InitialContext();
                        dataSource = (DataSource) ctx.lookup(dataSourceName);
                    } catch (NamingException e) {
                        throw new Exception("Error while looking up the data " +
                                "source: " + dataSourceName, e);
                    }
                }
            }
        }
    }


    /**
     * Utility method to get a new database connection
     *
     * @return Connection
     * @throws SQLException if failed to get Connection
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        else {
            try {
                initialize();
                return dataSource.getConnection();

            } catch (Exception e) {
                throw new SQLException("Data source is not configured properly.");
            }
        }

    }

    public static ThrottledEventDTO[] getThrottledEvents(String query) {


        ThrottledEventDTO[] throttledEventDTOs = null;
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<ThrottledEventDTO> throttledEventDTOList = new ArrayList<ThrottledEventDTO>();
        String sqlQuery = "select THROTTLE_KEY from ThrotleTable";
        try {
            conn = ThrottlingDBUtil.getConnection();
            ps = conn.prepareStatement(sqlQuery);
            rs = ps.executeQuery();
            while (rs.next()) {
                String throttleKey = rs.getString("THROTTLE_KEY");
                ThrottledEventDTO throttledEventDTO = new ThrottledEventDTO();
                throttledEventDTO.setThrottleKey(throttleKey);
                throttledEventDTOList.add(throttledEventDTO);
            }
            int count = 1;
            if(query != null && query.length()> 0){
                count =  Integer.parseInt(query);
                for (int i = 0; i < count; i++ ){
                    ThrottledEventDTO throttledEventDTO = new ThrottledEventDTO();
                    throttledEventDTO.setThrottleKey("key_"+i);
                    throttledEventDTOList.add(throttledEventDTO);
                }
            }

            throttledEventDTOs = throttledEventDTOList.toArray(new ThrottledEventDTO[throttledEventDTOList.size()]);
        } catch (SQLException e) {
            log.error("Error while executing SQL", e);
        } finally {
            ThrottlingDBUtil.closeAllConnections(ps, conn, rs);
        }
        return throttledEventDTOs;
    }


    public static String getThrottledEventsAsString(String query) {

        String throttledEventString = "";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String sqlQuery = "select THROTTLE_KEY from ThrotleTable";
        try {
            conn = ThrottlingDBUtil.getConnection();
            ps = conn.prepareStatement(sqlQuery);
            rs = ps.executeQuery();
            while (rs.next()) {
                String throttleKey = rs.getString("THROTTLE_KEY");
                if(throttledEventString.length()>1) {
                    throttledEventString = throttledEventString + "," + throttleKey;
                }
                else {
                    throttledEventString = throttleKey;
                }
            }
            int count = 1;
            if(query != null && query.length()> 0){
                count =  Integer.parseInt(query);
                for (int i = 0; i < count; i++ ){
                    throttledEventString = throttledEventString +","+"key_"+i;
                }
            }
        } catch (SQLException e) {
            log.error("Error while executing SQL", e);
        } finally {
            ThrottlingDBUtil.closeAllConnections(ps, conn, rs);
        }
        return throttledEventString;
    }


    public static ThrottledEventDTO isThrottled(String query) {
        ThrottledEventDTO throttledEventDTO = new ThrottledEventDTO();
        throttledEventDTO.setThrottleState("ALLOWED");
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<ThrottledEventDTO> throttledEventDTOList = new ArrayList<ThrottledEventDTO>();
        String sqlQuery = "select THROTTLE_KEY from ThrotleTable WHERE THROTTLE_KEY=="+query;
        try {
            conn = ThrottlingDBUtil.getConnection();
            ps = conn.prepareStatement(sqlQuery);
            rs = ps.executeQuery();
            while (rs.next()) {
                String throttleKey = rs.getString("THROTTLE_KEY");
                throttledEventDTO.setThrottleState("THROTTLED");
            }
        } catch (SQLException e) {
            log.error("Error while executing SQL", e);
        } finally {
            ThrottlingDBUtil.closeAllConnections(ps, conn, rs);
        }
        return throttledEventDTO;
    }


    public static void closeAllConnections(PreparedStatement preparedStatement, Connection connection,
                                           ResultSet resultSet) {
        closeConnection(connection);
        closeResultSet(resultSet);
        closeStatement(preparedStatement);
    }

    /**
     * Close Connection
     *
     * @param dbConnection Connection
     */
    private static void closeConnection(Connection dbConnection) {
        if (dbConnection != null) {
            try {
                dbConnection.close();
            } catch (SQLException e) {
                log.warn("Database error. Could not close database connection. Continuing with " +
                        "others. - " + e.getMessage(), e);
            }
        }
    }

    /**
     * Close ResultSet
     *
     * @param resultSet ResultSet
     */
    private static void closeResultSet(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                log.warn("Database error. Could not close ResultSet  - " + e.getMessage(), e);
            }
        }

    }

    /**
     * Close PreparedStatement
     *
     * @param preparedStatement PreparedStatement
     */
    public static void closeStatement(PreparedStatement preparedStatement) {
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                log.warn("Database error. Could not close PreparedStatement. Continuing with" +
                        " others. - " + e.getMessage(), e);
            }
        }

    }
}