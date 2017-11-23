package com.elavon.converge.model.mapper;

import com.elavon.converge.exception.ConvergeMapperException;
import com.elavon.converge.model.ElavonTransactionRequest;
import com.elavon.converge.model.type.ElavonTransactionType;
import com.elavon.converge.util.CurrencyUtil;

import co.poynt.api.model.EBTType;
import co.poynt.api.model.Transaction;

public class MsrEbtMapper extends InterfaceMapper {
    @Override
    ElavonTransactionRequest createAuth(final Transaction t) {
        throw new ConvergeMapperException("Auth not allowed in EBT transaction");
    }

    @Override
    ElavonTransactionRequest createRefund(final Transaction t) {
        throw new ConvergeMapperException("Please implement");
    }

    @Override
    ElavonTransactionRequest createSale(final Transaction t) {
        // converge api only allows msr for food stamp
        if (t.getFundingSource().getEbtDetails().getType() != EBTType.FOOD_STAMP) {
            throw new ConvergeMapperException("Only food stamp allowed for MSR EBT");
        }

        final ElavonTransactionRequest request = new ElavonTransactionRequest();
        request.setTransactionType(ElavonTransactionType.EBT_SALE);
        request.setEncryptedTrackData(t.getFundingSource().getCard().getTrack2data());
        request.setAmount(CurrencyUtil.getAmount(t.getAmounts().getTransactionAmount(), t.getAmounts().getCurrency()));
        request.setPinKsn(t.getFundingSource().getVerificationData().getKeySerialNumber());
        request.setKeyPointer("T");
        request.setPinBlock(t.getFundingSource().getVerificationData().getPin());
        return request;
    }

    @Override
    ElavonTransactionRequest createVerify(final Transaction t) {
        throw new ConvergeMapperException("Verify not allowed in EBT transaction");
    }

    @Override
    ElavonTransactionRequest createReverse(final String t) {
        throw new ConvergeMapperException("Reverse not allowed in EBT transaction");
    }
}
