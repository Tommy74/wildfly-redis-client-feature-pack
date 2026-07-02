/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.web;

import org.wildfly.subsystem.SubsystemConfiguration;
import org.wildfly.subsystem.SubsystemExtension;
import org.wildfly.subsystem.SubsystemPersistence;

/**
 * Extension that adds Redis session management support to the distributable-web subsystem.
 * This class shadows the base WildFly DistributableWebExtension to register our enhanced
 * SubsystemResourceDefinitionRegistrar that includes Redis providers.
 * 
 * @author WildFly Redis Team
 */
public class DistributableWebExtension extends SubsystemExtension<DistributableWebSubsystemSchema> {

    public DistributableWebExtension() {
        super(SubsystemConfiguration.of(DistributableWebSubsystemResourceDefinitionRegistrar.REGISTRATION.getName(),
                                       DistributableWebSubsystemModel.CURRENT,
                                       DistributableWebSubsystemResourceDefinitionRegistrar::new),
              SubsystemPersistence.of(DistributableWebSubsystemSchema.CURRENT));
    }
}
