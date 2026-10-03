package com.orderhub.security;

import com.orderhub.domain.Customer;
import com.orderhub.domain.Order;
import com.orderhub.domain.Role;
import com.orderhub.domain.User;
import com.orderhub.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAccessPolicyTest {

    private static final long CURRENT_USER_ID = 3L;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private OrderAccessPolicy orderAccessPolicy;

    @Test
    void staffCanAccessAnyOrder() {
        AppUserDetails staff = userWithRole(Role.STAFF);
        Order order = orderOwnedBy(customerWithId(20L));

        assertThatCode(() -> orderAccessPolicy.assertCanAccess(order, staff)).doesNotThrowAnyException();
        verify(customerRepository, never()).findByUserId(anyLong());
    }

    @Test
    void adminCanAccessAnyOrder() {
        AppUserDetails admin = userWithRole(Role.ADMIN);
        Order order = orderOwnedBy(customerWithId(20L));

        assertThatCode(() -> orderAccessPolicy.assertCanAccess(order, admin)).doesNotThrowAnyException();
        verify(customerRepository, never()).findByUserId(anyLong());
    }

    @Test
    void customerCanAccessOwnOrder() {
        Customer self = customerWithId(10L);
        when(customerRepository.findByUserId(CURRENT_USER_ID)).thenReturn(Optional.of(self));

        Order order = orderOwnedBy(self);

        assertThatCode(() -> orderAccessPolicy.assertCanAccess(order, userWithRole(Role.CUSTOMER)))
                .doesNotThrowAnyException();
    }

    @Test
    void customerCannotAccessAnotherCustomersOrder() {
        when(customerRepository.findByUserId(CURRENT_USER_ID)).thenReturn(Optional.of(customerWithId(10L)));

        Order order = orderOwnedBy(customerWithId(20L));

        assertThatThrownBy(() -> orderAccessPolicy.assertCanAccess(order, userWithRole(Role.CUSTOMER)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Order does not belong to current customer");
    }

    @Test
    void userWithoutCustomerProfileIsDenied() {
        when(customerRepository.findByUserId(CURRENT_USER_ID)).thenReturn(Optional.empty());

        Order order = orderOwnedBy(customerWithId(20L));

        assertThatThrownBy(() -> orderAccessPolicy.assertCanAccess(order, userWithRole(Role.CUSTOMER)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No customer profile for current user");
    }

    private static AppUserDetails userWithRole(Role role) {
        User user = new User();
        user.setId(CURRENT_USER_ID);
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setRole(role);
        return new AppUserDetails(user);
    }

    private static Customer customerWithId(long id) {
        Customer customer = new Customer(null, "Customer " + id, "customer" + id + "@example.com");
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private static Order orderOwnedBy(Customer customer) {
        Order order = new Order();
        order.setCustomer(customer);
        return order;
    }
}
