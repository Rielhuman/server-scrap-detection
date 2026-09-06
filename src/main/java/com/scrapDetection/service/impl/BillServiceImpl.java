package com.scrapDetection.service.impl;

import com.scrapDetection.dto.bill.BillItemRequestDTO;
import com.scrapDetection.dto.bill.BillRequestDTO;
import com.scrapDetection.dto.bill.BillResponseDTO;
import com.scrapDetection.dto.bill.BillSummaryDTO;
import com.scrapDetection.entity.*;
import com.scrapDetection.exception.InvalidRequestException;
import com.scrapDetection.exception.ResourceNotFoundException;
import com.scrapDetection.mapper.BillMapper;
import com.scrapDetection.repository.AccountRepository;
import com.scrapDetection.repository.BillRepository;
import com.scrapDetection.repository.MaterialRepository;
import com.scrapDetection.service.BillService;
import com.scrapDetection.service.CurrentUserService;
import com.scrapDetection.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BillServiceImpl implements BillService {

    private final BillRepository billRepository;
    private final MaterialRepository materialRepository;
    private final AccountRepository accountRepository;
    private final BillMapper billMapper;
    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    @Override
    public BillResponseDTO createBill(BillRequestDTO requestDTO) {
        Account currentUser = currentUserService.getCurrentUser();

        Account customer;
        if (requestDTO.getCustomerId() != null) {
            customer = accountRepository.findById(requestDTO.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", requestDTO.getCustomerId()));
        } else {
            customer = null;
        }

        Bill bill = Bill.builder()
                .customer(customer)
                .createdBy(currentUser)
                .totalWorth(0.0) // will be calculated
                .build();

        double totalWorth = 0.0;

        for (BillItemRequestDTO itemDto : requestDTO.getItems()) {
            Material material = materialRepository.findById(itemDto.getMaterialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material", itemDto.getMaterialId()));

            if (!material.getScrapYard().getYardId().equals(currentUser.getScrapYard().getYardId())) {
                throw new InvalidRequestException(
                        "You can only create bills for materials in your yard (materialId=" + itemDto.getMaterialId() + ")");
            }

            double lineWorth = itemDto.getWeight() * material.getItemPrice();

            Transaction transaction = Transaction.builder()
                    .material(material)
                    .weight(itemDto.getWeight())
                    .lineWorth(lineWorth)
                    .isOverridden(itemDto.getIsOverridden() != null ? itemDto.getIsOverridden() : false)
                    .originalWeight(itemDto.getOriginalWeight())
                    .build();

            if (itemDto.getOriginalMaterialId() != null) {
                Material originalMaterial = materialRepository.findById(itemDto.getOriginalMaterialId())
                        .orElse(null);
                transaction.setOriginalMaterial(originalMaterial);
            }

            bill.addTransaction(transaction);
            totalWorth += lineWorth;

            // Update stock
            material.setStock(material.getStock() + itemDto.getWeight());
            materialRepository.save(material);
        }

        bill.setTotalWorth(totalWorth);

        Bill savedBill = billRepository.save(bill);
        if(currentUser.getRole().equals(Role.STAFF)) {
            Account yardOwner = accountRepository.
                    findByScrapYardYardIdAndRole(currentUser.getScrapYard().getYardId(),Role.YARD_OWNER)
                    .getFirst();
            notificationService.createBillNotification(yardOwner, savedBill);
        }
        notificationService.createBillNotification(currentUser, savedBill);
        if (customer != null && !customer.getAccountId().equals(currentUser.getAccountId())) {
            notificationService.createBillNotification(customer, savedBill);
        }
        return billMapper.toResponseDTO(savedBill);
    }

    @Override
    public BillResponseDTO getBillById(Long billId) {
        Account currentUser = currentUserService.getCurrentUser();
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        if (currentUser.getRole().equals(Role.CUSTOMER)
                && (bill.getCustomer() == null || !bill.getCustomer().equals(currentUser))) {
            throw new InvalidRequestException("You can only get your bill as a customer!");
        }else if(currentUser.getRole().equals(Role.STAFF) && !bill.getCreatedBy().equals(currentUser)){
            throw new InvalidRequestException("You can only get the bills you created!");
        }else if(currentUser.getRole().equals(Role.YARD_OWNER) && !bill.getCreatedBy().getScrapYard().equals(currentUser.getScrapYard())){
            throw new InvalidRequestException("You can only get the bills of your yard!");
        }
        return billMapper.toResponseDTO(bill);
    }

    @Override
    public List<BillSummaryDTO> getBillsByCustomer() {
        Long customerId = currentUserService.getCurrentUser().getAccountId();
        return billMapper.toSummaryDTOList(billRepository.findByCustomerAccountIdOrderByCreatedAtDesc(customerId));
    }

    @Override
    public List<BillSummaryDTO> getBillsByYard(Long yardId) {
        return billMapper.toSummaryDTOList(billRepository.findByYardId(yardId));
    }

    @Override
    public List<BillSummaryDTO> getBillsByStaff(Long staffId) {
        return billMapper.toSummaryDTOList(billRepository.findByCreatedByAccountId(staffId));
    }

    @Override
    public List<BillSummaryDTO> getBillsByDateRange(LocalDateTime start, LocalDateTime end) {
        Long yardId = currentUserService.getCurrentUser().getScrapYard().getYardId();
        return billMapper.toSummaryDTOList(billRepository.findByCreatedByScrapYardYardIdAndCreatedAtBetweenOrderByCreatedAtDesc(yardId, start, end));
    }
}