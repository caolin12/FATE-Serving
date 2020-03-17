package com.webank.ai.fate.serving.interceptor;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import com.webank.ai.fate.serving.pojo.InferenceRequest;
import com.webank.ai.fate.serving.core.bean.Context;
import com.webank.ai.fate.serving.core.exceptions.GuestInvalidParamException;
import com.webank.ai.fate.serving.core.rpc.core.InboundPackage;
import com.webank.ai.fate.serving.core.rpc.core.Interceptor;
import com.webank.ai.fate.serving.core.rpc.core.OutboundPackage;
import com.webank.ai.fate.serving.utils.InferenceUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class GuestSingleParamInterceptor implements Interceptor {

    @Override
    public void doPreProcess(Context context, InboundPackage inboundPackage, OutboundPackage outboundPackage) throws Exception {

        try {
            byte[] reqBody = (byte[]) inboundPackage.getBody();
            InferenceRequest inferenceRequest = null;
            inferenceRequest = JSON.parseObject(reqBody, InferenceRequest.class);
            inboundPackage.setBody(inferenceRequest);
            Preconditions.checkArgument(inferenceRequest != null, "request message parse error");
            Preconditions.checkArgument(inferenceRequest.getFeatureData() != null, "no feature data");
            Preconditions.checkArgument(inferenceRequest.getSendToRemoteFeatureData() != null, "no send to remote feature data");
            Preconditions.checkArgument(StringUtils.isNotBlank(inferenceRequest.getServiceId()), "no service id");
            if (inferenceRequest.getCaseid().length() == 0) {
                inferenceRequest.setCaseId(InferenceUtils.generateCaseid());
            }
            context.setCaseId(inferenceRequest.getCaseid());
            context.setServiceId(inferenceRequest.getServiceId());
        }catch(Exception e){
            throw  new GuestInvalidParamException(e.getMessage());
        }

    }


}
