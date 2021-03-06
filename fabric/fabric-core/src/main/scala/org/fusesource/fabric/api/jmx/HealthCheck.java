/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.api.jmx;

import org.fusesource.fabric.api.FabricService;
import org.fusesource.fabric.api.FabricStatus;
import org.fusesource.fabric.api.ProfileStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An MBean for checking the health of a Fabric
 */
public class HealthCheck implements HealthCheckMBean {
    private static final transient Logger LOG = LoggerFactory.getLogger(HealthCheck.class);

    private final FabricService fabricService;
    private NumberFormat percentInstance = NumberFormat.getPercentInstance();
    private ObjectName objectName;

    public HealthCheck(FabricService fabricService) {
        this.fabricService = fabricService;
    }

    public ObjectName getObjectName() throws MalformedObjectNameException {
        if (objectName == null) {
            // TODO to avoid mbean clashes if ever a JVM had multiple FabricService instances, we may
            // want to add a parameter of the fabric ID here...
            objectName = new ObjectName("org.fusesource.fabric:service=Health");
        }
        return objectName;
    }

    public void setObjectName(ObjectName objectName) {
        this.objectName = objectName;
    }

    public void registerMBeanServer(MBeanServer mbeanServer) {
        try {
            ObjectName name = getObjectName();
            ObjectInstance objectInstance = mbeanServer.registerMBean(this, name);
        } catch (Exception e) {
            LOG.warn("An error occured during mbean server registration: " + e, e);
        }
    }

    public void unregisterMBeanServer(MBeanServer mbeanServer) {
        if (mbeanServer != null) {
            try {
                ObjectName name = getObjectName();
                mbeanServer.unregisterMBean(name);
            } catch (Exception e) {
                LOG.warn("An error occured during mbean server registration: " + e, e);
            }
        }
    }


    @Override
    public List<HealthStatus> healthList() {
        List<HealthStatus> answer = new ArrayList<HealthStatus>();
        FabricStatus status = fabricService.getFabricStatus();
        Collection<ProfileStatus> statuses = status.getProfileStatusMap().values();
        for (ProfileStatus profile : statuses) {
            String id = profile.getProfile();
            int instances = profile.getCount();
            Integer minimum = profile.getMinimumInstances();
            Integer maximum = profile.getMaximumInstances();
            double healthPercent = profile.getHealth(instances);

            String level = "INFO";
            String message = "Profile " + id + " has health " + percentInstance.format(healthPercent);
            if (minimum != null) {
                if (instances <= 0) {
                    level = "ERROR";
                    message = "Profile " + id + " has no instances running! Should have at least " + minimum;
                } else if (instances < minimum) {
                    level = "WARNING";
                    message = "Profile " + id + " needs more instances running. Should have at least " + minimum + " but currently has only " + instances;
                }
            }
            if (maximum != null && level.equals("INFO") && instances > maximum) {
                level = "WARNING";
                message = "Profile " + id + " has too many instances running. Should have at most " + maximum + " but currently has only " + instances;
            }
            answer.add(new HealthStatus("org.fusesource.fabric.profileHealth", id, level, message, instances, minimum, maximum, healthPercent));
        }
        return answer;
    }
}
