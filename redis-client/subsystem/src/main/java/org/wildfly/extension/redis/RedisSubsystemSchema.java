/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.redis;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.staxmapper.IntVersion;

enum RedisSubsystemSchema implements SubsystemResourceXMLSchema<RedisSubsystemSchema> {
    VERSION_1_0(1, 0),
    ;

    static final RedisSubsystemSchema CURRENT = VERSION_1_0;
    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);
    private final VersionedNamespace<IntVersion, RedisSubsystemSchema> namespace;

    RedisSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(RedisSubsystemRegistrar.NAME, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, RedisSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        return this.factory.subsystemElement(RedisSubsystemRegistrar.REGISTRATION)
                .withContent(
                        this.factory.choice()
                                .withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                                .addElement(
                                        this.factory.namedElement(RedisConnectionProviderRegistrar.REGISTRATION)
                                                .addAttributes(RedisConnectionProviderRegistrar.ATTRIBUTES)
                                                .build()
                                )
                                .build()
                )
                .build();
    }
}
