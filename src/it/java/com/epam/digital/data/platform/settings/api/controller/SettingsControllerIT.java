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

package com.epam.digital.data.platform.settings.api.controller;

import static com.epam.digital.data.platform.settings.api.TestUtils.readClassPathResource;
import static com.epam.digital.data.platform.settings.api.utils.Header.X_ACCESS_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epam.digital.data.platform.settings.api.UserSettingsServiceApiApplication;
import com.epam.digital.data.platform.settings.api.repository.NotificationChannelRepository;
import com.epam.digital.data.platform.settings.api.service.ChannelVerificationService;
import com.epam.digital.data.platform.settings.model.dto.ActivateChannelInputDto;
import com.epam.digital.data.platform.settings.model.dto.Channel;
import com.epam.digital.data.platform.settings.model.dto.SettingsDeactivateChannelInputDto;
import com.epam.digital.data.platform.settings.model.dto.SettingsEmailInputDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@Transactional
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = UserSettingsServiceApiApplication.class)
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092",
    "port=9092"})
class SettingsControllerIT {

  private static final String BASE_URL = "/api/settings";
  private static final UUID SETTINGS_ID_1 = UUID.fromString("321e7654-e89b-12d3-a456-426655441111");
  private static final String EMAIL_1 = "settings@gmail.com";
  private static final UUID SETTINGS_ID_2 = UUID.fromString("7f18fd5f-d68e-4609-85a8-eb5745488ac2");
  private static final String EMAIL_2 = "settings2@yahoo.com";
  private static final UUID SETTINGS_ID_3 = UUID.fromString("321e7654-e89b-12d3-a456-426655441112");


  private static final UUID SEARCHED_KEYCLOAK_ID =
      UUID.fromString("496fd2fd-3497-4391-9ead-41410522d06f");

  private static String TOKEN_OFFICER;
  private static String TOKEN_OFFICER_2;
  private static String TOKEN_CITIZEN;

  @Autowired
  MockMvc mockMvc;
  @Autowired
  ObjectMapper objectMapper;
  @Autowired
  NotificationChannelRepository notificationChannelRepository;

  @MockBean
  ChannelVerificationService channelVerificationService;

  @BeforeAll
  static void init() throws IOException {
    TOKEN_OFFICER = readClassPathResource("/token_officer.txt");
    TOKEN_OFFICER_2 = readClassPathResource("/token_officer2.txt");
    TOKEN_CITIZEN = readClassPathResource("/token_citizen.txt");
  }

  @Test
  void shouldFindSettingsFromToken() throws Exception {
    mockMvc
        .perform(get(BASE_URL + "/me").header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER))
        .andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            jsonPath("$.settingsId", is(SETTINGS_ID_1.toString())),
            jsonPath("$.channels[0].channel", is(Channel.EMAIL.getValue())),
            jsonPath("$.channels[0].activated", is(true)),
            jsonPath("$.channels[0].address", is(EMAIL_1)),
            jsonPath("$.channels[0].deactivationReason").doesNotExist());
  }

  @Test
  void shouldFindSettingsByKeycloakId() throws Exception {
    mockMvc
        .perform(
            get(BASE_URL + "/" + SEARCHED_KEYCLOAK_ID)
                .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER))
        .andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            jsonPath("$.settingsId", is(SETTINGS_ID_1.toString())),
            jsonPath("$.channels[0].channel", is(Channel.EMAIL.getValue())),
            jsonPath("$.channels[0].activated", is(true)),
            jsonPath("$.channels[0].address", is(EMAIL_1)),
            jsonPath("$.channels[0].deactivationReason").doesNotExist());
  }

  @Test
  void shouldActivateEmailChannelForOfficer() throws Exception {
    var input = new ActivateChannelInputDto();
    input.setAddress("new@email.com");
    input.setVerificationCode("123456");

    when(channelVerificationService.verify(any(Channel.class), anyString(), anyString(),
        anyString()))
        .thenReturn(true);

    mockMvc
        .perform(post(BASE_URL + "/me/channels/email/activate")
            .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER)
            .content(objectMapper.writeValueAsString(input))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpectAll(
            status().isOk());

    var activatedChannel =
        notificationChannelRepository.findBySettingsIdAndChannel(SETTINGS_ID_1, Channel.EMAIL)
            .get();

    assertThat(activatedChannel.getSettingsId()).isEqualTo(SETTINGS_ID_1);
    assertThat(activatedChannel.getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(activatedChannel.getAddress()).isEqualTo("new@email.com");
    assertThat(activatedChannel.isActivated()).isTrue();
    assertThat(activatedChannel.getDeactivationReason()).isNull();
  }

  @Test
  void shouldActivateDiiaChannelForCitizen() throws Exception {
    var input = new ActivateChannelInputDto();
    input.setAddress("0101010101");
    input.setVerificationCode("123456");
    when(channelVerificationService.verify(any(Channel.class), anyString(), anyString(),
        anyString()))
        .thenReturn(true);
    mockMvc
        .perform(
            post(BASE_URL + "/me/channels/diia/activate")
                .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_CITIZEN)
                .content(objectMapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpectAll(status().isOk());

    var activatedChannel =
        notificationChannelRepository.findBySettingsIdAndChannel(SETTINGS_ID_3, Channel.DIIA).get();

    assertThat(activatedChannel.getSettingsId()).isEqualTo(SETTINGS_ID_3);
    assertThat(activatedChannel.getChannel()).isEqualTo(Channel.DIIA);
    assertThat(activatedChannel.getAddress()).isEqualTo("0101010101");
    assertThat(activatedChannel.isActivated()).isTrue();
    assertThat(activatedChannel.getDeactivationReason()).isNull();
  }

  @Test
  void forbiddenActivateDiiaChannelForOfficer() throws Exception {
    var input = new ActivateChannelInputDto();
    input.setAddress("1010101014");
    input.setVerificationCode("123456");
    when(channelVerificationService.verify(any(Channel.class), anyString(), anyString(),
            anyString()))
            .thenReturn(true);
    mockMvc
            .perform(
                    post(BASE_URL + "/me/channels/diia/activate")
                            .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER)
                            .content(objectMapper.writeValueAsString(input))
                            .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isForbidden());
  }

  @Test
  void shouldDeactivateChannelForCitizen() throws Exception {
    var input = new SettingsDeactivateChannelInputDto();
    input.setDeactivationReason("User deactivated");

    mockMvc
        .perform(
            post(BASE_URL + "/me/channels/diia/deactivate")
                .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_CITIZEN)
                .content(objectMapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpectAll(status().isOk());

    var deactivatedChannel =
        notificationChannelRepository.findBySettingsIdAndChannel(SETTINGS_ID_3, Channel.DIIA).get();

    assertThat(deactivatedChannel.getSettingsId()).isEqualTo(SETTINGS_ID_3);
    assertThat(deactivatedChannel.getChannel()).isEqualTo(Channel.DIIA);
    assertThat(deactivatedChannel.getAddress()).isNull();
    assertThat(deactivatedChannel.isActivated()).isFalse();
    assertThat(deactivatedChannel.getDeactivationReason()).isEqualTo("User deactivated");
  }

  @Test
  void forbiddenDeactivateChannelForOfficer() throws Exception {
    var input = new SettingsDeactivateChannelInputDto();
    input.setDeactivationReason("User deactivated");

    mockMvc
            .perform(
                    post(BASE_URL + "/me/channels/diia/deactivate")
                            .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER)
                            .content(objectMapper.writeValueAsString(input))
                            .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isForbidden());
  }

  @Test
  void shouldDeactivateChannelWithUpdatedAddressForOfficer() throws Exception {
    var input = new SettingsDeactivateChannelInputDto();
    input.setAddress(EMAIL_2);
    input.setDeactivationReason("User deactivated");

    mockMvc
            .perform(
                    post(BASE_URL + "/me/channels/email/deactivate")
                            .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER)
                            .content(objectMapper.writeValueAsString(input))
                            .contentType(MediaType.APPLICATION_JSON))
            .andExpectAll(status().isOk());

    var deactivatedChannel =
            notificationChannelRepository.findBySettingsIdAndChannel(SETTINGS_ID_1, Channel.EMAIL).get();

    assertThat(deactivatedChannel.getSettingsId()).isEqualTo(SETTINGS_ID_1);
    assertThat(deactivatedChannel.getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(deactivatedChannel.getAddress()).isEqualTo(EMAIL_2);
    assertThat(deactivatedChannel.isActivated()).isFalse();
    assertThat(deactivatedChannel.getDeactivationReason()).isEqualTo("User deactivated");
  }

  @Test
  void shouldCreateDeactivatedChannelForOfficer() throws Exception {
    var input = new SettingsDeactivateChannelInputDto();
    input.setDeactivationReason("Address deactivated");
    input.setAddress(EMAIL_2);

    mockMvc
        .perform(
            post(BASE_URL + "/me/channels/email/deactivate")
                .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER_2)
                .content(objectMapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpectAll(status().isOk());

    var deactivatedChannel =
        notificationChannelRepository
            .findBySettingsIdAndChannel(SETTINGS_ID_2, Channel.EMAIL)
             .get();

    assertThat(deactivatedChannel.getSettingsId()).isEqualTo(SETTINGS_ID_2);
    assertThat(deactivatedChannel.getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(deactivatedChannel.getAddress()).isEqualTo(EMAIL_2);
    assertThat(deactivatedChannel.isActivated()).isFalse();
    assertThat(deactivatedChannel.getDeactivationReason()).isEqualTo("Address deactivated");
  }

  @Test
  void shouldFailEmailAddressValidationWhenAddressIsEmptyForOfficer() throws Exception {
    var input = new SettingsEmailInputDto();
    input.setAddress("");

    mockMvc
        .perform(
            post(BASE_URL + "/me/channels/email/validate")
                .header(X_ACCESS_TOKEN.getHeaderName(), TOKEN_OFFICER)
                .content(objectMapper.writeValueAsString(input))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpectAll(status().isUnprocessableEntity(),
            content().contentType(MediaType.APPLICATION_JSON),
            jsonPath("$.message", is("Email address is empty")),
            jsonPath("$.localizedMessage", is("Поле обов'язкове")));
  }

  @Test
  void unauthorizedIfAccessTokenAbsent() throws Exception {
    mockMvc
        .perform(get(BASE_URL))
        .andExpect(status().isUnauthorized());
  }
}
