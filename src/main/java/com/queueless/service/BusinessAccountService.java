package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.BusinessAccount;
import com.queueless.entity.User;
import com.queueless.repository.BusinessAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessAccountService {

    private final BusinessAccountRepository businessAccountRepository;

    @Transactional
    public BusinessAccountDto getMyBusinessAccount(UUID ownerId) {
        BusinessAccount account = businessAccountRepository.findByOwnerId(ownerId)
                .orElseGet(() -> createDefaultAccount(ownerId));
        return toDto(account);
    }

    @Transactional
    public BusinessAccountDto updateBusinessAccount(UUID ownerId, UpdateBusinessAccountRequest request) {
        BusinessAccount account = businessAccountRepository.findByOwnerId(ownerId)
                .orElseGet(() -> createDefaultAccount(ownerId));

        account.setName(request.getName());
        account.setBillingEmail(request.getBillingEmail());
        account.setGstin(request.getGstin());
        if (request.getTaxPercent() != null) account.setTaxPercent(request.getTaxPercent());
        if (request.getInvoicePrefix() != null) account.setInvoicePrefix(request.getInvoicePrefix());
        
        if (request.getRazorpayKeyId() != null) account.setRazorpayKeyId(request.getRazorpayKeyId());
        if (request.getRazorpayKeySecret() != null) account.setRazorpayKeySecret(request.getRazorpayKeySecret());
        
        account.setSettlementFrequency(request.getSettlementFrequency());
        account.setPayoutAccountName(request.getPayoutAccountName());
        if (request.getPayoutAccountNumber() != null) {
            String masked = maskAccountNumber(request.getPayoutAccountNumber());
            account.setPayoutAccountNumberMasked(masked);
        }
        account.setPayoutIfsc(request.getPayoutIfsc());
        account.setSmsSenderId(request.getSmsSenderId());
        account.setWhatsappNumber(request.getWhatsappNumber());

        return toDto(businessAccountRepository.save(account));
    }

    private BusinessAccount createDefaultAccount(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        BusinessAccount account = BusinessAccount.builder()
                .owner(owner)
                .name("My Business")
                .active(true)
                .build();
        return businessAccountRepository.save(account);
    }

    private String maskAccountNumber(String acc) {
        if (acc == null || acc.length() < 4) return "****";
        return "****" + acc.substring(acc.length() - 4);
    }

    private BusinessAccountDto toDto(BusinessAccount account) {
        return BusinessAccountDto.builder()
                .id(account.getId())
                .name(account.getName())
                .billingEmail(account.getBillingEmail())
                .gstin(account.getGstin())
                .taxPercent(account.getTaxPercent())
                .invoicePrefix(account.getInvoicePrefix())
                .razorpayKeyId(account.getRazorpayKeyId())
                .payoutEnabled(account.isPayoutEnabled())
                .settlementFrequency(account.getSettlementFrequency())
                .payoutAccountName(account.getPayoutAccountName())
                .payoutAccountNumberMasked(account.getPayoutAccountNumberMasked())
                .payoutIfsc(account.getPayoutIfsc())
                .smsSenderId(account.getSmsSenderId())
                .whatsappNumber(account.getWhatsappNumber())
                .active(account.isActive())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
