package com.netflix.conductor.validations;

import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;

import static java.lang.annotation.ElementType.FIELD;

public class TaskDefConstraint {
    @Singleton
    @ProvidesIntoSet
    public static ConstraintMapping getWorkflowTaskConstraint(final HibernateValidatorConfiguration configuration) {
        ConstraintMapping mapping = configuration.createConstraintMapping();

        mapping.type(WorkflowTask.class)
                .constraint(new WorkflowTaskConstraintDef())
                .property("name", FIELD)
                    .constraint(new CheckTaskDefExistsConstraintDef());

        return mapping;
    }

    @Singleton
    @ProvidesIntoSet
    public static ConstraintMapping getWorkflowDefConstraint(final HibernateValidatorConfiguration configuration) {
        ConstraintMapping mapping = configuration.createConstraintMapping();

        mapping.type(WorkflowDef.class)
                .constraint(new WorkflowDefConstraintDef());

        return mapping;
    }

}
