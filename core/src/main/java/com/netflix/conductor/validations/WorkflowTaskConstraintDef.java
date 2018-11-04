package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class WorkflowTaskConstraintDef extends ConstraintDef<WorkflowTaskConstraintDef, WorkflowTaskConstraint> {

    public WorkflowTaskConstraintDef() {
        super( WorkflowTaskConstraint.class );
    }

}