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
package org.fusesource.fabric.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.fusesource.fabric.api.Container;
import org.fusesource.fabric.api.Profile;
import org.fusesource.fabric.api.Version;
import org.fusesource.fabric.commands.support.ContainerUpgradeSupport;

@Command(name = "container-upgrade", scope = "fabric", description = "Upgrade containers to a new version")
public class ContainerUpgrade extends ContainerUpgradeSupport {

    @Option(name = "--version", description = "The version to upgrade", required = true)
    private String version;

    @Argument(index = 0, name = "container", description = "The list of containers to upgrade. Empty list assumes current container only.", required = false, multiValued = true)
    private List<String> containerIds;

    @Override
    protected Object doExecute() throws Exception {
        checkFabricAvailable();
        // check and validate version
        Version version = fabricService.getVersion(this.version);

        if (containerIds == null || containerIds.isEmpty()) {
            containerIds = Arrays.asList(fabricService.getCurrentContainer().getId());
        }

        List<Container> toUpgrade = new ArrayList<Container>();
        List<Container> same = new ArrayList<Container>();
        for (String containerName : containerIds) {
            Container container = fabricService.getContainer(containerName);

            // check first that all can upgrade
            int num = canUpgrade(version, container);
            if (num < 0) {
                throw new IllegalArgumentException("Container " + container.getId() + " has already higher version " + container.getVersion()
                        + " than the requested version " + version + " to update.");
            } else if (num == 0) {
                // same version
                same.add(container);
            } else {
                // needs upgrade
                toUpgrade.add(container);
            }
        }
        
        // report same version
        for (Container container : same) {
            System.out.println("Container " + container.getId() + " is already version " + version);
        }
        
        // report and do upgrades
        for (Container container : toUpgrade) {
            Version oldVersion = container.getVersion();
            Profile[] oldProfiles = container.getProfiles();
            
            // create list of new profiles
            Profile[] newProfiles = getProfilesForUpgradeOrRollback(oldProfiles, version);

            // upgrade version first
            container.setVersion(version);
            // then set new profiles, which triggers container to update bundles and whatnot
            container.setProfiles(newProfiles);
            
            log.debug("Upgraded container {} from {} to {}", new Object[]{container, oldVersion, version});
            System.out.println("Upgraded container " + container.getId() + " from version " + oldVersion + " to " + version);
        }

        return null;
    }
    
}