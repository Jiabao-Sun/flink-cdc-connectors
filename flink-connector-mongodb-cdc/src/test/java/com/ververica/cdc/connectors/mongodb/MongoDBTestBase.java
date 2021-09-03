/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ververica.cdc.connectors.mongodb;

import org.apache.flink.test.util.AbstractTestBase;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.ververica.cdc.connectors.mongodb.utils.MongoDBContainer;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertNotNull;

/**
 * Basic class for testing MongoDB source, this contains a MongoDB container which enables binlog.
 */
public class MongoDBTestBase extends AbstractTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBTestBase.class);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("^(.*)//.*$");

    protected static final String FLINK_USER = "flinkuser";
    protected static final String FLINK_USER_PASSWORD = "flinkpw";

    protected static final String MONGO_SUPER_USER = "superuser";
    protected static final String MONGO_SUPER_PASSWORD = "superpw";

    protected static final MongoDBContainer MONGODB_CONTAINER =
            new MongoDBContainer().withLogConsumer(new Slf4jLogConsumer(LOG));

    protected static MongoClient mongodbClient;

    @BeforeClass
    public static void startContainers() {
        LOG.info("Starting containers...");
        Startables.deepStart(Stream.of(MONGODB_CONTAINER)).join();
        executeCommandFile("setup");
        initialClient();
        LOG.info("Containers are started.");
    }

    private static void initialClient() {
        MongoClientSettings settings =
                MongoClientSettings.builder()
                        .applyConnectionString(
                                new ConnectionString(
                                        MONGODB_CONTAINER.getConnectionString(
                                                MONGO_SUPER_USER, MONGO_SUPER_PASSWORD)))
                        .build();
        mongodbClient = MongoClients.create(settings);
    }

    protected static MongoDatabase getMongoDatabase(String dbName) {
        return mongodbClient.getDatabase(dbName);
    }

    /** Executes a mongo command file. */
    protected static void executeCommandFile(String cmdFile) {
        final String ddlFile = String.format("ddl/%s.js", cmdFile);
        final URL ddlTestFile = MongoDBTestBase.class.getClassLoader().getResource(ddlFile);
        assertNotNull("Cannot locate " + ddlFile, ddlTestFile);

        try {
            String command =
                    Files.readAllLines(Paths.get(ddlTestFile.toURI())).stream()
                            .map(String::trim)
                            .filter(x -> !x.startsWith("//") && !x.isEmpty())
                            .map(
                                    x -> {
                                        final Matcher m = COMMENT_PATTERN.matcher(x);
                                        return m.matches() ? m.group(1) : x;
                                    })
                            .collect(Collectors.joining("\n"));

            MONGODB_CONTAINER.executeCommand(command);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}