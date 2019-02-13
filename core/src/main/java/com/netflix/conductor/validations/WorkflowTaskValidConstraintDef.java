package com.netflix.conductor.validations;

import org.hibernate.validator.cfg.ConstraintDef;

public class WorkflowTaskValidConstraintDef extends ConstraintDef<WorkflowTaskValidConstraintDef, WorkflowTaskValidConstraint> {

    public WorkflowTaskValidConstraintDef() {
        super(WorkflowTaskValidConstraint.class);
    }

}
