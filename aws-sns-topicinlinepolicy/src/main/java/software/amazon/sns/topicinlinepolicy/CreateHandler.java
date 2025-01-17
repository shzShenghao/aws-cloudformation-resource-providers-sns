package software.amazon.sns.topicinlinepolicy;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.utils.CollectionUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.HandlerErrorCode;


public class CreateHandler extends BaseHandlerStd {
    private software.amazon.cloudformation.proxy.Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SnsClient> proxyClient,
            final Logger logger) {

        this.logger = logger;
        final String action = "Create";
        ResourceModel resourceModel = request.getDesiredResourceState();

        if (resourceModel == null || StringUtils.isNullOrEmpty(resourceModel.getTopicArn())
                || CollectionUtils.isNullOrEmpty(resourceModel.getPolicyDocument())) {
            return ProgressEvent.failed(resourceModel, callbackContext, HandlerErrorCode.InvalidRequest, EMPTY_POLICY_AND_TOPICARN_ERROR_MESSAGE );
        }

        if(isDefaultPolicy(request, resourceModel)){
            return ProgressEvent.failed(resourceModel, callbackContext, HandlerErrorCode.InvalidRequest, DEFAULT_POLICY_ERROR_MESSAGE);
        }

        logger.log(String.format("[StackId: %s, ClientRequestToken: %s] Calling Create SNS TopicInlinePolicy", request.getStackId(), request.getClientRequestToken()));

        return ProgressEvent.progress(resourceModel, callbackContext)
                .then(progress -> {
                    if (doesTopicPolicyExist(proxyClient, request, resourceModel) && !callbackContext.isPropagationDelay()) {
                        return ProgressEvent.failed(null, callbackContext, HandlerErrorCode.AlreadyExists, alreadyExistsErrorMessage(resourceModel));
                    }
                    return progress;
                })
                .then(progress -> updateTopicPolicy(proxy, proxyClient, request, progress, logger, action, getPolicyDocument(request), resourceModel.getTopicArn()))
                .then(progress -> {
                    if (!callbackContext.isPropagationDelay()) {
                        callbackContext.setPropagationDelay(true);
                        return ProgressEvent.defaultInProgressHandler(callbackContext,
                                STABILIZATION_DELAY_IN_SECONDS,
                                resourceModel);
                    }
                    logger.log(String.format("Resource created in StackId: %s in Topic: %s",
                            request.getStackId(), resourceModel.getTopicArn()));
                    progress.getCallbackContext().setPropagationDelay(true);
                    return ProgressEvent.defaultSuccessHandler(progress.getResourceModel());
                });
    }

}
