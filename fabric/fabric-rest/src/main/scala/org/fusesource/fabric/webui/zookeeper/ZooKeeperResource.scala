/*
 * Copyright 2010 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.fusesource.fabric.webui.zookeeper

import javax.ws.rs.{PathParam, GET, Path}
import org.fusesource.fabric.zookeeper.IZKClient
import javax.xml.bind.annotation.{XmlElement, XmlAttribute, XmlRootElement}
import org.fusesource.fabric.webui.{Services, BaseResource}
import org.fusesource.fabric.webui.{Services, BaseResource}
import org.codehaus.jackson.annotate.JsonProperty

@Path("/zookeeper")
class ZooKeeperResource(val zookeeper: IZKClient, val path: String) extends BaseResource {

  def this() = this(Services.zoo_keeper, "/")

  @JsonProperty
  def getPath = path

  @JsonProperty
  def getValue = zookeeper.getStringData(path)

  @JsonProperty
  def getChildren: Array[String] = zookeeper.getChildren(path).toArray(new Array[String](0))

  @Path("{path:.*}")
  @GET
  def getChild(@PathParam("path") child: String): ZooKeeperResource = {
    val p = if (path.endsWith("/")) path else path + "/"
    new ZooKeeperResource(zookeeper, p + child)
  }

}

