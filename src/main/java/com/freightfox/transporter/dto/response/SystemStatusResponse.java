package com.freightfox.transporter.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Dynamic system status and diagnostics response DTO.
 */
public class SystemStatusResponse {

    private String status;
    private String service;
    private String version;
    private String environment;
    private int port;
    private String database;
    private String databaseStatus;
    private String algorithm;
    private int maxSupportedTransporters;
    private Map<String, Object> activeDataset;
    private Map<String, Object> operator;
    private Instant timestamp;

    public SystemStatusResponse() {
    }

    public SystemStatusResponse(String status, String service, String version, String environment,
                                int port, String database, String databaseStatus, String algorithm,
                                int maxSupportedTransporters, Map<String, Object> activeDataset,
                                Map<String, Object> operator, Instant timestamp) {
        this.status = status;
        this.service = service;
        this.version = version;
        this.environment = environment;
        this.port = port;
        this.database = database;
        this.databaseStatus = databaseStatus;
        this.algorithm = algorithm;
        this.maxSupportedTransporters = maxSupportedTransporters;
        this.activeDataset = activeDataset;
        this.operator = operator;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDatabaseStatus() {
        return databaseStatus;
    }

    public void setDatabaseStatus(String databaseStatus) {
        this.databaseStatus = databaseStatus;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getMaxSupportedTransporters() {
        return maxSupportedTransporters;
    }

    public void setMaxSupportedTransporters(int maxSupportedTransporters) {
        this.maxSupportedTransporters = maxSupportedTransporters;
    }

    public Map<String, Object> getActiveDataset() {
        return activeDataset;
    }

    public void setActiveDataset(Map<String, Object> activeDataset) {
        this.activeDataset = activeDataset;
    }

    public Map<String, Object> getOperator() {
        return operator;
    }

    public void setOperator(Map<String, Object> operator) {
        this.operator = operator;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

