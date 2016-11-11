/*
 * Copyright 2016-present Open Networking Laboratory
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
 */
package org.onosproject.evpn.rsc;

import static org.onlab.util.Tools.groupedThreads;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.evpn.rsc.baseport.BasePortService;
import org.onosproject.evpn.rsc.vpninstance.VpnInstanceService;
import org.onosproject.evpn.rsc.vpnport.VpnPortService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EtcdMonitor {
    private static final String KEYPATH = "/proton/net-l3vpn/";
    private static String etcduri;
    private CloseableHttpAsyncClient httpClient;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ExecutorService executorService = Executors
            .newSingleThreadExecutor(groupedThreads("EVPN-EtcdMonitor",
                                                    "executor-%d", log));

    public EtcdMonitor(String etcduri) {
        this.etcduri = etcduri + "/v2/keys" + KEYPATH + "?wait=true&recursive=true";
        RequestConfig requestConfig = RequestConfig.custom().build();
        httpClient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig).build();
        httpClient.start();
    }

    public void etcdMonitor(Long index) {
        String uri = etcduri;
        if (index != null) {
            uri += "&waitIndex=" + index;
        }
        HttpGet request = new HttpGet(URI.create(uri));
        log.info("Etcd monitor to url {} and keypath {}", uri, KEYPATH);
        executorService.execute(new Runnable() {
            public void run() {
                httpClient.execute(request, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(HttpResponse result) {
                        StatusLine statusLine = result.getStatusLine();
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == 200 && result.getEntity() != null) {
                            try {
                                String json = EntityUtils
                                        .toString(result.getEntity());
                                EtcdResponse response = getEtcdResponse(json);
                                if (response.action.toString().equals("set")) {
                                    log.info("Etcd monitor data is url {} and action {} value {}",
                                             response.key, response.action,
                                             response.value.toString());
                                } else {
                                    log.info("Etcd monitor data is url {} and action {}",
                                             response.key,
                                             response.action.toString());
                                }
                                processEtcdResponse(response);
                                etcdMonitor(response.modifiedIndex + 1);
                            } catch (IOException e) {
                                log.debug("Etcd monitor failed with error {}", e.getMessage());
                            }
                        }
                    }
                    @Override
                    public void cancelled() {
                        // TODO Auto-generated method stub
                    }
                    @Override
                    public void failed(Exception arg0) {
                        // TODO Auto-generated method stub
                    }
                });
            }
        });
    }

    private EtcdResponse getEtcdResponse(String result) {
        ObjectMapper mapper = new ObjectMapper();
        EtcdResponse response = null;
        try {
            JsonNode jsonNode = mapper.readTree(result);
            String action = jsonNode.get("action").asText();
            String key = jsonNode.get("node").get("key").asText();
            long mIndex = jsonNode.get("node").get("modifiedIndex").asLong();
            long cIndex = jsonNode.get("node").get("createdIndex").asLong();
            if (action.equals("set")) {
                String value = jsonNode.get("node").get("value").asText();
                JsonNode modifyValue = mapper.readTree(value.replace("\\", ""));
                response = new EtcdResponse(action, key, modifyValue, mIndex,
                                            cIndex);
            } else {
                response = new EtcdResponse(action, key, null, mIndex, cIndex);
            }
        } catch (IOException e) {
            log.debug("Change etcd response into json with error {}", e.getMessage());
        }
        return response;
    }

    private void processEtcdResponse(EtcdResponse watchResult) {
        String[] list = watchResult.key.split("/");
        String target = list[list.length - 2];
        if (target.equals("ProtonBasePort")) {
            BasePortService basePortService = DefaultServiceDirectory.getService(BasePortService.class);
            basePortService.processEtcdResponse(watchResult);
        } else if (target.equals("VpnInstance")) {
            VpnInstanceService vpnInstanceService = DefaultServiceDirectory.getService(VpnInstanceService.class);
            vpnInstanceService.processEtcdResponse(watchResult);
        } else if (target.equals("VPNPort")) {
            VpnPortService vpnPortService = DefaultServiceDirectory.getService(VpnPortService.class);
            vpnPortService.processEtcdResponse(watchResult);
        }
    }
}
