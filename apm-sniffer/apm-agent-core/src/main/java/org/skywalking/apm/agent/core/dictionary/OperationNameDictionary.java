/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.dictionary;

import io.netty.util.internal.ConcurrentSet;
import org.skywalking.apm.network.proto.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.skywalking.apm.agent.core.conf.Config.Dictionary.OPERATION_NAME_BUFFER_SIZE;

/**
 * @author wusheng
 */
public enum OperationNameDictionary {

    /**
     * 单例
     */
    INSTANCE;

    /**
     * 操作名与操作编号的映射
     */
    private Map<OperationNameKey, Integer> operationNameDictionary = new ConcurrentHashMap<OperationNameKey, Integer>();
    /**
     * 未注册的操作名集合
     */
    private Set<OperationNameKey> unRegisterOperationNames = new ConcurrentSet<OperationNameKey>();

    public PossibleFound findOrPrepare4Register(int applicationId, String operationName) {
        return find0(applicationId, operationName, true);
    }

    public PossibleFound findOnly(int applicationId, String operationName) {
        return find0(applicationId, operationName, false);
    }

    private PossibleFound find0(int applicationId, String operationName, boolean registerWhenNotFound) {
        if (operationName == null || operationName.length() == 0) {
            return new NotFound();
        }
        OperationNameKey key = new OperationNameKey(applicationId, operationName);
        Integer operationId = operationNameDictionary.get(key);
        if (operationId != null) {
            return new Found(operationId);
        } else {
            // 添加到 unRegisterOperationNames
            if (registerWhenNotFound &&
                operationNameDictionary.size() + unRegisterOperationNames.size() < OPERATION_NAME_BUFFER_SIZE) {
                unRegisterOperationNames.add(key);
            }
            return new NotFound();
        }
    }

    public void syncRemoteDictionary(
        ServiceNameDiscoveryServiceGrpc.ServiceNameDiscoveryServiceBlockingStub serviceNameDiscoveryServiceBlockingStub) {
        if (unRegisterOperationNames.size() > 0) {
            // 创建请求
            ServiceNameCollection.Builder builder = ServiceNameCollection.newBuilder();
            for (OperationNameKey operationNameKey : unRegisterOperationNames) {
                ServiceNameElement serviceNameElement = ServiceNameElement.newBuilder()
                    .setApplicationId(operationNameKey.getApplicationId())
                    .setServiceName(operationNameKey.getOperationName())
                    .build();
                builder.addElements(serviceNameElement);
            }
            // 查找未知应用编码集合
            ServiceNameMappingCollection serviceNameMappingCollection = serviceNameDiscoveryServiceBlockingStub.discovery(builder.build());
            if (serviceNameMappingCollection.getElementsCount() > 0) {
                for (ServiceNameMappingElement serviceNameMappingElement : serviceNameMappingCollection.getElementsList()) {
                    OperationNameKey key = new OperationNameKey(
                        serviceNameMappingElement.getElement().getApplicationId(),
                        serviceNameMappingElement.getElement().getServiceName());
                    unRegisterOperationNames.remove(key);
                    operationNameDictionary.put(key, serviceNameMappingElement.getServiceId());
                }
            }
        }
    }

    /**
     * 操作名 Key
     */
    private class OperationNameKey {

        /**
         * 应用编号
         */
        private int applicationId;
        /**
         * 操作名
         */
        private String operationName;

        public OperationNameKey(int applicationId, String operationName) {
            this.applicationId = applicationId;
            this.operationName = operationName;
        }

        public int getApplicationId() {
            return applicationId;
        }

        public String getOperationName() {
            return operationName;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            OperationNameKey key = (OperationNameKey)o;

            if (applicationId != key.applicationId)
                return false;
            return operationName.equals(key.operationName);
        }

        @Override public int hashCode() {
            int result = applicationId;
            result = 31 * result + operationName.hashCode();
            return result;
        }
    }
}
