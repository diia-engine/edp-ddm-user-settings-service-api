/*
 * Copyright 2023 EPAM Systems.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.settings.api;

import com.epam.digital.data.platform.settings.api.config.OpenApiConfig;
import com.epam.digital.data.platform.settings.api.config.TestConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * The class represents a spring boot application runner that is used for running the application
 * for openapi-doc generation.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@Import({TestConfig.class, OpenApiConfig.class})
@ComponentScan(
    basePackages = "com.epam.digital.data.platform.settings.api.controller"
)
public class TestApp {

  public static void main(String[] args) {
    SpringApplication.run(TestApp.class, args);
  }

}
