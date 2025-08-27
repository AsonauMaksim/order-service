package com.internship.orderservice.unit;

import com.internship.orderservice.client.UserClient;
import com.internship.orderservice.dto.external.UserResponse;
import com.internship.orderservice.dto.request.OrderItemRequest;
import com.internship.orderservice.dto.request.OrderRequest;
import com.internship.orderservice.dto.response.OrderResponse;
import com.internship.orderservice.entity.Item;
import com.internship.orderservice.entity.Order;
import com.internship.orderservice.entity.OrderItem;
import com.internship.orderservice.entity.OrderStatus;
import com.internship.orderservice.exception.NotFoundException;
import com.internship.orderservice.mapper.OrderMapper;
import com.internship.orderservice.repository.ItemRepository;
import com.internship.orderservice.repository.OrderRepository;
import com.internship.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private OrderServiceImpl service;

    private Item item1;
    private Item item2;

    @BeforeEach
    void setUp() {
        item1 = new Item(1L, "USB-C Cable 1m", new BigDecimal("9.99"));
        item2 = new Item(2L, "Wireless Mouse", new BigDecimal("24.90"));
    }

    @Test
    void createOrder_happyPath() {

        Long credentialsId = 111L;
        Long actualUserId = 4L;

        OrderRequest req = OrderRequest.builder()
                .userId(credentialsId)
                .status("PENDING")
                .items(List.of(
                        OrderItemRequest.builder().itemId(1L).quantity(2).build(),
                        OrderItemRequest.builder().itemId(2L).quantity(1).build()
                ))
                .build();

        UserResponse resolvedUser = new UserResponse();
        resolvedUser.setId(actualUserId);
        resolvedUser.setName("Rita");
        resolvedUser.setSurname("Sokolova");
        resolvedUser.setEmail("margo@gmail.com");

        Order mapped = new Order();
        mapped.setUserId(actualUserId);

        when(userClient.getByCredentialsId(credentialsId)).thenReturn(resolvedUser);
        when(userClient.getByUserId(actualUserId)).thenReturn(resolvedUser);
        when(orderMapper.toEntity(req)).thenReturn(mapped);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(10L);
            return o;
        });
        when(orderMapper.toDto(any(Order.class), eq(resolvedUser))).thenReturn(new OrderResponse());

        OrderResponse resp = service.createOrder(req);

        assertThat(resp).isNotNull();

        ArgumentCaptor<Order> savedCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedCaptor.capture());
        Order saved = savedCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(actualUserId);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getOrderItems()).hasSize(2);
        assertThat(saved.getOrderItems().stream().map(oi -> oi.getItem().getId()).toList())
                .containsExactlyInAnyOrder(1L, 2L);

        verify(orderMapper).toDto(saved, resolvedUser);
        verify(userClient).getByCredentialsId(credentialsId);
        verify(userClient).getByUserId(actualUserId);
    }

    @Test
    void createOrder_userDoesNotExist_throwsNotFound() {
        Long credentialsId = 777L;

        OrderRequest req = OrderRequest.builder()
                .userId(credentialsId)
                .status("PENDING")
                .items(List.of(OrderItemRequest.builder().itemId(1L).quantity(1).build()))
                .build();

        when(userClient.getByCredentialsId(credentialsId))
                .thenThrow(new NotFoundException("User not found"));

        assertThatThrownBy(() -> service.createOrder(req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(orderRepository, never()).save(any());
        verify(userClient, never()).getByUserId(any());
    }

    @Test
    void createOrder_itemNotFound_throwsNotFound() {
        Long credentialsId = 111L;
        Long actualUserId = 4L;

        OrderRequest req = OrderRequest.builder()
                .userId(credentialsId)
                .status("PENDING")
                .items(List.of(OrderItemRequest.builder().itemId(999L).quantity(1).build()))
                .build();

        UserResponse resolvedUser = new UserResponse();
        resolvedUser.setId(actualUserId);

        when(userClient.getByCredentialsId(credentialsId)).thenReturn(resolvedUser);
        when(userClient.getByUserId(actualUserId)).thenReturn(resolvedUser);
        when(orderMapper.toEntity(req)).thenReturn(new Order());
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrder(req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Item not found");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_invalidStatus_throwsIAE() {
        Long credentialsId = 111L;
        Long actualUserId = 4L;

        OrderRequest req = OrderRequest.builder()
                .userId(credentialsId)
                .status("WRONG_STATUS")
                .items(List.of(OrderItemRequest.builder().itemId(1L).quantity(1).build()))
                .build();

        UserResponse resolvedUser = new UserResponse();
        resolvedUser.setId(actualUserId);

        when(userClient.getByCredentialsId(credentialsId)).thenReturn(resolvedUser);
        when(userClient.getByUserId(actualUserId)).thenReturn(resolvedUser);
        when(orderMapper.toEntity(req)).thenReturn(new Order());

        assertThatThrownBy(() -> service.createOrder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");
    }

    @Test
    void getOrderById_userWasRemoved_mapperGetsNullUser() {
        Order o = new Order();
        o.setId(5L);
        o.setUserId(4L);
        o.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(o));
        when(userClient.getByUserId(4L)).thenThrow(new NotFoundException("User not found"));
        when(orderMapper.toDto(eq(o), eq(null))).thenReturn(new OrderResponse());

        OrderResponse resp = service.getOrderById(5L);

        assertThat(resp).isNotNull();
        verify(orderMapper).toDto(o, null);
    }

    @Test
    void getOrderById_orderMissing_throwsNotFound() {
        when(orderRepository.findById(123L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrderById(123L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Order not found with id: 123");
    }

    @Test
    void getOrdersByIds_ShouldReturnOrders_WhenFound() {
        List<Long> ids = List.of(1L, 2L);

        Order o1 = Order.builder().id(1L).userId(10L).status(OrderStatus.PENDING).build();
        Order o2 = Order.builder().id(2L).userId(20L).status(OrderStatus.SHIPPED).build();
        when(orderRepository.findByIdIn(ids)).thenReturn(List.of(o1, o2));

        UserResponse u1 = UserResponse.builder().id(10L).name("Max").surname("Ivanov").email("max@gmail.com").build();
        UserResponse u2 = UserResponse.builder().id(20L).name("Bob").surname("Smith").email("bob@gmail.com").build();
        when(userClient.getByUserId(10L)).thenReturn(u1);
        when(userClient.getByUserId(20L)).thenReturn(u2);

        OrderResponse r1 = OrderResponse.builder().id(1L).userId(10L).status("PENDING").items(List.of()).build();
        OrderResponse r2 = OrderResponse.builder().id(2L).userId(20L).status("SHIPPED").items(List.of()).build();
        when(orderMapper.toDto(o1, u1)).thenReturn(r1);
        when(orderMapper.toDto(o2, u2)).thenReturn(r2);

        List<OrderResponse> out = service.getOrdersByIds(ids);

        assertThat(out).hasSize(2);
        assertThat(out).extracting(OrderResponse::getId).containsExactlyInAnyOrder(1L, 2L);
        verify(userClient).getByUserId(10L);
        verify(userClient).getByUserId(20L);
        verify(orderMapper).toDto(o1, u1);
        verify(orderMapper).toDto(o2, u2);
    }

    @Test
    void getOrdersByIds_ShouldReturnEmpty_WhenIdsEmpty() {
        when(orderRepository.findByIdIn(Collections.emptyList())).thenReturn(List.of());

        List<OrderResponse> out = service.getOrdersByIds(List.of());

        assertThat(out).isEmpty();
        verify(orderRepository).findByIdIn(Collections.emptyList());
        verifyNoInteractions(userClient, orderMapper);
    }

    @Test
    void getOrdersByIds_ShouldMapUserNull_WhenUserServiceReturnsNull() {
        List<Long> ids = List.of(1L);
        Order o = Order.builder().id(1L).userId(10L).status(OrderStatus.PENDING).build();
        when(orderRepository.findByIdIn(ids)).thenReturn(List.of(o));

        when(userClient.getByUserId(10L)).thenReturn(null);

        OrderResponse r = OrderResponse.builder().id(1L).userId(10L).status("PENDING").items(List.of()).build();
        when(orderMapper.toDto(o, null)).thenReturn(r);

        List<OrderResponse> out = service.getOrdersByIds(ids);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getUser()).isNull();
        verify(orderMapper).toDto(o, null);
    }

    @Test
    void getOrdersByStatuses_ShouldReturnOrders_WhenFound() {
        List<OrderStatus> statuses = List.of(OrderStatus.PENDING, OrderStatus.SHIPPED);

        Order o1 = Order.builder().id(11L).userId(100L).status(OrderStatus.PENDING).build();
        Order o2 = Order.builder().id(12L).userId(200L).status(OrderStatus.SHIPPED).build();
        when(orderRepository.findByStatusIn(statuses)).thenReturn(List.of(o1, o2));

        UserResponse u1 = UserResponse.builder().id(100L).name("Alex").email("a@ex.com").build();
        UserResponse u2 = UserResponse.builder().id(200L).name("Kate").email("k@ex.com").build();
        when(userClient.getByUserId(100L)).thenReturn(u1);
        when(userClient.getByUserId(200L)).thenReturn(u2);

        OrderResponse r1 = OrderResponse.builder().id(11L).userId(100L).status("PENDING").items(List.of()).build();
        OrderResponse r2 = OrderResponse.builder().id(12L).userId(200L).status("SHIPPED").items(List.of()).build();
        when(orderMapper.toDto(o1, u1)).thenReturn(r1);
        when(orderMapper.toDto(o2, u2)).thenReturn(r2);

        List<OrderResponse> out = service.getOrdersByStatuses(statuses);

        assertThat(out).hasSize(2);
        assertThat(out).extracting(OrderResponse::getStatus)
                .containsExactlyInAnyOrder("PENDING", "SHIPPED");
        verify(orderRepository).findByStatusIn(statuses);
    }

    @Test
    void getOrdersByStatuses_ShouldReturnEmpty_WhenRepoReturnsEmpty() {
        List<OrderStatus> statuses = List.of(OrderStatus.PAID, OrderStatus.SHIPPED);
        when(orderRepository.findByStatusIn(statuses)).thenReturn(List.of());

        List<OrderResponse> out = service.getOrdersByStatuses(statuses);

        assertThat(out).isEmpty();
        verify(orderRepository).findByStatusIn(statuses);
        verifyNoInteractions(userClient, orderMapper);
    }

    @Test
    void getOrdersByStatuses_ShouldMapUserNull_WhenUserServiceReturnsNull() {
        List<OrderStatus> statuses = List.of(OrderStatus.PENDING);
        Order o = Order.builder().id(5L).userId(77L).status(OrderStatus.PENDING).build();
        when(orderRepository.findByStatusIn(statuses)).thenReturn(List.of(o));

        when(userClient.getByUserId(77L)).thenReturn(null);

        OrderResponse r = OrderResponse.builder().id(5L).userId(77L).status("PENDING").items(List.of()).build();
        when(orderMapper.toDto(o, null)).thenReturn(r);

        List<OrderResponse> out = service.getOrdersByStatuses(statuses);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getUser()).isNull();
        verify(orderMapper).toDto(o, null);
    }

    @Test
    void updateOrder_ShouldThrowNotFound_WhenOrderMissing() {
        Long id = 999L;
        Long credentialsId = 111L;
        OrderRequest req = OrderRequest.builder().status("SHIPPED").items(List.of()).build();

        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOrder(id, req, credentialsId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Order not found with id: 999");

        verify(orderRepository).findById(id);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(userClient, orderMapper);
    }

    @Test
    void updateOrder_replacesItemsAndStatus_andSoftDegradesUser() {
        Long credentialsId = 111L;
        Long actualUserId = 4L;

        Order existing = new Order();
        existing.setId(9L);
        existing.setUserId(actualUserId);
        existing.setStatus(OrderStatus.PENDING);
        existing.setOrderItems(new ArrayList<>(List.of(
                OrderItem.builder().order(existing).item(item1).quantity(1).build()
        )));

        OrderRequest req = OrderRequest.builder()
                .status("PAID")
                .items(List.of(OrderItemRequest.builder().itemId(2L).quantity(3).build()))
                .build();

        when(orderRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userClient.getByCredentialsId(credentialsId)).thenReturn(UserResponse.builder().id(actualUserId).build());
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        when(userClient.getByUserId(actualUserId)).thenThrow(new NotFoundException("gone"));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toDto(any(Order.class), eq(null))).thenReturn(new OrderResponse());

        OrderResponse resp = service.updateOrder(9L, req, credentialsId);
        assertThat(resp).isNotNull();

        ArgumentCaptor<Order> savedCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedCaptor.capture());
        Order saved = savedCaptor.getValue();

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getOrderItems()).hasSize(1);
        assertThat(saved.getOrderItems().getFirst().getItem().getId()).isEqualTo(2L);
        assertThat(saved.getOrderItems().getFirst().getQuantity()).isEqualTo(3);
    }

    @Test
    void updateOrder_wrongOwner_throws403() {
        Long credentialsId = 111L;
        Long actualUserId = 5L; // НЕ владелец
        Order existing = Order.builder()
                .id(9L)
                .userId(4L) // владелец другой
                .status(OrderStatus.PENDING)
                .build();

        OrderRequest req = OrderRequest.builder()
                .status("PAID")
                .items(List.of())
                .build();

        when(orderRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userClient.getByCredentialsId(credentialsId)).thenReturn(UserResponse.builder().id(actualUserId).build());

        assertThatThrownBy(() -> service.updateOrder(9L, req, credentialsId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("update only your orders");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deleteOrder_notFound_throws404() {
        Long credentialsId = 111L;
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOrder(1L, credentialsId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Order not found");

        verify(orderRepository, never()).delete(any());
    }

    @Test
    void deleteOrder_wrongOwner_throws403() {
        Long credentialsId = 111L;
        Long actualUserId = 5L;

        Order existing = Order.builder()
                .id(10L)
                .userId(4L)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userClient.getByCredentialsId(credentialsId)).thenReturn(UserResponse.builder().id(actualUserId).build());

        assertThatThrownBy(() -> service.deleteOrder(10L, credentialsId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("delete only your orders");

        verify(orderRepository, never()).delete(any());
    }

    @Test
    void deleteOrder_ok() {
        Long credentialsId = 111L;
        Long actualUserId = 4L;

        Order existing = Order.builder()
                .id(10L)
                .userId(actualUserId)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userClient.getByCredentialsId(credentialsId)).thenReturn(UserResponse.builder().id(actualUserId).build());

        doNothing().when(orderRepository).delete(existing);

        service.deleteOrder(10L, credentialsId);

        verify(orderRepository).delete(existing);
    }
}
