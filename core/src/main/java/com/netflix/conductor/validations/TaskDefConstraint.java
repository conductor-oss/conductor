package com.netflix.conductor.validations;

import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;

public class TaskDefConstraint {
    @Singleton
    @ProvidesIntoSet
    public static ConstraintMapping getWorkflowTaskConstraint(final HibernateValidatorConfiguration configuration) {
        ConstraintMapping mapping = configuration.createConstraintMapping();

        mapping.type(WorkflowTask.class)
                .constraint(new WorkflowTaskTypeConstraintDef())
                .constraint(new WorkflowTaskValidConstraintDef());

        return mapping;
    }

}
