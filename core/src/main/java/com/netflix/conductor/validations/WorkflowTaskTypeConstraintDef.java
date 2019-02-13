package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class WorkflowTaskTypeConstraintDef extends ConstraintDef<WorkflowTaskTypeConstraintDef, WorkflowTaskTypeConstraint> {

    public WorkflowTaskTypeConstraintDef() {
        super( WorkflowTaskTypeConstraint.class );
    }

}
