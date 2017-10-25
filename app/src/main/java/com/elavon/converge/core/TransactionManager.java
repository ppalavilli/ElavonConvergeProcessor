package com.elavon.converge.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import co.poynt.api.model.AdjustTransactionRequest;
import co.poynt.api.model.EMVData;
import co.poynt.api.model.FundingSourceType;
import co.poynt.api.model.Processor;
import co.poynt.api.model.ProcessorResponse;
import co.poynt.api.model.ProcessorStatus;
import co.poynt.api.model.Transaction;
import co.poynt.api.model.TransactionAction;
import co.poynt.api.model.TransactionAmounts;
import co.poynt.api.model.TransactionStatus;
import co.poynt.os.model.PoyntError;
import co.poynt.os.services.v1.IPoyntSecurityService;
import co.poynt.os.services.v1.IPoyntTransactionServiceListener;
import fr.devnied.bitlib.BytesUtils;

public class TransactionManager {

    private static final String TAG = "TransactionManager";
    private static TransactionManager transactionManager;
    private IPoyntSecurityService poyntSecurityService;
    private Context context;
    private Map<UUID, Transaction> TRANSACTION_CACHE;

    private TransactionManager(Context context) {
        this.context = context;
        TRANSACTION_CACHE = new HashMap<>();
        bind();
    }

    public static TransactionManager getInstance(Context context) {
        if (transactionManager == null) {
            transactionManager = new TransactionManager(context);
        }
        return transactionManager;
    }

    public synchronized void bind() {
        if (poyntSecurityService == null) {
            context.bindService(new Intent(IPoyntSecurityService.class.getName()),
                    mConnection, Context.BIND_AUTO_CREATE);
        } else {
            // already connected ?
        }
    }

    public boolean isConnected() {
        if (poyntSecurityService != null) {
            return true;
        } else {
            return false;
        }
    }

    public void shutdown() {
        context.unbindService(mConnection);
    }

    /**
     * Class for interacting with the BusinessService
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.e("TransactionManager", "IPoyntSecurityService is now connected");
            // Following the example above for an AIDL interface,
            // this gets an instance of the IRemoteInterface, which we can use to call on the service
            poyntSecurityService = IPoyntSecurityService.Stub.asInterface(service);
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.e("TransactionManager", "IPoyntSecurityService has unexpectedly disconnected - reconnecting");
            poyntSecurityService = null;
            bind();
        }
    };

    public void processTransaction(final Transaction transaction, final String requestId, final IPoyntTransactionServiceListener listener) {
        Log.d(TAG, "PROCESSED TRANSACTION");

        // get track1 length if available
        int track1Len = 0;
        if (transaction.getFundingSource().getEmvData() != null) {
            if (transaction.getFundingSource().getEmvData().getEmvTags().containsKey("0x1F8151")) {
                track1Len = BytesUtils.byteArrayToInt(
                        BytesUtils.fromString(
                                transaction.getFundingSource().getEmvData().getEmvTags().get("0x1F8151")));
                Log.d(TAG, "Track 1 length returned:" + track1Len);
            }
        }

        if (transaction.getId() == null) {
            transaction.setId(UUID.randomUUID());
        }
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(Calendar.getInstance());
        }
        if (transaction.getUpdatedAt() == null) {
            transaction.setUpdatedAt(Calendar.getInstance());
        }

        if (transaction.getAction() == TransactionAction.AUTHORIZE
                || transaction.getAction() == TransactionAction.SALE) {
            // add processor response - below are different test cases to simulate
            ProcessorResponse processorResponse = new ProcessorResponse();
            processorResponse.setApprovalCode("123456");
            // 5.55 will trigger partial approval
            if (transaction.getAmounts().getTransactionAmount() == 555l) {
                processorResponse.setStatus(ProcessorStatus.Successful);
                processorResponse.setApprovedAmount(100l);
                transaction.getAmounts().setTransactionAmount(100l);
                transaction.getAmounts().setOrderAmount(100l);
                processorResponse.setStatusMessage("Partially Approved");
                if (transaction.getAction() == TransactionAction.AUTHORIZE) {
                    transaction.setStatus(TransactionStatus.AUTHORIZED);
                } else {
                    transaction.setStatus(TransactionStatus.CAPTURED);
                }
            } else if (transaction.getAmounts().getTransactionAmount() == 666l) {
                // 6.66 will trigger decline
                processorResponse.setStatus(ProcessorStatus.Successful);
                processorResponse.setApprovedAmount(0l);
                processorResponse.setStatusMessage("Declined - la la la la la");
                processorResponse.setStatusCode("9999");
                Map<String, String> emvTags = new HashMap();
                emvTags.put("0x8A", "3531");
                processorResponse.setEmvTags(emvTags);
                transaction.setStatus(TransactionStatus.DECLINED);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        for (int i = 0; i < 100; i++) {
                            Intent declineIntent = new Intent("poynt.intent.action.DECLINED");
                            context.sendBroadcast(declineIntent);
                        }
                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else if (transaction.getAmounts().getTransactionAmount() == 777l) {
                // 7.77 will trigger remaining balance set to 2.00
                processorResponse.setStatus(ProcessorStatus.Successful);
                processorResponse.setApprovedAmount(transaction.getAmounts().getTransactionAmount());
                processorResponse.setStatusMessage("Approved");
                processorResponse.setRemainingBalance(200l);
                if (transaction.getAction() == TransactionAction.AUTHORIZE) {
                    transaction.setStatus(TransactionStatus.AUTHORIZED);
                } else {
                    transaction.setStatus(TransactionStatus.CAPTURED);
                }
            } else {
                processorResponse.setStatus(ProcessorStatus.Successful);
                processorResponse.setApprovedAmount(transaction.getAmounts().getTransactionAmount());
                processorResponse.setStatusMessage("Approved");
                if (transaction.getAction() == TransactionAction.AUTHORIZE) {
                    transaction.setStatus(TransactionStatus.AUTHORIZED);
                } else {
                    transaction.setStatus(TransactionStatus.CAPTURED);
                }
            }

            processorResponse.setTransactionId(transaction.getId().toString());
            processorResponse.setAcquirer(Processor.CHASE_PAYMENTECH);
            processorResponse.setProcessor(Processor.CREDITCALL);
            transaction.setProcessorResponse(processorResponse);
        } else if (transaction.getAction() == TransactionAction.REFUND) {
            // add processor response
            ProcessorResponse processorResponse = new ProcessorResponse();
            processorResponse.setApprovalCode("123456");
            processorResponse.setStatus(ProcessorStatus.Successful);
            processorResponse.setApprovedAmount(100l);
            transaction.getAmounts().setTransactionAmount(100l);
            transaction.getAmounts().setOrderAmount(100l);
            processorResponse.setStatusMessage("Successful");
            transaction.setStatus(TransactionStatus.REFUNDED);
            processorResponse.setTransactionId(transaction.getId().toString());
            processorResponse.setAcquirer(Processor.REDE);
            processorResponse.setProcessor(Processor.REDE);
            transaction.setProcessorResponse(processorResponse);
        }

        // remove track data and PAN (for now it's actually required)
        if (transaction.getFundingSource().getType() == FundingSourceType.CREDIT_DEBIT) {
            transaction.getFundingSource().getCard().setTrack1data(null);
            transaction.getFundingSource().getCard().setTrack2data(null);
            transaction.getFundingSource().getCard().setTrack3data(null);
            transaction.getFundingSource().getCard().setNumber(null);
            // generate unique hash of the card
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-256");
                digest.update(requestId.getBytes());
                transaction.getFundingSource().getCard().setNumberHashed(
                        BytesUtils.bytesToStringNoSpace(digest.digest())
                );
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            transaction.getFundingSource().getCard().setEncrypted(false);
            // temporary workaround to get transaction
            transaction.getFundingSource().getCard().setExpirationYear(2020);
            transaction.getFundingSource().getCard().setExpirationMonth(12);

        }

        try {
            TRANSACTION_CACHE.put(transaction.getId(), transaction);
            listener.onResponse(transaction, requestId, null);
        } catch (RemoteException e) {
            e.printStackTrace();
            PoyntError poyntError = new PoyntError();
            poyntError.setCode(PoyntError.CARD_DECLINE);
            try {
                listener.onResponse(transaction, requestId, poyntError);
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        }

    }


    public void captureTransaction(String transactionId, AdjustTransactionRequest adjustTransactionRequest, String requestId, IPoyntTransactionServiceListener listener) {
        Log.d(TAG, "CAPTURED TRANSACTION: " + transactionId);
        // get cached transaction
        Transaction transaction = TRANSACTION_CACHE.get(UUID.fromString(transactionId));
        if (transaction == null) {
            // if we did not find one just generate a new one
            transaction = new Transaction();
            transaction.setId(UUID.fromString(transactionId));
            transaction.setCreatedAt(Calendar.getInstance());
        }
        transaction.setAmounts(adjustTransactionRequest.getAmounts());
        transaction.setStatus(TransactionStatus.CAPTURED);
        transaction.setUpdatedAt(Calendar.getInstance());
        try {
            TRANSACTION_CACHE.put(transaction.getId(), transaction);
            if (listener != null) {
                listener.onResponse(transaction, requestId, null);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void updateTransaction(String transactionId, AdjustTransactionRequest adjustTransactionRequest, String requestId, IPoyntTransactionServiceListener listener) {
        Log.d(TAG, "UPDATE TRANSACTION: " + transactionId);
        // get cached transaction
        Transaction transaction = TRANSACTION_CACHE.get(UUID.fromString(transactionId));
        if (transaction == null) {
            // if we did not find one just generate a new one
            transaction = new Transaction();
            transaction.setId(UUID.fromString(transactionId));
            transaction.setCreatedAt(Calendar.getInstance());
        }
        transaction.setAmounts(adjustTransactionRequest.getAmounts());
        transaction.setUpdatedAt(Calendar.getInstance());
        try {
            TRANSACTION_CACHE.put(transaction.getId(), transaction);
            if (listener != null) {
                listener.onResponse(transaction, requestId, null);
            } else {
                Log.d(TAG, "ignoring callback as it's null");
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public void voidTransaction(String transactionId,
                                EMVData emvData,
                                String requestId,
                                IPoyntTransactionServiceListener listener) {
        // get cached transaction
        Transaction transaction = TRANSACTION_CACHE.get(UUID.fromString(transactionId));
        if (transaction == null) {
            // if we did not find one just generate a new one
            transaction = new Transaction();
            transaction.setId(UUID.fromString(transactionId));
            transaction.setCreatedAt(Calendar.getInstance());
            TransactionAmounts amounts = new TransactionAmounts();
            amounts.setOrderAmount(100l);
            amounts.setTransactionAmount(100l);
            transaction.setAmounts(amounts);
        }
        transaction.setStatus(TransactionStatus.VOIDED);
        transaction.setUpdatedAt(Calendar.getInstance());

        try {
            TRANSACTION_CACHE.put(transaction.getId(), transaction);
            listener.onResponse(transaction, requestId, null);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void getTransaction(String transactionId, String requestId, IPoyntTransactionServiceListener listener) {
        Log.d(TAG, "CAPTURED TRANSACTION: " + transactionId);
        // get cached transaction
        Transaction transaction = TRANSACTION_CACHE.get(UUID.fromString(transactionId));
        try {
            listener.onResponse(transaction, requestId, null);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}