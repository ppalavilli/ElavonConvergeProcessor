package com.elavon.converge.model.mapper;

import com.elavon.converge.model.ElavonTransactionRequest;
import com.elavon.converge.model.type.ElavonTransactionType;
import com.elavon.converge.util.CardUtil;
import com.elavon.converge.util.CurrencyUtil;

import co.poynt.api.model.Transaction;

public class KeyedGiftcardMapper extends InterfaceMapper {
    @Override
    ElavonTransactionRequest createAuth(final Transaction t) {
        throw new RuntimeException("Please implement");
    }

    @Override
    ElavonTransactionRequest createRefund(final Transaction t) {
        final ElavonTransactionRequest request = new ElavonTransactionRequest();
        request.setTransactionType(ElavonTransactionType.GIFT_CARD_REFUND);
        // TODO need unencrypted card number
        request.setCardNumber(t.getFundingSource().getCard().getNumber());
        request.setExpDate(CardUtil.getCardExpiry(t.getFundingSource().getCard()));
        request.setAmount(CurrencyUtil.getAmount(t.getAmounts().getTransactionAmount(), t.getAmounts().getCurrency()));
        if (t.getAmounts().getTipAmount() != null) {
            request.setTipAmount((CurrencyUtil.getAmount(t.getAmounts().getTipAmount(), t.getAmounts().getCurrency())));
        }
        return request;
    }

    @Override
    ElavonTransactionRequest createSale(final Transaction t) {
        final ElavonTransactionRequest request = new ElavonTransactionRequest();
        request.setTransactionType(ElavonTransactionType.GIFT_CARD_SALE);
        // TODO for activation
        // request.setGiftcardTenderType("1");
        // TODO need unencrypted card number
        request.setCardNumber(t.getFundingSource().getCard().getNumber());
        request.setExpDate(CardUtil.getCardExpiry(t.getFundingSource().getCard()));
        request.setAmount(CurrencyUtil.getAmount(t.getAmounts().getTransactionAmount(), t.getAmounts().getCurrency()));
        if (t.getAmounts().getTipAmount() != null) {
            request.setTipAmount((CurrencyUtil.getAmount(t.getAmounts().getTipAmount(), t.getAmounts().getCurrency())));
        }
        return request;
    }

    @Override
    ElavonTransactionRequest createVerify(final Transaction t) {
        throw new RuntimeException("Please implement");
    }

    @Override
    ElavonTransactionRequest createReverse(String t) {
        throw new RuntimeException("Please implement");
    }
}
