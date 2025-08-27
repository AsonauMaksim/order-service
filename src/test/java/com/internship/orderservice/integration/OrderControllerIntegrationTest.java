package com.internship.orderservice.integration;

import com.internship.orderservice.entity.Item;
import com.internship.orderservice.repository.ItemRepository;
import com.internship.orderservice.repository.OrderItemRepository;
import com.internship.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.jayway.jsonpath.JsonPath;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.hamcrest.Matchers.containsString;

public class OrderControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    private static final String USER_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        itemRepository.deleteAll();
        WIREMOCK.resetAll();
    }

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        itemRepository.deleteAll();
        WIREMOCK.resetAll();
    }

    private void stubUserMappingAndDetails(long credentialsId, long actualUserId, String userJson) {
        // credentialsId -> user.id
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/by-credentials-id/" + credentialsId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));
        // детали пользователя по реальному user.id
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + actualUserId))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));
    }

    private void stubUserMappingNotFound(long credentialsId) {
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/by-credentials-id/" + credentialsId))
                .willReturn(aResponse().withStatus(404)));
    }

    @Test
    void createOrder_HappyPath_Returns201AndUserFromWireMock() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "USB-C Cable 1m", new BigDecimal("9.99")));
        Item i2 = itemRepository.save(new Item(null, "Wireless Mouse", new BigDecimal("24.90")));

        long credentialsId = 4L; // используем один и тот же id для простоты
        long actualUserId  = 4L;

        String userJson = """
              {"id": %d, "name":"Rita", "surname":"Sokolova", "email":"margo@gmail.com"}
            """.formatted(actualUserId);

        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String reqJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 2},
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i1.getId(), i2.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value((int) actualUserId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.user.id").value((int) actualUserId))
                .andExpect(jsonPath("$.user.email").value("margo@gmail.com"));

        // verify mapping called
        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/by-credentials-id/" + credentialsId)));
        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/" + actualUserId)));
    }

    @Test
    void createOrder_UserNotFound_Returns404() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "USB-C Cable 1m", new BigDecimal("9.99")));
        long credentialsId = 9999L;

        stubUserMappingNotFound(credentialsId);

        String reqJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i1.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                        .value(containsString("User does not exist")));

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/by-credentials-id/" + credentialsId)));
        // деталей пользователя по id не должно быть
        WIREMOCK.verify(0, getRequestedFor(urlMatching("/api/users/\\d+")));
    }

    @Test
    void createOrder_ItemNotFound_Returns404() throws Exception {
        long credentialsId = 7L, actualUserId = 7L;

        String userJson = """
              {"id": %d, "name":"Test", "surname":"User", "email":"test@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String reqJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": 999, "quantity": 1}
                ]
              }
            """;

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(containsString("Item not found")));

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/by-credentials-id/" + credentialsId)));
        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/" + actualUserId)));
    }

    @Test
    void getOrderById_HappyPath_Returns200AndUser() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "SSD 512GB", new BigDecimal("89.90")));
        long credentialsId = 11L, actualUserId = 11L;

        String userJson = """
              {"id": %d, "name":"Alex", "surname":"Doe", "email":"alex@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String createJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i1.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        String body = created.getResponse().getContentAsString();

        Number idNum = JsonPath.read(body, "$.id");
        long orderId = idNum.longValue();

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value((int) orderId))
                .andExpect(jsonPath("$.userId").value((int) actualUserId))
                .andExpect(jsonPath("$.user.email").value("alex@example.com"));

        // mapping (1x при создании) + детали (1x при создании + 1x при GET)
        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/by-credentials-id/" + credentialsId)));
        WIREMOCK.verify(2, getRequestedFor(urlEqualTo("/api/users/" + actualUserId)));
    }

    @Test
    void getOrderById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void getOrderById_UserService404_UserNullInResponse() throws Exception {
        Item i = itemRepository.save(new Item(null, "HDMI Cable", new BigDecimal("7.90")));
        long credentialsId = 44L, actualUserId = 44L;

        String okUserJson = """
              {"id": %d, "name":"Test", "surname":"User", "email":"ok@example.com"}
            """.formatted(actualUserId);

        // создание: ok для mapping и деталей
        stubUserMappingAndDetails(credentialsId, actualUserId, okUserJson);

        String createJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        // при GET пользователь уже 404
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + actualUserId))
                .willReturn(aResponse().withStatus(404)));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value((int) orderId))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void updateOrder_ChangeStatusToPaid_Returns200() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "Keyboard", new BigDecimal("39.90")));
        long credentialsId = 22L, actualUserId = 22L;

        String userJson = """
              {"id": %d, "name":"Kate", "surname":"White", "email":"kate@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String createJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 2}
                ]
              }
            """.formatted(i1.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        String updateJson = """
              {
                "status": "PAID",
                "items": [
                  {"itemId": %d, "quantity": 2}
                ]
              }
            """.formatted(i1.getId());

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value((int) orderId))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.user.email").value("kate@example.com"));

        WIREMOCK.verify(2, getRequestedFor(urlEqualTo("/api/users/by-credentials-id/" + credentialsId)));
        WIREMOCK.verify(2, getRequestedFor(urlEqualTo("/api/users/" + actualUserId)));
    }

    @Test
    void updateOrder_NotFound_Returns404() throws Exception {
        long credentialsId = 123L;
        String updateJson = """
              {
                "status": "PAID",
                "items": [
                  {"itemId": 1, "quantity": 1}
                ]
              }
            """;

        mockMvc.perform(put("/api/orders/{id}", 99999)
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void deleteOrder_NoContent_ThenGetNotFound() throws Exception {
        Item i = itemRepository.save(new Item(null, "Flash Drive 64GB", new BigDecimal("12.50")));
        long credentialsId = 33L, actualUserId = 33L;

        String userJson = """
              {"id": %d, "name":"Nick", "surname":"Ray", "email":"nick@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String createJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header(USER_HEADER, credentialsId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound());

        WIREMOCK.verify(2, getRequestedFor(urlEqualTo("/api/users/by-credentials-id/" + credentialsId)));
    }

    @Test
    void deleteOrder_NotFound_Returns404() throws Exception {
        long credentialsId = 999L;
        mockMvc.perform(delete("/api/orders/{id}", 424242)
                        .header(USER_HEADER, credentialsId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void createOrder_InvalidStatus_Returns400() throws Exception {
        Item i = itemRepository.save(new Item(null, "NVMe 1TB", new BigDecimal("129.90")));
        long credentialsId = 55L, actualUserId = 55L;

        String userJson = """
              {"id": %d, "name":"Test", "surname":"User", "email":"t@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String badReq = """
              {
                "status": "WRONG_STATUS",
                "items": [
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badReq))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void updateOrder_ItemNotFound_Returns404() throws Exception {
        Item i = itemRepository.save(new Item(null, "USB Hub", new BigDecimal("19.90")));
        long credentialsId = 66L, actualUserId = 66L;

        String userJson = """
              {"id": %d, "name":"Lena", "surname":"Fox", "email":"lena@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String createJson = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 1}
                ]
              }
            """.formatted(i.getId());

        var created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        String updateJson = """
              {
                "status": "PAID",
                "items": [
                  {"itemId": 999, "quantity": 1}
                ]
              }
            """;

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Item not found")));
    }

    @Test
    void createOrder_EmptyItems_Returns400() throws Exception {
        long credentialsId = 77L, actualUserId = 77L;

        String userJson = """
              {"id": %d, "name":"User", "surname":"X", "email":"u@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String req = """
              {
                "status": "PENDING",
                "items": []
              }
            """;

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void createOrder_QuantityZero_Returns400() throws Exception {
        Item item = itemRepository.save(new Item(null, "Dummy", new BigDecimal("1.00")));
        long credentialsId = 78L, actualUserId = 78L;

        String userJson = """
              {"id": %d, "name":"User", "surname":"Y", "email":"y@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String req = """
              {
                "status": "PENDING",
                "items": [
                  {"itemId": %d, "quantity": 0}
                ]
              }
            """.formatted(item.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void updateOrder_MissingUserId_Returns401() throws Exception {
        Item item = itemRepository.save(new Item(null, "X", new BigDecimal("5.00")));
        long credentialsId = 79L, actualUserId = 79L;

        String userJson = """
              {"id": %d, "name":"User", "surname":"Z", "email":"z@example.com"}
            """.formatted(actualUserId);
        stubUserMappingAndDetails(credentialsId, actualUserId, userJson);

        String create = """
              {
                "status": "PENDING",
                "items": [{"itemId": %d, "quantity": 1}]
              }
            """.formatted(item.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credentialsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        String update = """
              {
                "status": "PAID",
                "items": [{"itemId": %d, "quantity": 1}]
              }
            """.formatted(item.getId());

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        // .header(USER_HEADER, credentialsId) // специально не передаем
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Missing required header: X-User-Id"));
    }

    @Test
    void getOrdersByIds_ReturnsList() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "Cable", new BigDecimal("5.00")));
        Item i2 = itemRepository.save(new Item(null, "Mouse", new BigDecimal("20.00")));

        long credA = 101L, userA = 101L;
        long credB = 102L, userB = 102L;

        String userAJson = """
              {"id": %d, "name":"A", "surname":"A", "email":"a@example.com"}
            """.formatted(userA);
        String userBJson = """
              {"id": %d, "name":"B", "surname":"B", "email":"b@example.com"}
            """.formatted(userB);

        stubUserMappingAndDetails(credA, userA, userAJson);
        stubUserMappingAndDetails(credB, userB, userBJson);

        var resA = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"PENDING", "items":[{"itemId": %d, "quantity":1}]}
                                """.formatted(i1.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        var resB = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"SHIPPED", "items":[{"itemId": %d, "quantity":2}]}
                                """.formatted(i2.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNumA = JsonPath.read(resA.getResponse().getContentAsString(), "$.id");
        long idA = idNumA.longValue();

        Number idNumB = JsonPath.read(resB.getResponse().getContentAsString(), "$.id");
        long idB = idNumB.longValue();


        mockMvc.perform(get("/api/orders/by-ids")
                        .param("ids", String.valueOf(idA), String.valueOf(idB)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id",
                        org.hamcrest.Matchers.containsInAnyOrder((int) idA, (int) idB)));
    }

    @Test
    void getOrdersByStatuses_ReturnsList() throws Exception {
        Item item = itemRepository.save(new Item(null, "SSD", new BigDecimal("80.00")));
        long credX = 201L, userX = 201L;
        long credY = 202L, userY = 202L;

        String userXJson = """
              {"id": %d, "name":"X", "surname":"X", "email":"x@example.com"}
            """.formatted(userX);
        String userYJson = """
              {"id": %d, "name":"Y", "surname":"Y", "email":"y@example.com"}
            """.formatted(userY);

        stubUserMappingAndDetails(credX, userX, userXJson);
        stubUserMappingAndDetails(credY, userY, userYJson);

        var createdPending = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"PENDING", "items":[{"itemId": %d, "quantity":1}]}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        var createdPaid = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, credY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"PAID", "items":[{"itemId": %d, "quantity":1}]}
                                """.formatted(item.getId())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonPath.read(createdPending.getResponse().getContentAsString(), "$.id");
        JsonPath.read(createdPaid.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/orders/by-statuses")
                        .param("statuses", "PENDING", "PAID"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status",
                        org.hamcrest.Matchers.containsInAnyOrder("PENDING", "PAID")));
    }
}