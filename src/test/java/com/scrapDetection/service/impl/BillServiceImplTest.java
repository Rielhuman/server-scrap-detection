package com.scrapDetection.service.impl;

import com.scrapDetection.entity.Account;
import com.scrapDetection.entity.Bill;
import com.scrapDetection.entity.Role;
import com.scrapDetection.exception.InvalidRequestException;
import com.scrapDetection.mapper.BillMapper;
import com.scrapDetection.repository.AccountRepository;
import com.scrapDetection.repository.BillRepository;
import com.scrapDetection.repository.MaterialRepository;
import com.scrapDetection.service.CurrentUserService;
import com.scrapDetection.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BillMapper billMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private BillServiceImpl billService;

    @Test
    void customerCannotReadWalkInBill() {
        Account customer = Account.builder()
                .accountId(10L)
                .role(Role.CUSTOMER)
                .build();
        Bill walkInBill = Bill.builder()
                .billId(20L)
                .customer(null)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(customer);
        when(billRepository.findById(20L)).thenReturn(Optional.of(walkInBill));

        assertThrows(InvalidRequestException.class, () -> billService.getBillById(20L));
        verifyNoInteractions(billMapper);
    }
}
