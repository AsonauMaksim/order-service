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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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

    @Test
    void createOrder_HappyPath_Returns201AndUserFromWireMock() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "USB-C Cable 1m", new BigDecimal("9.99")));
        Item i2 = itemRepository.save(new Item(null, "Wireless Mouse", new BigDecimal("24.90")));

        long userId = 4L;

        String userJson = """
                  {"id": %d, "name":"Rita", "surname":"Sokolova", "email":"margo@gmail.com"}
                """.formatted(userId);

        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

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
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value((int) userId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.user.id").value((int) userId))
                .andExpect(jsonPath("$.user.email").value("margo@gmail.com"));

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/" + userId)));
    }

    @Test
    void createOrder_UserNotFound_Returns404() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "USB-C Cable 1m", new BigDecimal("9.99")));
        long userId = 9999L;

        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse().withStatus(404)));

        String reqJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 1}
                    ]
                  }
                """.formatted(i1.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("User does not exist")));

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/" + userId)));
    }

    @Test
    void createOrder_ItemNotFound_Returns404() throws Exception {
        long userId = 7L;

        String userJson = """
                  {"id": %d, "name":"Test", "surname":"User", "email":"test@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String reqJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": 999, "quantity": 1}
                    ]
                  }
                """;

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Item not found")));

        WIREMOCK.verify(1, getRequestedFor(urlEqualTo("/api/users/" + userId)));
    }

    @Test
    void getOrderById_HappyPath_Returns200AndUser() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "SSD 512GB", new BigDecimal("89.90")));
        long userId = 11L;

        String userJson = """
                  {"id": %d, "name":"Alex", "surname":"Doe", "email":"alex@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String createJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 1}
                    ]
                  }
                """.formatted(i1.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
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
                .andExpect(jsonPath("$.userId").value((int) userId))
                .andExpect(jsonPath("$.user.email").value("alex@example.com"));

        WIREMOCK.verify(2, getRequestedFor(urlEqualTo("/api/users/" + userId)));
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
        long userId = 44L;

        String okUserJson = """
                  {"id": %d, "name":"Test", "surname":"User", "email":"ok@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(okUserJson)));

        String createJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 1}
                    ]
                  }
                """.formatted(i.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        WIREMOCK.resetAll();
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
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
        long userId = 22L;

        String userJson = """
                  {"id": %d, "name":"Kate", "surname":"White", "email":"kate@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String createJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 2}
                    ]
                  }
                """.formatted(i1.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
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
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value((int) orderId))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.user.email").value("kate@example.com"));

        WIREMOCK.verify(2, getRequestedFor(urlEqualTo("/api/users/" + userId)));
    }

    @Test
    void updateOrder_NotFound_Returns404() throws Exception {
        long userId = 123L;
        String updateJson = """
                  {
                    "status": "PAID",
                    "items": [
                      {"itemId": 1, "quantity": 1}
                    ]
                  }
                """;

        mockMvc.perform(put("/api/orders/{id}", 99999)
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void deleteOrder_NoContent_ThenGetNotFound() throws Exception {
        Item i = itemRepository.save(new Item(null, "Flash Drive 64GB", new BigDecimal("12.50")));
        long userId = 33L;

        String userJson = """
                  {"id": %d, "name":"Nick", "surname":"Ray", "email":"nick@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String createJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 1}
                    ]
                  }
                """.formatted(i.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn();

        Number idNum = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        long orderId = idNum.longValue();

        mockMvc.perform(delete("/api/orders/{id}", orderId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOrder_NotFound_Returns404() throws Exception {
        mockMvc.perform(delete("/api/orders/{id}", 424242))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Order not found")));
    }

    @Test
    void createOrder_InvalidStatus_Returns400() throws Exception {
        Item i = itemRepository.save(new Item(null, "NVMe 1TB", new BigDecimal("129.90")));
        long userId = 55L;

        String userJson = """
                  {"id": %d, "name":"Test", "surname":"User", "email":"t@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(userJson)));

        String badReq = """
                  {
                    "status": "WRONG_STATUS",
                    "items": [
                      {"itemId": %d, "quantity": 1}
                    ]
                  }
                """.formatted(i.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badReq))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void updateOrder_ItemNotFound_Returns404() throws Exception {
        Item i = itemRepository.save(new Item(null, "USB Hub", new BigDecimal("19.90")));
        long userId = 66L;

        String userJson = """
                  {"id": %d, "name":"Lena", "surname":"Fox", "email":"lena@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String createJson = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 1}
                    ]
                  }
                """.formatted(i.getId());

        var created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
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
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", containsString("Item not found")));
    }

    @Test
    void createOrder_EmptyItems_Returns400() throws Exception {
        long userId = 77L;

        String userJson = """
                  {"id": %d, "name":"User", "surname":"X", "email":"u@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String req = """
                  {
                    "status": "PENDING",
                    "items": []
                  }
                """;

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void createOrder_QuantityZero_Returns400() throws Exception {
        Item item = itemRepository.save(new Item(null, "Dummy", new BigDecimal("1.00")));
        long userId = 78L;

        String userJson = """
                  {"id": %d, "name":"User", "surname":"Y", "email":"y@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String req = """
                  {
                    "status": "PENDING",
                    "items": [
                      {"itemId": %d, "quantity": 0}
                    ]
                  }
                """.formatted(item.getId());

        mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }

    @Test
    void updateOrder_MissingUserId_Returns400() throws Exception {
        Item item = itemRepository.save(new Item(null, "X", new BigDecimal("5.00")));
        long userId = 79L;

        String userJson = """
                  {"id": %d, "name":"User", "surname":"Z", "email":"z@example.com"}
                """.formatted(userId);
        WIREMOCK.stubFor(WireMock.get(urlEqualTo("/api/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson)));

        String create = """
                  {
                    "status": "PENDING",
                    "items": [{"itemId": %d, "quantity": 1}]
                  }
                """.formatted(item.getId());

        MvcResult created = mockMvc.perform(post("/api/orders")
                        .header(USER_HEADER, userId)
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

        // Без userId header — теперь 400!
        mockMvc.perform(put("/api/orders/{id}", orderId)
                        // .header(USER_HEADER, userId) // специально не передаем
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Required request header 'X-User-Id' " +
                        "for method parameter type Long is not present"));
    }

    @Test
    void getOrdersByIds_ReturnsList() throws Exception {
        Item i1 = itemRepository.save(new Item(null, "Cable", new BigDecimal("5.00")));
        Item i2 = itemRepository.save(new Item(null, "Mouse", new BigDecimal("20.00")));

        long userA = 101L, userB = 102L;

        String userAJson = """
                  {"id": %d, "name":"A", "surname":"A", "email":"a@example.com"}
                """.formatted(userA);
        String userBJson = """
                  {"id": %d, "name":"B", "surname":"B", "email":"b@example.com"}
                """.formatted(userB);

        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/api/users/" + userA))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userAJson)));
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/api/users/" + userB))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userBJson)));

        var resA = mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header(USER_HEADER, userA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"PENDING", "items":[{"itemId": %d, "quantity":1}]}
                                """.formatted(i1.getId())))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();

        var resB = mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header(USER_HEADER, userB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"SHIPPED", "items":[{"itemId": %d, "quantity":2}]}
                                """.formatted(i2.getId())))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();

        Number nA = JsonPath.read(resA.getResponse().getContentAsString(), "$.id");
        Number nB = JsonPath.read(resB.getResponse().getContentAsString(), "$.id");
        long idA = nA.longValue();
        long idB = nB.longValue();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/by-ids")
                        .param("ids", String.valueOf(idA), String.valueOf(idB)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].id",
                        org.hamcrest.Matchers.containsInAnyOrder((int) idA, (int) idB)));
    }

    @Test
    void getOrdersByStatuses_ReturnsList() throws Exception {
        Item item = itemRepository.save(new Item(null, "SSD", new BigDecimal("80.00")));
        long userX = 201L, userY = 202L;

        String userXJson = """
                  {"id": %d, "name":"X", "surname":"X", "email":"x@example.com"}
                """.formatted(userX);
        String userYJson = """
                  {"id": %d, "name":"Y", "surname":"Y", "email":"y@example.com"}
                """.formatted(userY);

        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/api/users/" + userX))
                .willReturn(WireMock.aResponse()
                        .withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(userXJson)));

        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/api/users/" + userY))
                .willReturn(WireMock.aResponse()
                        .withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(userYJson)));

        var createdPending = mockMvc.perform(MockMvcRequestBuilders.post("/api/orders")
                        .header(USER_HEADER, userX)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"status":"PENDING", "items":[{"itemId": %d, "quantity":1}]}
                                """.formatted(item.getId())))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();

        var createdPaid = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/orders")
                                .header(USER_HEADER, userY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                          {"status":"PAID", "items":[{"itemId": %d, "quantity":1}]}
                                        """.formatted(item.getId())))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();

        JsonPath.read(createdPending.getResponse().getContentAsString(), "$.id");
        JsonPath.read(createdPaid.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/by-statuses")
                        .param("statuses", "PENDING", "PAID"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].status",
                        org.hamcrest.Matchers.containsInAnyOrder("PENDING", "PAID")));
    }
}
